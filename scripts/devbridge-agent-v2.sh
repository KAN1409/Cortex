#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
STATE="$ROOT/processed.txt"
TARGET="$ROOT/agent-v3.runtime.sh"
REPO="KAN1409/Cortex"

if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/${REPO}.git"
fi

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
mkdir -p "$ROOT/logs" "$ROOT/work"
touch "$STATE"

apksigner_bin(){
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1
}

parse_cert(){
  sed -n -E 's/^.*certificate SHA-256 digest:[[:space:]]*//p' \
    | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
}

publish_result(){
  local job="$1" status="$2" rc="$3" log_file="$4" result_file tailtext
  if [ ! -d "$RESULTS/.git" ]; then
    rm -rf "$RESULTS"
    git clone --filter=blob:none --no-tags "$REMOTE" "$RESULTS" >/dev/null 2>&1 || return 1
  fi
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  git -C "$RESULTS" fetch origin "$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" pull --ff-only origin "$RESULT_BRANCH" >/dev/null 2>&1 || true
  mkdir -p "$RESULTS/.devbridge/results"
  tailtext="$(tail -c 60000 "$log_file" 2>/dev/null || true)"
  result_file="$RESULTS/.devbridge/results/$job.json"
  jq -n \
    --arg protocol CORTEX_DEVBRIDGE_V1 \
    --arg agent_version '3+relay-install-verify' \
    --arg job_id "$job" \
    --arg capability RELAY_INSTALL_EXPORTED \
    --arg status "$status" \
    --argjson exit_code "$rc" \
    --arg finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg log_tail "$tailtext" \
    '{protocol:$protocol,agent_version:$agent_version,job_id:$job_id,capability:$capability,status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail}' \
    > "$result_file"
  git -C "$RESULTS" add ".devbridge/results/$job.json"
  if ! git -C "$RESULTS" diff --cached --quiet; then
    git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' \
      commit -m "devbridge(relay-install): $job ${status,,}" >/dev/null || return 1
    git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  fi
}

