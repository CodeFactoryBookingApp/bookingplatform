#!/usr/bin/env bash
# Local stack for the end-to-end suites (docs/qa/e2e.md).
#
#   scripts/e2e/stack.sh up     start Supabase (Auth + Postgres + Mailpit) and the backend on :8080
#   scripts/e2e/stack.sh down   stop the backend and Supabase, dropping the local data
#   scripts/e2e/stack.sh env    print the variables the suites read, as `export` lines
#
# Auth is the real GoTrue from the Supabase CLI. It signs tokens with an RS256 key so the backend
# validates them through the JWKS endpoint exactly as it does in production (ADR-0003). Emails never
# leave the machine: Mailpit captures them and the suites read the verification and recovery links
# from its API.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="$ROOT/target/e2e"
PID_FILE="$STATE_DIR/backend.pid"
LOG_FILE="$STATE_DIR/backend.log"
SIGNING_KEYS="$ROOT/supabase/signing_keys.json"
BACKEND_PORT="${BACKEND_PORT:-8080}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-180}"

cd "$ROOT"

supabase_value() {
  supabase status -o env 2>/dev/null | sed -n "s/^$1=\"\{0,1\}\([^\"]*\)\"\{0,1\}$/\1/p"
}

print_env() {
  local api_url
  api_url="$(supabase_value API_URL)"
  echo "export SUPABASE_URL=$api_url"
  echo "export SUPABASE_SECRET_KEY=$(supabase_value SECRET_KEY)"
  echo "export MAILPIT_URL=$(supabase_value MAILPIT_URL)"
  echo "export E2E_DB_URL=$(supabase_value DB_URL)"
  echo "export E2E_API_URL=http://localhost:$BACKEND_PORT"
}

ensure_signing_key() {
  if [[ ! -s "$SIGNING_KEYS" ]]; then
    echo "Generating the local RS256 signing key ($SIGNING_KEYS)"
    echo '[]' > "$SIGNING_KEYS"
    supabase gen signing-key --algorithm RS256 >/dev/null
  fi
}

backend_running() {
  [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null
}

start_backend() {
  if backend_running; then
    echo "Backend already running (pid $(cat "$PID_FILE"))"
    return
  fi
  local jar
  jar="$(ls target/bookingplatform-*.jar 2>/dev/null | grep -v plain | head -1 || true)"
  # Rebuild when there is no jar or the sources changed after it was packaged: a stale jar would
  # make the suites test code that is no longer the one on disk.
  if [[ -z "$jar" || "${E2E_REBUILD:-false}" == "true" ]] \
      || [[ -n "$(find src/main pom.xml -newer "$jar" -print -quit)" ]]; then
    echo "Packaging the backend"
    ./mvnw -q -DskipTests -Djacoco.skip=true package
    jar="$(ls target/bookingplatform-*.jar | grep -v plain | head -1)"
  fi

  local db_url
  db_url="$(supabase_value DB_URL)"
  mkdir -p "$STATE_DIR"
  echo "Starting $jar on :$BACKEND_PORT (log: $LOG_FILE)"
  DATABASE_URL="jdbc:postgresql://${db_url#postgresql://postgres:postgres@}" \
  DATABASE_USER=postgres \
  DATABASE_PASSWORD=postgres \
  SUPABASE_URL="$(supabase_value API_URL)" \
  SUPABASE_SECRET_KEY="$(supabase_value SECRET_KEY)" \
  PORT="$BACKEND_PORT" \
    nohup java -jar "$jar" > "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"

  local waited=0
  until curl -sf "http://localhost:$BACKEND_PORT/actuator/health" >/dev/null; do
    if ! backend_running; then
      echo "Backend exited during startup:" >&2
      tail -40 "$LOG_FILE" >&2
      exit 1
    fi
    if (( waited >= HEALTH_TIMEOUT_SECONDS )); then
      echo "Backend not healthy after ${HEALTH_TIMEOUT_SECONDS}s" >&2
      tail -40 "$LOG_FILE" >&2
      exit 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
  echo "Backend healthy on http://localhost:$BACKEND_PORT"
}

stop_backend() {
  if backend_running; then
    kill "$(cat "$PID_FILE")"
    echo "Backend stopped"
  fi
  rm -f "$PID_FILE"
}

case "${1:-}" in
  up)
    ensure_signing_key
    supabase start
    start_backend
    ;;
  down)
    stop_backend
    supabase stop --no-backup
    ;;
  env)
    print_env
    ;;
  *)
    echo "usage: $0 up|down|env" >&2
    exit 2
    ;;
esac
