#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL="$ROOT/control"
TARGET="$ROOT/agent-v3.runtime.sh"
RELAY_C7="$ROOT/relay-c7-finalizer.runtime.sh"

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true

# One bounded, idempotent Relay Candidate7 finalization pass. The script is activated only by its
# exact authorized control document, signs with the existing local Relay signer, uses pm install -r,
# and never uninstalls or clears app data.
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-relay-c7-finalizer.sh" > "$RELAY_C7" 2>/dev/null \
   && bash -n "$RELAY_C7" >/dev/null 2>&1; then
  chmod 700 "$RELAY_C7"
  "$RELAY_C7" || true
else
  rm -f "$RELAY_C7"
fi

git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
