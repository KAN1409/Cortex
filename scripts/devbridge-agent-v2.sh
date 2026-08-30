#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
RESULT_BRANCH="${CORTEX_DEVBRIDGE_RESULT_BRANCH:-device/termux-dev-bridge-results}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
LOCAL_REPO="${CORTEX_DEVBRIDGE_LOCAL_REPO:-$HOME/Cortex}"
CONTROL="$ROOT/control"
RESULTS="$ROOT/results"
STATE="$ROOT/processed.txt"
WORK="$ROOT/work/relay-sign-export"
TARGET="$ROOT/agent-v3.runtime.sh"
REPO="KAN1409/Cortex"

if [ -n "${CORTEX_DEVBRIDGE_REMOTE:-}" ]; then
  REMOTE="$CORTEX_DEVBRIDGE_REMOTE"
elif [ -d "$LOCAL_REPO/.git" ] && git -C "$LOCAL_REPO" remote get-url origin >/dev/null 2>&1; then
  REMOTE="$(git -C "$LOCAL_REPO" remote get-url origin)"
else
  REMOTE="https://github.com/${REPO}.git"
fi

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
mkdir -p "$WORK" "$ROOT/logs"
touch "$STATE"

apksigner_bin(){
  if command -v apksigner >/dev/null 2>&1; then command -v apksigner; return 0; fi
  find "$HOME" -type f -path '*/build-tools/*/apksigner' 2>/dev/null | sort -V | tail -n1
}

apk_cert_sha(){
  local signer="$1" apk="$2"
  "$signer" verify --print-certs --min-sdk-version 24 "$apk" 2>/dev/null \
    | sed -n -E 's/^.*certificate SHA-256 digest:[[:space:]]*//p' \
    | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
}

git_credential_field(){
  local key="$1" cred
  cred="$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null || true)"
  printf '%s\n' "$cred" | sed -n "s/^${key}=//p" | head -n1
}

download_artifact(){
  local url="$1" out="$2" token user rc
  rm -f "$out"
  token="$(git_credential_field password)"
  user="$(git_credential_field username)"
  if [ -n "$token" ]; then
    curl -L --fail --retry 2 --connect-timeout 20 --max-time 900 \
      -u "${user:-x-access-token}:$token" \
      -H 'Accept: application/vnd.github+json' \
      -H 'X-GitHub-Api-Version: 2022-11-28' \
      "$url" -o "$out" >/dev/null 2>&1
    rc=$?
  else
    curl -L --fail --retry 2 --connect-timeout 20 --max-time 900 \
      -H 'Accept: application/vnd.github+json' \
      -H 'X-GitHub-Api-Version: 2022-11-28' \
      "$url" -o "$out" >/dev/null 2>&1
    rc=$?
  fi
  unset token user
  [ $rc -eq 0 ] && [ -s "$out" ]
}

extract_unsigned(){
  local archive="$1" out="$2"
  rm -f "$out"
  if command -v unzip >/dev/null 2>&1; then
    unzip -p "$archive" app-release-unsigned.apk > "$out" 2>/dev/null || return 1
  elif command -v python >/dev/null 2>&1; then
    python - "$archive" "$out" <<'PY'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as z, open(dst, 'wb') as f:
    f.write(z.read('app-release-unsigned.apk'))
PY
  else
    return 1
  fi
  [ -s "$out" ]
}

zip_signed(){
  local apk="$1" out="$2"
  rm -f "$out"
  if command -v zip >/dev/null 2>&1; then
    (cd "$(dirname "$apk")" && zip -9 -j "$out" "$(basename "$apk")" >/dev/null) || return 1
  elif command -v python >/dev/null 2>&1; then
    python - "$apk" "$out" <<'PY'
import os, sys, zipfile
apk, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.write(apk, arcname=os.path.basename(apk))
PY
  else
    return 1
  fi
  [ -s "$out" ]
}

