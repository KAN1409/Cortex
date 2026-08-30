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
# Android 16: copy the installed package to a short-lived shared probe through the privileged
# shell before certificate parsing. This avoids rish stdout transport quirks while preserving the
# existing fail-closed signer equality gate. The probe is deleted immediately after reading.
python - "$TARGET" <<'PY'
import sys
p=sys.argv[1]
s=open(p,encoding='utf-8').read()
old1='  rish -c "cat \'$installed_path\'" > "$installed_tmp" 2>/dev/null || { rm -f "$installed_tmp"; echo INSTALLED_APK_COPY_FAIL; return 26; }'
old2='  rish -c "/system/bin/cat \'$installed_path\'" > "$installed_tmp" 2>/dev/null || { rm -f "$installed_tmp"; echo INSTALLED_APK_COPY_FAIL; return 26; }'
new='''  installed_probe="/sdcard/Download/.cortex-devbridge-installed-probe.apk"
  rish -c "rm -f '$installed_probe'; /system/bin/cp '$installed_path' '$installed_probe'; /system/bin/chmod 644 '$installed_probe'" >/dev/null 2>&1 || { echo INSTALLED_APK_COPY_FAIL; return 26; }
  cp -f "$installed_probe" "$installed_tmp" 2>/dev/null || { rish -c "rm -f '$installed_probe'" >/dev/null 2>&1 || true; rm -f "$installed_tmp"; echo INSTALLED_APK_COPY_FAIL; return 26; }
  rish -c "rm -f '$installed_probe'" >/dev/null 2>&1 || true
  [ -s "$installed_tmp" ] || { rm -f "$installed_tmp"; echo INSTALLED_APK_COPY_FAIL; return 26; }'''
if old1 in s:
    s=s.replace(old1,new)
elif old2 in s:
    s=s.replace(old2,new)
else:
    raise SystemExit('DEVBRIDGE_INSTALLED_COPY_PATCH_TARGET_MISSING')
open(p,'w',encoding='utf-8').write(s)
PY
[ $? -eq 0 ] || { rm -f "$TARGET"; echo DEVBRIDGE_V3_PATCH_FAILED >&2; exit 73; }
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
