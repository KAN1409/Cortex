#!/data/data/com.termux/files/usr/bin/bash
set -u

REPO="${CORTEX_DEVBRIDGE_REPO:-KAN1409/Cortex}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
WORK="$ROOT/work/relay-candidate5-finalizer"
FLAG_PATH=".devbridge/relay-candidate5-finalize.json"
STATE="$ROOT/relay-candidate5-finalized.txt"

if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/${REPO}.git"
fi

log(){ printf '%s\n' "$*"; }
fail(){ log "FINALIZER_FAIL: $*"; return 1; }

apksigner_bin(){
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1
}

sha_cert_apk(){
  local signer="$1" apk="$2"
  "$signer" verify --print-certs --min-sdk-version 24 "$apk" 2>/dev/null \
    | sed -n -E 's/^.*certificate SHA-256 digest:[[:space:]]*//p' \
    | head -n1 | tr -d ':\r\n ' | tr '[:upper:]' '[:lower:]'
}

read_git_credential(){
  local key="$1" cred
  cred="$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null || true)"
  printf '%s\n' "$cred" | sed -n "s/^${key}=//p" | head -n1
}

download_artifact(){
  local url="$1" out="$2" token user
  rm -f "$out"
  if curl -L --fail --retry 2 --connect-timeout 20 --max-time 600 \
      -H 'Accept: application/vnd.github+json' \
      -H 'X-GitHub-Api-Version: 2022-11-28' \
      "$url" -o "$out" >/dev/null 2>&1; then
    return 0
  fi
  rm -f "$out"
  token="$(read_git_credential password)"
  user="$(read_git_credential username)"
  [ -n "$token" ] || return 1
  curl -L --fail --retry 2 --connect-timeout 20 --max-time 600 \
    -u "${user:-x-access-token}:$token" \
    -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' \
    "$url" -o "$out" >/dev/null 2>&1
  local rc=$?
  unset token user
  return $rc
}

extract_unsigned(){
  local zipfile="$1" out="$2"
  rm -f "$out"
  if command -v unzip >/dev/null 2>&1; then
    unzip -p "$zipfile" app-release-unsigned.apk > "$out" 2>/dev/null || return 1
  elif command -v python >/dev/null 2>&1; then
    python - "$zipfile" "$out" <<'PY'
import sys, zipfile
z, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(z) as f, open(out, 'wb') as w:
    w.write(f.read('app-release-unsigned.apk'))
PY
  else
    return 1
  fi
  [ -s "$out" ]
}

make_zip(){
  local apk="$1" out="$2"
  rm -f "$out"
  if command -v zip >/dev/null 2>&1; then
    (cd "$(dirname "$apk")" && zip -9 -j "$out" "$(basename "$apk")" >/dev/null) || return 1
  elif command -v python >/dev/null 2>&1; then
    python - "$apk" "$out" <<'PY'
import os, sys, zipfile
apk, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.write(apk, arcname=os.path.basename(apk))
PY
  else
    return 1
  fi
  [ -s "$out" ]
}

