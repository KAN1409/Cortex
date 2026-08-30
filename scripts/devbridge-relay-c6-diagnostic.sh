#!/data/data/com.termux/files/usr/bin/bash
set -u
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
RESULTS="$ROOT/results"
STATE="$ROOT/relay-c6-diagnostic-v1.txt"
JOB='relay_c6_artifact_path_diagnostic_v1'
OUT='.devbridge/artifacts/relay-c6-export-diagnostic.txt'
mkdir -p "$ROOT"
touch "$STATE"
grep -Fxq "$JOB" "$STATE" 2>/dev/null && exit 0
TMP="$ROOT/relay-c6-diagnostic.txt"
{
  echo "generated_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "root=$ROOT"
  echo 'candidate_files:'
  while IFS= read -r f; do
    [ -f "$f" ] || continue
    printf '%s\t%s\t%s\n' "$(sha256sum "$f" 2>/dev/null | awk '{print $1}')" "$(wc -c < "$f" 2>/dev/null | tr -d ' ')" "$f"
  done < <(find /sdcard/Download "$ROOT" -maxdepth 6 -type f \( -iname '*candidate6*' -o -iname '*relay*c6*' -o -iname '*relay*candidate*' \) 2>/dev/null | sort -u)
} > "$TMP"
if [ -d "$LOCAL_REPO/.git" ]; then REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin 2>/dev/null || true)"; else REMOTE=''; fi
[ -n "$REMOTE" ] || REMOTE='https://github.com/KAN1409/Cortex.git'
if [ ! -d "$RESULTS/.git" ]; then
  rm -rf "$RESULTS"
  git clone --filter=blob:none --no-tags --branch "$RESULT_BRANCH" "$REMOTE" "$RESULTS" >/dev/null 2>&1 || exit 0
else
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || exit 0
  git -C "$RESULTS" fetch --prune origin "$RESULT_BRANCH" >/dev/null 2>&1 || exit 0
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || exit 0
fi
mkdir -p "$(dirname "$RESULTS/$OUT")"
cp -f "$TMP" "$RESULTS/$OUT"
git -C "$RESULTS" add "$OUT"
if ! git -C "$RESULTS" diff --cached --quiet; then
  git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' commit -m 'devbridge(diag): Relay candidate6 artifact path' >/dev/null || exit 0
  git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1 || exit 0
fi
printf '%s\n' "$JOB" >> "$STATE"
exit 0
