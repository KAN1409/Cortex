#!/data/data/com.termux/files/usr/bin/bash
set -u

ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
FLAG_PATH='.devbridge/relay-candidate8-export.json'
APK='/sdcard/Download/Cortex-Relay-v2.0.0-candidate8-permanent.apk'
EXPECTED_SHA='1b2e91b76242ea1be1443c33d77955750a4906f42bdc2d1a4c6ca2724a0b50dd'
EXPECTED_CERT='fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74'
WORK="$ROOT/work/relay-candidate8-export"
mkdir -p "$WORK" "$ROOT/logs"

if [ -d "$LOCAL_REPO/.git" ]; then REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin 2>/dev/null || true)"; else REMOTE=''; fi
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

main(){
  [ -d "$CONTROL/.git" ] || return 0
  git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || return 0
  local flag="$ROOT/relay-c8-export.json" job lock signer sha cert zip size result tailtext log
  git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$FLAG_PATH" > "$flag" 2>/dev/null || return 0
  job="$(jq -r '.job_id // empty' "$flag")"
  [ "$job" = 'job_relay_candidate8_export_20260830_1931' ] || return 0
  [ "$(jq -r '.authorized_owner // empty' "$flag")" = 'KAN1409' ] || return 0
  lock="$ROOT/${job}.attempted"; [ -e "$lock" ] && return 0; date -u +%FT%TZ > "$lock"
  log="$ROOT/logs/$job.log"; : > "$log"
  exec 9>&1; exec >> "$log" 2>&1
  echo "started_at=$(date -u +%FT%TZ)"; echo 'foreground_launch_executed=false'
  [ -s "$APK" ] || { echo APK_NOT_FOUND; exec 1>&9 9>&-; return 91; }
  sha="$(sha256sum "$APK" | awk '{print $1}')"; echo "apk_sha256=$sha"; [ "$sha" = "$EXPECTED_SHA" ] || { echo APK_SHA_MISMATCH; exec 1>&9 9>&-; return 92; }
  signer="$(apksigner_bin)"; [ -n "$signer" ] || { echo APKSIGNER_NOT_FOUND; exec 1>&9 9>&-; return 93; }
  cert="$(apk_cert "$signer" "$APK")"; echo "signer_sha256=$cert"; [ "$cert" = "$EXPECTED_CERT" ] || { echo SIGNER_MISMATCH; exec 1>&9 9>&-; return 94; }
  zip="$WORK/Cortex-Relay-v2.0.0-candidate8-permanent.zip"; rm -f "$zip"
  if command -v zip >/dev/null 2>&1; then
    zip -6 -j "$zip" "$APK" >/dev/null || { echo ZIP_FAILED; exec 1>&9 9>&-; return 95; }
  else
    python - "$APK" "$zip" <<'PY'
import os,sys,zipfile
apk,out=sys.argv[1:]
with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z: z.write(apk,arcname=os.path.basename(apk))
PY
  fi
  size="$(wc -c < "$zip" | tr -d ' ')"; echo "zip_bytes=$size"; [ "$size" -lt 100000000 ] || { echo ZIP_TOO_LARGE; exec 1>&9 9>&-; return 96; }
  if [ ! -d "$RESULTS/.git" ]; then rm -rf "$RESULTS"; git clone --filter=blob:none --no-tags "$REMOTE" "$RESULTS" >/dev/null 2>&1 || { echo RESULTS_CLONE_FAILED; exec 1>&9 9>&-; return 97; }; fi
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  git -C "$RESULTS" fetch origin "$RESULT_BRANCH" >/dev/null 2>&1 || { echo RESULTS_FETCH_FAILED; exec 1>&9 9>&-; return 98; }
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || { echo RESULTS_CHECKOUT_FAILED; exec 1>&9 9>&-; return 99; }
  git -C "$RESULTS" reset --hard "origin/$RESULT_BRANCH" >/dev/null 2>&1 || true
  mkdir -p "$RESULTS/.devbridge/results" "$RESULTS/.devbridge/artifacts"
  cp -f "$zip" "$RESULTS/.devbridge/artifacts/$job.zip" || { echo ARTIFACT_COPY_FAILED; exec 1>&9 9>&-; return 100; }
  echo "finished_at=$(date -u +%FT%TZ)"; exec 1>&9 9>&-
  tailtext="$(tail -c 30000 "$log" 2>/dev/null || true)"
  result="$RESULTS/.devbridge/results/$job.json"
  jq -n --arg protocol CORTEX_DEVBRIDGE_V1 --arg agent_version '3+relay-c8-export' --arg job_id "$job" --arg capability RELAY_CANDIDATE8_EXPORT --arg status SUCCESS --argjson exit_code 0 --arg finished_at "$(date -u +%FT%TZ)" --arg log_tail "$tailtext" --arg artifact_path ".devbridge/artifacts/$job.zip" --arg artifact_sha256 "$(sha256sum "$zip" | awk '{print $1}')" --argjson artifact_size_bytes "$size" '{protocol:$protocol,agent_version:$agent_version,job_id:$job_id,capability:$capability,status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail,artifact_path:$artifact_path,artifact_sha256:$artifact_sha256,artifact_size_bytes:$artifact_size_bytes}' > "$result"
  git -C "$RESULTS" add ".devbridge/results/$job.json" ".devbridge/artifacts/$job.zip"
  git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' commit -m "devbridge(relay): $job success" >/dev/null || return 101
  git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1 || return 102
  return 0
}

main || true
exit 0
