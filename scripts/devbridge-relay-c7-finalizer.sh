#!/data/data/com.termux/files/usr/bin/bash
set -u

ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
FLAG_PATH='.devbridge/relay-candidate7-finalize.json'
STATE="$ROOT/relay-candidate7-finalized.txt"
WORK="$ROOT/work/relay-candidate7-finalizer"
PKG='com.kareem.secondbrain'
EXPECTED_CERT='fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74'
EXPECTED_VERSION_CODE='26'
EXPECTED_VERSION_NAME='2.0.0-candidate7'
KEYSTORE="$HOME/.secondbrain-signing/second-brain-permanent.p12"
PASSWORD_FILE="$HOME/.secondbrain-signing/password.txt"
KEY_ALIAS='secondbrain'

mkdir -p "$ROOT" "$WORK"
touch "$STATE"

if [ -d "$LOCAL_REPO/.git" ]; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin 2>/dev/null || true)"
else
  REMOTE=''
fi
[ -n "$REMOTE" ] || REMOTE='https://github.com/KAN1409/Cortex.git'

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

read_git_credential(){
  local key="$1" cred
  cred="$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null || true)"
  printf '%s\n' "$cred" | sed -n "s/^${key}=//p" | head -n1
}

download_artifact(){
  local url="$1" out="$2" token user rc
  rm -f "$out"
  token="$(read_git_credential password)"
  user="$(read_git_credential username)"
  if [ -n "$token" ]; then
    curl -L --fail --retry 2 --connect-timeout 20 --max-time 900 \
      -u "${user:-x-access-token}:$token" \
      -H 'Accept: application/vnd.github+json' \
      -H 'X-GitHub-Api-Version: 2022-11-28' \
      "$url" -o "$out" >/dev/null 2>&1
    rc=$?
    unset token user
    [ $rc -eq 0 ] && return 0
  fi
  unset token user
  curl -L --fail --retry 2 --connect-timeout 20 --max-time 900 \
    -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' \
    "$url" -o "$out" >/dev/null 2>&1
}

extract_unsigned(){
  local zipfile="$1" out="$2"
  rm -f "$out"
  if command -v unzip >/dev/null 2>&1; then
    unzip -p "$zipfile" app-release-unsigned.apk > "$out" 2>/dev/null || return 1
  elif command -v python >/dev/null 2>&1; then
    python - "$zipfile" "$out" <<'PY'
import sys, zipfile
src, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as z, open(out, 'wb') as w:
    w.write(z.read('app-release-unsigned.apk'))
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
  local job="$1" status="$2" rc="$3" log="$4" artifact_zip="${5:-}"
  local result artifact_rel='' artifact_sha='' artifact_size=0 tailtext
  if [ ! -d "$RESULTS/.git" ]; then
    rm -rf "$RESULTS"
    git clone --filter=blob:none --no-tags "$REMOTE" "$RESULTS" >/dev/null 2>&1 || return 1
  fi
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" fetch origin "$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" reset --hard "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  mkdir -p "$RESULTS/.devbridge/results" "$RESULTS/.devbridge/artifacts"
  if [ -n "$artifact_zip" ] && [ -s "$artifact_zip" ]; then
    artifact_size="$(wc -c < "$artifact_zip" | tr -d ' ')"
    if [ "$artifact_size" -lt 100000000 ]; then
      artifact_rel=".devbridge/artifacts/${job}.zip"
      cp -f "$artifact_zip" "$RESULTS/$artifact_rel" || return 1
      artifact_sha="$(sha256sum "$artifact_zip" | awk '{print $1}')"
    else
      artifact_size=0
    fi
  fi
  tailtext="$(tail -c 60000 "$log" 2>/dev/null || true)"
  result="$RESULTS/.devbridge/results/$job.json"
  jq -n \
    --arg protocol CORTEX_DEVBRIDGE_V1 \
    --arg agent_version '3+relay-c7-finalizer' \
    --arg job_id "$job" \
    --arg capability 'RELAY_CANDIDATE7_FINALIZE_UI_BUS' \
    --arg status "$status" \
    --argjson exit_code "$rc" \
    --arg finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg log_tail "$tailtext" \
    --arg artifact_path "$artifact_rel" \
    --arg artifact_sha256 "$artifact_sha" \
    --argjson artifact_size_bytes "$artifact_size" \
    '{protocol:$protocol,agent_version:$agent_version,job_id:$job_id,capability:$capability,status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail,artifact_path:$artifact_path,artifact_sha256:$artifact_sha256,artifact_size_bytes:$artifact_size_bytes}' \
    > "$result"
  git -C "$RESULTS" add ".devbridge/results/$job.json"
  [ -n "$artifact_rel" ] && git -C "$RESULTS" add "$artifact_rel"
  git -C "$RESULTS" diff --cached --quiet && return 0
  git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' \
    commit -m "devbridge(relay): $job ${status,,}" >/dev/null || return 1
  git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1
}

