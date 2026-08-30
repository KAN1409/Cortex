#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL="$ROOT/control"
TARGET="$ROOT/agent-v3.runtime.sh"
EXPORTER="$ROOT/cortex-gptoss-exporter.runtime.sh"
RELAY_EXPORTER="$ROOT/relay-c6-exporter.runtime.sh"

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true

# Fast bounded one-shot: publish the already-built 0.7.2 APK. It never installs/uninstalls anything.
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-cortex-gptoss-exporter.sh" > "$EXPORTER" 2>/dev/null \
   && bash -n "$EXPORTER" >/dev/null 2>&1; then
  chmod 700 "$EXPORTER"
  "$EXPORTER" || true
else
  rm -f "$EXPORTER"
fi

# Fast bounded one-shot: expose the already signed Relay candidate6 APK as a direct artifact.
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-relay-c6-exporter.sh" > "$RELAY_EXPORTER" 2>/dev/null \
   && bash -n "$RELAY_EXPORTER" >/dev/null 2>&1; then
  chmod 700 "$RELAY_EXPORTER"
  "$RELAY_EXPORTER" || true
else
  rm -f "$RELAY_EXPORTER"
fi

git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
