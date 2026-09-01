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
mkdir -p "$ROOT" "$ROOT/logs" "$ROOT/work" "$HOME/.termux/boot"

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
mkdir -p "$ROOT/logs"
while true; do
  if [ ! -d "$CONTROL/.git" ]; then
    rm -rf "$CONTROL"
    git clone --filter=blob:none --no-tags "$REMOTE" "$CONTROL" >/dev/null 2>&1 || { sleep "$POLL"; continue; }
  fi
  git -C "$CONTROL" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  if git -C "$CONTROL" fetch --prune origin "$CONTROL_BRANCH" >/dev/null 2>&1; then
    NEXT="$ROOT/agent.next.sh"
    CURRENT="$ROOT/agent.current.sh"
    if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v2.sh" > "$NEXT" 2>/dev/null && bash -n "$NEXT" >/dev/null 2>&1; then
      chmod 700 "$NEXT"
      mv -f "$NEXT" "$CURRENT"
      "$CURRENT" --once >> "$ROOT/agent.stdout.log" 2>> "$ROOT/agent.stderr.log" || true
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

# Stop any previous poller before first-contact processing so it cannot race the one-shot agent.
for pidfile in "$ROOT/agent.pid" "$ROOT/supervisor.pid"; do
  if [ -f "$pidfile" ]; then
    oldpid="$(cat "$pidfile" 2>/dev/null || true)"
    if [ -n "$oldpid" ] && kill -0 "$oldpid" 2>/dev/null; then
      kill "$oldpid" 2>/dev/null || true
      sleep 1
    fi
  fi
done

# Refresh control and execute queued work synchronously before starting continuous polling.
CONTROL="$ROOT/control"
if [ ! -d "$CONTROL/.git" ]; then
  rm -rf "$CONTROL"
  git clone --filter=blob:none --no-tags "$REMOTE" "$CONTROL" >/dev/null 2>&1 || fail "control clone failed"
fi
git -C "$CONTROL" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
git -C "$CONTROL" fetch --prune origin "$CONTROL_BRANCH" >/dev/null 2>&1 || fail "control fetch failed"
BOOT_AGENT="$ROOT/bootstrap-agent-once.sh"
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v2.sh" > "$BOOT_AGENT" 2>/dev/null || fail "agent fetch failed"
bash -n "$BOOT_AGENT" >/dev/null 2>&1 || fail "agent syntax failed"
chmod 700 "$BOOT_AGENT"
printf 'CORTEX_DEVBRIDGE_PROCESSING_QUEUED_JOBS\n'
"$BOOT_AGENT" --once || true
printf 'CORTEX_DEVBRIDGE_ONE_SHOT_DONE\n'

# Only after the one-shot is finished, restore the long-lived poller.
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
nohup "$ROOT/supervisor.sh" >> "$ROOT/supervisor.stdout.log" 2>> "$ROOT/supervisor.stderr.log" < /dev/null &
echo $! > "$ROOT/supervisor.pid"
sleep 2
kill -0 "$(cat "$ROOT/supervisor.pid")" 2>/dev/null || fail "supervisor did not stay running"

printf 'CORTEX_DEVBRIDGE_V2_BOOTSTRAP_OK\npid=%s\ncontrol=%s\nresults=%s\nsigner_source=%s\n' \
  "$(cat "$ROOT/supervisor.pid")" "$CONTROL_BRANCH" "$RESULT_BRANCH" "$LOCAL_REPO/app/cortex-debug.keystore"
