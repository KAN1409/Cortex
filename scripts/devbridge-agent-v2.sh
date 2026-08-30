#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL="$ROOT/control"
TARGET="$ROOT/agent-v3.runtime.sh"
RELAY_C8_RETRY="$ROOT/relay-c8-install-retry.runtime.sh"

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true

# One bounded background-only install retry. No activity launch, no uninstall and no app-data clear.
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-relay-c8-install-retry.sh" > "$RELAY_C8_RETRY" 2>/dev/null \
   && bash -n "$RELAY_C8_RETRY" >/dev/null 2>&1; then
  chmod 700 "$RELAY_C8_RETRY"
  "$RELAY_C8_RETRY" || true
else
  rm -f "$RELAY_C8_RETRY"
fi

git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
