#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="${CORTEX_DEVBRIDGE_REPO:-KAN1409/Cortex}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
REMOTE="${CORTEX_DEVBRIDGE_REMOTE:-https://github.com/${REPO}.git}"

fail() { printf 'CORTEX_DEVBRIDGE_BOOTSTRAP_FAIL: %s\n' "$*" >&2; exit 1; }
command -v pkg >/dev/null 2>&1 || fail "Termux pkg not found"
pkg install -y git jq coreutils >/dev/null
command -v rish >/dev/null 2>&1 || fail "rish not found"
rish -c 'id' >/dev/null 2>&1 || fail "Shizuku/rish unavailable"

mkdir -p "$ROOT" "$ROOT/logs" "$ROOT/work"

cat > "$ROOT/agent.sh" <<'AGENT'
#!/data/data/com.termux/files/usr/bin/bash
set -u

REPO="${CORTEX_DEVBRIDGE_REPO:-KAN1409/Cortex}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
REMOTE="${CORTEX_DEVBRIDGE_REMOTE:-https://github.com/${REPO}.git}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
STATE="$ROOT/processed.txt"
LOGS="$ROOT/logs"
WORK="$ROOT/work"
POLL="${CORTEX_DEVBRIDGE_POLL_SECONDS:-20}"
PKG_ALLOW='^(com\.kareem\.cortex|com\.kareem\.secondbrain)$'
REF_ALLOW='^[A-Za-z0-9_./-]+$'
TASK_ALLOW='^[A-Za-z0-9_.:-]+$'
CAP_ALLOW='^(PING|GIT_STATUS|BUILD|BUILD_INSTALL_SMOKE|INSTALL_UPDATE|LAUNCH|STOP|LOGCAT|DUMPSYS_PACKAGE)$'

mkdir -p "$LOGS" "$WORK"
touch "$STATE"

clone_or_fetch() {
  local dir="$1" branch="$2"
  if [ ! -d "$dir/.git" ]; then
    rm -rf "$dir"
    git clone --filter=blob:none --no-tags "$REMOTE" "$dir" >/dev/null 2>&1 || return 1
  fi
  git -C "$dir" remote set-url origin "$REMOTE" || return 1
  git -C "$dir" fetch --prune origin "$branch" >/dev/null 2>&1 || return 1
}

safe_pkg() { [[ "${1:-}" =~ $PKG_ALLOW ]]; }
safe_ref() { [[ "${1:-}" =~ $REF_ALLOW ]]; }
safe_task() { [[ "${1:-}" =~ $TASK_ALLOW ]]; }
safe_cap() { [[ "${1:-}" =~ $CAP_ALLOW ]]; }

apksigner_bin() {
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort | tail -n1
}

signer_sha() {
  local signer apk digest
  signer="$(apksigner_bin)"
  [ -n "$signer" ] || return 1
  apk="$1"
  digest="$("$signer" verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1 | tr -d ':[:space:]' | tr 'A-F' 'a-f')"
  [ -n "$digest" ] || return 1
  printf '%s' "$digest"
}

prepare_worktree() {
  local job="$1" ref="$2" dir="$WORK/$job"
  clone_or_fetch "$CONTROL" "$CONTROL_BRANCH" || return 1
  git -C "$CONTROL" fetch origin "$ref" >/dev/null 2>&1 || return 1
  if [ -e "$dir" ]; then
    git -C "$CONTROL" worktree remove --force "$dir" >/dev/null 2>&1 || true
    rm -rf "$dir"
  fi
  git -C "$CONTROL" worktree add --detach "$dir" FETCH_HEAD >/dev/null 2>&1 || return 1
  printf '%s' "$dir"
}

cleanup_worktree() {
  local dir="${1:-}"
  [ -n "$dir" ] || return 0
  git -C "$CONTROL" worktree remove --force "$dir" >/dev/null 2>&1 || true
  git -C "$CONTROL" worktree prune >/dev/null 2>&1 || true
  rm -rf "$dir"
}

install_update() {
  local pkg="$1" apk="$2" installed_path installed_tmp candidate_sha installed_sha remote out
  safe_pkg "$pkg" || { echo "DENY package=$pkg"; return 22; }
  [ -f "$apk" ] || { echo "APK_NOT_FOUND $apk"; return 23; }
  candidate_sha="$(signer_sha "$apk")" || { echo "APKSIGNER_CANDIDATE_FAIL"; return 24; }
  installed_path="$(rish -c "pm path $pkg" 2>/dev/null | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
  [ -n "$installed_path" ] || { echo "INSTALLED_APK_NOT_FOUND"; return 25; }
  installed_tmp="$ROOT/installed-$pkg.apk"
  rish -c "cat '$installed_path'" > "$installed_tmp" 2>/dev/null || { rm -f "$installed_tmp"; echo "INSTALLED_APK_COPY_FAIL"; return 26; }
  installed_sha="$(signer_sha "$installed_tmp")" || { rm -f "$installed_tmp"; echo "APKSIGNER_INSTALLED_FAIL"; return 27; }
  rm -f "$installed_tmp"
  echo "candidate_signer_sha256=$candidate_sha"
  echo "installed_signer_sha256=$installed_sha"
  [ "$candidate_sha" = "$installed_sha" ] || { echo "SIGNER_MISMATCH_REFUSE_INSTALL"; return 28; }
  remote="/data/local/tmp/cortex-devbridge-$$.apk"
  cat "$apk" | rish -c "cat > '$remote'" >/dev/null 2>&1 || { echo "APK_STAGE_FAIL"; return 29; }
  out="$(rish -c "pm install -r '$remote'" 2>&1)"; rc=$?
  rish -c "rm -f '$remote'" >/dev/null 2>&1 || true
  printf '%s\n' "$out"
  [ $rc -eq 0 ] && printf '%s' "$out" | grep -q 'Success' || return 30
  echo "apk_sha256=$(sha256sum "$apk" | awk '{print $1}')"
}

