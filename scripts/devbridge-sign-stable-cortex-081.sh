#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UNSIGNED="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
KEYSTORE="$ROOT/app/cortex-debug.keystore"
EXPECTED_CERT="5c6550a070abe477dcad5f23f3f437e183bff8aeaeb6ac52e1beaa8243ee69a7"
OUTDIR="$HOME/.cortex-rebuild-signing"
OUT="$OUTDIR/Cortex-0.8.1-voice-player-permanent.apk"
DOWNLOAD="/sdcard/Download/Cortex-0.8.1-voice-player-permanent.apk"

[ -s "$UNSIGNED" ] || { echo CORTEX_081_UNSIGNED_APK_MISSING >&2; exit 2; }
[ -s "$KEYSTORE" ] || { echo CORTEX_081_SIGNER_OVERLAY_MISSING >&2; exit 3; }

APKSIGNER="$(command -v apksigner 2>/dev/null || true)"
if [ -z "$APKSIGNER" ]; then
  APKSIGNER="$(find "${ANDROID_HOME:-$HOME/android-sdk}" -type f -name apksigner 2>/dev/null | sort -V | tail -n1)"
fi
[ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ] || { echo CORTEX_081_APKSIGNER_MISSING >&2; exit 4; }

KEY_CERT="$(keytool -list -v -keystore "$KEYSTORE" -storepass android -alias androiddebugkey 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
[ "$KEY_CERT" = "$EXPECTED_CERT" ] || { echo "CORTEX_081_SIGNER_SOURCE_MISMATCH:$KEY_CERT" >&2; exit 5; }

mkdir -p "$OUTDIR"
chmod 700 "$OUTDIR" 2>/dev/null || true
rm -f "$OUT" "$OUT.idsig"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --min-sdk-version 26 \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --out "$OUT" "$UNSIGNED"

"$APKSIGNER" verify --verbose --print-certs --min-sdk-version 26 "$OUT" >/tmp/cortex081-apksigner.txt
OUT_CERT="$(sed -n -E 's/^.*certificate SHA-256 digest:[[:space:]]*//p' /tmp/cortex081-apksigner.txt | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
rm -f /tmp/cortex081-apksigner.txt
[ "$OUT_CERT" = "$EXPECTED_CERT" ] || { rm -f "$OUT"; echo "CORTEX_081_OUTPUT_SIGNER_MISMATCH:$OUT_CERT" >&2; exit 6; }

chmod 600 "$OUT" 2>/dev/null || true
cp -f "$OUT" "$DOWNLOAD" 2>/dev/null || true
chmod 644 "$DOWNLOAD" 2>/dev/null || true

echo "permanent_signer_sha256=$OUT_CERT"
echo "stable_apk=$OUT"
echo "download_apk=$DOWNLOAD"
echo "stable_apk_sha256=$(sha256sum "$OUT" | awk '{print $1}')"
echo CORTEX_081_PERMANENT_APK_READY
