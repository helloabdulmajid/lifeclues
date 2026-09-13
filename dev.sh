#!/usr/bin/env bash
# Loads the private .env file and starts the LifeClues backend.
# Usage:
#   ./dev.sh            (starts the app)
#   ./dev.sh test       (runs the automated tests)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

if [[ ! -f .env ]]; then
  echo "Missing .env file. Copy .env.example to .env and fill in your values first."
  exit 1
fi

set -a
source .env
set +a

# Early, friendly checks so problems show up as one clear sentence
# instead of a big Java stack trace.
REQUIRED_KEYS=(LC_DB_URL LC_DB_USER LC_DB_PASSWORD LC_JWT_SECRET)
missing=()
for key in "${REQUIRED_KEYS[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    missing+=("$key")
  fi
done

if [[ "${#missing[@]}" -gt 0 ]]; then
  echo "Missing required value(s) in .env: ${missing[*]}"
  echo "Add them to .env (see .env.example), then run ./dev.sh again."
  exit 1
fi

if [[ "${1:-}" == "test" ]]; then
  ./mvnw test
  exit $?
fi

PORT="${LC_PORT:-8080}"
if (command -v lsof >/dev/null && lsof -iTCP:"$PORT" -sTCP:LISTEN -P -n >/dev/null 2>&1); then
  echo "Port $PORT is already in use — a LifeClues instance may already be running."
  echo "  • If you just want to use it, open:  http://localhost:$PORT"
  echo "  • To restart it, stop the old process first, then run ./dev.sh:"
  echo "      pkill -f LifecluesApplication && ./dev.sh"
  echo "  • Or start this one on a different port:"
  echo "      LC_PORT=18080 ./dev.sh"
  exit 1
fi

echo "Starting LifeClues on http://localhost:$PORT ..."
./mvnw spring-boot:run