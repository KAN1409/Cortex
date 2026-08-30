#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PKG="com.kareem.cortex.rebuild"
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
UNSIGNED="${1:-$SELF_DIR/app-release-unsigned.apk}"
FINAL="/sdcard/Download/Cortex-0.7.1-Capture-Policy-permanent.apk"
REPO="${CORTEX_REPO:-$HOME/Cortex}"
RUN53_FP="0ba27a570fe6f2bb6ada4b548da7f3b7192a7ed65585615fb31bb75527bc3991"
RUN55_FP="6e5f0d282b87a8e0145c2d2c47e590bad5d4dbe79ad4ce171a2afe26da4cc47d"

fail() { echo "ERROR: $*" >&2; exit 1; }

[ -f "$UNSIGNED" ] || fail "Unsigned Cortex 0.7.1 APK not found: $UNSIGNED"
command -v rish >/dev/null 2>&1 || fail "rish is required for the device-side package check"

if ! command -v apksigner >/dev/null 2>&1; then
  echo "Installing Termux apksigner helper..."
  pkg install -y apksigner >/dev/null
fi
command -v apksigner >/dev/null 2>&1 || fail "apksigner is unavailable"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

normalize_fp() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -d ':\r\n '; }

apk_fp_python() {
  local apk="$1"
  if ! command -v python >/dev/null 2>&1; then
    echo "Installing Python fallback for APK signer inspection..." >&2
    pkg install -y python >/dev/null
  fi
  python - "$apk" <<'PY'
import hashlib, struct, sys

path = sys.argv[1]
with open(path, 'rb') as f:
    data = f.read()

# Find the ZIP End Of Central Directory and therefore the APK Signing Block footer.
eocd = data.rfind(b'PK\x05\x06', max(0, len(data) - 65557))
if eocd < 0 or eocd + 20 > len(data):
    raise SystemExit(2)
cd_offset = struct.unpack_from('<I', data, eocd + 16)[0]
if cd_offset < 24:
    raise SystemExit(3)
footer = cd_offset - 24
size = struct.unpack_from('<Q', data, footer)[0]
if data[footer + 8:footer + 24] != b'APK Sig Block 42':
    raise SystemExit(4)
start = cd_offset - (size + 8)
if start < 0 or struct.unpack_from('<Q', data, start)[0] != size:
    raise SystemExit(5)

pairs = {}
off = start + 8
while off < footer:
    if off + 8 > footer:
        break
    pair_len = struct.unpack_from('<Q', data, off)[0]
    off += 8
    if pair_len < 4 or off + pair_len > footer:
        break
    block_id = struct.unpack_from('<I', data, off)[0]
    pairs[block_id] = data[off + 4:off + pair_len]
    off += pair_len

def lp(buf, pos):
    if pos + 4 > len(buf):
        raise ValueError('short length prefix')
    n = struct.unpack_from('<I', buf, pos)[0]
    pos += 4
    if pos + n > len(buf):
        raise ValueError('short value')
    return buf[pos:pos+n], pos+n

# Prefer v3.1/v3, then v2. The first certificate in the first signer is the signer cert.
for block_id in (0x1b93ad61, 0xf05368c0, 0x7109871a):
    block = pairs.get(block_id)
    if not block:
        continue
    try:
        signer, _ = lp(block, 0)
        signed_data, _ = lp(signer, 0)
        _, p = lp(signed_data, 0)          # digests
        certs, _ = lp(signed_data, p)      # certificates
        cert, _ = lp(certs, 0)             # first signer certificate DER
        print(hashlib.sha256(cert).hexdigest())
        raise SystemExit(0)
    except (ValueError, struct.error):
        continue
raise SystemExit(6)
PY
}

apk_fp() {
  local apk="$1" fp
  fp="$(apksigner verify --print-certs "$apk" 2>/dev/null \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
    | head -n1 | tr '[:upper:]' '[:lower:]' | tr -d ':\r\n ' || true)"
  if [ -n "$fp" ]; then
    printf '%s\n' "$fp"
    return 0
  fi
  apk_fp_python "$apk" 2>/dev/null || true
}

