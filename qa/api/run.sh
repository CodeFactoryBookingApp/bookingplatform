#!/usr/bin/env bash
# Ejecuta la suite de API con Newman y deja los reportes en qa/api/reports/.
# Uso: ./run.sh [iteraciones]      (por defecto 3)
# Variables opcionales: BASE_URL (http://localhost:18321/api/v1), STUB_URL (http://127.0.0.1:19921)
set -euo pipefail
cd "$(dirname "$0")"
ITER="${1:-3}"
BASE_URL="${BASE_URL:-http://localhost:18321/api/v1}"
STUB_URL="${STUB_URL:-http://127.0.0.1:19921}"
HEALTH="${BASE_URL%/api/v1}/actuator/health"

curl -fs "$HEALTH" >/dev/null || { echo "La API no responde en $HEALTH"; exit 2; }
curl -fs "$STUB_URL/auth/v1/.well-known/jwks.json" >/dev/null || { echo "El simulador de Auth no responde en $STUB_URL"; exit 2; }
[ -d node_modules/newman ] || npm ci --no-fund --no-audit

mkdir -p reports
STAMP="$(date +%Y%m%d-%H%M%S)"
npx newman run bookingplatform-sprint3.postman_collection.json \
  -e local.postman_environment.json \
  --env-var "baseUrl=$BASE_URL" --env-var "stubUrl=$STUB_URL" \
  -n "$ITER" --timeout-request 10000 \
  -r cli,htmlextra,json,junit \
  --reporter-htmlextra-export "reports/api-report-$STAMP.html" \
  --reporter-htmlextra-title "bookingplatform - Pruebas de API Sprint 3" \
  --reporter-htmlextra-browserTitle "API Sprint 3" \
  --reporter-json-export "reports/api-report-$STAMP.json" \
  --reporter-junit-export "reports/junit-$STAMP.xml"
