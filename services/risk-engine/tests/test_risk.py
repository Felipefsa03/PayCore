from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def base_payload(**overrides):
    payload = {
        "payment_id": "pay-1",
        "account_id": "acc-1",
        "amount_cents": 1000,
        "currency": "BRL",
        "account_age_days": 30,
        "transactions_last_24h": 1,
        "country_mismatch": False,
        "device_trusted": True,
        "rule_version": "1.0.0",
    }
    payload.update(overrides)
    return payload


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_approve_low_risk():
    response = client.post("/risk/evaluate", json=base_payload())
    assert response.status_code == 200
    data = response.json()
    assert data["decision"] == "APPROVE"
    assert data["score"] == 0
    assert data["reasons"] == []


def test_reject_high_risk():
    response = client.post(
        "/risk/evaluate",
        json=base_payload(
            amount_cents=200_000,
            transactions_last_24h=10,
            account_age_days=1,
            country_mismatch=True,
            device_trusted=False,
        ),
    )
    assert response.status_code == 200
    data = response.json()
    assert data["decision"] == "REJECT"
    assert data["score"] >= 70
    assert {reason["code"] for reason in data["reasons"]} == {
        "HIGH_AMOUNT",
        "HIGH_FREQUENCY",
        "NEW_ACCOUNT",
        "COUNTRY_MISMATCH",
        "UNTRUSTED_DEVICE",
    }


def test_review_medium_risk():
    response = client.post(
        "/risk/evaluate",
        json=base_payload(amount_cents=200_000, account_age_days=1),
    )
    assert response.status_code == 200
    assert response.json()["decision"] == "REVIEW"


def test_deterministic_same_input_and_version():
    payload = base_payload(amount_cents=200_000, country_mismatch=True)
    first = client.post("/risk/evaluate", json=payload).json()
    second = client.post("/risk/evaluate", json=payload).json()
    assert first == second


def test_unknown_rule_version_returns_400():
    response = client.post("/risk/evaluate", json=base_payload(rule_version="9.9.9"))
    assert response.status_code == 400