ui_probe(){
  local xml='/sdcard/Download/.cortex-relay-c7-ui.xml' local_xml="$WORK/ui.xml" density raw_density
  rish -c "uiautomator dump '$xml' >/dev/null 2>&1" || return 1
  cp -f "$xml" "$local_xml" 2>/dev/null || {
    rish -c "cp '$xml' /sdcard/Download/.cortex-relay-c7-ui-copy.xml" >/dev/null 2>&1 || return 1
    cp -f /sdcard/Download/.cortex-relay-c7-ui-copy.xml "$local_xml" 2>/dev/null || return 1
  }
  raw_density="$(rish -c 'wm density' 2>/dev/null | tr -d '\r' | tail -n1)"
  density="$(printf '%s' "$raw_density" | sed -n -E 's/.*: ([0-9]+).*/\1/p')"
  [ -n "$density" ] || density=420
  python - "$local_xml" "$density" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, density = sys.argv[1], int(sys.argv[2])
root = ET.parse(path).getroot()
def bounds(node):
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    return tuple(map(int,m.groups())) if m else None
header = None
ready = False
connected = False
waiting = None
texts=[]
for n in root.iter('node'):
    t=n.attrib.get('text','')
    if t: texts.append(t)
    if t == 'Cortex Relay' and header is None: header=bounds(n)
    if t == 'Ready': ready=True
    if t.startswith('Connected'): connected=True
# Find the numeric value paired with Waiting / in flight in document order.
for i,t in enumerate(texts):
    if t == 'Waiting / in flight':
        for v in texts[i+1:i+5]:
            if v.isdigit(): waiting=int(v); break
        break
threshold=max(48, round(32*density/160))
print('ui_header_bounds=' + (str(header) if header else 'missing'))
print('ui_safe_top_threshold_px=' + str(threshold))
print('ui_safe_top=' + ('true' if header and header[1] >= threshold else 'false'))
print('ui_ready=' + str(ready).lower())
print('ui_connected=' + str(connected).lower())
print('ui_waiting=' + ('unknown' if waiting is None else str(waiting)))
if not header or header[1] < threshold: raise SystemExit(41)
PY
}

