#!/usr/bin/env bash
# Stops the server and web that up.sh started. The paddleup_demo database stays until the next up.sh.
RUN="$(cd "$(dirname "$0")" && pwd)/.run"
for port in 8080 3000; do
  for pid in $(lsof -tiTCP:$port -sTCP:LISTEN || true); do kill "$pid" && echo "stopped :$port ($pid)"; done
done
rm -f "$RUN/server.pid" "$RUN/web.pid"