# Resolve the installed base APK without assuming the Termux/rish shell exposes the `pm`
# wrapper. Some Android/Shizuku combinations allow dumpsys but return nothing for `pm path`.
installed_base_apk() {
  local out base code

  out="$(rish -c "/system/bin/pm path $PKG" 2>/dev/null || true)"
  base="$(printf '%s\n' "$out" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
  if [ -n "$base" ]; then printf '%s\n' "$base"; return 0; fi

  out="$(rish -c "cmd package path $PKG" 2>/dev/null || true)"
  base="$(printf '%s\n' "$out" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
  if [ -n "$base" ]; then printf '%s\n' "$base"; return 0; fi

  code="$(rish -c "dumpsys package $PKG" 2>/dev/null \
    | tr -d '\r' \
    | sed -n 's/^[[:space:]]*codePath=//p' \
    | head -n1)"
  if [ -n "$code" ]; then printf '%s/base.apk\n' "${code%/}"; return 0; fi

  return 1
}

BASE="$(installed_base_apk || true)"
[ -n "$BASE" ] || fail "Could not resolve installed $PKG base APK (dumpsys/pm/cmd all returned no path)"

echo "Resolved installed base APK: $BASE"
rish -c "/system/bin/cat '$BASE'" > "$TMP/installed.apk"
[ -s "$TMP/installed.apk" ] || fail "Could not read installed base APK"

INST_FP="$(normalize_fp "$(apk_fp "$TMP/installed.apk")")"
[ -n "$INST_FP" ] || fail "Could not read installed Cortex signer with apksigner or APK Signing Block parser"

echo "===== INSTALLED CORTEX ====="
rish -c "dumpsys package $PKG" \
  | grep -E 'versionCode=|versionName=|firstInstallTime=|lastUpdateTime=' \
  | head -n8 || true
echo "Installed signer SHA-256: $INST_FP"

if [ "$INST_FP" = "$RUN53_FP" ]; then
  echo "Installed signer matches Cortex CI run #53 disposable debug signer."
elif [ "$INST_FP" = "$RUN55_FP" ]; then
  echo "Installed signer matches Cortex CI run #55 disposable debug signer."
fi

echo
echo "===== SEARCH MATCHING PRIVATE KEY ====="
MATCH_APK=""
MATCH_SOURCE=""
ATTEMPT=0

try_keystore() {
  local ks="$1" label="$2" out fp
  [ -s "$ks" ] || return 1
  ATTEMPT=$((ATTEMPT+1))
  out="$TMP/signed-$ATTEMPT.apk"
  if ! apksigner sign \
      --ks "$ks" \
      --ks-key-alias androiddebugkey \
      --ks-pass pass:android \
      --key-pass pass:android \
      --out "$out" \
      "$UNSIGNED" >/dev/null 2>&1; then
    return 1
  fi
  fp="$(normalize_fp "$(apk_fp "$out")")"
  [ -n "$fp" ] || return 1
  if [ "$fp" = "$INST_FP" ]; then
    MATCH_APK="$out"
    MATCH_SOURCE="$label"
    return 0
  fi
  return 1
}

# First test the local persistent-key locations used by Cortex development.
for ks in \
  "$REPO/app/cortex-debug.keystore" \
  "$HOME/.android/debug.keystore" \
  "$HOME/cortex-debug.keystore" \
  "$SELF_DIR/cortex-debug.keystore"
do
  if try_keystore "$ks" "$ks"; then break; fi
done

# Also test any nearby Android-debug-format keystores without printing their contents.
if [ -z "$MATCH_APK" ]; then
  while IFS= read -r ks; do
    case "$ks" in
      "$REPO/app/cortex-debug.keystore"|"$HOME/.android/debug.keystore"|"$HOME/cortex-debug.keystore"|"$SELF_DIR/cortex-debug.keystore") continue ;;
    esac
    if try_keystore "$ks" "$ks"; then break; fi
  done < <(find "$HOME" -maxdepth 5 -type f \( -name 'cortex-debug.keystore' -o -name 'debug.keystore' -o -name '*.jks' \) 2>/dev/null | sort -u)
