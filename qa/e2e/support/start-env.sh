#!/usr/bin/env bash
# Levanta el entorno local para las pruebas E2E: PostgreSQL (Docker), simulador de Supabase Auth,
# backend (jar ya construido) y front (Vite). Logs y PIDs en qa/e2e/.env-run/.
# Variables opcionales: JAR, E2E_JAVA_HOME (JDK 21+), NOTICE_POLL_MS (2000: avisos de reserva cada 2 s), PG_PORT, STUB_PORT, APP_PORT, FRONT_PORT.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
E2E="$(cd "$HERE/.." && pwd)"
REPO="$(cd "$E2E/../.." && pwd)"
RUN="$E2E/.env-run"; mkdir -p "$RUN"
PG_PORT=${PG_PORT:-15711}; STUB_PORT=${STUB_PORT:-19911}; APP_PORT=${APP_PORT:-18311}; FRONT_PORT=${FRONT_PORT:-5181}
JAR=${JAR:-$REPO/target/bookingplatform-0.0.1-SNAPSHOT.jar}
PG_NAME=${PG_NAME:-s3qa-e2e-pg}

wait_http() { # url etiqueta segundos
  for _ in $(seq 1 "$3"); do
    c=$(curl -s -o /dev/null -w '%{http_code}' "$1" || true); [ "$c" != 000 ] && { echo "$2 arriba ($c)"; return 0; }
    sleep 1
  done; echo "$2 no arrancó"; return 1
}

echo "== PostgreSQL :$PG_PORT"
docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=reservas -p "$PG_PORT:5432" postgres:16 >/dev/null
for _ in $(seq 1 60); do docker exec "$PG_NAME" pg_isready -U postgres -d reservas >/dev/null 2>&1 && break; sleep 1; done
sleep 2

echo "== Simulador Supabase Auth :$STUB_PORT"
nohup python "$HERE/stub/gotrue_stub.py" "$STUB_PORT" "http://127.0.0.1:$STUB_PORT" > "$RUN/stub.log" 2>&1 &
echo $! > "$RUN/stub.pid"
wait_http "http://127.0.0.1:$STUB_PORT/auth/v1/.well-known/jwks.json" stub 20

echo "== Backend :$APP_PORT ($JAR)"
[ -f "$JAR" ] || { echo "No existe $JAR (ejecuta ./mvnw -DskipTests package o define JAR=...)"; exit 1; }
export JAVA_HOME="${E2E_JAVA_HOME:-/c/Program Files/Java/jdk-22.0.2}"; export PATH="$JAVA_HOME/bin:$PATH"  # JDK 21+
SPRING_PROFILES_ACTIVE=cloud PORT=$APP_PORT \
DATABASE_URL="jdbc:postgresql://localhost:$PG_PORT/reservas" DATABASE_USER=postgres DATABASE_PASSWORD=postgres \
SUPABASE_URL="http://127.0.0.1:$STUB_PORT" SUPABASE_SECRET_KEY=sb_secret_e2e \
  nohup java -Dapp.booking-notices.poll-ms="${NOTICE_POLL_MS:-2000}" -jar "$JAR" > "$RUN/app.log" 2>&1 &
echo $! > "$RUN/app.pid"
for _ in $(seq 1 120); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$APP_PORT/actuator/health")" = 200 ] && { echo "backend arriba"; break; }
  sleep 1
done
[ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$APP_PORT/actuator/health")" = 200 ] || { tail -40 "$RUN/app.log"; exit 1; }

echo "== Front (Vite) :$FRONT_PORT"
cd "$REPO/front"
[ -d node_modules ] || npm ci --no-audit --no-fund
VITE_API_TARGET="http://localhost:$APP_PORT" nohup npx vite --port "$FRONT_PORT" --strictPort --host 127.0.0.1 > "$RUN/front.log" 2>&1 &
echo $! > "$RUN/front.pid"
wait_http "http://127.0.0.1:$FRONT_PORT/" front 60
echo "Entorno listo: front http://127.0.0.1:$FRONT_PORT  api http://localhost:$APP_PORT  stub http://127.0.0.1:$STUB_PORT"
