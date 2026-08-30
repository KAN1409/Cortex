#!/data/data/com.termux/files/usr/bin/bash
set -u

ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
RESULTS="$ROOT/results"
STATE="$ROOT/relay-c6-direct-exported.txt"
ZIP_PATH='.devbridge/artifacts/job_relay_c6_finalize_stress_20260830_1054.zip'
OUT_PATH='.devbridge/artifacts/Cortex-Relay-v2.0.0-candidate6-permanent.apk'
SHA_PATH="${OUT_PATH}.sha256"
EXPECTED_ZIP_SHA='e597489a1e550b73b74efa1fa8faa55503f1a0c54db01794dcac4a4ea32130a0'
EXPECTED_APK_SHA='a3cba89d677d4fa84bf6e6e2aaa00d357b80ebe623333aaa2158ccc704afbb37'
EXPECTED_CERT='fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74'
JOB='relay_candidate6_direct_apk_export_v1'
TMP="$ROOT/relay-c6-export"

mkdir -p "$ROOT" "$TMP"
touch "$STATE"
grep -Fxq "$JOB" "$STATE" 2>/dev/null && exit 0

if [ -d "$LOCAL_REPO/.git" ]; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin 2>/dev/null || true)"
else
  REMOTE=''
fi
[ -n "$REMOTE" ] || REMOTE='https://github.com/KAN1409/Cortex.git'

if [ ! -d "$RESULTS/.git" ]; then
  rm -rf "$RESULTS"
  git clone --filter=blob:none --no-tags --branch "$RESULT_BRANCH" "$REMOTE" "$RESULTS" >/dev/null 2>&1 || exit 0
else
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || exit 0
  git -C "$RESULTS" fetch --prune origin "$RESULT_BRANCH" >/dev/null 2>&1 || exit 0
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || exit 0
fi

git -C "$RESULTS" reset --hard "origin/$RESULT_BRANCH" >/dev/null 2>&1 || exit 0

if [ -s "$RESULTS/$OUT_PATH" ]; then
  EXISTING_SHA="$(sha256sum "$RESULTS/$OUT_PATH" | awk '{print $1}')"
  if [ "$EXISTING_SHA" = "$EXPECTED_APK_SHA" ]; then
    printf '%s\n' "$JOB" >> "$STATE"
    cp -f "$RESULTS/$OUT_PATH" /sdcard/Download/Cortex-Relay-v2.0.0-candidate6-permanent.apk 2>/dev/null || true
    exit 0
  fi
fi

SRC_ZIP="$RESULTS/$ZIP_PATH"
[ -s "$SRC_ZIP" ] || exit 0
ZIP_SHA="$(sha256sum "$SRC_ZIP" | awk '{print $1}')"
[ "$ZIP_SHA" = "$EXPECTED_ZIP_SHA" ] || exit 0

rm -rf "$TMP/extracted"
mkdir -p "$TMP/extracted"
command -v python >/dev/null 2>&1 || exit 0
python - "$SRC_ZIP" "$TMP/extracted" <<'PY'
import os, sys, zipfile
src, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as z:
    for n in z.namelist():
        if n.lower().endswith('.apk') and not n.endswith('/'):
            target = os.path.join(out, os.path.basename(n))
            with z.open(n) as r, open(target, 'wb') as w:
                while True:
                    b = r.read(1024*1024)
                    if not b: break
                    w.write(b)
PY

MATCH=''
while IFS= read -r f; do
  [ -s "$f" ] || continue
  S="$(sha256sum "$f" | awk '{print $1}')"
  if [ "$S" = "$EXPECTED_APK_SHA" ]; then MATCH="$f"; break; fi
done < <(find "$TMP/extracted" -maxdepth 1 -type f -name '*.apk' 2>/dev/null)
[ -n "$MATCH" ] || exit 0

SIGNER="$(command -v apksigner 2>/dev/null || true)"
if [ -z "$SIGNER" ]; then
  SIGNER="$(find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1)"
fi
[ -n "$SIGNER" ] || exit 0
CERT="$("$SIGNER" verify --print-certs --min-sdk-version 24 "$MATCH" 2>/dev/null | sed -n -E 's/^.*certificate SHA-256 digest:[[:space:]]*//p' | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
[ "$CERT" = "$EXPECTED_CERT" ] || exit 0

mkdir -p "$(dirname "$RESULTS/$OUT_PATH")"
cp -f "$MATCH" "$RESULTS/$OUT_PATH" || exit 0
printf '%s  %s\n' "$EXPECTED_APK_SHA" 'Cortex-Relay-v2.0.0-candidate6-permanent.apk' > "$RESULTS/$SHA_PATH"

git -C "$RESULTS" add "$OUT_PATH" "$SHA_PATH"
git -C "$RESULTS" diff --cached --quiet && exit 0
git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' commit -m 'devbridge(artifact): Relay candidate6 signed APK' >/dev/null || exit 0
git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1 || exit 0

cp -f "$MATCH" /sdcard/Download/Cortex-Relay-v2.0.0-candidate6-permanent.apk 2>/dev/null || true
printf '%s\n' "$JOB" >> "$STATE"
exit 0