publish_custom(){
  local job="$1" status="$2" rc="$3" log_file="$4" zipfile="${5:-}"
  local result_file artifact_rel='' artifact_sha='' artifact_size=0 tailtext
  if [ ! -d "$RESULTS/.git" ]; then
    rm -rf "$RESULTS"
    git clone --filter=blob:none --no-tags "$REMOTE" "$RESULTS" >/dev/null 2>&1 || return 1
  fi
  git -C "$RESULTS" remote set-url origin "$REMOTE" >/dev/null 2>&1 || true
  git -C "$RESULTS" fetch origin "$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" checkout -B "$RESULT_BRANCH" "origin/$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  git -C "$RESULTS" pull --ff-only origin "$RESULT_BRANCH" >/dev/null 2>&1 || true
  mkdir -p "$RESULTS/.devbridge/results" "$RESULTS/.devbridge/artifacts"

  if [ -n "$zipfile" ] && [ -s "$zipfile" ]; then
    artifact_size="$(wc -c < "$zipfile" | tr -d ' ')"
    if [ "$artifact_size" -lt 100000000 ]; then
      artifact_rel=".devbridge/artifacts/${job}.zip"
      cp -f "$zipfile" "$RESULTS/$artifact_rel" || return 1
      artifact_sha="$(sha256sum "$zipfile" | awk '{print $1}')"
    else
      printf 'artifact_publish_skipped=size_%s\n' "$artifact_size" >> "$log_file"
      artifact_size=0
    fi
  fi

  tailtext="$(tail -c 60000 "$log_file" 2>/dev/null || true)"
  result_file="$RESULTS/.devbridge/results/$job.json"
  jq -n \
    --arg protocol CORTEX_DEVBRIDGE_V1 \
    --arg agent_version '3+relay-sign-export' \
    --arg job_id "$job" \
    --arg capability RELAY_SIGN_EXPORT \
    --arg status "$status" \
    --argjson exit_code "$rc" \
    --arg finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg log_tail "$tailtext" \
    --arg artifact_path "$artifact_rel" \
    --arg artifact_sha256 "$artifact_sha" \
    --argjson artifact_size_bytes "$artifact_size" \
    '{protocol:$protocol,agent_version:$agent_version,job_id:$job_id,capability:$capability,status:$status,exit_code:$exit_code,finished_at:$finished_at,log_tail:$log_tail,artifact_path:$artifact_path,artifact_sha256:$artifact_sha256,artifact_size_bytes:$artifact_size_bytes}' \
    > "$result_file"

  git -C "$RESULTS" add ".devbridge/results/$job.json"
  [ -n "$artifact_rel" ] && git -C "$RESULTS" add "$artifact_rel"
  if ! git -C "$RESULTS" diff --cached --quiet; then
    git -C "$RESULTS" -c user.name='Cortex Dev Bridge' -c user.email='cortex-devbridge@localhost' \
      commit -m "devbridge(relay): $job ${status,,}" >/dev/null || return 1
    git -C "$RESULTS" push origin "HEAD:$RESULT_BRANCH" >/dev/null 2>&1 || return 1
  fi
}

