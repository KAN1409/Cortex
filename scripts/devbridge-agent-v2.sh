#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
TARGET="$ROOT/agent-v3.runtime.sh"
WORK="$ROOT/work/relay-c6-finalize"
FLAG_PATH=".devbridge/relay-candidate6-finalize.json"
DONE="$ROOT/relay-candidate6-finalized.txt"
REPO="KAN1409/Cortex"

if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/${REPO}.git"
fi

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
mkdir -p "$WORK" "$ROOT/logs"
touch "$DONE"

apksigner_bin(){
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1
}

aapt_bin(){
  if command -v aapt >/dev/null 2>&1; then command -v aapt; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/aapt' 2>/dev/null | sort -V | tail -n1
}

apk_cert_sha(){
  local signer="$1" apk="$2"
  "$signer" verify --print-certs --min-sdk-version 24 "$apk" 2>/dev/null \
    | sed -n -E 's/^.*certificate SHA-256 digest:[[:space:]]*//p' \
    | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
}

git_credential_field(){
  local key="$1" cred
  cred="$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null || true)"
  printf '%s\n' "$cred" | sed -n "s/^${key}=//p" | head -n1
}

download_artifact(){
  local url="$1" out="$2" user token rc
  rm -f "$out"
  if curl -L --fail --retry 2 --connect-timeout 20 --max-time 600 \
      -H 'Accept: application/vnd.github+json' \
      -H 'X-GitHub-Api-Version: 2022-11-28' "$url" -o "$out" >/dev/null 2>&1; then
    return 0
  fi
  user="$(git_credential_field username)"
  token="$(git_credential_field password)"
  [ -n "$token" ] || return 1
  curl -L --fail --retry 2 --connect-timeout 20 --max-time 600 \
    -u "${user:-x-access-token}:$token" \
    -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' "$url" -o "$out" >/dev/null 2>&1
  rc=$?
  unset user token
  return $rc
}

extract_unsigned(){
  local zipfile="$1" out="$2"
  rm -f "$out"
  if command -v unzip >/dev/null 2>&1; then
    unzip -p "$zipfile" app-release-unsigned.apk > "$out" 2>/dev/null
  else
    python - "$zipfile" "$out" <<'PY'
import sys, zipfile
z, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(z) as f, open(out, 'wb') as w:
    w.write(f.read('app-release-unsigned.apk'))
PY
  fi
  [ -s "$out" ]
}

make_zip(){
  local apk="$1" out="$2"
  rm -f "$out"
  if command -v zip >/dev/null 2>&1; then
    (cd "$(dirname "$apk")" && zip -9 -j "$out" "$(basename "$apk")" >/dev/null)
  else
    python - "$apk" "$out" <<'PY'
import os, sys, zipfile
apk, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.write(apk, arcname=os.path.basename(apk))
PY
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
      artifact_rel=''
      artifact_sha=''
      artifact_size=0
    fi
  fi

  log_tail="$(tail -c 60000 "$log_file" 2>/dev/null || true)"
  result_file="$RESULTS/.devbridge/results/$job.json"
  jq -n \
    --arg protocol CORTEX_DEVBRIDGE_V1 \
    --arg agent_version '3+relay-c6-finalize' \
    --arg job_id "$job" \
    --arg capability RELAY_CANDIDATE6_FINALIZE_STRESS \
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
  if ! git -C "$RESULTS" diff --cached --quiet; then
    git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' \
      commit -m "devbridge(relay): $job ${status,,}" >/dev/null || return 1
    git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  fi
}

