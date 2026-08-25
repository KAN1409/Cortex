#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# One-shot validation build for PR #59. Never uninstalls Cortex and never clears app data.
export CORTEX_BUILD_REF="robot-user-journey-fixes-20260825"
export CORTEX_CLEAN_BUILD="1"
export CORTEX_AUTO_INSTALL="1"

ROOT="${CORTEX_REPO_DIR:-$HOME/Cortex}"
if [ ! -f "$ROOT/termux-build-cortex.sh" ]; then
  printf 'Cortex repo not found at %s\n' "$ROOT" >&2
  printf 'Set CORTEX_REPO_DIR to the existing clone, then run this script again.\n' >&2
  exit 1
fi

cd "$ROOT"
exec bash termux-build-cortex.sh
