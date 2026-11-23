#!/usr/bin/env bash
# Fresh demo world: recreates the paddleup_demo database (the normal paddleup dev DB is untouched),
# restarts the server on it, makes sure the web app runs, then seeds every scenario.
#   scripts/demo/up.sh      rebuild everything
#   scripts/demo/start.sh   start server and web again on the existing demo data
#   scripts/demo/down.sh    stop the server and web
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN="$ROOT/scripts/demo/.run"
DB=paddleup_demo
PG=paddleup-postgres-1
mkdir -p "$RUN"

docker compose -f "$ROOT/compose.yaml" up -d --wait >/dev/null 2>&1

echo "Stopping any server on :8080"
for pid in $(lsof -tiTCP:8080 -sTCP:LISTEN || true); do kill "$pid" || true; done
for _ in $(seq 1 40); do lsof -tiTCP:8080 -sTCP:LISTEN >/dev/null || break; sleep 0.5; done

echo "Recreating database $DB"
docker exec "$PG" psql -U paddleup -d paddleup -qc "DROP DATABASE IF EXISTS $DB WITH (FORCE)" 2>/dev/null
docker exec "$PG" psql -U paddleup -d paddleup -qc "CREATE DATABASE $DB"
rm -f "$RUN/sessions.json" "$RUN/demo.json"

"$ROOT/scripts/demo/start.sh"
node "$ROOT/scripts/demo/seed.mjs"