run_candidate6(){
  local flag="$1" job artifact_url zip_sha unsigned_sha expected_cert package version_code version_name
  local log_file ks passfile storepass keypass signer aapt zipfile unsigned signed signed_zip output_apk
  local key_cert signed_cert installed_path installed_probe installed_cert staged install_out pkg_info act pid crashes gfx janky total_frames rc=0

  job="$(jq -r '.job_id // empty' "$flag")"
  artifact_url="$(jq -r '.artifact_url // empty' "$flag")"
  zip_sha="$(jq -r '.artifact_zip_sha256 // empty' "$flag")"
  unsigned_sha="$(jq -r '.unsigned_apk_sha256 // empty' "$flag")"
  expected_cert="$(jq -r '.expected_cert_sha256 // empty' "$flag" | tr '[:upper:]' '[:lower:]')"
  package="$(jq -r '.package // empty' "$flag")"
  version_code="$(jq -r '.version_code // empty' "$flag")"
  version_name="$(jq -r '.version_name // empty' "$flag")"

  [ "$job" = 'job_relay_c6_finalize_stress_20260830_1054' ] || return 80
  [ "$(jq -r '.authorized_owner // empty' "$flag")" = 'KAN1409' ] || return 81
  [ "$package" = 'com.kareem.secondbrain' ] || return 82
  [ "$expected_cert" = 'fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74' ] || return 83
  [ "$version_code" = '25' ] || return 84
  [ "$version_name" = '2.0.0-candidate6' ] || return 85

  mkdir -p "$WORK"
  log_file="$ROOT/logs/$job.log"
  : > "$log_file"
  exec 9>&1
  exec >> "$log_file" 2>&1
  echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo 'agent_version=3+relay-c6-finalize'

  ks="$HOME/.secondbrain-signing/second-brain-permanent.p12"
  passfile="$HOME/.secondbrain-signing/password.txt"
  signer="$(apksigner_bin)"
  aapt="$(aapt_bin)"
  [ -n "$signer" ] || { echo APKSIGNER_NOT_FOUND; rc=90; }
  [ $rc -ne 0 ] || [ -n "$aapt" ] || { echo AAPT_NOT_FOUND; rc=91; }
  [ $rc -ne 0 ] || [ -f "$ks" ] || { echo PERMANENT_KEYSTORE_NOT_FOUND; rc=92; }
  [ $rc -ne 0 ] || [ -s "$passfile" ] || { echo PASSWORD_FILE_NOT_FOUND; rc=93; }

  storepass="$WORK/store.pass"
  keypass="$WORK/key.pass"
  if [ $rc -eq 0 ]; then
    tr -d '\r\n' < "$passfile" > "$storepass"
    cp -f "$storepass" "$keypass"
    chmod 600 "$storepass" "$keypass"
    key_cert="$(keytool -list -v -storetype PKCS12 -keystore "$ks" -storepass "$(cat "$storepass")" -alias secondbrain 2>/dev/null \
      | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
    echo "keystore_signer_sha256=$key_cert"
    [ "$key_cert" = "$expected_cert" ] || { echo KEYSTORE_SIGNER_MISMATCH; rc=94; }
  fi

  zipfile="$WORK/candidate6-unsigned.zip"
  unsigned="$WORK/app-release-unsigned.apk"
  signed="$WORK/Cortex-Relay-v2.0.0-candidate6-permanent.apk"
  signed_zip="$WORK/${job}.zip"
  output_apk="/sdcard/Download/Cortex-Relay-v2.0.0-candidate6-permanent.apk"

  if [ $rc -eq 0 ]; then
    download_artifact "$artifact_url" "$zipfile" || { echo ARTIFACT_DOWNLOAD_FAILED; rc=95; }
  fi
  if [ $rc -eq 0 ]; then
    actual="$(sha256sum "$zipfile" | awk '{print $1}')"
    echo "artifact_zip_sha256=$actual"
    [ "$actual" = "$zip_sha" ] || { echo ARTIFACT_ZIP_SHA_MISMATCH; rc=96; }
  fi
  if [ $rc -eq 0 ]; then
    extract_unsigned "$zipfile" "$unsigned" || { echo UNSIGNED_EXTRACT_FAILED; rc=97; }
    actual="$(sha256sum "$unsigned" | awk '{print $1}')"
    echo "unsigned_apk_sha256=$actual"
    [ "$actual" = "$unsigned_sha" ] || { echo UNSIGNED_APK_SHA_MISMATCH; rc=98; }
  fi

  if [ $rc -eq 0 ]; then
    rm -f "$signed" "$signed.idsig"
    "$signer" sign \
      --ks "$ks" --ks-type PKCS12 --ks-key-alias secondbrain \
      --ks-pass "file:$storepass" --key-pass "file:$keypass" \
      --min-sdk-version 24 --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
      --out "$signed" "$unsigned" || { echo SIGN_FAILED; rc=99; }
  fi
  if [ $rc -eq 0 ]; then
    "$signer" verify --verbose --print-certs --min-sdk-version 24 "$signed"
    signed_cert="$(apk_cert_sha "$signer" "$signed")"
    echo "signed_signer_sha256=$signed_cert"
    [ "$signed_cert" = "$expected_cert" ] || { echo SIGNED_SIGNER_MISMATCH; rc=100; }
    echo "signed_apk_sha256=$(sha256sum "$signed" | awk '{print $1}')"
    "$aapt" dump badging "$signed" | head -n1
    "$aapt" dump badging "$signed" | head -n1 | grep -q "versionCode='$version_code'" || { echo VERSION_CODE_BADGING_FAIL; rc=101; }
    [ $rc -ne 0 ] || "$aapt" dump badging "$signed" | head -n1 | grep -q "versionName='$version_name'" || { echo VERSION_NAME_BADGING_FAIL; rc=102; }
  fi

  if [ $rc -eq 0 ]; then
    installed_path="$(rish -c "pm path '$package'" 2>/dev/null | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
    echo "installed_path=$installed_path"
    [ -n "$installed_path" ] || { echo INSTALLED_APK_NOT_FOUND; rc=103; }
  fi
  installed_probe="/sdcard/Download/.relay-c6-installed-probe.apk"
  if [ $rc -eq 0 ]; then
    rish -c "rm -f '$installed_probe'; cp '$installed_path' '$installed_probe'; chmod 644 '$installed_probe'" || { echo INSTALLED_COPY_FAILED; rc=104; }
    installed_cert="$(apk_cert_sha "$signer" "$installed_probe")"
    echo "installed_signer_sha256=$installed_cert"
    rish -c "rm -f '$installed_probe'" >/dev/null 2>&1 || true
    [ "$installed_cert" = "$expected_cert" ] || { echo INSTALLED_SIGNER_MISMATCH; rc=105; }
  fi

  if [ $rc -eq 0 ]; then
    staged="/data/local/tmp/Cortex-Relay-v2.0.0-candidate6-permanent.apk"
    cat "$signed" | rish -c "cat > '$staged'; chmod 644 '$staged'" || { echo APK_STAGE_FAILED; rc=106; }
    install_out="$(rish -c "pm install -r '$staged'; x=\$?; rm -f '$staged'; exit \$x" 2>&1)"
    printf '%s\n' "$install_out"
    printf '%s\n' "$install_out" | grep -q 'Success' || { echo UPDATE_INSTALL_FAILED; rc=107; }
  fi
  if [ $rc -eq 0 ]; then
    pkg_info="$(rish -c "dumpsys package '$package' | grep -E 'versionCode=|versionName=|lastUpdateTime=' | head -n6" 2>/dev/null)"
    printf '%s\n' "$pkg_info"
    printf '%s\n' "$pkg_info" | grep -q "versionCode=$version_code" || { echo INSTALLED_VERSION_CODE_FAIL; rc=108; }
    [ $rc -ne 0 ] || printf '%s\n' "$pkg_info" | grep -q "versionName=$version_name" || { echo INSTALLED_VERSION_NAME_FAIL; rc=109; }
  fi

  if [ $rc -eq 0 ]; then
    cp -f "$signed" "$output_apk" || { echo DOWNLOAD_COPY_FAILED; rc=110; }
    rish -c 'logcat -b crash -c' >/dev/null 2>&1 || true
    rish -c "am force-stop '$package'" >/dev/null 2>&1 || true
    act="$(rish -c "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$package' 2>/dev/null | tail -n1" | tr -d '\r')"
    echo "launch_activity=$act"
    [ -n "$act" ] || { echo LAUNCH_ACTIVITY_NOT_FOUND; rc=111; }
    [ $rc -ne 0 ] || rish -c "am start -W -n '$act'" || { echo LAUNCH_FAILED; rc=112; }
  fi
  if [ $rc -eq 0 ]; then
    sleep 4
    pid="$(rish -c "pidof '$package'" 2>/dev/null | tr -d '\r')"
    echo "pid_before_stress=$pid"
    [ -n "$pid" ] || { echo PROCESS_NOT_RUNNING; rc=113; }
  fi

  # Reproduce the original trigger: Android screen recording posts an ongoing timer notification.
  if [ $rc -eq 0 ]; then
    rish -c "dumpsys gfxinfo '$package' reset" >/dev/null 2>&1 || true
    rish -c "rm -f /sdcard/Download/.relay-c6-stress.mp4; screenrecord --bit-rate 3000000 --time-limit 8 /sdcard/Download/.relay-c6-stress.mp4" >/dev/null 2>&1 &
    sr=$!
    wait "$sr" || true
    sleep 1
    gfx="$(rish -c "dumpsys gfxinfo '$package'" 2>/dev/null || true)"
    janky="$(printf '%s\n' "$gfx" | grep -m1 'Janky frames:' | sed 's/^[[:space:]]*//')"
    total_frames="$(printf '%s\n' "$gfx" | grep -m1 'Total frames rendered:' | sed 's/^[[:space:]]*//')"
    echo "gfx_total=${total_frames:-unavailable}"
    echo "gfx_janky=${janky:-unavailable}"
    rish -c "rm -f /sdcard/Download/.relay-c6-stress.mp4" >/dev/null 2>&1 || true
  fi

  if [ $rc -eq 0 ]; then
    pid="$(rish -c "pidof '$package'" 2>/dev/null | tr -d '\r')"
    echo "pid_after_stress=$pid"
    [ -n "$pid" ] || { echo PROCESS_DIED_DURING_STRESS; rc=114; }
    crashes="$(rish -c 'logcat -b crash -d -t 240' 2>/dev/null || true)"
    if printf '%s' "$crashes" | grep -q "$package" && printf '%s' "$crashes" | grep -Eq 'FATAL EXCEPTION|Process:'; then
      printf '%s\n' "$crashes" | tail -n100
      echo STRESS_FATAL_CRASH
      rc=115
    else
      echo STRESS_NO_FATAL_CRASH
    fi
  fi

  if [ -s "$signed" ]; then
    make_zip "$signed" "$signed_zip" || true
    [ -s "$signed_zip" ] && echo "return_zip_sha256=$(sha256sum "$signed_zip" | awk '{print $1}')"
    [ -s "$signed_zip" ] && echo "return_zip_bytes=$(wc -c < "$signed_zip" | tr -d ' ')"
  fi
  rm -f "$storepass" "$keypass"
  echo "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  exec 1>&9 9>&-

  if [ $rc -eq 0 ]; then
    publish_result "$job" SUCCESS 0 "$log_file" "$signed_zip" || return 116
    printf '%s\n' "$job" >> "$DONE"
    return 0
  fi
  publish_result "$job" FAILED "$rc" "$log_file" "${signed_zip:-}" || true
  return "$rc"
}

# One-shot bounded Relay finalization. Ordinary bridge jobs still delegate to Agent V3.
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
FLAG="$ROOT/relay-candidate6-finalize.json"
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$FLAG_PATH" > "$FLAG" 2>/dev/null; then
  job="$(jq -r '.job_id // empty' "$FLAG")"
  if [ -n "$job" ] && ! grep -Fxq "$job" "$DONE" 2>/dev/null; then
    run_candidate6 "$FLAG" || true
  fi
fi

git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
