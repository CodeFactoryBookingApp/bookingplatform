#!/usr/bin/env bash
# Ejecución local: carga .env a variables de entorno del proceso y arranca la app.
# Uso: ./run-local.sh
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f .env ]; then
  echo "No existe .env. Cópialo desde .env.example y completa tus credenciales." >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source .env
set +a
./mvnw spring-boot:run "$@"
