#!/data/data/com.termux/files/usr/bin/bash
set -u

REPO="${CORTEX_DEVBRIDGE_REPO:-KAN1409/Cortex}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
SIGNER_SOURCE="${CORTEX_DEVBRIDGE_SIGNER_SOURCE:-$LOCAL_REPO/app/cortex-debug.keystore}"
if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/${REPO}.git"
fi
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
STATE="$ROOT/processed.txt"
LOGS="$ROOT/logs"
WORK="$ROOT/work"
PKG_ALLOW='^(com\.kareem\.cortex|com\.kareem\.secondbrain)$'
REF_ALLOW='^[A-Za-z0-9_./-]+$'
TASK_ALLOW='^[A-Za-z0-9_.:-]+$'
CAP_ALLOW='^(PING|GIT_STATUS|BUILD|BUILD_INSTALL_SMOKE|INSTALL_UPDATE|LAUNCH|STOP|LOGCAT|DUMPSYS_PACKAGE)$'

mkdir -p "$ROOT" "$LOGS" "$WORK"
touch "$STATE"

safe_pkg(){ [[ "${1:-}" =~ $PKG_ALLOW ]]; }
safe_ref(){ [[ "${1:-}" =~ $REF_ALLOW ]]; }
safe_task(){ [[ "${1:-}" =~ $TASK_ALLOW ]]; }
safe_cap(){ [[ "${1:-}" =~ $CAP_ALLOW ]]; }

clone_or_fetch(){
  local dir="$1" branch="$2"
  if [ ! -d "$dir/.git" ]; then
    rm -rf "$dir"
    git clone --filter=blob:none --no-tags "$REMOTE" "$dir" >/dev/null 2>&1 || return 1
  fi
  git -C "$dir" remote set-url origin "$REMOTE" >/dev/null 2>&1 || return 1
  git -C "$dir" fetch --prune origin "$branch" >/dev/null 2>&1 || return 1
}

prepare_worktree(){
  local job="$1" ref="$2" dir="$WORK/$job"
  clone_or_fetch "$CONTROL" "$CONTROL_BRANCH" || return 1
  git -C "$CONTROL" fetch origin "$ref" >/dev/null 2>&1 || return 1
  if [ -e "$dir" ]; then
    git -C "$CONTROL" worktree remove --force "$dir" >/dev/null 2>&1 || true
    rm -rf "$dir"
  fi
  git -C "$CONTROL" worktree add --detach "$dir" FETCH_HEAD >/dev/null 2>&1 || return 1
  if [ -f "$SIGNER_SOURCE" ] && [ -d "$dir/app" ]; then
    cp -f "$SIGNER_SOURCE" "$dir/app/cortex-debug.keystore" || return 1
    chmod 600 "$dir/app/cortex-debug.keystore" || true
    echo "signer_overlay=present" >&2
  else
    echo "signer_overlay=absent" >&2
  fi
  printf '%s' "$dir"
}

cleanup_worktree(){
  local dir="${1:-}"
  [ -n "$dir" ] || return 0
  git -C "$CONTROL" worktree remove --force "$dir" >/dev/null 2>&1 || true
  git -C "$CONTROL" worktree prune >/dev/null 2>&1 || true
  rm -rf "$dir"
}

apksigner_bin(){
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort | tail -n1
}

signer_sha(){
  local signer apk digest
  signer="$(apksigner_bin)"; [ -n "$signer" ] || return 1
  apk="$1"
  digest="$("$signer" verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1 | tr -d ':[:space:]' | tr 'A-F' 'a-f')"
  [ -n "$digest" ] || return 1
  printf '%s' "$digest"
}

install_update(){
  local pkg="$1" apk="$2" installed_path installed_tmp candidate_sha installed_sha staged out rc
  safe_pkg "$pkg" || { echo "DENY_PACKAGE"; return 22; }
  [ -f "$apk" ] || { echo "APK_NOT_FOUND"; return 23; }
  candidate_sha="$(signer_sha "$apk")" || { echo "APKSIGNER_CANDIDATE_FAIL"; return 24; }
  installed_path="$(rish -c "pm path '$pkg'" 2>/dev/null | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
  [ -n "$installed_path" ] || { echo "INSTALLED_APK_NOT_FOUND"; return 25; }
  installed_tmp="$ROOT/installed-$pkg.apk"
  rish -c "cat '$installed_path'" > "$installed_tmp" 2>/dev/null || { rm -f "$installed_tmp"; echo "INSTALLED_APK_COPY_FAIL"; return 26; }
  installed_sha="$(signer_sha "$installed_tmp")" || { rm -f "$installed_tmp"; echo "APKSIGNER_INSTALLED_FAIL"; return 27; }
  rm -f "$installed_tmp"
  echo "candidate_signer_sha256=$candidate_sha"
  echo "installed_signer_sha256=$installed_sha"
  [ "$candidate_sha" = "$installed_sha" ] || { echo "SIGNER_MISMATCH_REFUSE_INSTALL"; return 28; }
  staged="/data/local/tmp/cortex-devbridge-$$.apk"
  cat "$apk" | rish -c "cat > '$staged'" >/dev/null 2>&1 || { echo "APK_STAGE_FAIL"; return 29; }
  out="$(rish -c "pm install -r '$staged'" 2>&1)"; rc=$?
  rish -c "rm -f '$staged'" >/dev/null 2>&1 || true
  printf '%s\n' "$out"
  [ $rc -eq 0 ] && printf '%s' "$out" | grep -q 'Success' || return 30
  echo "apk_sha256=$(sha256sum "$apk" | awk '{print $1}')"
}

