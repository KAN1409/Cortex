#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PKG="com.kareem.cortex.rebuild"
RUN53_FP="44b0180ba743460788171687f2dbcfb137c5e5a151f2783306747f84fbee39f4"
LOCAL_SIGNER="${CORTEX_SIGNER_SOURCE:-$HOME/Cortex/app/cortex-debug.keystore}"
BACKUP_ROOT="${CORTEX_BACKUP_ROOT:-$HOME/.cortex-rebuild-migration}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$BACKUP_ROOT/$STAMP"

fail(){ echo "ERROR: $*" >&2; exit 1; }
normalize_fp(){ printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -d ':\r\n '; }
command -v rish >/dev/null 2>&1 || fail "rish is required"
command -v python >/dev/null 2>&1 || fail "python is required"
command -v keytool >/dev/null 2>&1 || fail "keytool is required"
[ -s "$LOCAL_SIGNER" ] || fail "authoritative Cortex signer missing: $LOCAL_SIGNER"

mkdir -p "$BACKUP"
chmod 700 "$BACKUP"

apk_fp(){
  local apk="$1" digest=''
  if command -v apksigner >/dev/null 2>&1; then
    digest="$(apksigner verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1)"
    digest="$(normalize_fp "$digest")"
    if [ -n "$digest" ]; then printf '%s' "$digest"; return 0; fi
  fi
  python - "$apk" <<'PY'
import hashlib, struct, sys
p=sys.argv[1]
d=open(p,'rb').read()
e=d.rfind(b'PK\x05\x06', max(0, len(d)-65557))
if e < 0: raise SystemExit(2)
c=struct.unpack_from('<I', d, e+16)[0]
f=c-24
if f < 0 or d[f+8:f+24] != b'APK Sig Block 42': raise SystemExit(3)
sz=struct.unpack_from('<Q', d, f)[0]
s=c-(sz+8)
if s < 0 or struct.unpack_from('<Q', d, s)[0] != sz: raise SystemExit(4)
pairs={}; o=s+8
while o < f:
    n=struct.unpack_from('<Q', d, o)[0]; o += 8
    if n < 4 or o+n > f: break
    i=struct.unpack_from('<I', d, o)[0]
    pairs[i]=d[o+4:o+n]; o += n
def lp(b,p):
    n=struct.unpack_from('<I', b, p)[0]; p += 4
    if p+n > len(b): raise ValueError()
    return b[p:p+n], p+n
for i in (0x1b93ad61,0xf05368c0,0x7109871a):
    b=pairs.get(i)
    if not b: continue
    try:
        signer,_=lp(b,0)
        signed,_=lp(signer,0)
        _,q=lp(signed,0)
        certs,_=lp(signed,q)
        cert,_=lp(certs,0)
        print(hashlib.sha256(cert).hexdigest(), end='')
        raise SystemExit(0)
    except (ValueError, struct.error): pass
raise SystemExit(5)
PY
}

installed_base(){
  local out base code
  out="$(rish -c "/system/bin/pm path $PKG" 2>/dev/null || true)"
  base="$(printf '%s\n' "$out" | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
  [ -n "$base" ] && { printf '%s\n' "$base"; return 0; }
  out="$(rish -c "cmd package path $PKG" 2>/dev/null || true)"
  base="$(printf '%s\n' "$out" | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
  [ -n "$base" ] && { printf '%s\n' "$base"; return 0; }
  code="$(rish -c "dumpsys package $PKG" 2>/dev/null | tr -d '\r' | sed -n 's/^[[:space:]]*codePath=//p' | head -n1)"
  [ -n "$code" ] && { printf '%s/base.apk\n' "${code%/}"; return 0; }
  return 1
}

BASE="$(installed_base || true)"
[ -n "$BASE" ] || fail "$PKG is not installed"
rish -c "/system/bin/cat '$BASE'" > "$BACKUP/installed-base.apk"
[ -s "$BACKUP/installed-base.apk" ] || fail "could not copy installed APK"
INSTALLED_FP="$(apk_fp "$BACKUP/installed-base.apk")" || fail "could not inspect installed signer"
INSTALLED_FP="$(normalize_fp "$INSTALLED_FP")"

LOCAL_FP="$(keytool -list -v -keystore "$LOCAL_SIGNER" -storepass android -alias androiddebugkey 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | head -n1)"
LOCAL_FP="$(normalize_fp "$LOCAL_FP")"
[ -n "$LOCAL_FP" ] || fail "could not inspect authoritative local signer"