fi

# Historical Cortex commits once contained base64-encoded stable debug key material.
# Test every reachable historical blob locally, but never print or persist that key material.
if [ -z "$MATCH_APK" ] && [ -d "$REPO/.git" ]; then
  i=0
  while IFS= read -r blob; do
    [ -n "$blob" ] || continue
    i=$((i+1))
    b64="$TMP/historical-$i.b64"
    ks="$TMP/historical-$i.keystore"
    if git -C "$REPO" cat-file blob "$blob" > "$b64" 2>/dev/null \
       && base64 -d "$b64" > "$ks" 2>/dev/null; then
      if try_keystore "$ks" "historical Cortex signing material ($blob)"; then break; fi
    fi
  done < <(git -C "$REPO" rev-list --objects --all 2>/dev/null \
           | awk '$2 ~ /(^|\/)cortex-debug\.keystore\.b64$/ {print $1}' \
           | sort -u)
fi

if [ -z "$MATCH_APK" ]; then
  echo
  echo "===== SAFE STOP ====="
  echo "No recoverable private key matched the installed signer."
  if [ "$INST_FP" = "$RUN53_FP" ]; then
    echo "The installed package is signed by the disposable signer from CI run #53."
    echo "Run #53 and later CI builds use different private keys, so Android will reject update-in-place."
    echo "No uninstall, pm clear, package replacement, or data mutation was attempted."
    echo "CORTEX_SIGNER_BLOCKED_RUN53"
  else
    echo "Installed signer: $INST_FP"
    echo "No matching local or historical Cortex key was found."
    echo "No uninstall, pm clear, package replacement, or data mutation was attempted."
    echo "CORTEX_SIGNER_KEY_NOT_FOUND"
  fi
  exit 42
fi

echo "Matching private key recovered from: $MATCH_SOURCE"
SIGNED_FP="$(normalize_fp "$(apk_fp "$MATCH_APK")")"
[ "$SIGNED_FP" = "$INST_FP" ] || fail "Internal signer verification failed"

cp -f "$MATCH_APK" "$FINAL"
echo "Final signed APK: $FINAL"
echo "Final APK SHA-256: $(sha256sum "$FINAL" | awk '{print $1}')"

echo
echo "===== UPDATE-IN-PLACE ====="
BEFORE_FIRST="$(rish -c "dumpsys package $PKG" | sed -n 's/^[[:space:]]*firstInstallTime=//p' | head -n1 | tr -d '\r')"
INSTALL_OUT="$(rish -c "/system/bin/pm install -r '$FINAL'" 2>&1 | tr -d '\r')"
printf '%s\n' "$INSTALL_OUT"
printf '%s\n' "$INSTALL_OUT" | grep -q '^Success$' || fail "Android rejected update-in-place"

BASE2="$(installed_base_apk || true)"
[ -n "$BASE2" ] || fail "Could not resolve installed base APK after update"
rish -c "/system/bin/cat '$BASE2'" > "$TMP/installed-after.apk"
AFTER_FP="$(normalize_fp "$(apk_fp "$TMP/installed-after.apk")")"
[ "$AFTER_FP" = "$INST_FP" ] || fail "Installed signer changed unexpectedly"

AFTER_FIRST="$(rish -c "dumpsys package $PKG" | sed -n 's/^[[:space:]]*firstInstallTime=//p' | head -n1 | tr -d '\r')"
[ -z "$BEFORE_FIRST" ] || [ "$AFTER_FIRST" = "$BEFORE_FIRST" ] || fail "firstInstallTime changed; expected update-in-place"

echo
echo "===== VERIFIED INSTALLED ====="
rish -c "dumpsys package $PKG" \
  | grep -E 'versionCode=|versionName=|firstInstallTime=|lastUpdateTime=' \
  | head -n8 || true
echo "Installed signer SHA-256: $AFTER_FP"
echo "CORTEX_071_UPDATE_SUCCESS"
