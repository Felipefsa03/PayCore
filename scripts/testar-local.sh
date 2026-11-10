#!/usr/bin/env bash
# Executa o cenário M4 usando um PostgreSQL descartável e quatro serviços reais.
set -euo pipefail
cd "$(dirname "$0")/.."
for tool in docker java npm php composer python3 make; do
  command -v "$tool" >/dev/null || { echo "Instale $tool para continuar." >&2; exit 1; }
done
docker info >/dev/null
lab=$(mktemp -d)
container=
cleanup() {
  if [[ -n "$container" ]]; then docker rm -fv "$container" >/dev/null; fi
  rm -rf -- "$lab"
}
trap cleanup EXIT
password=$(python3 -c 'import secrets;print(secrets.token_hex(24))')
container=$(docker run -d -p 127.0.0.1::5432 -e POSTGRES_DB=paycore -e POSTGRES_USER=paycore \
  -e POSTGRES_PASSWORD="$password" postgres:16-alpine)
for ((i=0;i<60;i++)); do
  if docker exec "$container" pg_isready -h 127.0.0.1 -U paycore -d paycore >/dev/null; then break; fi
  sleep 1
done
docker exec "$container" pg_isready -h 127.0.0.1 -U paycore -d paycore
binding=$(docker port "$container" 5432/tcp)
port=${binding##*:}
export DATABASE_USER=paycore DATABASE_PASSWORD="$password"
export DATABASE_URL="jdbc:postgresql://127.0.0.1:$port/paycore"
export PAYCORE_INTEGRATION_DB_URL="postgresql://paycore:$password@127.0.0.1:$port/paycore"
python3 -m venv "$lab/venv"
"$lab/venv/bin/pip" install -q -r services/risk-engine/requirements.txt
make build integration-api-install
(cd services/integration-api && npm run build)
(cd services/reconciliation && composer install --no-interaction --prefer-dist)
"$lab/venv/bin/python" scripts/demo-m4.py
