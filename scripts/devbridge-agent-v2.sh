#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL="$ROOT/control"
TARGET="$ROOT/agent-v3.runtime.sh"
DIRECT="$ROOT/cortex0101-direct-export.runtime.sh"
PRIME_CREDS="$HOME/.cortex-prime-signing/credentials.env"

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true

# Prime signing credentials are generated and retained only on the Android device.
# They are never read from GitHub and are exported only into the local build process.
if [ -f "$PRIME_CREDS" ]; then
  # shellcheck disable=SC1090
  source "$PRIME_CREDS"
fi

# Bounded one-shot builder for the current Cortex 0.10.1 artifact. It uses an isolated clone,
# reads the existing permanent signer, exports only the signed APK, and never installs/uninstalls.
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-cortex0101-direct-export.sh" > "$DIRECT" 2>/dev/null \
   && bash -n "$DIRECT" >/dev/null 2>&1; then
  chmod 700 "$DIRECT"
  "$DIRECT" || true
else
  rm -f "$DIRECT"
fi

git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