launch_pkg() {
  local pkg="$1"
  safe_pkg "$pkg" || return 22
  rish -c "ACT=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$pkg' 2>/dev/null | tail -n1); [ -n \"\$ACT\" ] && am start -W -n \"\$ACT\""
}

run_capability() {
  local jobfile="$1" job cap ref pkg dir='' apk='' wait_s tasks task rc
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
      [ ${#tasks[@]} -gt 0 ] || tasks=(':app:assembleDebug' ':app:assembleDebugAndroidTest')
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
        wait_s="$(jq -r '.params.smoke_wait_seconds // 5' "$jobfile")"
        [[ "$wait_s" =~ ^[0-9]+$ ]] || wait_s=5
        [ "$wait_s" -le 20 ] || wait_s=20
        sleep "$wait_s"
        echo "pid=$(rish -c "pidof '$pkg'" 2>/dev/null | tr -d '\r')"
        rish -c "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -n4" 2>/dev/null || true
        crashes="$(rish -c 'logcat -b crash -d -t 120' 2>/dev/null)"
        printf '%s\n' "$crashes"
        if printf '%s' "$crashes" | grep -q "$pkg" && printf '%s' "$crashes" | grep -Eq 'FATAL EXCEPTION|Process:'; then cleanup_worktree "$dir"; echo SMOKE_CRASH; return 45; fi
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
    LAUNCH)
      launch_pkg "$pkg"
      ;;
    STOP)
      safe_pkg "$pkg" || return 22
      rish -c "am force-stop '$pkg'"
      ;;
    LOGCAT)
      safe_pkg "$pkg" || return 22
      rish -c 'logcat -d -t 600' 2>/dev/null | grep -F "$pkg" | tail -n 400 || true
      ;;
    DUMPSYS_PACKAGE)
      safe_pkg "$pkg" || return 22
      rish -c "dumpsys package '$pkg'" | tail -n 1200
      ;;
  esac
}

publish_result() {
  local job="$1" cap="$2" status="$3" rc="$4" log="$5" result_file tailtext
  clone_or_fetch "$RESULTS" "$RESULT_BRANCH" || return 1
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" pull --ff-only origin "$RESULT_BRANCH" >/dev/null 2>&1 || true
  mkdir -p "$RESULTS/.devbridge/results"
  result_file="$RESULTS/.devbridge/results/$job.json"
  tailtext="$(tail -c 50000 "$log" 2>/dev/null || true)"
  jq -n --arg protocol CORTEX_DEVBRIDGE_V1 --arg job_id "$job" --arg capability "$cap" --arg status "$status" --argjson exit_code "$rc" --arg finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg log_tail "$tailtext" '{protocol:$protocol,job_id:$job_id,capability:$capability,status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail}' > "$result_file"
  git -C "$RESULTS" add ".devbridge/results/$job.json"
  git -C "$RESULTS" diff --cached --quiet && return 0
  git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' commit -m "devbridge(result): $job ${status,,}" >/dev/null || return 1
  git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1
}

process_once() {
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
    printf 'started_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$log"
    if run_capability "$tmp" >> "$log" 2>&1; then rc=0; status=SUCCESS; else rc=$?; status=FAILED; fi
    printf 'finished_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$log"
    if publish_result "$job" "$cap" "$status" "$rc" "$log"; then printf '%s\n' "$job" >> "$STATE"; fi
  done < <(git -C "$CONTROL" ls-tree -r --name-only "origin/$CONTROL_BRANCH" '.devbridge/jobs' | grep '\.json$' || true)
}

if [ "${1:-}" = '--once' ]; then process_once; exit $?; fi
while true; do process_once || true; sleep "$POLL"; done
AGENT

chmod 700 "$ROOT/agent.sh"

mkdir -p "$HOME/.termux/boot"
cat > "$HOME/.termux/boot/20-cortex-devbridge" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
nohup "$ROOT/agent.sh" >> "$ROOT/agent.stdout.log" 2>> "$ROOT/agent.stderr.log" < /dev/null &
EOF
chmod 700 "$HOME/.termux/boot/20-cortex-devbridge"

if [ -f "$ROOT/agent.pid" ]; then
  oldpid="$(cat "$ROOT/agent.pid" 2>/dev/null || true)"
  if [ -n "$oldpid" ] && kill -0 "$oldpid" 2>/dev/null; then kill "$oldpid" 2>/dev/null || true; sleep 1; fi
fi

command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
nohup "$ROOT/agent.sh" >> "$ROOT/agent.stdout.log" 2>> "$ROOT/agent.stderr.log" < /dev/null &
echo $! > "$ROOT/agent.pid"
sleep 2
kill -0 "$(cat "$ROOT/agent.pid")" 2>/dev/null || fail "agent did not stay running"
printf 'CORTEX_DEVBRIDGE_BOOTSTRAP_OK\npid=%s\ncontrol=%s\nresults=%s\n' "$(cat "$ROOT/agent.pid")" "$CONTROL_BRANCH" "$RESULT_BRANCH"
