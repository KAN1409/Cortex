#!/data/data/com.termux/files/usr/bin/bash
set -u

ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
CONTROL="$ROOT/control"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
APK="$HOME/.cortex-rebuild-signing/Cortex-0.7.2-gptoss-brain-signed.apk"
EXPECTED_SHA="15f8868d6c6b231460c30aabeaf5ac2a5880d6af7e721d215fdd12a680b95c17"
EXPECTED_CERT="5c6550a070abe477dcad5f23f3f437e183bff8aeaeb6ac52e1beaa8243ee69a7"
FLAG_PATH=".devbridge/cortex-gptoss-export.json"
STATE="$ROOT/cortex-gptoss-exported.txt"
ART_PATH=".devbridge/artifacts/Cortex-0.7.2-gptoss-brain-signed.apk"
SHA_PATH="${ART_PATH}.sha256"

[ -d "$CONTROL/.git" ] || exit 0
touch "$STATE"
git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || exit 0
FLAG="$ROOT/cortex-gptoss-export.json"
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$FLAG_PATH" > "$FLAG" 2>/dev/null || exit 0
JOB="$(jq -r '.job_id // empty' "$FLAG" 2>/dev/null)"
[ "$JOB" = "job_20260830_gptoss_direct_export_01" ] || exit 0
grep -Fxq "$JOB" "$STATE" 2>/dev/null && exit 0
[ -s "$APK" ] || exit 0
ACTUAL_SHA="$(sha256sum "$APK" | awk '{print $1}')"
[ "$ACTUAL_SHA" = "$EXPECTED_SHA" ] || exit 0

SIGNER="$(command -v apksigner 2>/dev/null || true)"
if [ -z "$SIGNER" ]; then
  SIGNER="$(find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1)"
fi
[ -n "$SIGNER" ] || exit 0
CERT="$("$SIGNER" verify --print-certs "$APK" 2>/dev/null | sed -n -E 's/^.*certificate SHA-256 digest:[[:space:]]*//p' | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
[ "$CERT" = "$EXPECTED_CERT" ] || exit 0

if [ -d "$LOCAL_REPO/.git" ]; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin 2>/dev/null || true)"
else
  REMOTE=""
fi
[ -n "$REMOTE" ] || REMOTE="https://github.com/KAN1409/Cortex.git"

cred="$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null || true)"
user="$(printf '%s\n' "$cred" | sed -n 's/^username=//p' | head -n1)"
token="$(printf '%s\n' "$cred" | sed -n 's/^password=//p' | head -n1)"
[ -n "$token" ] || exit 0

API="https://api.github.com/repos/KAN1409/Cortex/contents"
CONTENT="$(base64 -w 0 "$APK" 2>/dev/null || base64 "$APK" | tr -d '\n')"
BODY="$(jq -nc --arg m 'devbridge(artifact): Cortex 0.7.2 GPT-OSS brain APK' --arg c "$CONTENT" --arg b "$RESULT_BRANCH" '{message:$m,content:$c,branch:$b}')"
HTTP="$(curl -sS -o "$ROOT/cortex-gptoss-export-response.json" -w '%{http_code}' --connect-timeout 10 --max-time 45 \
  -u "${user:-x-access-token}:$token" \
  -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' \
  -X PUT "$API/$ART_PATH" -d "$BODY" || true)"
unset CONTENT BODY token cred
[ "$HTTP" = "201" ] || [ "$HTTP" = "200" ] || exit 0

SHA_CONTENT="$(printf '%s  %s\n' "$EXPECTED_SHA" 'Cortex-0.7.2-gptoss-brain-signed.apk' | base64 -w 0 2>/dev/null || printf '%s  %s\n' "$EXPECTED_SHA" 'Cortex-0.7.2-gptoss-brain-signed.apk' | base64 | tr -d '\n')"
SHA_BODY="$(jq -nc --arg m 'devbridge(artifact): Cortex 0.7.2 APK checksum' --arg c "$SHA_CONTENT" --arg b "$RESULT_BRANCH" '{message:$m,content:$c,branch:$b}')"
curl -sS -o /dev/null --connect-timeout 10 --max-time 30 \
  -u "${user:-x-access-token}:$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null | sed -n 's/^password=//p' | head -n1)" \
  -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' \
  -X PUT "$API/$SHA_PATH" -d "$SHA_BODY" || true

printf '%s\n' "$JOB" >> "$STATE"
cp -f "$APK" /sdcard/Download/Cortex-0.7.2-gptoss-brain-signed.apk 2>/dev/null || true
exit 0