publish_result(){
  local job="$1" status="$2" exit_code="$3" log_file="$4" artifact_zip="${5:-}"
  local result_file log_tail artifact_rel='' artifact_sha='' artifact_size=0
  if [ ! -d "$RESULTS/.git" ]; then
    rm -rf "$RESULTS"
    git clone --filter=blob:none --no-tags "$REMOTE" "$RESULTS" >/dev/null 2>&1 || return 1
  fi
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  git -C "$RESULTS" fetch origin "$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" pull --ff-only origin "$RESULT_BRANCH" >/dev/null 2>&1 || true
  mkdir -p "$RESULTS/.devbridge/results" "$RESULTS/.devbridge/artifacts"

  if [ -n "$artifact_zip" ] && [ -s "$artifact_zip" ]; then
    artifact_rel=".devbridge/artifacts/${job}.zip"
    cp -f "$artifact_zip" "$RESULTS/$artifact_rel" || return 1
    artifact_sha="$(sha256sum "$artifact_zip" | awk '{print $1}')"
    artifact_size="$(wc -c < "$artifact_zip" | tr -d ' ')"
    if [ "$artifact_size" -ge 100000000 ]; then
      rm -f "$RESULTS/$artifact_rel"
      log "ARTIFACT_TOO_LARGE_FOR_GIT=$artifact_size" >> "$log_file"
      artifact_rel=''
      artifact_sha=''
      artifact_size=0
    fi
  fi

  result_file="$RESULTS/.devbridge/results/$job.json"
  log_tail="$(tail -c 60000 "$log_file" 2>/dev/null || true)"
  jq -n \
    --arg protocol CORTEX_DEVBRIDGE_V1 \
    --arg agent_version '4-relay-finalizer' \
    --arg job_id "$job" \
    --arg capability 'RELAY_CANDIDATE5_FINALIZE' \
    --arg status "$status" \
    --argjson exit_code "$exit_code" \
    --arg finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg log_tail "$log_tail" \
    --arg artifact_path "$artifact_rel" \
    --arg artifact_sha256 "$artifact_sha" \
    --argjson artifact_size "$artifact_size" \
    '{protocol:$protocol,agent_version:$agent_version,job_id:$job_id,capability:$capability,status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail,artifact_path:$artifact_path,artifact_sha256:$artifact_sha256,artifact_size_bytes:$artifact_size}' \
    > "$result_file"

  git -C "$RESULTS" add ".devbridge/results/$job.json"
  [ -n "$artifact_rel" ] && git -C "$RESULTS" add "$artifact_rel"
  git -C "$RESULTS" diff --cached --quiet && return 0
  git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' \
    commit -m "devbridge(relay): $job ${status,,}" >/dev/null || return 1
  git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1
}

