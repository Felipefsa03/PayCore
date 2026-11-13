"""Run with the risk-engine virtualenv after building Java/Nest and installing Composer dependencies."""
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import sys
import tempfile
import time
import uuid
from contextlib import ExitStack
from datetime import datetime, timezone

import httpx

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ROOT / "services"
PHP = SERVICES / "reconciliation"


def local_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def encode(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def token(owner, secret):
    now = int(time.time())
    head = encode(b'{"alg":"HS256","typ":"JWT"}')
    body = encode(json.dumps(dict(sub=owner, iss="paycore-lab", iat=now, exp=now + 120)).encode())
    signed = f"{head}.{body}"
    return signed + "." + encode(hmac.digest(secret.encode(), signed.encode(), hashlib.sha256))


def stop(proc):
    if proc.poll() is None:
        os.killpg(proc.pid, signal.SIGTERM)
        proc.wait(timeout=15)


def main():
    # The caller supplies fresh disposable PostgreSQL databases for Java and Nest.
    for name in ("DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD", "PAYCORE_INTEGRATION_DB_URL"):
        if not os.environ.get(name):
            raise SystemExit(f"Missing laboratory setting {name}")
    with tempfile.TemporaryDirectory() as tmp, ExitStack() as stack, httpx.Client(timeout=10) as http:
        def launch(directory, args, env, ready):
            log = stack.enter_context(tempfile.TemporaryFile())
            process = subprocess.Popen(args, cwd=directory, env=os.environ | env, stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            stack.callback(stop, process)
            for _ in range(180):
                if process.poll() is not None:
                    raise RuntimeError(f"{args[0]} exited with {process.returncode}")
                try:
                    if http.get(ready).status_code < 500:
                        return process
                except httpx.TransportError:
                    pass
                time.sleep(0.25)
            raise RuntimeError(f"Service did not start: {ready}")

        owner = str(uuid.uuid4())
        secret, key = secrets.token_hex(32), secrets.token_hex(24)
        rp, jp, np, lp = (local_port() for _ in range(4))
        risk, core, nest, laravel = [f"http://127.0.0.1:{port}" for port in (rp, jp, np, lp)]
        def start_engine():
            return launch(SERVICES / "risk-engine", [sys.executable, "-m", "uvicorn", "app.main:app",
                          "--port", str(rp)], {}, risk + "/health")

        engine = start_engine()
        launch(ROOT, ["java", "-jar", "services/financial-core/app/target/app-0.1.0-SNAPSHOT.jar"],
               dict(SERVER_PORT=str(jp), PAYCORE_RISK_ENABLED="true", PAYCORE_RISK_URL=risk, PAYCORE_RISK_TIMEOUT_MS="2000",
                    PAYCORE_AUTH_JWT_SECRET=secret), core + "/actuator/health")
        launch(SERVICES / "integration-api", ["node", "dist/main.js"], dict(PORT=str(np),
               PAYCORE_AUTH_JWT_SECRET=secret, PAYCORE_FINANCIAL_CORE_URL=core,
               PAYCORE_PARTNERS=json.dumps([dict(id="m4", name="M4", apiKey=key, ownerId=owner)])),
               nest + "/health")
        db = Path(tmp) / "demo.sqlite"
        db.touch()
        env = dict(DB_CONNECTION="sqlite", DB_DATABASE=str(db), APP_ENV="testing",
                   APP_KEY="base64:" + base64.b64encode(secrets.token_bytes(32)).decode(),
                   QUEUE_CONNECTION="sync", RECONCILIATION_API_KEY=key,
                   RECONCILIATION_INTEGRATION_URL=nest, RECONCILIATION_PARTNER_KEY=key)
        def artisan(*args):
            subprocess.run(["php", "artisan", *args], cwd=PHP, env=os.environ | env, check=True)

        artisan("migrate", "--force", "--quiet")
        launch(PHP, ["php", "-S", f"127.0.0.1:{lp}", "-t", "public", "public/index.php"], env, laravel + "/up")
        auth = {"Authorization": "Bearer " + token(owner, secret)}
        partner = {"X-Api-Key": key, "Accept": "application/json"}
        def account():
            return http.post(core + "/accounts", headers=auth,
                             json=dict(currency="BRL", overdraftPolicy="allowed")).raise_for_status().json()["id"]

        src, dst = account(), account()
        def pay(amount, idem):
            return http.post(core + "/transfers", headers=auth | {"Idempotency-Key": idem},
                             json=dict(sourceAccountId=src, destinationAccountId=dst,
                                       currency="BRL", amount=amount)).raise_for_status().json()

        idem = str(uuid.uuid4())
        payment = pay(1000, idem)
        ref = payment["id"]
        assert payment["state"] == "COMPLETED" and pay(1000, idem)["id"] == ref, payment
        assert pay(100000, str(uuid.uuid4()))["failureReason"] == "risk_review_required"
        def postings(account_id):
            return http.get(core + "/accounts/" + account_id + "/postings", headers=auth).raise_for_status().json()

        baseline = postings(src)
        stop(engine)
        for _ in range(3):
            failed = pay(1000, str(uuid.uuid4()))
            assert failed["failureReason"] == "risk_unavailable" and failed["journalEntryId"] is None
        restored_at = time.monotonic()
        start_engine()
        # Recovery of the dependency alone must not bypass the open breaker.
        open_circuit = pay(1000, str(uuid.uuid4()))
        assert open_circuit["failureReason"] == "risk_unavailable"
        assert baseline == postings(src)
        time.sleep(5.1)
        # Use a fresh source so that velocity rules do not mask circuit recovery.
        original_src = src
        src = account()
        recovered = pay(1000, str(uuid.uuid4()))
        assert recovered["state"] == "COMPLETED" and recovered["journalEntryId"]
        recovery_seconds = round(time.monotonic() - restored_at, 3)
        src = original_src
        view = http.get(nest + "/v1/transactions/" + ref, headers=partner).raise_for_status().json()
        assert view["journalEntryId"] == payment["journalEntryId"]
        artisan("reconciliation:sync", ref)
        def ledger():
            return [http.get(core + "/accounts/" + a + "/postings", headers=auth).raise_for_status().json()
                    for a in (src, dst)]
        before = ledger()
        detected = time.monotonic()
        csv = f"reference,amount_cents,currency,status,occurred_at\n{ref},1100,BRL,settled,{view['updatedAt']}\n"
        def upload():
            result = http.post(laravel + "/api/statement-imports", headers=partner,
                               files={"statement": ("statement.csv", csv, "text/csv")}).raise_for_status().json()
            return result.get("data", result)["id"]
        imp = upload()
        assert upload() == imp
        rows = http.get(f"{laravel}/api/statement-imports/{imp}/divergences", headers=partner).raise_for_status().json()["data"]
        assert len(rows) == 1 and rows[0]["type"] == "mismatch"
        uri = f"{laravel}/api/divergences/{rows[0]['id']}/decisions"
        for status, reason in [("investigating", "Extrato excede o valor contabilizado em 100 centavos"),
                               ("resolved", "Erro introduzido no extrato de laboratório; lançamento original confirmado")]:
            http.post(uri, headers=partner, json=dict(status=status, operator_reference="demo-m4",
                                                      reason=reason)).raise_for_status()
        assert len(http.get(uri, headers=partner).raise_for_status().json()) == 2
        day = datetime.now(timezone.utc).date().isoformat()
        report = http.get(laravel + "/api/reconciliation-report", params={"from":day,"to":day}, headers=partner)
        assert report.raise_for_status().json()["by_status"]["resolved"] == 1
        final_ledger = ledger()
        decisions = len(http.get(uri, headers=partner).raise_for_status().json())
        assert before == final_ledger
        print(json.dumps(dict(transfer=ref, journal=payment["journalEntryId"], correlation=payment["correlationId"],
                              decisions=decisions, ledger_preserved=before == final_ledger,
                              risk_recovery_seconds=recovery_seconds, incident_seconds=round(time.monotonic()-detected, 3))))

if __name__ == "__main__":
    main()
