#!/data/data/com.termux/files/usr/bin/bash
set -u

ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
FLAG_PATH='.devbridge/relay-candidate8-install-retry.json'
WORK="$ROOT/work/relay-candidate8-finalizer"
PKG='com.kareem.secondbrain'
EXPECTED_CERT='fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74'
EXPECTED_SIGNED_SHA='1b2e91b76242ea1be1443c33d77955750a4906f42bdc2d1a4c6ca2724a0b50dd'
EXPECTED_VERSION_CODE='27'
EXPECTED_VERSION_NAME='2.0.0-candidate8'
SIGNED="$WORK/Cortex-Relay-v2.0.0-candidate8-permanent.apk"

if [ -d "$LOCAL_REPO/.git" ]; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin 2>/dev/null || true)"
else
  REMOTE=''
fi
[ -n "$REMOTE" ] || REMOTE='https://github.com/KAN1409/Cortex.git'
mkdir -p "$ROOT/logs" "$WORK"

apksigner_bin(){
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1
}

apk_cert(){
  local signer="$1" apk="$2"
  "$signer" verify --print-certs --min-sdk-version 24 "$apk" 2>/dev/null \
    | sed -n -E 's/^.*certificate SHA-256 digest:[[:space:]]*//p' \
    | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
}

publish_result(){
  local job="$1" status="$2" rc="$3" log="$4" result tailtext
  if [ ! -d "$RESULTS/.git" ]; then
    rm -rf "$RESULTS"
    git clone --filter=blob:none --no-tags "$REMOTE" "$RESULTS" >/dev/null 2>&1 || return 1
  fi
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" fetch origin "$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" reset --hard "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  mkdir -p "$RESULTS/.devbridge/results"
  tailtext="$(tail -c 60000 "$log" 2>/dev/null || true)"
  result="$RESULTS/.devbridge/results/$job.json"
  jq -n \
    --arg protocol CORTEX_DEVBRIDGE_V1 \
    --arg agent_version '3+relay-c8-install-retry' \
    --arg job_id "$job" \
    --arg capability 'RELAY_CANDIDATE8_INSTALL_RETRY_NO_LAUNCH' \
    --arg status "$status" \
    --argjson exit_code "$rc" \
    --arg finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg log_tail "$tailtext" \
    '{protocol:$protocol,agent_version:$agent_version,job_id:$job_id,capability:$capability,status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail}' \
    > "$result"
  git -C "$RESULTS" add ".devbridge/results/$job.json"
  git -C "$RESULTS" diff --cached --quiet && return 0
  git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' \
    commit -m "devbridge(relay): $job ${status,,}" >/dev/null || return 1
  git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1
}

