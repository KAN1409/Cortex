#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="${CORTEX_DEVBRIDGE_REPO:-KAN1409/Cortex}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/${REPO}.git"
fi

fail(){ printf 'CORTEX_DEVBRIDGE_V2_BOOTSTRAP_FAIL: %s\n' "$*" >&2; exit 1; }
fetch_control(){
  git -C "$LOCAL_REPO" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  if command -v timeout >/dev/null 2>&1; then
    timeout 45 git -C "$LOCAL_REPO" fetch --prune origin "$CONTROL_BRANCH"
  else
    git -C "$LOCAL_REPO" fetch --prune origin "$CONTROL_BRANCH"
  fi
}
install_script(){
  local repo_path="$1" dest="$2" tmp="$dest.next"
  git -C "$LOCAL_REPO" show "origin/$CONTROL_BRANCH:$repo_path" > "$tmp" 2>/dev/null || fail "could not load $repo_path"
  bash -n "$tmp" >/dev/null 2>&1 || { rm -f "$tmp"; fail "syntax failed: $repo_path"; }
  chmod 700 "$tmp"
  mv -f "$tmp" "$dest"
}

command -v pkg >/dev/null 2>&1 || fail "Termux pkg not found"
missing=()
command -v git >/dev/null 2>&1 || missing+=(git)
command -v jq >/dev/null 2>&1 || missing+=(jq)
command -v sha256sum >/dev/null 2>&1 || missing+=(coreutils)
if [ ${#missing[@]} -gt 0 ]; then pkg install -y "${missing[@]}" || fail "dependency installation failed"; fi
command -v git >/dev/null 2>&1 || fail "git unavailable"
command -v jq >/dev/null 2>&1 || fail "jq unavailable"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum unavailable"
command -v rish >/dev/null 2>&1 || fail "rish not found"
rish -c 'id' >/dev/null 2>&1 || fail "Shizuku/rish unavailable"
[ -d "$LOCAL_REPO/.git" ] || fail "local Cortex repo missing: $LOCAL_REPO"
mkdir -p "$ROOT" "$ROOT/logs" "$ROOT/work" "$HOME/.termux/boot"

# One authoritative repository. Builds remain isolated in worktrees under $ROOT/work.
CONTROL="$ROOT/control"
rm -rf "$CONTROL"
ln -s "$LOCAL_REPO" "$CONTROL"
rm -rf "$ROOT/fetch.lock"

# Stop stale workers before the one bounded bootstrap fetch.
for pidfile in "$ROOT/agent.pid" "$ROOT/supervisor.pid" "$ROOT/watchdog.pid"; do
  pid="$(cat "$pidfile" 2>/dev/null || true)"
  [ -z "$pid" ] || kill "$pid" 2>/dev/null || true
done
sleep 1

fetch_control || fail "control fetch failed or timed out"
install_script scripts/devbridge-agent-v2.sh "$ROOT/agent.current.sh"
install_script scripts/devbridge-supervisor-v2.sh "$ROOT/supervisor.sh"
install_script scripts/devbridge-watchdog-v1.sh "$ROOT/watchdog.sh"

cat > "$HOME/.termux/boot/20-cortex-devbridge" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
ROOT="${ROOT}"
WD="\$ROOT/watchdog.sh"
PIDFILE="\$ROOT/watchdog.pid"
pid="\$(cat \"\$PIDFILE\" 2>/dev/null || true)"
if [ -z "\$pid" ] || ! kill -0 "\$pid" 2>/dev/null; then
  nohup "\$WD" >> "\$ROOT/watchdog.stdout.log" 2>> "\$ROOT/watchdog.stderr.log" < /dev/null &
  echo \$! > "\$PIDFILE"
fi
EOF
chmod 700 "$HOME/.termux/boot/20-cortex-devbridge"

printf 'CORTEX_DEVBRIDGE_PROCESSING_QUEUED_JOBS\n'
CORTEX_DEVBRIDGE_SUPERVISOR_V2=1 "$ROOT/agent.current.sh" --once || true
printf 'CORTEX_DEVBRIDGE_ONE_SHOT_DONE\n'

command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
nohup "$ROOT/watchdog.sh" >> "$ROOT/watchdog.stdout.log" 2>> "$ROOT/watchdog.stderr.log" < /dev/null &
echo $! > "$ROOT/watchdog.pid"
sleep 2
kill -0 "$(cat "$ROOT/watchdog.pid")" 2>/dev/null || fail "watchdog did not stay running"
# Watchdog will own supervisor lifecycle from this point onward.

printf 'CORTEX_DEVBRIDGE_V2_BOOTSTRAP_OK\nwatchdog_pid=%s\ncontrol=%s\nresults=%s\nsigner_source=%s\n' \
  "$(cat "$ROOT/watchdog.pid")" "$CONTROL_BRANCH" "$RESULT_BRANCH" "$LOCAL_REPO/app/cortex-debug.keystore"
