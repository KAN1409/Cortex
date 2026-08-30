#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL="$ROOT/control"
TARGET="$ROOT/agent-v3.runtime.sh"
EXPORTER="$ROOT/cortex-gptoss-exporter.runtime.sh"
RELAY_EXPORTER="$ROOT/relay-c6-exporter.runtime.sh"
RELAY_DIAG="$ROOT/relay-c6-diagnostic.runtime.sh"

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true

if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-cortex-gptoss-exporter.sh" > "$EXPORTER" 2>/dev/null \
   && bash -n "$EXPORTER" >/dev/null 2>&1; then
  chmod 700 "$EXPORTER"
  "$EXPORTER" || true
else
  rm -f "$EXPORTER"
fi

git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-relay-c6-exporter.sh" > "$RELAY_EXPORTER" 2>/dev/null \
   && bash -n "$RELAY_EXPORTER" >/dev/null 2>&1; then
  chmod 700 "$RELAY_EXPORTER"
  "$RELAY_EXPORTER" || true
else
  rm -f "$RELAY_EXPORTER"
fi

git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-relay-c6-diagnostic.sh" > "$RELAY_DIAG" 2>/dev/null \
   && bash -n "$RELAY_DIAG" >/dev/null 2>&1; then
  chmod 700 "$RELAY_DIAG"
  "$RELAY_DIAG" || true
else
  rm -f "$RELAY_DIAG"
fi

git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }
# Android 16/rish can return an unreadable installed APK stream through the shell's bare `cat`.
# Patch only that bounded read to the platform binary before signer verification. No capability or
# package rule is widened, and installation still fails closed on any signer mismatch.
python - "$TARGET" <<'PY'
import sys
p=sys.argv[1]
s=open(p,encoding='utf-8').read()
s=s.replace('rish -c "cat \'$installed_path\'" > "$installed_tmp"',
            'rish -c "/system/bin/cat \'$installed_path\'" > "$installed_tmp"')
open(p,'w',encoding='utf-8').write(s)
PY
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