main(){
  [ -d "$CONTROL/.git" ] || return 0
  git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || return 0
  local flag="$ROOT/relay-candidate8-install-retry.json"
  git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$FLAG_PATH" > "$flag" 2>/dev/null || return 0
  local job lock log signer signed_sha signed_cert installed_path code_path probe installed_cert staged install_out pkg_info rc=0
  job="$(jq -r '.job_id // empty' "$flag")"
  [ "$job" = 'job_relay_candidate8_install_retry_20260830_1927' ] || return 0
  [ "$(jq -r '.authorized_owner // empty' "$flag")" = 'KAN1409' ] || return 0
  [ "$(jq -r '.package // empty' "$flag")" = "$PKG" ] || return 0

  lock="$ROOT/${job}.attempted"
  [ -e "$lock" ] && return 0
  printf '%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$lock"
  log="$ROOT/logs/$job.log"
  : > "$log"
  exec 9>&1
  exec >> "$log" 2>&1
  echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo 'agent_version=3+relay-c8-install-retry'
  echo 'foreground_launch_requested=false'

  signer="$(apksigner_bin)"
  [ -n "$signer" ] || { echo APKSIGNER_NOT_FOUND; rc=90; }
  [ $rc -ne 0 ] || [ -s "$SIGNED" ] || { echo SIGNED_APK_NOT_FOUND; rc=91; }
  if [ $rc -eq 0 ]; then
    signed_sha="$(sha256sum "$SIGNED" | awk '{print $1}')"
    signed_cert="$(apk_cert "$signer" "$SIGNED")"
    echo "signed_apk_sha256=$signed_sha"
    echo "signed_signer_sha256=$signed_cert"
    [ "$signed_sha" = "$EXPECTED_SIGNED_SHA" ] || { echo SIGNED_APK_SHA_MISMATCH; rc=92; }
    [ $rc -ne 0 ] || [ "$signed_cert" = "$EXPECTED_CERT" ] || { echo SIGNED_SIGNER_MISMATCH; rc=93; }
  fi

  if [ $rc -eq 0 ]; then
    installed_path="$(rish -c "/system/bin/pm path '$PKG'" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
    if [ -z "$installed_path" ]; then
      installed_path="$(rish -c "cmd package path '$PKG'" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
    fi
    if [ -z "$installed_path" ]; then
      code_path="$(rish -c "dumpsys package '$PKG'" 2>/dev/null | tr -d '\r' | sed -n 's/^[[:space:]]*codePath=//p' | head -n1)"
      [ -n "$code_path" ] && installed_path="${code_path%/}/base.apk"
    fi
    echo "installed_path=${installed_path:-missing}"
    [ -n "$installed_path" ] || { echo INSTALLED_APK_NOT_FOUND; rc=94; }
  fi

  if [ $rc -eq 0 ]; then
    probe='/sdcard/Download/.cortex-relay-c8-installed-probe.apk'
    rish -c "cp '$installed_path' '$probe'; chmod 644 '$probe'" >/dev/null 2>&1 || { echo INSTALLED_APK_COPY_FAILED; rc=95; }
  fi
  if [ $rc -eq 0 ]; then
    installed_cert="$(apk_cert "$signer" "$probe")"
    echo "installed_signer_sha256=$installed_cert"
    rish -c "rm -f '$probe'" >/dev/null 2>&1 || true
    [ "$installed_cert" = "$EXPECTED_CERT" ] || { echo INSTALLED_SIGNER_MISMATCH; rc=96; }
    [ $rc -ne 0 ] || [ "$installed_cert" = "$signed_cert" ] || { echo SIGNER_CONTINUITY_MISMATCH; rc=97; }
  fi

  if [ $rc -eq 0 ]; then
    staged='/data/local/tmp/Cortex-Relay-v2.0.0-candidate8-permanent.apk'
    cat "$SIGNED" | rish -c "cat > '$staged'; chmod 644 '$staged'" || { echo APK_STAGE_FAILED; rc=98; }
  fi
  if [ $rc -eq 0 ]; then
    install_out="$(rish -c "pm install -r '$staged'; install_rc=\$?; rm -f '$staged'; exit \$install_rc" 2>&1)"
    printf '%s\n' "$install_out"
    printf '%s\n' "$install_out" | grep -q 'Success' || { echo UPDATE_INSTALL_FAILED; rc=99; }
  fi
  if [ $rc -eq 0 ]; then
    pkg_info="$(rish -c "dumpsys package '$PKG' | grep -E 'versionCode=|versionName=' | head -n4" 2>/dev/null | tr -d '\r')"
    printf '%s\n' "$pkg_info"
    printf '%s\n' "$pkg_info" | grep -Fq "versionCode=$EXPECTED_VERSION_CODE" || { echo INSTALLED_VERSION_CODE_FAIL; rc=100; }
    [ $rc -ne 0 ] || printf '%s\n' "$pkg_info" | grep -Fq "versionName=$EXPECTED_VERSION_NAME" || { echo INSTALLED_VERSION_NAME_FAIL; rc=101; }
  fi
  if [ $rc -eq 0 ]; then
    cp -f "$SIGNED" '/sdcard/Download/Cortex-Relay-v2.0.0-candidate8-permanent.apk' || { echo DOWNLOAD_COPY_FAILED; rc=102; }
    echo 'foreground_launch_executed=false'
    echo CORTEX_RELAY_CANDIDATE8_BACKGROUND_UPDATE_SUCCESS
  fi

  echo "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  exec 1>&9 9>&-
  if [ $rc -eq 0 ]; then
    publish_result "$job" SUCCESS 0 "$log" || return 120
  else
    publish_result "$job" FAILED "$rc" "$log" || true
  fi
  return "$rc"
}

main || true
exit 0