run_install(){
  local jobfile="$1" job package apk expected_sha expected_cert expected_version_code expected_version_name
  local signer actual_sha candidate_out candidate_cert installed_path installed_tmp installed_out installed_cert staged install_out pkg_info pid crashes log_file rc=0

  job="$(jq -r '.job_id // empty' "$jobfile")"
  package="$(jq -r '.package // empty' "$jobfile")"
  apk="$(jq -r '.params.apk_path // empty' "$jobfile")"
  expected_sha="$(jq -r '.params.apk_sha256 // empty' "$jobfile" | tr '[:upper:]' '[:lower:]')"
  expected_cert="$(jq -r '.params.expected_cert_sha256 // empty' "$jobfile" | tr '[:upper:]' '[:lower:]')"
  expected_version_code="$(jq -r '.params.version_code // empty' "$jobfile")"
  expected_version_name="$(jq -r '.params.version_name // empty' "$jobfile")"

  [ "$(jq -r '.protocol // empty' "$jobfile")" = CORTEX_DEVBRIDGE_V1 ] || return 80
  [ "$(jq -r '.repo // empty' "$jobfile")" = KAN1409/Cortex ] || return 81
  [ "$(jq -r '.authorized_owner // empty' "$jobfile")" = KAN1409 ] || return 82
  [ "$package" = com.kareem.secondbrain ] || return 83
  [ "$apk" = /sdcard/Download/Cortex-Relay-2.0.0-candidate5-permanent.apk ] || return 84
  [ "$expected_sha" = a1f441127b5c8fecb4f2da21bc3a1ff8992a46fc81ccd2db8f426bcf40806e9e ] || return 85
  [ "$expected_cert" = fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74 ] || return 86
  [ "$expected_version_code" = 24 ] || return 87
  [ "$expected_version_name" = 2.0.0-candidate5 ] || return 88

  log_file="$ROOT/logs/$job.log"
  : > "$log_file"
  exec 9>&1
  exec >> "$log_file" 2>&1
  printf 'started_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'agent_version=3+relay-install-verify\n'

  signer="$(apksigner_bin)"
  [ -n "$signer" ] || { echo APKSIGNER_NOT_FOUND; rc=90; }
  [ $rc -ne 0 ] || [ -f "$apk" ] || { echo EXPORTED_APK_NOT_FOUND; rc=91; }

  if [ $rc -eq 0 ]; then
    actual_sha="$(sha256sum "$apk" | awk '{print $1}')"
    printf 'candidate_apk_sha256=%s\n' "$actual_sha"
    [ "$actual_sha" = "$expected_sha" ] || { echo CANDIDATE_SHA_MISMATCH; rc=92; }
  fi

  if [ $rc -eq 0 ]; then
    candidate_out="$("$signer" verify --verbose --print-certs "$apk" 2>&1)" || true
    printf '%s\n' "$candidate_out" | grep -E 'Verified using v[23] scheme|certificate SHA-256 digest' || true
    candidate_cert="$(printf '%s\n' "$candidate_out" | parse_cert)"
    printf 'candidate_signer_sha256=%s\n' "$candidate_cert"
    [ "$candidate_cert" = "$expected_cert" ] || { echo CANDIDATE_SIGNER_MISMATCH; rc=93; }
  fi

  if [ $rc -eq 0 ]; then
    installed_path="$(rish -c "pm path '$package'" 2>/dev/null | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
    printf 'installed_path=%s\n' "$installed_path"
    [ -n "$installed_path" ] || { echo INSTALLED_APK_NOT_FOUND; rc=94; }
  fi

  if [ $rc -eq 0 ]; then
    installed_tmp="$ROOT/work/$job-installed-base.apk"
    rish -c "cat '$installed_path'" > "$installed_tmp" 2>/dev/null || { echo INSTALLED_APK_COPY_FAILED; rc=95; }
    [ $rc -ne 0 ] || [ -s "$installed_tmp" ] || { echo INSTALLED_APK_EMPTY; rc=96; }
  fi

  if [ $rc -eq 0 ]; then
    installed_out="$("$signer" verify --verbose --print-certs "$installed_tmp" 2>&1)" || true
    printf '%s\n' "$installed_out" | tail -n80
    installed_cert="$(printf '%s\n' "$installed_out" | parse_cert)"
    if [ -z "$installed_cert" ]; then
      installed_out="$("$signer" verify --verbose --print-certs --min-sdk-version 24 "$installed_tmp" 2>&1)" || true
      printf '%s\n' "$installed_out" | tail -n80
      installed_cert="$(printf '%s\n' "$installed_out" | parse_cert)"
    fi
    printf 'installed_signer_sha256=%s\n' "$installed_cert"
    [ -n "$installed_cert" ] || { echo INSTALLED_SIGNER_UNREADABLE; rc=97; }
    [ $rc -ne 0 ] || [ "$installed_cert" = "$candidate_cert" ] || { echo SIGNER_MISMATCH_REFUSE_INSTALL; rc=98; }
  fi

  if [ $rc -eq 0 ]; then
    rish -c 'logcat -b crash -c' >/dev/null 2>&1 || true
    staged="/data/local/tmp/Cortex-Relay-2.0.0-candidate5-permanent.apk"
    cat "$apk" | rish -c "cat > '$staged'; chmod 644 '$staged'" >/dev/null 2>&1 || { echo APK_STAGE_FAILED; rc=99; }
  fi

  if [ $rc -eq 0 ]; then
    install_out="$(rish -c "pm install -r '$staged'; x=\$?; rm -f '$staged'; exit \$x" 2>&1)" || rc=100
    printf '%s\n' "$install_out"
    [ $rc -ne 0 ] || printf '%s\n' "$install_out" | grep -q Success || { echo INSTALL_NO_SUCCESS; rc=101; }
  fi

  if [ $rc -eq 0 ]; then
    pkg_info="$(rish -c "dumpsys package '$package' | grep -E 'versionCode=|versionName=|lastUpdateTime=' | head -n8" 2>/dev/null)"
    printf '%s\n' "$pkg_info"
    printf '%s\n' "$pkg_info" | grep -q 'versionCode=24' || { echo VERSION_CODE_VERIFY_FAILED; rc=102; }
    [ $rc -ne 0 ] || printf '%s\n' "$pkg_info" | grep -q 'versionName=2.0.0-candidate5' || { echo VERSION_NAME_VERIFY_FAILED; rc=103; }
  fi

  if [ $rc -eq 0 ]; then
    rish -c "am force-stop '$package'" >/dev/null 2>&1 || true
    rish -c "ACT=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$package' 2>/dev/null | tail -n1); [ -n \"\$ACT\" ] && am start -W -n \"\$ACT\"" >/dev/null 2>&1 || { echo LAUNCH_FAILED; rc=104; }
    sleep 8
  fi

  if [ $rc -eq 0 ]; then
    pid="$(rish -c "pidof '$package'" 2>/dev/null | tr -d '\r')"
    printf 'pid=%s\n' "$pid"
    [ -n "$pid" ] || { echo PROCESS_NOT_RUNNING; rc=105; }
  fi

  if [ $rc -eq 0 ]; then
    crashes="$(rish -c 'logcat -b crash -d -t 240' 2>/dev/null)"
    if printf '%s' "$crashes" | grep -q "$package" && printf '%s' "$crashes" | grep -Eq 'FATAL EXCEPTION|Process:'; then
      printf '%s\n' "$crashes" | tail -n120
      echo SMOKE_CRASH
      rc=106
    else
      echo SMOKE_NO_FATAL_CRASH
    fi
  fi

  rm -f "${installed_tmp:-}"
  printf 'finished_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  exec 1>&9 9>&-

  if [ $rc -eq 0 ]; then
    publish_result "$job" SUCCESS 0 "$log_file" || return 110
  else
    publish_result "$job" FAILED "$rc" "$log_file" || true
  fi
  printf '%s\n' "$job" >> "$STATE"
  return "$rc"
}

while IFS= read -r path; do
  [ -n "$path" ] || continue
  jobfile="$ROOT/relay-install-current.json"
  git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$path" > "$jobfile" 2>/dev/null || continue
  [ "$(jq -r '.capability // empty' "$jobfile")" = RELAY_INSTALL_EXPORTED ] || continue
  job="$(jq -r '.job_id // empty' "$jobfile")"
  [ -n "$job" ] || continue
  grep -Fxq "$job" "$STATE" 2>/dev/null && continue
  run_install "$jobfile" || true
done < <(git -C "$CONTROL" ls-tree -r --name-only "origin/$CONTROL_BRANCH" '.devbridge/jobs' | grep '\.json$' || true)

# Delegate every ordinary bounded job to the stable Agent V3.
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