rish -c "dumpsys package $PKG" > "$BACKUP/package-dumpsys.txt"
VERSION="$(grep -m1 'versionName=' "$BACKUP/package-dumpsys.txt" | sed 's/.*versionName=//' | tr -d '\r')"
VERSION_CODE="$(grep -m1 'versionCode=' "$BACKUP/package-dumpsys.txt" | sed -E 's/.*versionCode=([0-9]+).*/\1/' | tr -d '\r')"

echo "===== CORTEX SIGNER STATE ====="
echo "Installed version: ${VERSION_CODE:-?} / ${VERSION:-?}"
echo "Installed signer: $INSTALLED_FP"
echo "Authoritative local signer: $LOCAL_FP"
if [ "$INSTALLED_FP" = "$RUN53_FP" ]; then
  echo "Installed signer provenance: GitHub Actions run #53 disposable debug signer"
fi

DATA_DIR="$(rish -c "run-as $PKG pwd" 2>/dev/null | tr -d '\r' | tail -n1)"
[ -n "$DATA_DIR" ] || DATA_DIR="/data/user/0/$PKG"

echo
echo "===== QUIESCE + SNAPSHOT CURRENT APP DATA ====="
rish -c "am force-stop $PKG" >/dev/null 2>&1 || true
REMOTE_TAR="$DATA_DIR/cache/cortex-signer-recovery.tar"
rish -c "run-as $PKG sh -c 'cd \"$DATA_DIR\" && toybox tar -cf \"$REMOTE_TAR\" databases shared_prefs files'"
rish -c "run-as $PKG /system/bin/cat '$REMOTE_TAR'" > "$BACKUP/app-data.tar"
rish -c "run-as $PKG rm -f '$REMOTE_TAR'" >/dev/null 2>&1 || true
[ -s "$BACKUP/app-data.tar" ] || fail "app-data snapshot is empty"
tar -tf "$BACKUP/app-data.tar" > "$BACKUP/app-data-files.txt"
grep -q '^databases/' "$BACKUP/app-data-files.txt" || fail "database files missing from snapshot"
grep -q '^shared_prefs/' "$BACKUP/app-data-files.txt" || fail "shared preferences missing from snapshot"

# Android Keystore secrets are intentionally not decrypted or reconstructed here. The encrypted
# secure preferences remain inside app-data.tar; changing the package signing/installation identity
# is not an allowed recovery strategy.
cat > "$BACKUP/credentials.properties" <<'EOF'
format=CORTEX_REBUILD_CREDENTIALS_ENCRYPTED_ONLY
action=preserved_inside_app_data_tar
EOF
chmod 600 "$BACKUP/credentials.properties"
sha256sum "$BACKUP/installed-base.apk" "$BACKUP/app-data.tar" "$BACKUP/credentials.properties" > "$BACKUP/SHA256SUMS.txt"
chmod 600 "$BACKUP/SHA256SUMS.txt" "$BACKUP/package-dumpsys.txt" "$BACKUP/app-data-files.txt"

MATCH=0
[ "$INSTALLED_FP" = "$LOCAL_FP" ] && MATCH=1
cat > "$BACKUP/RECOVERY_MANIFEST.txt" <<EOF
CORTEX_REBUILD_SIGNER_RECOVERY_V3
package=$PKG
installed_version_code=$VERSION_CODE
installed_version_name=$VERSION
installed_signer_sha256=$INSTALLED_FP
authoritative_local_signer_sha256=$LOCAL_FP
signer_match=$MATCH
update_in_place_possible=$MATCH
backup_path=$BACKUP
uninstall_performed=0
pm_clear_performed=0
signer_generated=0
signer_replaced=0
EOF
chmod 600 "$BACKUP/RECOVERY_MANIFEST.txt"

echo "Backup directory: $BACKUP"
echo "App-data bytes: $(wc -c < "$BACKUP/app-data.tar")"
echo "Signer match: $MATCH"
echo
if [ "$MATCH" = 1 ]; then
  echo "CORTEX_SIGNER_RECOVERY_BACKUP_READY"
  echo "Update-in-place signer continuity is available."
else
  echo "CORTEX_SIGNER_MISMATCH_BLOCKED"
  echo "Update-in-place is not possible with the authoritative local signer."
fi
echo "No signer was generated, replaced, reconstructed, or exposed."
echo "No uninstall, pm clear, install, package replacement, or database mutation was performed."
