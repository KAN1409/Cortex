#!/data/data/com.termux/files/usr/bin/bash
set -u
# CORTEX_DEVBRIDGE_WATCHDOG_V1
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
POLL="${CORTEX_DEVBRIDGE_WATCHDOG_SECONDS:-45}"
SUP="$ROOT/supervisor.sh"
PIDFILE="$ROOT/supervisor.pid"
WDPID="$ROOT/watchdog.pid"
mkdir -p "$ROOT/logs"
echo $$ > "$WDPID"
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
while true; do
  date -u +%Y-%m-%dT%H:%M:%SZ > "$ROOT/watchdog.heartbeat"
  pid="$(cat "$PIDFILE" 2>/dev/null || true)"
  alive=0
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    args="$(ps -p "$pid" -o args= 2>/dev/null || true)"
    printf '%s' "$args" | grep -Fq "$SUP" && alive=1
  fi
  if [ "$alive" != 1 ] && [ -x "$SUP" ]; then
    nohup "$SUP" >> "$ROOT/supervisor.stdout.log" 2>> "$ROOT/supervisor.stderr.log" < /dev/null &
    echo $! > "$PIDFILE"
    printf '%s supervisor_restart pid=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(cat "$PIDFILE" 2>/dev/null || true)" >> "$ROOT/watchdog.log"
  fi
  sleep "$POLL"
done
