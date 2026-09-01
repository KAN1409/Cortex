#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="${CORTEX_DEVBRIDGE_REPO:-KAN1409/Cortex}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
POLL="${CORTEX_DEVBRIDGE_POLL_SECONDS:-20}"
if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/${REPO}.git"
fi

fail(){ printf 'CORTEX_DEVBRIDGE_V2_BOOTSTRAP_FAIL: %s\n' "$*" >&2; exit 1; }
fetch_with_timeout(){
  if command -v timeout >/dev/null 2>&1; then
    timeout 45 git -C "$LOCAL_REPO" fetch --prune origin "$@"
  else
    git -C "$LOCAL_REPO" fetch --prune origin "$@"
  fi
}
command -v pkg >/dev/null 2>&1 || fail "Termux pkg not found"

missing=()
command -v git >/dev/null 2>&1 || missing+=(git)
command -v jq >/dev/null 2>&1 || missing+=(jq)
command -v sha256sum >/dev/null 2>&1 || missing+=(coreutils)
if [ ${#missing[@]} -gt 0 ]; then
  printf 'CORTEX_DEVBRIDGE_INSTALLING_DEPS: %s\n' "${missing[*]}"
  pkg install -y "${missing[@]}" || fail "dependency installation failed: ${missing[*]}"
fi

command -v git >/dev/null 2>&1 || fail "git unavailable"
command -v jq >/dev/null 2>&1 || fail "jq unavailable"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum unavailable"
command -v rish >/dev/null 2>&1 || fail "rish not found"
rish -c 'id' >/dev/null 2>&1 || fail "Shizuku/rish unavailable"
[ -d "$LOCAL_REPO/.git" ] || fail "local Cortex repo missing: $LOCAL_REPO"
mkdir -p "$ROOT" "$ROOT/logs" "$ROOT/work" "$HOME/.termux/boot"

# One authoritative repository only. Work remains isolated in git worktrees under $ROOT/work.
CONTROL="$ROOT/control"
rm -rf "$CONTROL"
ln -s "$LOCAL_REPO" "$CONTROL"

cat > "$ROOT/supervisor.sh" <<'SUPERVISOR'
#!/data/data/com.termux/files/usr/bin/bash
set -u
REPO="${CORTEX_DEVBRIDGE_REPO:-KAN1409/Cortex}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
POLL="${CORTEX_DEVBRIDGE_POLL_SECONDS:-20}"
if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/${REPO}.git"
fi
CONTROL="$ROOT/control"
LOCK="$ROOT/fetch.lock"
mkdir -p "$ROOT/logs"
ensure_control(){
  [ -d "$LOCAL_REPO/.git" ] || return 1
  if [ ! -L "$CONTROL" ] || [ "$(readlink "$CONTROL" 2>/dev/null || true)" != "$LOCAL_REPO" ]; then
    rm -rf "$CONTROL"
    ln -s "$LOCAL_REPO" "$CONTROL" || return 1
  fi
}
fetch_control(){
  mkdir "$LOCK" 2>/dev/null || return 1
  trap 'rmdir "$LOCK" 2>/dev/null || true' RETURN
  git -C "$LOCAL_REPO" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  if command -v timeout >/dev/null 2>&1; then
    timeout 45 git -C "$LOCAL_REPO" fetch --prune origin "$CONTROL_BRANCH" >/dev/null 2>&1
  else
    git -C "$LOCAL_REPO" fetch --prune origin "$CONTROL_BRANCH" >/dev/null 2>&1
  fi
  rc=$?
  rmdir "$LOCK" 2>/dev/null || true
  trap - RETURN
  return $rc
}
while true; do
  if ensure_control && fetch_control; then
    NEXT="$ROOT/agent.next.sh"
    CURRENT="$ROOT/agent.current.sh"
    if git -C "$LOCAL_REPO" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v2.sh" > "$NEXT" 2>/dev/null && bash -n "$NEXT" >/dev/null 2>&1; then
      chmod 700 "$NEXT"
      mv -f "$NEXT" "$CURRENT"
      CORTEX_DEVBRIDGE_SUPERVISED=1 "$CURRENT" --once >> "$ROOT/agent.stdout.log" 2>> "$ROOT/agent.stderr.log" || true
    else
      rm -f "$NEXT"
    fi
  fi
  sleep "$POLL"
done
SUPERVISOR
chmod 700 "$ROOT/supervisor.sh"

cat > "$HOME/.termux/boot/20-cortex-devbridge" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
nohup "$ROOT/supervisor.sh" >> "$ROOT/supervisor.stdout.log" 2>> "$ROOT/supervisor.stderr.log" < /dev/null &
echo \$! > "$ROOT/supervisor.pid"
EOF
chmod 700 "$HOME/.termux/boot/20-cortex-devbridge"

# Stop previous workers before first-contact processing so there is exactly one Git fetch owner.
for pidfile in "$ROOT/agent.pid" "$ROOT/supervisor.pid"; do
  if [ -f "$pidfile" ]; then
    oldpid="$(cat "$pidfile" 2>/dev/null || true)"
    if [ -n "$oldpid" ] && kill -0 "$oldpid" 2>/dev/null; then
      kill "$oldpid" 2>/dev/null || true
      sleep 1
    fi
  fi
done
rm -rf "$ROOT/fetch.lock"

# Bootstrap performs one bounded fetch, then the agent consumes already-fetched refs.
git -C "$LOCAL_REPO" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
fetch_with_timeout "$CONTROL_BRANCH" || fail "control fetch failed or timed out"
BOOT_AGENT="$ROOT/bootstrap-agent-once.sh"
git -C "$LOCAL_REPO" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v2.sh" > "$BOOT_AGENT" 2>/dev/null || fail "agent fetch failed"
bash -n "$BOOT_AGENT" >/dev/null 2>&1 || fail "agent syntax failed"
chmod 700 "$BOOT_AGENT"
printf 'CORTEX_DEVBRIDGE_PROCESSING_QUEUED_JOBS\n'
CORTEX_DEVBRIDGE_SUPERVISED=1 "$BOOT_AGENT" --once || true
printf 'CORTEX_DEVBRIDGE_ONE_SHOT_DONE\n'

# Only after one-shot completion restore continuous polling.
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
nohup "$ROOT/supervisor.sh" >> "$ROOT/supervisor.stdout.log" 2>> "$ROOT/supervisor.stderr.log" < /dev/null &
echo $! > "$ROOT/supervisor.pid"
sleep 2
kill -0 "$(cat "$ROOT/supervisor.pid")" 2>/dev/null || fail "supervisor did not stay running"

printf 'CORTEX_DEVBRIDGE_V2_BOOTSTRAP_OK\npid=%s\ncontrol=%s\nresults=%s\nsigner_source=%s\n' \
  "$(cat "$ROOT/supervisor.pid")" "$CONTROL_BRANCH" "$RESULT_BRANCH" "$LOCAL_REPO/app/cortex-debug.keystore"
