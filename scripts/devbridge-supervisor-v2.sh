#!/data/data/com.termux/files/usr/bin/bash
set -u
# CORTEX_DEVBRIDGE_SUPERVISOR_V2
REPO="${CORTEX_DEVBRIDGE_REPO:-KAN1409/Cortex}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
POLL="${CORTEX_DEVBRIDGE_POLL_SECONDS:-20}"
if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/${REPO}.git"
fi
CONTROL="$ROOT/control"
LOCK="$ROOT/fetch.lock"
mkdir -p "$ROOT/logs" "$ROOT/work"
ensure_control(){
  [ -d "$LOCAL_REPO/.git" ] || return 1
  if [ ! -L "$CONTROL" ] || [ "$(readlink "$CONTROL" 2>/dev/null || true)" != "$LOCAL_REPO" ]; then
    rm -rf "$CONTROL"
    ln -s "$LOCAL_REPO" "$CONTROL" || return 1
  fi
}
fetch_control(){
  mkdir "$LOCK" 2>/dev/null || return 1
  git -C "$LOCAL_REPO" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  if command -v timeout >/dev/null 2>&1; then
    timeout 45 git -C "$LOCAL_REPO" fetch --prune origin "$CONTROL_BRANCH" >/dev/null 2>&1
  else
    git -C "$LOCAL_REPO" fetch --prune origin "$CONTROL_BRANCH" >/dev/null 2>&1
  fi
  rc=$?
  rmdir "$LOCK" 2>/dev/null || true
  return $rc
}
while true; do
  if ensure_control && fetch_control; then
    NEXT="$ROOT/agent.next.sh"
    CURRENT="$ROOT/agent.current.sh"
    if git -C "$LOCAL_REPO" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v2.sh" > "$NEXT" 2>/dev/null && bash -n "$NEXT" >/dev/null 2>&1; then
      chmod 700 "$NEXT"
      mv -f "$NEXT" "$CURRENT"
      CORTEX_DEVBRIDGE_SUPERVISOR_V2=1 "$CURRENT" --once >> "$ROOT/agent.stdout.log" 2>> "$ROOT/agent.stderr.log" || true
    else
      rm -f "$NEXT"
    fi
  fi
  sleep "$POLL"
done
