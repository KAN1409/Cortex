#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL="$ROOT/control"
TARGET="$ROOT/agent-v3.runtime.sh"

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }

# Fresh-install extension for the original Cortex package only.
# Existing installs keep the V3 update-in-place signer comparison unchanged. When Cortex is absent,
# the candidate APK must match the permanent on-device Cortex keystore certificate before pm install.
python - "$TARGET" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
needle = '  [ -n "$installed_path" ] || { echo INSTALLED_APK_NOT_FOUND; return 25; }\n'
replacement = r'''  if [ -z "$installed_path" ]; then
    [ "$pkg" = 'com.kareem.cortex' ] || { echo FRESH_INSTALL_DENY_PACKAGE; return 25; }
    [ -f "$SIGNER_SOURCE" ] || { echo SIGNER_SOURCE_NOT_FOUND; return 31; }
    command -v keytool >/dev/null 2>&1 || { echo KEYTOOL_NOT_FOUND; return 32; }
    local source_sha
    source_sha="$(keytool -list -v -keystore "$SIGNER_SOURCE" -storepass android -alias androiddebugkey 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n1 | tr -d ':[:space:]' | tr 'A-F' 'a-f')"
    [ -n "$source_sha" ] || { echo SIGNER_SOURCE_SHA_FAIL; return 33; }
    echo "candidate_signer_sha256=$candidate_sha"
    echo "source_signer_sha256=$source_sha"
    [ "$candidate_sha" = "$source_sha" ] || { echo SIGNER_MISMATCH_REFUSE_FRESH_INSTALL; return 34; }
    staged="/data/local/tmp/cortex-devbridge-$$.apk"
    cat "$apk" | rish -c "cat > '$staged'" >/dev/null 2>&1 || { echo APK_STAGE_FAIL; return 29; }
    out="$(rish -c "pm install '$staged'" 2>&1)"; rc=$?
    rish -c "rm -f '$staged'" >/dev/null 2>&1 || true
    printf '%s\n' "$out"
    [ $rc -eq 0 ] && printf '%s' "$out" | grep -q 'Success' || return 30
    echo "fresh_install=true"
    echo "apk_sha256=$(sha256sum "$apk" | awk '{print $1}')"
    return 0
  fi
'''
if needle not in text:
    raise SystemExit('DEVBRIDGE_FRESH_INSTALL_PATCH_TARGET_MISSING')
text = text.replace(needle, replacement, 1)
text = text.replace(
    'candidate_signer_sha256=|installed_signer_sha256=|apk_sha256=',
    'candidate_signer_sha256=|installed_signer_sha256=|source_signer_sha256=|fresh_install=|apk_sha256=',
    1,
)
path.write_text(text)
PY

bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
