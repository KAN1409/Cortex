#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

APK="${1:-}"
[ -n "$APK" ] && [ -s "$APK" ] || { echo "APK_EXPORT_FAIL: missing apk" >&2; exit 2; }

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REMOTE="$(git -C "$ROOT" remote get-url origin)"
RESULT_BRANCH="device/termux-dev-bridge-results"
ARTIFACT_NAME="Cortex-0.8.0-situations-sources-permanent.apk"
TMP="$(mktemp -d "$HOME/.cortex-devbridge/export-apk.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

for attempt in 1 2 3 4; do
  rm -rf "$TMP/repo"
  git clone --filter=blob:none --no-tags --no-checkout "$REMOTE" "$TMP/repo" >/dev/null 2>&1 || { sleep 2; continue; }
  git -C "$TMP/repo" fetch origin "$RESULT_BRANCH" >/dev/null 2>&1 || { sleep 2; continue; }
  git -C "$TMP/repo" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || { sleep 2; continue; }
  mkdir -p "$TMP/repo/.devbridge/artifacts"
  cp -f "$APK" "$TMP/repo/.devbridge/artifacts/$ARTIFACT_NAME"
  sha256sum "$TMP/repo/.devbridge/artifacts/$ARTIFACT_NAME" > "$TMP/repo/.devbridge/artifacts/$ARTIFACT_NAME.sha256"
  git -C "$TMP/repo" add -f ".devbridge/artifacts/$ARTIFACT_NAME" ".devbridge/artifacts/$ARTIFACT_NAME.sha256"
  git -C "$TMP/repo" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' \
    commit -m "devbridge(artifact): Cortex 0.8 Situations/Sources APK" >/dev/null 2>&1 || true
  if git -C "$TMP/repo" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1; then
    echo "artifact_branch=$RESULT_BRANCH"
    echo "artifact_path=.devbridge/artifacts/$ARTIFACT_NAME"
    echo "artifact_sha256=$(sha256sum "$APK" | awk '{print $1}')"
    echo "CORTEX_SIGNED_APK_EXPORTED"
    exit 0
  fi
  sleep 2
done

echo "APK_EXPORT_FAIL: could not publish artifact after retries" >&2
exit 3