run_relay_sign_export(){
  local jobfile="$1" job log_file package artifact_url expected_zip expected_unsigned expected_cert expected_version_code expected_version_name
  local signer ks passfile alias pass_tmp archive unsigned signed out_apk return_zip rc=0 cert verify actual installed_path installed_tmp installed_cert pkg_line

  job="$(jq -r '.job_id // empty' "$jobfile")"
  package="$(jq -r '.package // empty' "$jobfile")"
  artifact_url="$(jq -r '.params.artifact_url // empty' "$jobfile")"
  expected_zip="$(jq -r '.params.artifact_zip_sha256 // empty' "$jobfile" | tr '[:upper:]' '[:lower:]')"
  expected_unsigned="$(jq -r '.params.unsigned_apk_sha256 // empty' "$jobfile" | tr '[:upper:]' '[:lower:]')"
  expected_cert="$(jq -r '.params.expected_cert_sha256 // empty' "$jobfile" | tr '[:upper:]' '[:lower:]')"
  expected_version_code="$(jq -r '.params.version_code // empty' "$jobfile")"
  expected_version_name="$(jq -r '.params.version_name // empty' "$jobfile")"

  [ "$(jq -r '.protocol // empty' "$jobfile")" = CORTEX_DEVBRIDGE_V1 ] || return 80
  [ "$(jq -r '.repo // empty' "$jobfile")" = KAN1409/Cortex ] || return 81
  [ "$(jq -r '.authorized_owner // empty' "$jobfile")" = KAN1409 ] || return 82
  [ "$package" = com.kareem.secondbrain ] || return 83
  [ "$expected_cert" = fd402eefcec5b1576d6e7b1e5663a835d4c439d03baaa04506dd662e4b4c7d74 ] || return 84
  case "$artifact_url" in
    https://api.github.com/repos/KAN1409/Second-Brain/actions/artifacts/*/zip) ;;
    *) return 85 ;;
  esac
  [ -n "$expected_zip" ] && [ -n "$expected_unsigned" ] && [ -n "$expected_version_code" ] && [ -n "$expected_version_name" ] || return 86

  log_file="$ROOT/logs/$job.log"
  : > "$log_file"
  exec 9>&1
  exec >> "$log_file" 2>&1
  printf 'started_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'agent_version=3+relay-sign-export\n'

  signer="$(apksigner_bin)"
  ks="$HOME/.secondbrain-signing/second-brain-permanent.p12"
  passfile="$HOME/.secondbrain-signing/password.txt"
  alias=secondbrain
  archive="$WORK/$job-unsigned.zip"
  unsigned="$WORK/$job-unsigned.apk"
  signed="$WORK/Cortex-Relay-${expected_version_name}-permanent.apk"
  out_apk="/sdcard/Download/Cortex-Relay-${expected_version_name}-permanent.apk"
  return_zip="$WORK/$job-signed.zip"
  pass_tmp="$WORK/$job.pass"

  [ -n "$signer" ] || { echo APKSIGNER_NOT_FOUND; rc=90; }
  [ $rc -ne 0 ] || [ -f "$ks" ] || { echo PERMANENT_KEYSTORE_NOT_FOUND; rc=91; }
  [ $rc -ne 0 ] || [ -s "$passfile" ] || { echo PASSWORD_FILE_NOT_FOUND; rc=92; }

  if [ $rc -eq 0 ]; then
    tr -d '\r\n' < "$passfile" > "$pass_tmp"
    chmod 600 "$pass_tmp"
    cert="$(keytool -list -v -storetype PKCS12 -keystore "$ks" -storepass "$(cat "$pass_tmp")" -alias "$alias" 2>/dev/null \
      | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
    printf 'keystore_signer_sha256=%s\n' "$cert"
    [ "$cert" = "$expected_cert" ] || { echo KEYSTORE_SIGNER_MISMATCH; rc=93; }
  fi

  if [ $rc -eq 0 ]; then
    download_artifact "$artifact_url" "$archive" || { echo ARTIFACT_DOWNLOAD_FAILED; rc=94; }
  fi
  if [ $rc -eq 0 ]; then
    actual="$(sha256sum "$archive" | awk '{print $1}')"
    printf 'artifact_zip_sha256=%s\n' "$actual"
    [ "$actual" = "$expected_zip" ] || { echo ARTIFACT_ZIP_SHA_MISMATCH; rc=95; }
  fi
  if [ $rc -eq 0 ]; then
    extract_unsigned "$archive" "$unsigned" || { echo UNSIGNED_EXTRACT_FAILED; rc=96; }
    actual="$(sha256sum "$unsigned" | awk '{print $1}')"
    printf 'unsigned_apk_sha256=%s\n' "$actual"
    [ "$actual" = "$expected_unsigned" ] || { echo UNSIGNED_APK_SHA_MISMATCH; rc=97; }
  fi

  if [ $rc -eq 0 ]; then
    rm -f "$signed" "$signed.idsig"
    "$signer" sign --ks "$ks" --ks-type PKCS12 --ks-key-alias "$alias" \
      --ks-pass "file:$pass_tmp" --key-pass "file:$pass_tmp" --min-sdk-version 24 \
      --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
      --out "$signed" "$unsigned" || { echo SIGN_FAILED; rc=98; }
  fi
  if [ $rc -eq 0 ]; then
    verify="$("$signer" verify --verbose --print-certs --min-sdk-version 24 "$signed" 2>&1)"
    printf '%s\n' "$verify" | grep -E 'Verified using v[23] scheme|certificate SHA-256 digest' || true
    printf '%s\n' "$verify" | grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' || { echo V2_VERIFY_FAILED; rc=99; }
    [ $rc -ne 0 ] || printf '%s\n' "$verify" | grep -q 'Verified using v3 scheme (APK Signature Scheme v3): true' || { echo V3_VERIFY_FAILED; rc=100; }
  fi
  if [ $rc -eq 0 ]; then
    cert="$(apk_cert_sha "$signer" "$signed")"
    printf 'signed_signer_sha256=%s\n' "$cert"
    [ "$cert" = "$expected_cert" ] || { echo SIGNED_SIGNER_MISMATCH; rc=101; }
    printf 'signed_apk_sha256=%s\n' "$(sha256sum "$signed" | awk '{print $1}')"
  fi

  if [ $rc -eq 0 ]; then
    if command -v aapt >/dev/null 2>&1; then
      pkg_line="$(aapt dump badging "$signed" 2>/dev/null | sed -n '1p')"
      printf 'aapt_badging=%s\n' "$pkg_line"
      printf '%s' "$pkg_line" | grep -q "name='com.kareem.secondbrain'" || { echo PACKAGE_NAME_VERIFY_FAILED; rc=102; }
      [ $rc -ne 0 ] || printf '%s' "$pkg_line" | grep -q "versionCode='$expected_version_code'" || { echo VERSION_CODE_VERIFY_FAILED; rc=103; }
      [ $rc -ne 0 ] || printf '%s' "$pkg_line" | grep -q "versionName='$expected_version_name'" || { echo VERSION_NAME_VERIFY_FAILED; rc=104; }
    fi
  fi

  if [ $rc -eq 0 ]; then
    cp -f "$signed" "$out_apk" || { echo DOWNLOAD_COPY_FAILED; rc=105; }
    printf 'device_output=%s\n' "$out_apk"
  fi

  if [ $rc -eq 0 ]; then
    installed_path="$(rish -c "pm path '$package'" 2>/dev/null | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
    if [ -n "$installed_path" ]; then
      installed_tmp="$WORK/$job-installed.apk"
      if rish -c "cat '$installed_path'" > "$installed_tmp" 2>/dev/null && [ -s "$installed_tmp" ]; then
        installed_cert="$(apk_cert_sha "$signer" "$installed_tmp")"
        printf 'installed_signer_sha256=%s\n' "$installed_cert"
        if [ "$installed_cert" = "$expected_cert" ]; then
          staged="/data/local/tmp/Cortex-Relay-${expected_version_name}-permanent.apk"
          cat "$signed" | rish -c "cat > '$staged'; chmod 644 '$staged'" >/dev/null 2>&1 || rc=106
          if [ $rc -eq 0 ]; then
            install_out="$(rish -c "pm install -r '$staged'; x=\$?; rm -f '$staged'; exit \$x" 2>&1)" || rc=107
            printf '%s\n' "$install_out"
            [ $rc -ne 0 ] || printf '%s\n' "$install_out" | grep -q Success || rc=108
          fi
          if [ $rc -eq 0 ]; then
            rish -c 'logcat -b crash -c' >/dev/null 2>&1 || true
            rish -c "ACT=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER '$package' 2>/dev/null | tail -n1); am start -W -n \"\$ACT\"" >/dev/null 2>&1 || rc=109
            sleep 6
            pid="$(rish -c "pidof '$package'" 2>/dev/null | tr -d '\r')"
            printf 'pid=%s\n' "$pid"
            [ -n "$pid" ] || rc=110
            crashes="$(rish -c 'logcat -b crash -d -t 180' 2>/dev/null)"
            if printf '%s' "$crashes" | grep -q "$package" && printf '%s' "$crashes" | grep -Eq 'FATAL EXCEPTION|Process:'; then
              printf '%s\n' "$crashes" | tail -n100
              rc=111
            fi
          fi
        else
          echo INSTALL_SKIPPED_INSTALLED_SIGNER_MISMATCH
        fi
      fi
      rm -f "$installed_tmp"
    else
      echo INSTALL_SKIPPED_PACKAGE_NOT_INSTALLED
    fi
  fi

  if [ $rc -eq 0 ]; then
    zip_signed "$signed" "$return_zip" || { echo RETURN_ZIP_FAILED; rc=112; }
    [ $rc -ne 0 ] || printf 'return_zip_sha256=%s\n' "$(sha256sum "$return_zip" | awk '{print $1}')"
    [ $rc -ne 0 ] || printf 'return_zip_bytes=%s\n' "$(wc -c < "$return_zip" | tr -d ' ')"
  fi

  rm -f "$pass_tmp" "$archive" "$unsigned"
  printf 'finished_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  exec 1>&9 9>&-

  if [ $rc -eq 0 ]; then
    publish_custom "$job" SUCCESS 0 "$log_file" "$return_zip" || return 113
  else
    publish_custom "$job" FAILED "$rc" "$log_file" '' || true
  fi
  printf '%s\n' "$job" >> "$STATE"
  return "$rc"
}

# Intercept only the explicitly bounded Relay sign/export capability, then delegate all normal jobs to Agent V3.
while IFS= read -r path; do
  [ -n "$path" ] || continue
  jobfile="$ROOT/relay-sign-current.json"
  git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$path" > "$jobfile" 2>/dev/null || continue
  [ "$(jq -r '.capability // empty' "$jobfile")" = RELAY_SIGN_EXPORT ] || continue
  job="$(jq -r '.job_id // empty' "$jobfile")"
  [ -n "$job" ] || continue
  grep -Fxq "$job" "$STATE" 2>/dev/null && continue
  run_relay_sign_export "$jobfile" || true
done < <(git -C "$CONTROL" ls-tree -r --name-only "origin/$CONTROL_BRANCH" '.devbridge/jobs' | grep '\.json$' || true)

# Hot-load the stable current Agent V3 for all ordinary bounded capabilities.
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }
bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
