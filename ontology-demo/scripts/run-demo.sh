#!/usr/bin/env bash
# Launch full WMS Adapter Demo: 2 mock WMS + ontology backend + AI sidecar + Vite frontend.
# Usage: bash ontology-demo/scripts/run-demo.sh
# Stop: bash ontology-demo/scripts/run-demo.sh stop
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$ROOT/target/demo-logs"
PID_DIR="$ROOT/target/demo-pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

MVN_FLAGS="${MVN_FLAGS:-}"

start_one() {
  local name="$1"
  local cmd="$2"
  local logfile="$LOG_DIR/$name.log"
  local pidfile="$PID_DIR/$name.pid"
  echo "[run-demo] Starting $name — log: $logfile"
  bash -lc "$cmd" >"$logfile" 2>&1 &
  echo $! >"$pidfile"
}

stop_all() {
  for pidfile in "$PID_DIR"/*.pid; do
    [ -f "$pidfile" ] || continue
    local pid
    pid="$(cat "$pidfile")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "[run-demo] stopping $(basename "$pidfile" .pid) (pid=$pid)"
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$pidfile"
  done
}

case "${1:-start}" in
  stop)
    stop_all
    exit 0
    ;;
  start)
    stop_all
    ;;
  *)
    echo "Usage: $0 [start|stop]"
    exit 1
    ;;
esac

# 1. WDT mock (port 9001)
start_one "wdt-mock" \
  "cd '$ROOT/mock-wms/wangdiantong-mock' && mvn $MVN_FLAGS spring-boot:run"

# 2. JST mock (port 9002)
start_one "jst-mock" \
  "cd '$ROOT/mock-wms/jushuitan-mock' && mvn $MVN_FLAGS spring-boot:run"

# 3. Ontology backend (port 8080)
start_one "ontology-backend" \
  "cd '$ROOT' && mvn $MVN_FLAGS spring-boot:run"

# 4. Python AI sidecar (port 8421) — only if dir exists
if [ -d "$ROOT/ai-sidecar" ] && [ -f "$ROOT/ai-sidecar/server.py" ]; then
  start_one "ai-sidecar" \
    "cd '$ROOT/ai-sidecar' && python3 server.py"
fi

# 5. Vite frontend (port 5173)
if [ -d "$ROOT/frontend" ]; then
  start_one "frontend" \
    "cd '$ROOT/frontend' && npm run dev"
fi

echo
echo "[run-demo] All services starting in background."
echo "[run-demo] Logs: $LOG_DIR"
echo "[run-demo] PIDs: $PID_DIR"
echo
echo "When ready (30-60s):"
echo "  - WDT mock:   http://localhost:9001/h2-console"
echo "  - JST mock:   http://localhost:9002/h2-console"
echo "  - Backend:    http://localhost:8080/api/sources"
echo "  - Frontend:   http://localhost:5173/sources"
echo
echo "Stop all:"
echo "  bash ontology-demo/scripts/run-demo.sh stop"
