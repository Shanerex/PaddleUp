#!/usr/bin/env bash
# Starts the demo server (on the paddleup_demo database) and the web app, detached, if they are not
# already running. Keeps the data: use up.sh for a fresh world.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN="$ROOT/scripts/demo/.run"
DB=paddleup_demo
mkdir -p "$RUN"

docker compose -f "$ROOT/compose.yaml" up -d --wait >/dev/null 2>&1

if ! curl -sf http://localhost:8080/actuator/health >/dev/null; then
  echo "Starting server (log: $RUN/server.log)"
  (
    cd "$ROOT/server"
    set -a; [ -f .env.local ] && . ./.env.local; set +a
    export SPRING_PROFILES_ACTIVE=dev
    export DATASOURCE_URL="jdbc:postgresql://localhost:5432/$DB"
    export PADDLEUP_ADMIN_EMAILS="cara@paddleup.test"
    export SPRING_APPLICATION_JSON='{"paddleup":{"auth":{"throttle":{"start-per-email":100,"start-per-ip":1000,"failed-per-ip":1000}}}}'
    nohup ./mvnw -q spring-boot:run </dev/null >"$RUN/server.log" 2>&1 &
  ) </dev/null >/dev/null 2>&1
  for i in $(seq 1 180); do
    curl -sf http://localhost:8080/actuator/health >/dev/null && break
    [ "$i" = 180 ] && { echo "Server did not come up; see $RUN/server.log"; exit 1; }
    sleep 1
  done
fi
echo "Server up at http://localhost:8080"

if ! lsof -tiTCP:3000 -sTCP:LISTEN >/dev/null; then
  echo "Starting web (log: $RUN/web.log)"
  (cd "$ROOT" && make -s web-install >/dev/null)
  (cd "$ROOT/web" && nohup npm run dev </dev/null >"$RUN/web.log" 2>&1 &) </dev/null >/dev/null 2>&1
  for _ in $(seq 1 120); do curl -sf -o /dev/null http://localhost:3000 && break; sleep 1; done
fi
echo "Web up at http://localhost:3000"