launch_pkg(){
  local pkg="$1"
  safe_pkg "$pkg" || return 22
  rish -c "ACT=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$pkg' 2>/dev/null | tail -n1); [ -n \"\$ACT\" ] && am start -W -n \"\$ACT\""
}

run_capability(){
  local jobfile="$1" job cap ref pkg dir='' apk='' apk_rel='' wait_s tasks task rc crashes
  job="$(jq -r '.job_id // empty' "$jobfile")"
  cap="$(jq -r '.capability // empty' "$jobfile")"
  ref="$(jq -r '.ref // "migration/cognitive-brain-v2-step1-2"' "$jobfile")"
  pkg="$(jq -r '.package // empty' "$jobfile")"
  [ "$(jq -r '.protocol // empty' "$jobfile")" = 'CORTEX_DEVBRIDGE_V1' ] || { echo BAD_PROTOCOL; return 10; }
  [ "$(jq -r '.repo // empty' "$jobfile")" = "$REPO" ] || { echo BAD_REPO; return 11; }
  [ "$(jq -r '.authorized_owner // empty' "$jobfile")" = 'KAN1409' ] || { echo BAD_OWNER; return 12; }
  [[ "$job" =~ ^job_[A-Za-z0-9_-]{3,120}$ ]] || { echo BAD_JOB_ID; return 13; }
  safe_cap "$cap" || { echo DENY_CAPABILITY; return 14; }
  safe_ref "$ref" || { echo DENY_REF; return 15; }
  if [ -n "$pkg" ]; then safe_pkg "$pkg" || { echo DENY_PACKAGE; return 16; }; fi

  case "$cap" in
    PING)
      echo 'pong=true'
      echo "agent_version=2"
      echo "signer_source_present=$([ -f "$SIGNER_SOURCE" ] && echo true || echo false)"
      rish -c 'id; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk'
      ;;
    GIT_STATUS)
      dir="$(prepare_worktree "$job" "$ref")" || return 40
      echo "head=$(git -C "$dir" rev-parse HEAD)"
      git -C "$dir" status --short
      cleanup_worktree "$dir"
      ;;
    BUILD|BUILD_INSTALL_SMOKE)
      dir="$(prepare_worktree "$job" "$ref")" || return 40
      mapfile -t tasks < <(jq -r '.params.gradle_tasks[]?' "$jobfile")
      [ ${#tasks[@]} -gt 0 ] || tasks=(':app:assembleDebug' ':app:compileDebugAndroidTestJavaWithJavac')
      for task in "${tasks[@]}"; do safe_task "$task" || { cleanup_worktree "$dir"; echo DENY_GRADLE_TASK; return 41; }; done
      chmod +x "$dir/gradlew"
      (cd "$dir" && ./gradlew --no-daemon "${tasks[@]}") || { rc=$?; cleanup_worktree "$dir"; return $rc; }
      echo BUILD_SUCCESS
      if [ "$cap" = BUILD_INSTALL_SMOKE ]; then
        [ -n "$pkg" ] || { cleanup_worktree "$dir"; echo PACKAGE_REQUIRED; return 42; }
        apk_rel="$(jq -r '.params.apk_path // empty' "$jobfile")"
        if [ -n "$apk_rel" ]; then
          [[ "$apk_rel" != /* && "$apk_rel" != *'..'* ]] || { cleanup_worktree "$dir"; echo DENY_APK_PATH; return 43; }
          apk="$dir/$apk_rel"
        else
          apk="$(find "$dir/app/build/outputs/apk" -type f -name '*.apk' ! -name '*androidTest.apk' 2>/dev/null | sort | tail -n1)"
        fi
        [ -n "$apk" ] || { cleanup_worktree "$dir"; echo APK_NOT_FOUND; return 44; }
        install_update "$pkg" "$apk" || { rc=$?; cleanup_worktree "$dir"; return $rc; }
        rish -c 'logcat -b crash -c' >/dev/null 2>&1 || true
        launch_pkg "$pkg" || { rc=$?; cleanup_worktree "$dir"; return $rc; }
        wait_s="$(jq -r '.params.smoke_wait_seconds // 6' "$jobfile")"
        [[ "$wait_s" =~ ^[0-9]+$ ]] || wait_s=6
        [ "$wait_s" -le 20 ] || wait_s=20
        sleep "$wait_s"
        echo "pid=$(rish -c "pidof '$pkg'" 2>/dev/null | tr -d '\r')"
        rish -c "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -n4" 2>/dev/null || true
        crashes="$(rish -c 'logcat -b crash -d -t 160' 2>/dev/null)"
        printf '%s\n' "$crashes"
        if printf '%s' "$crashes" | grep -q "$pkg" && printf '%s' "$crashes" | grep -Eq 'FATAL EXCEPTION|Process:'; then
          cleanup_worktree "$dir"; echo SMOKE_CRASH; return 45
        fi
      fi
      cleanup_worktree "$dir"
      ;;
    INSTALL_UPDATE)
      dir="$(prepare_worktree "$job" "$ref")" || return 40
      apk_rel="$(jq -r '.params.apk_path // empty' "$jobfile")"
      [ -n "$apk_rel" ] || { cleanup_worktree "$dir"; echo APK_PATH_REQUIRED; return 46; }
      [[ "$apk_rel" != /* && "$apk_rel" != *'..'* ]] || { cleanup_worktree "$dir"; echo DENY_APK_PATH; return 43; }
      install_update "$pkg" "$dir/$apk_rel"; rc=$?
      cleanup_worktree "$dir"
      return $rc
      ;;
    LAUNCH) launch_pkg "$pkg" ;;
    STOP) safe_pkg "$pkg" || return 22; rish -c "am force-stop '$pkg'" ;;
    LOGCAT) safe_pkg "$pkg" || return 22; rish -c 'logcat -d -t 800' 2>/dev/null | grep -F "$pkg" | tail -n 500 || true ;;
    DUMPSYS_PACKAGE) safe_pkg "$pkg" || return 22; rish -c "dumpsys package '$pkg'" | tail -n 1400 ;;
  esac
}

publish_result(){
  local job="$1" cap="$2" status="$3" rc="$4" log="$5" result_file tailtext
  clone_or_fetch "$RESULTS" "$RESULT_BRANCH" || return 1
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" pull --ff-only origin "$RESULT_BRANCH" >/dev/null 2>&1 || true
  mkdir -p "$RESULTS/.devbridge/results"
  result_file="$RESULTS/.devbridge/results/$job.json"
  tailtext="$(tail -c 60000 "$log" 2>/dev/null || true)"
  jq -n --arg protocol CORTEX_DEVBRIDGE_V1 --arg agent_version '2' --arg job_id "$job" --arg capability "$cap" --arg status "$status" --argjson exit_code "$rc" --arg finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg log_tail "$tailtext" '{protocol:$protocol,agent_version:$agent_version,job_id:$job_id,capability:$capability,status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail}' > "$result_file"
  git -C "$RESULTS" add ".devbridge/results/$job.json"
  git -C "$RESULTS" diff --cached --quiet && return 0
  git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' commit -m "devbridge(result): $job ${status,,}" >/dev/null || return 1
  git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1
}

process_once(){
  local path tmp job cap log rc status
  clone_or_fetch "$CONTROL" "$CONTROL_BRANCH" || return 1
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    tmp="$ROOT/current-job.json"
    git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$path" > "$tmp" 2>/dev/null || continue
    job="$(jq -r '.job_id // empty' "$tmp")"
    cap="$(jq -r '.capability // empty' "$tmp")"
    [ -n "$job" ] || continue
    grep -Fxq "$job" "$STATE" && continue
    log="$LOGS/$job.log"
    printf 'started_at=%s\nagent_version=2\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$log"
    if run_capability "$tmp" >> "$log" 2>&1; then rc=0; status=SUCCESS; else rc=$?; status=FAILED; fi
    printf 'finished_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$log"
    if publish_result "$job" "$cap" "$status" "$rc" "$log"; then printf '%s\n' "$job" >> "$STATE"; fi
  done < <(git -C "$CONTROL" ls-tree -r --name-only "origin/$CONTROL_BRANCH" '.devbridge/jobs' | grep '\.json$' || true)
}

case "${1:---once}" in
  --once) process_once ;;
  *) echo "usage: $0 --once" >&2; exit 2 ;;
esac
