#!/usr/bin/env bash
# Detiene lo que levantó start-env.sh y borra el contenedor de PostgreSQL.
HERE="$(cd "$(dirname "$0")" && pwd)"; RUN="$HERE/../.env-run"
APP_PORT=${APP_PORT:-18311}; STUB_PORT=${STUB_PORT:-19911}; FRONT_PORT=${FRONT_PORT:-5181}
kill_port() { # En Windows (Git Bash) nohup deja PIDs de MSYS; se cierra también por puerto.
  if command -v netstat >/dev/null && command -v taskkill >/dev/null; then
    for p in $(netstat -ano 2>/dev/null | grep -E "[:.]$1 .*LISTEN" | awk '{print $NF}' | sort -u); do taskkill //F //T //PID "$p" >/dev/null 2>&1 || true; done
  else
    command -v fuser >/dev/null && fuser -k "$1/tcp" >/dev/null 2>&1 || true
  fi
}
for f in front app stub; do [ -f "$RUN/$f.pid" ] && { kill "$(cat "$RUN/$f.pid")" 2>/dev/null || true; rm -f "$RUN/$f.pid"; }; done
kill_port "$FRONT_PORT"; kill_port "$APP_PORT"; kill_port "$STUB_PORT"
docker rm -f "${PG_NAME:-s3qa-e2e-pg}" >/dev/null 2>&1 || true
echo "Entorno detenido"
