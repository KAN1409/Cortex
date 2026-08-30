#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

OLD_FP="44b0180ba743460788171687f2dbcfb137c5e5a151f2783306747f84fbee39f4"
BACKUP_ROOT="${CORTEX_BACKUP_ROOT:-$HOME/.cortex-rebuild-migration}"
SIGN_ROOT="${CORTEX_SIGN_ROOT:-$HOME/.cortex-rebuild-signing}"
LOCAL_SIGNER="${CORTEX_SIGNER_SOURCE:-$HOME/Cortex/app/cortex-debug.keystore}"

die(){ echo "RECOVERY_VALIDATE_FAIL: $*" >&2; exit 1; }
normalize_fp(){ printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -d ':\r\n '; }

BACKUP="$(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d ! -name 'precutover-*' 2>/dev/null | sort | tail -n1)"
[ -n "$BACKUP" ] || die "no backup directory"
for f in installed-base.apk app-data.tar app-data-files.txt package-dumpsys.txt SHA256SUMS.txt credentials.properties; do
  [ -s "$BACKUP/$f" ] || die "missing $BACKUP/$f"
done

grep -q '^databases/' "$BACKUP/app-data-files.txt" || die "database files absent from tar index"
grep -q '^shared_prefs/' "$BACKUP/app-data-files.txt" || die "shared preferences absent from tar index"
(
  cd "$BACKUP"
  sha256sum -c SHA256SUMS.txt >/dev/null
) || die "backup hash verification failed"

KS="$SIGN_ROOT/cortex-rebuild-permanent.keystore"
APK="$SIGN_ROOT/Cortex-0.7.1-stable-signed.apk"
MANIFEST="$SIGN_ROOT/SIGNER_MANIFEST.txt"
[ -s "$LOCAL_SIGNER" ] || die "authoritative local signer missing: $LOCAL_SIGNER"
[ -s "$KS" ] || die "authoritative signer backup missing"
[ -s "$APK" ] || die "stable signed APK missing"
[ -s "$MANIFEST" ] || die "signer manifest missing"

LOCAL_FP="$(keytool -list -v -keystore "$LOCAL_SIGNER" -storepass android -alias androiddebugkey 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | head -n1)"
LOCAL_FP="$(normalize_fp "$LOCAL_FP")"
[ -n "$LOCAL_FP" ] || die "could not read authoritative local signer fingerprint"
MANIFEST_FP="$(normalize_fp "$(sed -n 's/^signer_sha256=//p' "$MANIFEST" | head -n1)")"
[ "$MANIFEST_FP" = "$LOCAL_FP" ] || die "signer manifest does not match authoritative local signer"

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
    n=struct.unpack_from('<I',b,p)[0]; p += 4
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

OLD_ACTUAL="$(apk_fp "$BACKUP/installed-base.apk")" || die "cannot read installed-backup signer"
CANDIDATE_ACTUAL="$(apk_fp "$APK")" || die "cannot read candidate signer"
[ "$OLD_ACTUAL" = "$OLD_FP" ] || die "backup installed signer mismatch"
[ "$CANDIDATE_ACTUAL" = "$LOCAL_FP" ] || die "candidate signer does not match authoritative local signer"

KS_SHA="$(sha256sum "$KS" | awk '{print $1}')"
LOCAL_KS_SHA="$(sha256sum "$LOCAL_SIGNER" | awk '{print $1}')"
[ "$KS_SHA" = "$LOCAL_KS_SHA" ] || die "signer backup differs from authoritative local signer file"

APK_SHA="$(sha256sum "$APK" | awk '{print $1}')"
MANIFEST_APK_SHA="$(sed -n 's/^apk_sha256=//p' "$MANIFEST" | head -n1)"
[ -n "$MANIFEST_APK_SHA" ] && [ "$APK_SHA" = "$MANIFEST_APK_SHA" ] || die "candidate APK hash mismatch"

CRED_FORMAT="$(sed -n 's/^format=//p' "$BACKUP/credentials.properties" | head -n1)"

echo "backup_path=$BACKUP"
echo "backup_app_data_bytes=$(wc -c < "$BACKUP/app-data.tar")"
echo "installed_backup_signer_sha256=$OLD_ACTUAL"
echo "credential_backup_format=${CRED_FORMAT:-unknown}"
echo "authoritative_local_signer_sha256=$LOCAL_FP"
echo "candidate_signer_sha256=$CANDIDATE_ACTUAL"
echo "stable_apk=$APK"
echo "stable_apk_sha256=$APK_SHA"
echo "signer_match_installed=$([ "$OLD_ACTUAL" = "$LOCAL_FP" ] && echo 1 || echo 0)"
echo "CORTEX_RECOVERY_STATE_VALIDATED"