main(){
  [ -d "$CONTROL/.git" ] || return 0
  git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || return 0
  local flag="$ROOT/relay-candidate7-finalize.json"
  git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$FLAG_PATH" > "$flag" 2>/dev/null || return 0
  local job artifact_url artifact_zip_sha unsigned_sha log zip unsigned signed signer ks_pass key_pass key_cert installed_path probe installed_cert signed_cert verify staged install_out pkg_info rc=0 artifact_zip
  job="$(jq -r '.job_id // empty' "$flag")"
  [ "$job" = 'job_relay_candidate7_ui_bus_20260830_1816' ] || return 0
  grep -Fxq "$job" "$STATE" 2>/dev/null && return 0
  [ "$(jq -r '.authorized_owner // empty' "$flag")" = 'KAN1409' ] || return 0
  artifact_url="$(jq -r '.artifact_url // empty' "$flag")"
  artifact_zip_sha="$(jq -r '.artifact_zip_sha256 // empty' "$flag")"
  unsigned_sha="$(jq -r '.unsigned_apk_sha256 // empty' "$flag")"
  [ -n "$artifact_url" ] && [ -n "$artifact_zip_sha" ] && [ -n "$unsigned_sha" ] || return 0

  log="$ROOT/logs/$job.log"
  mkdir -p "$ROOT/logs" "$WORK"
  : > "$log"
  exec 9>&1
  exec >> "$log" 2>&1
  echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo 'agent_version=3+relay-c7-finalizer'

  signer="$(apksigner_bin)"
  [ -n "$signer" ] || { echo APKSIGNER_NOT_FOUND; rc=90; }
  [ $rc -ne 0 ] || [ -s "$KEYSTORE" ] || { echo PERMANENT_KEYSTORE_NOT_FOUND; rc=91; }
  [ $rc -ne 0 ] || [ -s "$PASSWORD_FILE" ] || { echo PASSWORD_FILE_NOT_FOUND; rc=92; }

  ks_pass="$WORK/ks-pass.txt"
  key_pass="$WORK/key-pass.txt"
  if [ $rc -eq 0 ]; then
    tr -d '\r\n' < "$PASSWORD_FILE" > "$ks_pass"
    cp -f "$ks_pass" "$key_pass"
    chmod 600 "$ks_pass" "$key_pass"
    key_cert="$(keytool -list -v -storetype PKCS12 -keystore "$KEYSTORE" -storepass "$(cat "$ks_pass")" -alias "$KEY_ALIAS" 2>/dev/null \
      | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
    echo "keystore_signer_sha256=$key_cert"
    [ "$key_cert" = "$EXPECTED_CERT" ] || { echo KEYSTORE_SIGNER_MISMATCH; rc=93; }
  fi

  zip="$WORK/candidate7-unsigned.zip"
  unsigned="$WORK/app-release-unsigned.apk"
  signed="$WORK/Cortex-Relay-v2.0.0-candidate7-permanent.apk"
  artifact_zip="$WORK/${job}.zip"

  if [ $rc -eq 0 ]; then
    download_artifact "$artifact_url" "$zip" || { echo ARTIFACT_DOWNLOAD_FAILED; rc=94; }
  fi
  if [ $rc -eq 0 ]; then
    actual="$(sha256sum "$zip" | awk '{print $1}')"
    echo "artifact_zip_sha256=$actual"
    [ "$actual" = "$artifact_zip_sha" ] || { echo ARTIFACT_ZIP_SHA_MISMATCH; rc=95; }
  fi
  if [ $rc -eq 0 ]; then
    extract_unsigned "$zip" "$unsigned" || { echo UNSIGNED_EXTRACT_FAILED; rc=96; }
    actual="$(sha256sum "$unsigned" | awk '{print $1}')"
    echo "unsigned_apk_sha256=$actual"
    [ "$actual" = "$unsigned_sha" ] || { echo UNSIGNED_APK_SHA_MISMATCH; rc=97; }
  fi

  if [ $rc -eq 0 ]; then
    rm -f "$signed" "$signed.idsig"
    "$signer" sign \
      --ks "$KEYSTORE" --ks-type PKCS12 --ks-key-alias "$KEY_ALIAS" \
      --ks-pass "file:$ks_pass" --key-pass "file:$key_pass" \
      --min-sdk-version 24 \
      --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
      --out "$signed" "$unsigned" || { echo SIGN_FAILED; rc=98; }
  fi
  if [ $rc -eq 0 ]; then
    verify="$("$signer" verify --verbose --print-certs --min-sdk-version 24 "$signed" 2>&1)"
    printf '%s\n' "$verify" | grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' || { echo V2_VERIFY_FAILED; rc=99; }
    [ $rc -ne 0 ] || printf '%s\n' "$verify" | grep -q 'Verified using v3 scheme (APK Signature Scheme v3): true' || { echo V3_VERIFY_FAILED; rc=100; }
  fi
  if [ $rc -eq 0 ]; then
    signed_cert="$(apk_cert "$signer" "$signed")"
    echo "signed_signer_sha256=$signed_cert"
    [ "$signed_cert" = "$EXPECTED_CERT" ] || { echo SIGNED_SIGNER_MISMATCH; rc=101; }
    echo "signed_apk_sha256=$(sha256sum "$signed" | awk '{print $1}')"
  fi

  if [ $rc -eq 0 ]; then
    installed_path="$(rish -c "pm path '$PKG'" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
    [ -n "$installed_path" ] || { echo INSTALLED_APK_NOT_FOUND; rc=102; }
  fi
  if [ $rc -eq 0 ]; then
    probe='/sdcard/Download/.cortex-relay-installed-c7-probe.apk'
    rish -c "cp '$installed_path' '$probe'; chmod 644 '$probe'" >/dev/null 2>&1 || { echo INSTALLED_APK_COPY_FAILED; rc=103; }
  fi
  if [ $rc -eq 0 ]; then
    installed_cert="$(apk_cert "$signer" "$probe")"
    echo "installed_signer_sha256=$installed_cert"
    [ "$installed_cert" = "$EXPECTED_CERT" ] || { echo INSTALLED_SIGNER_MISMATCH; rc=104; }
  fi

  if [ $rc -eq 0 ]; then
    cp -f "$signed" /sdcard/Download/Cortex-Relay-v2.0.0-candidate7-permanent.apk || { echo DOWNLOAD_COPY_FAILED; rc=105; }
    staged='/data/local/tmp/Cortex-Relay-v2.0.0-candidate7-permanent.apk'
    rish -c "cp /sdcard/Download/Cortex-Relay-v2.0.0-candidate7-permanent.apk '$staged'; chmod 644 '$staged'" >/dev/null 2>&1 || { echo APK_STAGE_FAILED; rc=106; }
  fi
  if [ $rc -eq 0 ]; then
    install_out="$(rish -c "pm install -r '$staged'; x=\$?; rm -f '$staged'; exit \$x" 2>&1)"
    printf '%s\n' "$install_out"
    printf '%s\n' "$install_out" | grep -q 'Success' || { echo UPDATE_INSTALL_FAILED; rc=107; }
  fi
  if [ $rc -eq 0 ]; then
    pkg_info="$(rish -c "dumpsys package '$PKG'" 2>/dev/null | grep -E 'versionCode=|versionName=' | head -n6)"
    printf '%s\n' "$pkg_info"
    printf '%s\n' "$pkg_info" | grep -Eq "versionCode=${EXPECTED_VERSION_CODE}([[:space:]]|$)" || { echo INSTALLED_VERSION_CODE_FAIL; rc=108; }
    [ $rc -ne 0 ] || printf '%s\n' "$pkg_info" | grep -Fq "versionName=$EXPECTED_VERSION_NAME" || { echo INSTALLED_VERSION_NAME_FAIL; rc=109; }
  fi

  if [ $rc -eq 0 ]; then
    rish -c 'logcat -b crash -c' >/dev/null 2>&1 || true
    rish -c "am force-stop '$PKG'; ACT=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$PKG' 2>/dev/null | tail -n1); am start -W -n \"\$ACT\"" 2>&1 || { echo LAUNCH_FAILED; rc=110; }
    sleep 15
    echo "pid=$(rish -c "pidof '$PKG'" 2>/dev/null | tr -d '\r')"
    crashes="$(rish -c 'logcat -b crash -d -t 250' 2>/dev/null)"
    if printf '%s' "$crashes" | grep -q "$PKG" && printf '%s' "$crashes" | grep -Eq 'FATAL EXCEPTION|Process:'; then
      echo SMOKE_CRASH
      rc=111
    else
      echo SMOKE_NO_FATAL_CRASH
    fi
  fi

  if [ $rc -eq 0 ]; then
    ui_probe || { p=$?; echo UI_SAFE_INSETS_PROBE_FAILED; rc=$((120+p)); }
  fi
  if [ $rc -eq 0 ]; then
    bus="$(rish -c 'dumpsys activity services com.kareem.cortex.rebuild' 2>/dev/null | grep -E 'com\.kareem\.cortex\.rebuild.*CortexLocalBusService|com\.kareem\.secondbrain' | head -n30)"
    printf '%s\n' "$bus" | sed 's/^/bus_probe=/'
    if printf '%s\n' "$bus" | grep -Fq 'CortexLocalBusService'; then echo 'bus_service_present=true'; else echo 'bus_service_present=false'; fi
  fi

  # Give the durable queue a little time to drain, then re-read only the safe UI labels.
  if [ $rc -eq 0 ]; then
    sleep 15
    ui_probe || { p=$?; echo UI_POST_DRAIN_PROBE_FAILED; rc=$((180+p)); }
  fi

  if [ $rc -eq 0 ]; then
    make_zip "$signed" "$artifact_zip" || { echo RETURN_ARTIFACT_ZIP_FAILED; rc=112; }
    echo "return_zip_sha256=$(sha256sum "$artifact_zip" | awk '{print $1}')"
    echo "return_zip_bytes=$(wc -c < "$artifact_zip" | tr -d ' ')"
  fi

  rm -f "$ks_pass" "$key_pass" /sdcard/Download/.cortex-relay-installed-c7-probe.apk /sdcard/Download/.cortex-relay-c7-ui.xml /sdcard/Download/.cortex-relay-c7-ui-copy.xml 2>/dev/null || true
  echo "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  exec 1>&9 9>&-

  if [ $rc -eq 0 ]; then
    publish_result "$job" SUCCESS 0 "$log" "$artifact_zip" || return 1
    printf '%s\n' "$job" >> "$STATE"
  else
    publish_result "$job" FAILED "$rc" "$log" "${artifact_zip:-}" || true
  fi
}

main
exit 0
