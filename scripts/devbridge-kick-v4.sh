#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
SUPERVISOR="$ROOT/supervisor.sh"
AGENT="$ROOT/manual-agent-once.sh"

[ -d "$LOCAL_REPO/.git" ] || { echo DEVBRIDGE_LOCAL_REPO_MISSING; exit 70; }
command -v rish >/dev/null 2>&1 || { echo RISH_MISSING; exit 71; }
rish -c id >/dev/null 2>&1 || { echo RISH_UNAVAILABLE; exit 72; }

# Stop the poller first so it cannot race the one-shot agent for the shared control repo.
if [ -f "$ROOT/supervisor.pid" ]; then
  pid="$(cat "$ROOT/supervisor.pid" 2>/dev/null || true)"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    sleep 1
  fi
fi

# Refresh the control branch through the user's main checkout, then stage the current wrapper.
git -C "$LOCAL_REPO" fetch origin "$CONTROL_BRANCH"
git -C "$LOCAL_REPO" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v2.sh" > "$AGENT"
bash -n "$AGENT"
chmod 700 "$AGENT"

printf 'CORTEX_DEVBRIDGE_PROCESSING_QUEUED_JOBS\n'
"$AGENT" --once || true
printf 'CORTEX_DEVBRIDGE_ONE_SHOT_DONE\n'

# Restore continuous polling after the one-shot finishes.
if [ -x "$SUPERVISOR" ]; then
  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
  nohup "$SUPERVISOR" >> "$ROOT/supervisor.stdout.log" 2>> "$ROOT/supervisor.stderr.log" < /dev/null &
  echo $! > "$ROOT/supervisor.pid"
  sleep 1
  kill -0 "$(cat "$ROOT/supervisor.pid")" 2>/dev/null || { echo DEVBRIDGE_SUPERVISOR_RESTART_FAILED; exit 73; }
  printf 'CORTEX_DEVBRIDGE_SUPERVISOR_OK pid=%s\n' "$(cat "$ROOT/supervisor.pid")"
else
  echo DEVBRIDGE_SUPERVISOR_SCRIPT_MISSING
  exit 74
fi