run_finalizer(){
  local flag="$1" job log_file artifact_url zip_sha apk_sha expected_cert package version_code version_name
  local ks passfile alias signer zipfile unsigned signed output_apk tmp_pass installed_path installed_tmp installed_cert key_cert signed_cert
  local verify install_out pkg_info artifact_zip rc=0 crashes

  job="$(jq -r '.job_id // empty' "$flag")"
  artifact_url="$(jq -r '.artifact_url // empty' "$flag")"
  zip_sha="$(jq -r '.artifact_zip_sha256 // empty' "$flag")"
  apk_sha="$(jq -r '.unsigned_apk_sha256 // empty' "$flag")"
  expected_cert="$(jq -r '.expected_cert_sha256 // empty' "$flag" | tr '[:upper:]' '[:lower:]')"
  package="$(jq -r '.package // empty' "$flag")"
  version_code="$(jq -r '.version_code // empty' "$flag")"
  version_name="$(jq -r '.version_name // empty' "$flag")"

  [ "$job" = 'job_relay_candidate5_finalize_20260830' ] || return 80
  [ "$(jq -r '.authorized_owner // empty' "$flag")" = 'KAN1409' ] || return 81
  [ "$package" = 'com.kareem.secondbrain' ] || return 82
  [ "$expected_cert" = 'fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74' ] || return 83
  [ "$version_code" = '24' ] || return 84
  [ "$version_name" = '2.0.0-candidate5' ] || return 85
  [ -n "$artifact_url" ] && [ -n "$zip_sha" ] && [ -n "$apk_sha" ] || return 86

  mkdir -p "$WORK"
  log_file="$ROOT/logs/$job.log"
  : > "$log_file"
  exec 9>&1
  exec >> "$log_file" 2>&1
  log "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  log 'agent_version=4-relay-finalizer'

  ks="$HOME/.secondbrain-signing/second-brain-permanent.p12"
  passfile="$HOME/.secondbrain-signing/password.txt"
  alias='secondbrain'
  signer="$(apksigner_bin)"
  [ -n "$signer" ] || { fail APKSIGNER_NOT_FOUND; rc=90; }
  [ $rc -eq 0 ] && [ -f "$ks" ] || { [ $rc -ne 0 ] || rc=91; [ $rc -ne 91 ] || fail PERMANENT_KEYSTORE_NOT_FOUND; }
  [ $rc -eq 0 ] && [ -s "$passfile" ] || { [ $rc -ne 0 ] || rc=92; [ $rc -ne 92 ] || fail PASSWORD_FILE_NOT_FOUND; }

  if [ $rc -eq 0 ]; then
    tmp_pass="$WORK/pass.txt"
    tr -d '\r\n' < "$passfile" > "$tmp_pass"
    chmod 600 "$tmp_pass"
    key_cert="$(keytool -list -v -storetype PKCS12 -keystore "$ks" -storepass "$(cat "$tmp_pass")" -alias "$alias" 2>/dev/null \
      | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n1 | tr -d ':\r\n ' | tr '[:upper:]' '[:lower:]')"
    log "keystore_signer_sha256=$key_cert"
    [ "$key_cert" = "$expected_cert" ] || { fail KEYSTORE_SIGNER_MISMATCH; rc=93; }
  fi

  zipfile="$WORK/candidate5-unsigned.zip"
  unsigned="$WORK/app-release-unsigned.apk"
  signed="$WORK/Cortex-Relay-v2.0.0-candidate5-permanent.apk"
  output_apk="/sdcard/Download/Cortex-Relay-v2.0.0-candidate5-permanent.apk"
  artifact_zip="$WORK/${job}.zip"

  if [ $rc -eq 0 ]; then
    log DOWNLOAD_UNSIGNED_ARTIFACT
    download_artifact "$artifact_url" "$zipfile" || { fail ARTIFACT_DOWNLOAD_FAILED; rc=94; }
  fi
  if [ $rc -eq 0 ]; then
    actual_zip_sha="$(sha256sum "$zipfile" | awk '{print $1}')"
    log "artifact_zip_sha256=$actual_zip_sha"
    [ "$actual_zip_sha" = "$zip_sha" ] || { fail ARTIFACT_ZIP_SHA_MISMATCH; rc=95; }
  fi
  if [ $rc -eq 0 ]; then
    extract_unsigned "$zipfile" "$unsigned" || { fail UNSIGNED_EXTRACT_FAILED; rc=96; }
    actual_apk_sha="$(sha256sum "$unsigned" | awk '{print $1}')"
    log "unsigned_apk_sha256=$actual_apk_sha"
    [ "$actual_apk_sha" = "$apk_sha" ] || { fail UNSIGNED_APK_SHA_MISMATCH; rc=97; }
  fi

  if [ $rc -eq 0 ]; then
    installed_path="$(rish -c "pm path '$package'" 2>/dev/null | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
    [ -n "$installed_path" ] || { fail INSTALLED_APK_NOT_FOUND; rc=98; }
  fi
  if [ $rc -eq 0 ]; then
    installed_tmp="$WORK/installed-base.apk"
    rish -c "cat '$installed_path'" > "$installed_tmp" 2>/dev/null || { fail INSTALLED_APK_COPY_FAILED; rc=99; }
    installed_cert="$(sha_cert_apk "$signer" "$installed_tmp")"
    log "installed_signer_sha256=$installed_cert"
    [ "$installed_cert" = "$expected_cert" ] || { fail INSTALLED_SIGNER_MISMATCH; rc=100; }
  fi

  if [ $rc -eq 0 ]; then
    rm -f "$signed" "$signed.idsig"
    "$signer" sign \
      --ks "$ks" --ks-type PKCS12 --ks-key-alias "$alias" \
      --ks-pass "file:$tmp_pass" --key-pass "file:$tmp_pass" \
      --min-sdk-version 24 \
      --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
      --out "$signed" "$unsigned" || { fail SIGN_FAILED; rc=101; }
  fi
  if [ $rc -eq 0 ]; then
    verify="$("$signer" verify --verbose --print-certs --min-sdk-version 24 "$signed" 2>&1)"
    printf '%s\n' "$verify"
    printf '%s\n' "$verify" | grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' || { fail V2_VERIFY_FAILED; rc=102; }
    [ $rc -ne 0 ] || printf '%s\n' "$verify" | grep -q 'Verified using v3 scheme (APK Signature Scheme v3): true' || { fail V3_VERIFY_FAILED; rc=103; }
  fi
  if [ $rc -eq 0 ]; then
    signed_cert="$(sha_cert_apk "$signer" "$signed")"
    log "signed_signer_sha256=$signed_cert"
    [ "$signed_cert" = "$expected_cert" ] || { fail SIGNED_SIGNER_MISMATCH; rc=104; }
    log "signed_apk_sha256=$(sha256sum "$signed" | awk '{print $1}')"
  fi

  if [ $rc -eq 0 ]; then
    rish -c 'logcat -b crash -c' >/dev/null 2>&1 || true
    staged="/data/local/tmp/Cortex-Relay-v2.0.0-candidate5-permanent.apk"
    cat "$signed" | rish -c "cat > '$staged'; chmod 644 '$staged'" || { fail APK_STAGE_FAILED; rc=105; }
  fi
  if [ $rc -eq 0 ]; then
    install_out="$(rish -c "pm install -r '$staged'; rc=\$?; rm -f '$staged'; exit \$rc" 2>&1)"
    printf '%s\n' "$install_out"
    printf '%s\n' "$install_out" | grep -q 'Success' || { fail UPDATE_INSTALL_FAILED; rc=106; }
  fi
  if [ $rc -eq 0 ]; then
    pkg_info="$(rish -c "dumpsys package '$package' | grep -E 'versionCode=|versionName=' | head -n4" 2>/dev/null)"
    printf '%s\n' "$pkg_info"
    printf '%s\n' "$pkg_info" | grep -q "versionCode=$version_code" || { fail VERSION_CODE_VERIFY_FAILED; rc=107; }
    [ $rc -ne 0 ] || printf '%s\n' "$pkg_info" | grep -q "versionName=$version_name" || { fail VERSION_NAME_VERIFY_FAILED; rc=108; }
  fi
  if [ $rc -eq 0 ]; then
    cp -f "$signed" "$output_apk" || { fail DOWNLOAD_COPY_FAILED; rc=109; }
    rish -c "am force-stop '$package'" >/dev/null 2>&1 || true
    rish -c "ACT=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$package' 2>/dev/null | tail -n1); am start -W -n \"\$ACT\"" 2>&1 || { fail LAUNCH_FAILED; rc=110; }
    sleep 8
    pid="$(rish -c "pidof '$package'" 2>/dev/null | tr -d '\r')"
    log "pid=$pid"
    [ -n "$pid" ] || { fail PROCESS_NOT_RUNNING; rc=111; }
  fi
  if [ $rc -eq 0 ]; then
    crashes="$(rish -c 'logcat -b crash -d -t 220' 2>/dev/null)"
    if printf '%s' "$crashes" | grep -q "$package" && printf '%s' "$crashes" | grep -Eq 'FATAL EXCEPTION|Process:'; then
      printf '%s\n' "$crashes" | tail -n120
      fail SMOKE_CRASH
      rc=112
    fi
  fi
  if [ $rc -eq 0 ]; then
    make_zip "$signed" "$artifact_zip" || { fail ARTIFACT_ZIP_CREATE_FAILED; rc=113; }
    log "return_artifact_zip_sha256=$(sha256sum "$artifact_zip" | awk '{print $1}')"
    log "return_artifact_zip_bytes=$(wc -c < "$artifact_zip" | tr -d ' ')"
  fi

  rm -f "$WORK/pass.txt" "$WORK/installed-base.apk"
  log "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  exec 1>&9 9>&-

  if [ $rc -eq 0 ]; then
    publish_result "$job" SUCCESS 0 "$log_file" "$artifact_zip" || return 114
    printf '%s\n' "$job" >> "$STATE"
    return 0
  fi
  publish_result "$job" FAILED "$rc" "$log_file" '' || true
  return "$rc"
}

# The one-shot extension is activated only by an explicitly authorized control document.
if [ -d "$CONTROL/.git" ] && git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$FLAG_PATH" > "$ROOT/relay-candidate5-finalize.json" 2>/dev/null; then
  job="$(jq -r '.job_id // empty' "$ROOT/relay-candidate5-finalize.json")"
  if [ -n "$job" ] && ! grep -Fxq "$job" "$STATE" 2>/dev/null; then
    run_finalizer "$ROOT/relay-candidate5-finalize.json" || true
  fi
fi

# Delegate all ordinary bridge behavior to the stable v3 agent.
V3="$ROOT/agent-v3.delegate.sh"
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$V3" 2>/dev/null && bash -n "$V3" >/dev/null 2>&1; then
  chmod 700 "$V3"
  exec "$V3" "$@"
fi

echo DEVBRIDGE_V3_DELEGATE_FAILED >&2
exit 72
