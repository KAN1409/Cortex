#!/data/data/com.termux/files/usr/bin/bash
set -u

PKG="com.kareem.cortex.prime"
EXPECTED_OLD_030="cf2007c96527bd35b6aaa7b9dca0a244e8e1eacf987b227725f5eb0f55ac2e1d"
COMPROMISED_040="5c6550a070abe477dcad5f23f3f437e183bff8aeaeb6ac52e1beaa8243ee69a7"
EXPECTED_NEW="${CORTEX_PRIME_EXPECTED_SIGNER_SHA256:-}"
APK="${1:-prime/app/build/outputs/apk/debug/app-debug.apk}"
BACKUP_ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}/backups/prime-signer-migration"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="$BACKUP_ROOT/$STAMP"
PRIVATE_TAR="$BACKUP_DIR/private-data.tar"
PRIVATE_MANIFEST="$BACKUP_DIR/private-data.sha256"
OLD_APK="$BACKUP_DIR/old-installed.apk"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_ROOT" "$BACKUP_DIR" 2>/dev/null || true

log(){ printf '%s\n' "$*"; }
fail(){ log "PRIME_MIGRATION_FAILED=$*"; exit 1; }

command -v rish >/dev/null 2>&1 || fail RISH_NOT_FOUND
[ -n "$EXPECTED_NEW" ] || fail NEW_SIGNER_FINGERPRINT_NOT_SET
[ -f "$APK" ] || fail CANDIDATE_APK_NOT_FOUND

apksigner_bin(){
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return 0; fi
  if [ -n "${ANDROID_HOME:-}" ]; then
    find "$ANDROID_HOME/build-tools" -type f -name apksigner 2>/dev/null | sort | tail -n1
    return 0
  fi
  find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort | tail -n1
}

signer_sha(){
  local signer digest apk="$1"
  signer="$(apksigner_bin)"
  if [ -n "$signer" ] && [ -x "$signer" ]; then
    digest="$("$signer" verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1 | tr -d ':[:space:]' | tr 'A-F' 'a-f')"
    if [ -n "$digest" ]; then printf '%s' "$digest"; return 0; fi
  fi
  command -v python >/dev/null 2>&1 || return 1
  python - "$apk" <<'PYAPK'
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
    except (ValueError, struct.error):
        pass
raise SystemExit(5)
PYAPK
}

stage_install(){
  local src="$1" mode="${2:-fresh}" staged out rc
  staged="/data/local/tmp/cortex-prime-signer-migrate-$$.apk"
  cat "$src" | rish -c "cat > '$staged'" >/dev/null 2>&1 || return 20
  if [ "$mode" = update ]; then
    out="$(rish -c "pm install -r '$staged'" 2>&1)"; rc=$?
  else
    out="$(rish -c "pm install '$staged'" 2>&1)"; rc=$?
  fi
  rish -c "rm -f '$staged'" >/dev/null 2>&1 || true
  printf '%s\n' "$out"
  [ $rc -eq 0 ] && printf '%s' "$out" | grep -q 'Success'
}

restore_private(){
  [ -f "$PRIVATE_TAR" ] || return 30
  rish -c "am force-stop '$PKG'" >/dev/null 2>&1 || true
  cat "$PRIVATE_TAR" | rish -c "run-as '$PKG' sh -c 'cd /data/user/0/$PKG && /system/bin/tar -xf -'" >/dev/null 2>&1 || return 31
  if [ -s "$PRIVATE_MANIFEST" ]; then
    cat "$PRIVATE_MANIFEST" | rish -c "run-as '$PKG' sh -c 'cd /data/user/0/$PKG && sha256sum -c -'" >/dev/null 2>&1 || return 32
  fi
  return 0
}

launch_prime(){
  rish -c "ACT=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$PKG' 2>/dev/null | tail -n1); [ -n \"\$ACT\" ] && am start -W -n \"\$ACT\"" 2>/dev/null
}

rollback_old(){
  local reason="$1"
  log "ROLLBACK_REASON=$reason"
  rish -c "pm uninstall '$PKG'" >/dev/null 2>&1 || true
  if stage_install "$OLD_APK" fresh >/dev/null 2>&1 && restore_private; then
    launch_prime >/dev/null 2>&1 || true
    log "ROLLBACK_STATUS=SUCCESS"
  else
    log "ROLLBACK_STATUS=FAILED"
    log "RECOVERY_BACKUP=$BACKUP_DIR"
  fi
}

candidate_signer="$(signer_sha "$APK")" || fail CANDIDATE_SIGNER_READ_FAILED
log "candidate_signer_sha256=$candidate_signer"
log "expected_secure_signer_sha256=$EXPECTED_NEW"
[ "$candidate_signer" = "$EXPECTED_NEW" ] || fail CANDIDATE_SIGNER_NOT_SECURE_LOCAL_KEY
log "candidate_apk_sha256=$(sha256sum "$APK" | awk '{print $1}')"

installed_path="$(rish -c "pm path '$PKG'" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
if [ -z "$installed_path" ]; then
  log "installed_package=absent"
  stage_install "$APK" fresh || fail FRESH_INSTALL_FAILED
  launch_prime >/dev/null 2>&1 || true
  log "PRIME_MIGRATION_STATUS=FRESH_INSTALL_SUCCESS"
  exit 0
fi

