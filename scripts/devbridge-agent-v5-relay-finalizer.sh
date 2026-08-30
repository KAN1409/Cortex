#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
WORK="$ROOT/work/relay-candidate5-finalizer-v2"
FLAG_PATH=".devbridge/relay-candidate5-finalize.json"
STATE="$ROOT/relay-candidate5-finalized-v2.txt"
if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then REMOTE="$CORTEX_DEVBRIDGE_REMOTE"; elif [ -d "$LOCAL_REPO/.git" ]; then REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"; else REMOTE="https://github.com/KAN1409/Cortex.git"; fi

apksigner_bin(){ command -v apksigner 2>/dev/null || find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1; }
cert_of_apk(){
  local signer="$1" apk="$2"
  "$signer" verify --print-certs --min-sdk-version 24 "$apk" 2>/dev/null \
    | grep -i 'certificate SHA-256 digest:' | head -n1 \
    | sed -E 's/^.*certificate SHA-256 digest:[[:space:]]*//' \
    | tr -d ':\r\n ' | tr '[:upper:]' '[:lower:]'
}
git_cred(){ printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null || true; }
download_artifact(){
  local url="$1" out="$2" cred user pass
  rm -f "$out"
  curl -L --fail --retry 2 --connect-timeout 20 --max-time 600 -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' "$url" -o "$out" >/dev/null 2>&1 && return 0
  cred="$(git_cred)"; user="$(printf '%s\n' "$cred" | sed -n 's/^username=//p' | head -n1)"; pass="$(printf '%s\n' "$cred" | sed -n 's/^password=//p' | head -n1)"
  [ -n "$pass" ] || return 1
  curl -L --fail --retry 2 --connect-timeout 20 --max-time 600 -u "${user:-x-access-token}:$pass" -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' "$url" -o "$out" >/dev/null 2>&1
}
extract_apk(){
  local z="$1" out="$2"
  if command -v unzip >/dev/null 2>&1; then unzip -p "$z" app-release-unsigned.apk > "$out" 2>/dev/null; else python - "$z" "$out" <<'PY'
import sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as z, open(sys.argv[2],'wb') as f: f.write(z.read('app-release-unsigned.apk'))
PY
  fi
  [ -s "$out" ]
}
zip_apk(){
  local apk="$1" out="$2"
  if command -v zip >/dev/null 2>&1; then (cd "$(dirname "$apk")" && zip -9 -j "$out" "$(basename "$apk")" >/dev/null); else python - "$apk" "$out" <<'PY'
import os,sys,zipfile
with zipfile.ZipFile(sys.argv[2],'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:z.write(sys.argv[1],os.path.basename(sys.argv[1]))
PY
  fi
}
publish(){
  local job="$1" status="$2" rc="$3" log="$4" artifact="${5:-}" result rel='' sha='' size=0 tail
  if [ ! -d "$RESULTS/.git" ]; then rm -rf "$RESULTS"; git clone --filter=blob:none --no-tags "$REMOTE" "$RESULTS" >/dev/null 2>&1 || return 1; fi
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  git -C "$RESULTS" fetch origin "$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" pull --ff-only origin "$RESULT_BRANCH" >/dev/null 2>&1 || true
  mkdir -p "$RESULTS/.devbridge/results" "$RESULTS/.devbridge/artifacts"
  if [ -n "$artifact" ] && [ -s "$artifact" ]; then
    size="$(wc -c < "$artifact" | tr -d ' ')"
    if [ "$size" -lt 100000000 ]; then rel=".devbridge/artifacts/${job}.zip"; cp -f "$artifact" "$RESULTS/$rel"; sha="$(sha256sum "$artifact"|awk '{print $1}')"; else size=0; fi
  fi
  tail="$(tail -c 60000 "$log" 2>/dev/null || true)"; result="$RESULTS/.devbridge/results/$job.json"
  jq -n --arg protocol CORTEX_DEVBRIDGE_V1 --arg agent_version '5-relay-finalizer' --arg job_id "$job" --arg status "$status" --argjson exit_code "$rc" --arg finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg log_tail "$tail" --arg artifact_path "$rel" --arg artifact_sha256 "$sha" --argjson artifact_size "$size" '{protocol:$protocol,agent_version:$agent_version,job_id:$job_id,capability:"RELAY_CANDIDATE5_FINALIZE",status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail,artifact_path:$artifact_path,artifact_sha256:$artifact_sha256,artifact_size_bytes:$artifact_size}' > "$result"
  git -C "$RESULTS" add ".devbridge/results/$job.json"; [ -n "$rel" ] && git -C "$RESULTS" add "$rel"
  git -C "$RESULTS" diff --cached --quiet && return 0
  git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' commit -m "devbridge(relay): $job ${status,,}" >/dev/null || return 1
  git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1
}
finalize(){
  local flag="$1" job expected package vc vn url zipsha apksha log rc=0 signer ks passfile pass keycert zip unsigned signed installed_path installed signedcert verify staged out info pid crashes returnzip
  job="$(jq -r '.job_id' "$flag")"; expected="$(jq -r '.expected_cert_sha256' "$flag"|tr '[:upper:]' '[:lower:]')"; package="$(jq -r '.package' "$flag")"; vc="$(jq -r '.version_code' "$flag")"; vn="$(jq -r '.version_name' "$flag")"; url="$(jq -r '.artifact_url' "$flag")"; zipsha="$(jq -r '.artifact_zip_sha256' "$flag")"; apksha="$(jq -r '.unsigned_apk_sha256' "$flag")"
  [ "$job" = job_relay_candidate5_finalize_v2_20260830 ] || return 80; [ "$package" = com.kareem.secondbrain ] || return 81; [ "$expected" = fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74 ] || return 82; [ "$vc" = 24 ] || return 83; [ "$vn" = 2.0.0-candidate5 ] || return 84
  mkdir -p "$WORK" "$ROOT/logs"; log="$ROOT/logs/$job.log"; :>"$log"
  echo "started_at=$(date -u +%FT%TZ)" >>"$log"
  signer="$(apksigner_bin)"; ks="$HOME/.secondbrain-signing/second-brain-permanent.p12"; passfile="$HOME/.secondbrain-signing/password.txt"
  [ -n "$signer" ] || { echo APKSIGNER_NOT_FOUND >>"$log"; rc=90; }; [ $rc -ne 0 ] || [ -f "$ks" ] || { echo KEYSTORE_MISSING >>"$log"; rc=91; }; [ $rc -ne 0 ] || [ -s "$passfile" ] || { echo PASSWORD_MISSING >>"$log"; rc=92; }
  if [ $rc -eq 0 ]; then pass="$(tr -d '\r\n' <"$passfile")"; keycert="$(keytool -list -v -storetype PKCS12 -keystore "$ks" -storepass "$pass" -alias secondbrain 2>/dev/null|sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p'|head -n1|tr -d ':\r\n '|tr '[:upper:]' '[:lower:]')"; echo "keystore_signer_sha256=$keycert">>"$log"; [ "$keycert" = "$expected" ] || rc=93; fi
  zip="$WORK/unsigned.zip"; unsigned="$WORK/app-release-unsigned.apk"; signed="$WORK/Cortex-Relay-v2.0.0-candidate5-permanent.apk"; returnzip="$WORK/$job.zip"
  if [ $rc -eq 0 ]; then download_artifact "$url" "$zip" || rc=94; fi
  if [ $rc -eq 0 ]; then a="$(sha256sum "$zip"|awk '{print $1}')"; echo "artifact_zip_sha256=$a">>"$log"; [ "$a" = "$zipsha" ] || rc=95; fi
  if [ $rc -eq 0 ]; then extract_apk "$zip" "$unsigned" || rc=96; fi
  if [ $rc -eq 0 ]; then a="$(sha256sum "$unsigned"|awk '{print $1}')"; echo "unsigned_apk_sha256=$a">>"$log"; [ "$a" = "$apksha" ] || rc=97; fi
  if [ $rc -eq 0 ]; then installed_path="$(rish -c "pm path '$package'" 2>/dev/null|sed -n 's/^package://p'|grep 'base.apk$'|head -n1)"; [ -n "$installed_path" ] || rc=98; fi
  if [ $rc -eq 0 ]; then installed="$WORK/installed.apk"; rish -c "cat '$installed_path'" >"$installed" 2>/dev/null || rc=99; fi
  if [ $rc -eq 0 ]; then c="$(cert_of_apk "$signer" "$installed")"; echo "installed_signer_sha256=$c">>"$log"; [ "$c" = "$expected" ] || rc=100; fi
  if [ $rc -eq 0 ]; then printf '%s\n' "$pass" >"$WORK/pass"; chmod 600 "$WORK/pass"; "$signer" sign --ks "$ks" --ks-type PKCS12 --ks-key-alias secondbrain --ks-pass "file:$WORK/pass" --key-pass "file:$WORK/pass" --min-sdk-version 24 --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out "$signed" "$unsigned" || rc=101; fi
  if [ $rc -eq 0 ]; then verify="$("$signer" verify --verbose --print-certs --min-sdk-version 24 "$signed" 2>&1)"; printf '%s\n' "$verify">>"$log"; printf '%s\n' "$verify"|grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' || rc=102; [ $rc -ne 0 ] || printf '%s\n' "$verify"|grep -q 'Verified using v3 scheme (APK Signature Scheme v3): true' || rc=103; fi
  if [ $rc -eq 0 ]; then signedcert="$(cert_of_apk "$signer" "$signed")"; echo "signed_signer_sha256=$signedcert">>"$log"; [ "$signedcert" = "$expected" ] || rc=104; echo "signed_apk_sha256=$(sha256sum "$signed"|awk '{print $1}')">>"$log"; fi
  if [ $rc -eq 0 ]; then rish -c 'logcat -b crash -c' >/dev/null 2>&1||true; staged=/data/local/tmp/Cortex-Relay-v2.0.0-candidate5-permanent.apk; cat "$signed"|rish -c "cat >'$staged';chmod 644 '$staged'" || rc=105; fi
  if [ $rc -eq 0 ]; then out="$(rish -c "pm install -r '$staged';x=\$?;rm -f '$staged';exit \$x" 2>&1)"; printf '%s\n' "$out">>"$log"; printf '%s\n' "$out"|grep -q Success || rc=106; fi
  if [ $rc -eq 0 ]; then info="$(rish -c "dumpsys package '$package'|grep -E 'versionCode=|versionName='|head -n4")"; printf '%s\n' "$info">>"$log"; printf '%s\n' "$info"|grep -q "versionCode=$vc" || rc=107; [ $rc -ne 0 ] || printf '%s\n' "$info"|grep -q "versionName=$vn" || rc=108; fi
  if [ $rc -eq 0 ]; then cp -f "$signed" /sdcard/Download/Cortex-Relay-v2.0.0-candidate5-permanent.apk || rc=109; fi
  if [ $rc -eq 0 ]; then rish -c "am force-stop '$package';ACT=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$package'|tail -n1);am start -W -n \"\$ACT\"" >>"$log" 2>&1 || rc=110; sleep 8; pid="$(rish -c "pidof '$package'" 2>/dev/null|tr -d '\r')"; echo "pid=$pid">>"$log"; [ -n "$pid" ] || rc=111; fi
  if [ $rc -eq 0 ]; then crashes="$(rish -c 'logcat -b crash -d -t 220' 2>/dev/null)"; if printf '%s' "$crashes"|grep -q "$package" && printf '%s' "$crashes"|grep -Eq 'FATAL EXCEPTION|Process:'; then printf '%s\n' "$crashes">>"$log"; rc=112; fi; fi
  if [ $rc -eq 0 ]; then zip_apk "$signed" "$returnzip" || rc=113; fi
  rm -f "$WORK/pass" "$WORK/installed.apk"; echo "final_exit_code=$rc">>"$log"; echo "finished_at=$(date -u +%FT%TZ)">>"$log"
  if [ $rc -eq 0 ]; then publish "$job" SUCCESS 0 "$log" "$returnzip" || return 114; echo "$job">>"$STATE"; else publish "$job" FAILED "$rc" "$log" '' || true; fi
  return "$rc"
}

if [ -d "$CONTROL/.git" ] && git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$FLAG_PATH" >"$ROOT/relay-candidate5-finalize-v2.json" 2>/dev/null; then
  JOB="$(jq -r '.job_id//empty' "$ROOT/relay-candidate5-finalize-v2.json")"
  if [ "$JOB" = job_relay_candidate5_finalize_v2_20260830 ] && ! grep -Fxq "$JOB" "$STATE" 2>/dev/null; then finalize "$ROOT/relay-candidate5-finalize-v2.json" || true; fi
fi

V3="$ROOT/agent-v3.delegate.sh"
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" >"$V3" 2>/dev/null && bash -n "$V3" >/dev/null 2>&1 || exit 72
chmod 700 "$V3"; exec "$V3" "$@"
