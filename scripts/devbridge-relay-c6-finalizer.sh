#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
WORK="$ROOT/work/relay-c6-finalizer"
STATE="$ROOT/relay-c6-finalized.txt"
FLAG_PATH=".devbridge/relay-candidate6-finalize.json"
EXPECTED_JOB="job_relay_c6_finalize_stress_20260830_1054"
EXPECTED_CERT="fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74"

if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/KAN1409/Cortex.git"
fi

mkdir -p "$WORK" "$ROOT/logs"
touch "$STATE"

apksigner_bin(){
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1
}
aapt_bin(){
  if command -v aapt >/dev/null 2>&1; then command -v aapt; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/aapt' 2>/dev/null | sort -V | tail -n1
}
apk_cert_sha(){
  local signer="$1" apk="$2" digest
  digest="$("$signer" verify --print-certs --min-sdk-version 24 "$apk" 2>/dev/null \
    | sed -n -E 's/^.*certificate SHA-256 digest:[[:space:]]*//p' \
    | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
  [ -n "$digest" ] || return 1
  printf '%s' "$digest"
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
  elif command -v python >/dev/null 2>&1; then
    python - "$zipfile" "$out" <<'PYZIP'
import sys, zipfile
z, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(z) as f, open(out, "wb") as w:
    w.write(f.read("app-release-unsigned.apk"))
PYZIP
  else
    return 1
  fi
  [ -s "$out" ]
}
make_zip(){
  local apk="$1" out="$2"
  rm -f "$out"
  if command -v zip >/dev/null 2>&1; then
    (cd "$(dirname "$apk")" && zip -9 -j "$out" "$(basename "$apk")" >/dev/null)
  elif command -v python >/dev/null 2>&1; then
    python - "$apk" "$out" <<'PYZIP'
import os, sys, zipfile
apk, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.write(apk, arcname=os.path.basename(apk))
PYZIP
  else
    return 1
  fi
  [ -s "$out" ]
}
clone_or_fetch_results(){
  if [ ! -d "$RESULTS/.git" ]; then
    rm -rf "$RESULTS"
    git clone --filter=blob:none --no-tags "$REMOTE" "$RESULTS" >/dev/null 2>&1 || return 1
  fi
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  git -C "$RESULTS" fetch origin "$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" pull --ff-only origin "$RESULT_BRANCH" >/dev/null 2>&1 || true
}
publish_result(){
  local job="$1" status="$2" rc="$3" log="$4" artifact="${5:-}"
  local result_file tailtext artifact_rel="" artifact_sha="" artifact_size=0
  clone_or_fetch_results || return 1
  mkdir -p "$RESULTS/.devbridge/results" "$RESULTS/.devbridge/artifacts"
  if [ -n "$artifact" ] && [ -s "$artifact" ]; then
    artifact_size="$(wc -c < "$artifact" | tr -d ' ')"
    if [ "$artifact_size" -lt 100000000 ]; then
      artifact_rel=".devbridge/artifacts/${job}.zip"
      cp -f "$artifact" "$RESULTS/$artifact_rel" || return 1
      artifact_sha="$(sha256sum "$artifact" | awk '{print $1}')"
    else
      artifact_size=0
    fi
  fi
  tailtext="$(tail -c 60000 "$log" 2>/dev/null || true)"
  result_file="$RESULTS/.devbridge/results/$job.json"
  jq -n \
    --arg protocol CORTEX_DEVBRIDGE_V1 \
    --arg agent_version '3+relay-c6-finalizer' \
    --arg job_id "$job" \
    --arg capability RELAY_CANDIDATE6_FINALIZE_STRESS \
    --arg status "$status" \
    --argjson exit_code "$rc" \
    --arg finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg log_tail "$tailtext" \
    --arg artifact_path "$artifact_rel" \
    --arg artifact_sha256 "$artifact_sha" \
    --argjson artifact_size_bytes "$artifact_size" \
    '{protocol:$protocol,agent_version:$agent_version,job_id:$job_id,capability:$capability,status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail,artifact_path:$artifact_path,artifact_sha256:$artifact_sha256,artifact_size_bytes:$artifact_size_bytes}' \
    > "$result_file"
  git -C "$RESULTS" add ".devbridge/results/$job.json"
  [ -n "$artifact_rel" ] && git -C "$RESULTS" add "$artifact_rel"
  if ! git -C "$RESULTS" diff --cached --quiet; then
    git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' \
      commit -m "devbridge(relay): $job ${status,,}" >/dev/null || return 1
    git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  fi
}
run_finalize(){
  local flag="$1" job package version_code version_name artifact_url artifact_zip_sha unsigned_sha
  local log ks passfile storepass keypass signer aapt zipfile unsigned signed return_zip output_apk
  local key_cert signed_cert installed_path probe installed_cert staged install_out pkginfo act pid crashes gfx rc=0 actual

  job="$(jq -r '.job_id // empty' "$flag")"
  package="$(jq -r '.package // empty' "$flag")"
  version_code="$(jq -r '.version_code // empty' "$flag")"
  version_name="$(jq -r '.version_name // empty' "$flag")"
  artifact_url="$(jq -r '.artifact_url // empty' "$flag")"
  artifact_zip_sha="$(jq -r '.artifact_zip_sha256 // empty' "$flag")"
  unsigned_sha="$(jq -r '.unsigned_apk_sha256 // empty' "$flag")"

  [ "$job" = "$EXPECTED_JOB" ] || return 80
  [ "$(jq -r '.authorized_owner // empty' "$flag")" = 'KAN1409' ] || return 81
  [ "$package" = 'com.kareem.secondbrain' ] || return 82
  [ "$version_code" = '25' ] || return 83
  [ "$version_name" = '2.0.0-candidate6' ] || return 84
  [ "$(jq -r '.expected_cert_sha256 // empty' "$flag" | tr '[:upper:]' '[:lower:]')" = "$EXPECTED_CERT" ] || return 85
  [ -n "$artifact_url" ] && [ -n "$artifact_zip_sha" ] && [ -n "$unsigned_sha" ] || return 86

  log="$ROOT/logs/$job.log"
  : > "$log"
  exec 9>&1
  exec >> "$log" 2>&1
  echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "agent_version=3+relay-c6-finalizer"

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
    [ "$key_cert" = "$EXPECTED_CERT" ] || { echo KEYSTORE_SIGNER_MISMATCH; rc=94; }
  fi

  zipfile="$WORK/candidate6-unsigned.zip"
  unsigned="$WORK/app-release-unsigned.apk"
  signed="$WORK/Cortex-Relay-v2.0.0-candidate6-permanent.apk"
  return_zip="$WORK/${job}.zip"
  output_apk="/sdcard/Download/Cortex-Relay-v2.0.0-candidate6-permanent.apk"

  if [ $rc -eq 0 ]; then
    download_artifact "$artifact_url" "$zipfile" || { echo ARTIFACT_DOWNLOAD_FAILED; rc=95; }
  fi
  if [ $rc -eq 0 ]; then
    actual="$(sha256sum "$zipfile" | awk '{print $1}')"
    echo "artifact_zip_sha256=$actual"
    [ "$actual" = "$artifact_zip_sha" ] || { echo ARTIFACT_ZIP_SHA_MISMATCH; rc=96; }
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
      --min-sdk-version 24 \
      --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
      --out "$signed" "$unsigned" || { echo SIGN_FAILED; rc=99; }
  fi
  if [ $rc -eq 0 ]; then
    "$signer" verify --verbose --print-certs --min-sdk-version 24 "$signed"
    signed_cert="$(apk_cert_sha "$signer" "$signed")"
    echo "signed_signer_sha256=$signed_cert"
    [ "$signed_cert" = "$EXPECTED_CERT" ] || { echo SIGNED_SIGNER_MISMATCH; rc=100; }
    echo "signed_apk_sha256=$(sha256sum "$signed" | awk '{print $1}')"
    "$aapt" dump badging "$signed" | head -n1
    "$aapt" dump badging "$signed" | head -n1 | grep -q "versionCode='$version_code'" || { echo VERSION_CODE_BADGING_FAIL; rc=101; }
    [ $rc -ne 0 ] || "$aapt" dump badging "$signed" | head -n1 | grep -q "versionName='$version_name'" || { echo VERSION_NAME_BADGING_FAIL; rc=102; }
  fi

  if [ $rc -eq 0 ]; then
    installed_path="$(rish -c "/system/bin/pm path '$package'" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
    [ -n "$installed_path" ] || installed_path="$(rish -c "cmd package path '$package'" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
    echo "installed_path=$installed_path"
    [ -n "$installed_path" ] || { echo INSTALLED_APK_NOT_FOUND; rc=103; }
  fi
  probe="/sdcard/Download/.relay-c6-installed-probe.apk"
  if [ $rc -eq 0 ]; then
    rish -c "rm -f '$probe'; cp '$installed_path' '$probe'; chmod 644 '$probe'" || { echo INSTALLED_COPY_FAILED; rc=104; }
    installed_cert="$(apk_cert_sha "$signer" "$probe")"
    echo "installed_signer_sha256=$installed_cert"
    rish -c "rm -f '$probe'" >/dev/null 2>&1 || true
    [ "$installed_cert" = "$EXPECTED_CERT" ] || { echo INSTALLED_SIGNER_MISMATCH; rc=105; }
  fi

  if [ $rc -eq 0 ]; then
    staged="/data/local/tmp/Cortex-Relay-v2.0.0-candidate6-permanent.apk"
    cat "$signed" | rish -c "cat > '$staged'; chmod 644 '$staged'" || { echo APK_STAGE_FAILED; rc=106; }
    install_out="$(rish -c "pm install -r '$staged'; x=\$?; rm -f '$staged'; exit \$x" 2>&1)"
    printf '%s\n' "$install_out"
    printf '%s\n' "$install_out" | grep -q 'Success' || { echo UPDATE_INSTALL_FAILED; rc=107; }
  fi
  if [ $rc -eq 0 ]; then
    pkginfo="$(rish -c "dumpsys package '$package' | grep -E 'versionCode=|versionName=|lastUpdateTime=' | head -n6" 2>/dev/null)"
    printf '%s\n' "$pkginfo"
    printf '%s\n' "$pkginfo" | grep -q "versionCode=$version_code" || { echo INSTALLED_VERSION_CODE_FAIL; rc=108; }
    [ $rc -ne 0 ] || printf '%s\n' "$pkginfo" | grep -q "versionName=$version_name" || { echo INSTALLED_VERSION_NAME_FAIL; rc=109; }
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
  if [ $rc -eq 0 ]; then
    rish -c "dumpsys gfxinfo '$package' reset" >/dev/null 2>&1 || true
    rish -c "rm -f /sdcard/Download/.relay-c6-stress.mp4; screenrecord --bit-rate 3000000 --time-limit 8 /sdcard/Download/.relay-c6-stress.mp4" >/dev/null 2>&1 || true
    sleep 1
    gfx="$(rish -c "dumpsys gfxinfo '$package'" 2>/dev/null || true)"
    printf '%s\n' "$gfx" | grep -m1 -E 'Total frames rendered:|Janky frames:' || true
    printf '%s\n' "$gfx" | grep -E 'Total frames rendered:|Janky frames:' | head -n2 || true
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
    make_zip "$signed" "$return_zip" || true
    [ -s "$return_zip" ] && echo "return_zip_sha256=$(sha256sum "$return_zip" | awk '{print $1}')"
    [ -s "$return_zip" ] && echo "return_zip_bytes=$(wc -c < "$return_zip" | tr -d ' ')"
  fi
  rm -f "$storepass" "$keypass"
  echo "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  exec 1>&9 9>&-

  if [ $rc -eq 0 ]; then
    publish_result "$job" SUCCESS 0 "$log" "$return_zip" || return 116
    printf '%s\n' "$job" >> "$STATE"
    return 0
  fi
  publish_result "$job" FAILED "$rc" "$log" "${return_zip:-}" || true
  return "$rc"
}

FLAG="$ROOT/relay-candidate6-finalize.json"
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || exit 0
if ! git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$FLAG_PATH" > "$FLAG" 2>/dev/null; then
  exit 0
fi
job="$(jq -r '.job_id // empty' "$FLAG")"
[ "$job" = "$EXPECTED_JOB" ] || exit 0
grep -Fxq "$job" "$STATE" 2>/dev/null && exit 0
run_finalize "$FLAG"
