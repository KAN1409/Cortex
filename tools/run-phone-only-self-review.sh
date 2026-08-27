#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
printf 'Cortex phone-only self review\nrepo: %s\nbranch: %s\nhead: %s\n\n' "$ROOT" "$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)" "$(git rev-parse --short HEAD 2>/dev/null || true)"

printf '[1/3] Repository audit...\n'; bash scripts/cortex-repo-audit.sh "$ROOT"
if [ -x ./gradlew ]; then GRADLE=(./gradlew); elif command -v gradle >/dev/null 2>&1; then GRADLE=(gradle); else echo 'Gradle is unavailable' >&2; exit 2; fi
printf '\n[2/3] Building app + instrumentation compile gate...\n'; "${GRADLE[@]}" :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace --console=plain
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"; [ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 2; }; ls -lh "$APK"

printf '\n[3/3] Opening Android package installer from Termux...\n'; printf 'Tap Update/Install in the installer. No Shizuku, adb, PC or Wi-Fi debugging is required.\n'
if command -v termux-open >/dev/null 2>&1; then termux-open --view "$APK" >/dev/null 2>&1 || termux-open "$APK" >/dev/null 2>&1 || true; else echo 'termux-open is unavailable. Open the APK from a file manager.' >&2; fi
cat <<'EOF'

After Android finishes the update, return to Termux and run:

  termux-open-url 'cortex://self-test'

If termux-open-url is unavailable, try:

  am start -W -a android.intent.action.VIEW -d 'cortex://self-test'

Cortex will run the review inside its own process and save the ZIP to:
  Downloads/Cortex/FullCortexPhoneOnlyReview_<timestamp>.zip
EOF
