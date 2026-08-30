#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL="$ROOT/control"
TARGET="$ROOT/agent-v3.runtime.sh"
RELAY_C8="$ROOT/relay-c8-finalizer.runtime.sh"

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true

# Candidate8 updater is strictly background-only: it may sign and pm install -r, but contains no
# activity launch or foreground validation. Its per-job local attempt lock prevents supervisor loops.
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-relay-c8-finalizer.sh" > "$RELAY_C8" 2>/dev/null \
   && bash -n "$RELAY_C8" >/dev/null 2>&1; then
  chmod 700 "$RELAY_C8"
  "$RELAY_C8" || true
else
  rm -f "$RELAY_C8"
fi

git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