rish -c "cat '$installed_path'" > "$OLD_APK" 2>/dev/null || fail OLD_APK_BACKUP_FAILED
[ -s "$OLD_APK" ] || fail OLD_APK_BACKUP_EMPTY
old_signer="$(signer_sha "$OLD_APK")" || fail OLD_SIGNER_READ_FAILED
log "installed_signer_sha256=$old_signer"
log "installed_apk_sha256=$(sha256sum "$OLD_APK" | awk '{print $1}')"

if [ "$old_signer" = "$EXPECTED_NEW" ]; then
  log "signer_state=already_secure_permanent"
  stage_install "$APK" update || fail NORMAL_UPDATE_FAILED
  launch_prime >/dev/null 2>&1 || true
  log "PRIME_MIGRATION_STATUS=ALREADY_SECURE_UPDATED"
  exit 0
fi

if [ "$old_signer" = "$EXPECTED_OLD_030" ]; then
  log "signer_state=confirmed_legacy_030"
elif [ "$old_signer" = "$COMPROMISED_040" ]; then
  log "signer_state=confirmed_compromised_040"
else
  fail UNEXPECTED_INSTALLED_SIGNER
fi

rish -c "am force-stop '$PKG'" >/dev/null 2>&1 || true
rish -c "run-as '$PKG' id" >/dev/null 2>&1 || fail RUN_AS_UNAVAILABLE_ABORT_BEFORE_UNINSTALL

rish -c "run-as '$PKG' sh -c 'cd /data/user/0/$PKG && find . -type f -exec sha256sum {} \\; 2>/dev/null | sort'" > "$PRIVATE_MANIFEST" 2>/dev/null || fail DATA_MANIFEST_FAILED
rish -c "run-as '$PKG' sh -c 'cd /data/user/0/$PKG && /system/bin/tar -cf - .'" > "$PRIVATE_TAR" 2>/dev/null || fail PRIVATE_BACKUP_FAILED
[ -s "$PRIVATE_TAR" ] || fail PRIVATE_BACKUP_EMPTY
if command -v tar >/dev/null 2>&1; then
  tar -tf "$PRIVATE_TAR" >/dev/null 2>&1 || fail PRIVATE_BACKUP_VERIFY_FAILED
fi
log "backup_dir=$BACKUP_DIR"
log "backup_tar_sha256=$(sha256sum "$PRIVATE_TAR" | awk '{print $1}')"
log "backup_tar_bytes=$(wc -c < "$PRIVATE_TAR" | tr -d ' ')"
log "backup_manifest_entries=$(wc -l < "$PRIVATE_MANIFEST" | tr -d ' ')"

uninstall_out="$(rish -c "pm uninstall '$PKG'" 2>&1)"; uninstall_rc=$?
printf '%s\n' "$uninstall_out"
[ $uninstall_rc -eq 0 ] && printf '%s' "$uninstall_out" | grep -q 'Success' || fail UNINSTALL_FAILED_AFTER_BACKUP

if ! stage_install "$APK" fresh; then
  rollback_old NEW_INSTALL_FAILED
  fail NEW_INSTALL_FAILED_ROLLED_BACK
fi

new_path="$(rish -c "pm path '$PKG'" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
[ -n "$new_path" ] || { rollback_old NEW_PACKAGE_PATH_MISSING; fail NEW_PACKAGE_PATH_MISSING_ROLLED_BACK; }
new_copy="$BACKUP_DIR/new-installed.apk"
rish -c "cat '$new_path'" > "$new_copy" 2>/dev/null || { rollback_old NEW_APK_COPY_FAILED; fail NEW_APK_COPY_FAILED_ROLLED_BACK; }
new_signer="$(signer_sha "$new_copy")" || { rollback_old NEW_SIGNER_READ_FAILED; fail NEW_SIGNER_READ_FAILED_ROLLED_BACK; }
log "new_installed_signer_sha256=$new_signer"
[ "$new_signer" = "$EXPECTED_NEW" ] || { rollback_old NEW_SIGNER_VERIFY_FAILED; fail NEW_SIGNER_VERIFY_FAILED_ROLLED_BACK; }

if ! restore_private; then
  rollback_old DATA_RESTORE_VERIFY_FAILED
  fail DATA_RESTORE_VERIFY_FAILED_ROLLED_BACK
fi
log "data_restore=VERIFIED"

rish -c 'logcat -b crash -c' >/dev/null 2>&1 || true
launch_prime >/dev/null 2>&1 || { rollback_old LAUNCH_FAILED; fail LAUNCH_FAILED_ROLLED_BACK; }
sleep 6
pid="$(rish -c "pidof '$PKG'" 2>/dev/null | tr -d '\r')"
log "pid=$pid"
crashes="$(rish -c 'logcat -b crash -d -t 160' 2>/dev/null | grep -E "AndroidRuntime|FATAL EXCEPTION|Process: $PKG|Caused by:|at $PKG" | tail -n100)"
printf '%s\n' "$crashes"
if printf '%s' "$crashes" | grep -q "$PKG" && printf '%s' "$crashes" | grep -Eq 'FATAL EXCEPTION|Process:'; then
  rollback_old POST_MIGRATION_CRASH
  fail POST_MIGRATION_CRASH_ROLLED_BACK
fi

log "PRIME_MIGRATION_STATUS=SUCCESS"
log "PERMANENT_SIGNER_LOCKED=$EXPECTED_NEW"
log "RECOVERY_BACKUP=$BACKUP_DIR"
