#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL="$ROOT/control"
TARGET="$ROOT/agent-v3.runtime.sh"

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
if [ -L "$CONTROL" ]; then
  git -C "$CONTROL" show-ref --verify --quiet "refs/remotes/origin/$CONTROL_BRANCH" || { echo DEVBRIDGE_CONTROL_REF_MISSING >&2; exit 73; }
elif command -v timeout >/dev/null 2>&1; then
  timeout 45 git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
else
  git -C "$CONTROL" fetch origin "$CONTROL_BRANCH" >/dev/null 2>&1 || true
fi

# Unattended mode prefers the newest explicitly autorun job. This prevents stale backlog from
# blocking a current device update while preserving normal backlog behavior when no autorun exists.
if [ -z "${CORTEX_DEVBRIDGE_ONLY_JOB:-}" ] && command -v jq >/dev/null 2>&1; then
  AUTO_JOB="$(
    while IFS= read -r path; do
      [ -n "$path" ] || continue
      raw="$(git -C "$CONTROL" show "origin/$CONTROL_BRANCH:$path" 2>/dev/null || true)"
      [ -n "$raw" ] || continue
      printf '%s' "$raw" | jq -e '.params.autorun == true' >/dev/null 2>&1 || continue
      printf '%s\n' "$raw" | jq -r '.job_id // empty'
    done < <(git -C "$CONTROL" ls-tree -r --name-only "origin/$CONTROL_BRANCH" '.devbridge/jobs' 2>/dev/null | grep '\.json$' || true)
  )"
  AUTO_JOB="$(printf '%s\n' "$AUTO_JOB" | grep '^job_' | tail -n1)"
  [ -n "$AUTO_JOB" ] && export CORTEX_DEVBRIDGE_ONLY_JOB="$AUTO_JOB"
fi

git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }

python - "$TARGET" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
needle = '  [ -n "$installed_path" ] || { echo INSTALLED_APK_NOT_FOUND; return 25; }\n'
replacement = r'''  if [ -z "$installed_path" ]; then
    [ "$pkg" = 'com.kareem.cortex' ] || { echo FRESH_INSTALL_DENY_PACKAGE; return 25; }
    [ -f "$SIGNER_SOURCE" ] || { echo SIGNER_SOURCE_NOT_FOUND; return 31; }
    command -v keytool >/dev/null 2>&1 || { echo KEYTOOL_NOT_FOUND; return 32; }
    local source_sha
    source_sha="$(keytool -list -v -keystore "$SIGNER_SOURCE" -storepass android -alias androiddebugkey 2>/dev/null | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n1 | tr -d ':[:space:]' | tr 'A-F' 'a-f')"
    [ -n "$source_sha" ] || { echo SIGNER_SOURCE_SHA_FAIL; return 33; }
    echo "candidate_signer_sha256=$candidate_sha"
    echo "source_signer_sha256=$source_sha"
    [ "$candidate_sha" = "$source_sha" ] || { echo SIGNER_MISMATCH_REFUSE_FRESH_INSTALL; return 34; }
    staged="/data/local/tmp/cortex-devbridge-$$.apk"
    cat "$apk" | rish -c "cat > '$staged'" >/dev/null 2>&1 || { echo APK_STAGE_FAIL; return 29; }
    out="$(rish -c "pm install '$staged'" 2>&1)"; rc=$?
    rish -c "rm -f '$staged'" >/dev/null 2>&1 || true
    printf '%s\n' "$out"
    [ $rc -eq 0 ] && printf '%s' "$out" | grep -q 'Success' || return 30
    echo "fresh_install=true"
    echo "apk_sha256=$(sha256sum "$apk" | awk '{print $1}')"
    return 0
  fi
'''
if needle not in text: raise SystemExit('DEVBRIDGE_FRESH_INSTALL_PATCH_TARGET_MISSING')
text = text.replace(needle, replacement, 1)
filter_needle = '''    [ -n "$job" ] || continue\n    grep -Fxq "$job" "$STATE" && continue\n'''
filter_replacement = '''    [ -n "$job" ] || continue\n    if [ -n "${CORTEX_DEVBRIDGE_ONLY_JOB:-}" ] && [ "$job" != "$CORTEX_DEVBRIDGE_ONLY_JOB" ]; then continue; fi\n    grep -Fxq "$job" "$STATE" && continue\n'''
if filter_needle not in text: raise SystemExit('DEVBRIDGE_ONLY_JOB_PATCH_TARGET_MISSING')
text = text.replace(filter_needle, filter_replacement, 1)
parser_needle = '''        signer,_=lp(b,0)\n        signed,_=lp(signer,0)\n        _,q=lp(signed,0)\n        certs,_=lp(signed,q)\n        cert,_=lp(certs,0)\n'''
parser_replacement = '''        signers,_=lp(b,0)\n        signer,_=lp(signers,0)\n        signed,_=lp(signer,0)\n        _,q=lp(signed,0)\n        certs,_=lp(signed,q)\n        cert,_=lp(certs,0)\n'''
if parser_needle not in text: raise SystemExit('DEVBRIDGE_SIGNER_PARSER_PATCH_TARGET_MISSING')
text = text.replace(parser_needle, parser_replacement, 1)
clone_needle = '''  git -C "$dir" remote set-url origin "$REMOTE" >/dev/null 2>&1 || return 1\n  git -C "$dir" fetch --prune origin "$branch" >/dev/null 2>&1 || return 1\n'''
clone_replacement = '''  git -C "$dir" remote set-url origin "$REMOTE" >/dev/null 2>&1 || return 1\n  if [ "$dir" = "$CONTROL" ] && [ -L "$CONTROL" ]; then\n    git -C "$dir" show-ref --verify --quiet "refs/remotes/origin/$branch" || return 1\n  elif command -v timeout >/dev/null 2>&1; then\n    timeout 45 git -C "$dir" fetch --prune origin "$branch" >/dev/null 2>&1 || return 1\n  else\n    git -C "$dir" fetch --prune origin "$branch" >/dev/null 2>&1 || return 1\n  fi\n'''
if clone_needle not in text: raise SystemExit('DEVBRIDGE_LOCAL_CONTROL_CLONE_PATCH_TARGET_MISSING')
text = text.replace(clone_needle, clone_replacement, 1)
prepare_fetch_needle = '  git -C "$CONTROL" fetch origin "$ref" >/dev/null 2>&1 || return 1\n'
prepare_fetch_replacement = '''  local source_ref='FETCH_HEAD'\n  if [ -L "$CONTROL" ]; then\n    source_ref="refs/remotes/origin/$ref"\n    git -C "$CONTROL" show-ref --verify --quiet "$source_ref" || return 1\n  elif command -v timeout >/dev/null 2>&1; then\n    timeout 45 git -C "$CONTROL" fetch origin "$ref" >/dev/null 2>&1 || return 1\n  else\n    git -C "$CONTROL" fetch origin "$ref" >/dev/null 2>&1 || return 1\n  fi\n'''
if prepare_fetch_needle not in text: raise SystemExit('DEVBRIDGE_LOCAL_CONTROL_REF_PATCH_TARGET_MISSING')
text = text.replace(prepare_fetch_needle, prepare_fetch_replacement, 1)
worktree_needle = '  git -C "$CONTROL" worktree add --detach "$dir" FETCH_HEAD >/dev/null 2>&1 || return 1\n'
worktree_replacement = '  git -C "$CONTROL" worktree add --detach "$dir" "$source_ref" >/dev/null 2>&1 || return 1\n'
if worktree_needle not in text: raise SystemExit('DEVBRIDGE_LOCAL_CONTROL_WORKTREE_PATCH_TARGET_MISSING')
text = text.replace(worktree_needle, worktree_replacement, 1)
text = text.replace('candidate_signer_sha256=|installed_signer_sha256=|apk_sha256=','candidate_signer_sha256=|installed_signer_sha256=|source_signer_sha256=|fresh_install=|apk_sha256=',1)
path.write_text(text)
PY

bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
"$TARGET" "$@"
rc=$?

# Install the current supervisor source. Only an agent that was actually invoked by an OLD
# supervisor replaces/restarts that parent. Bootstrap/manual one-shots never spawn duplicates.
SUP_NEXT="$ROOT/supervisor.next.sh"
SUP="$ROOT/supervisor.sh"
if git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-supervisor-v2.sh" > "$SUP_NEXT" 2>/dev/null && bash -n "$SUP_NEXT" >/dev/null 2>&1; then
  chmod 700 "$SUP_NEXT"
  mv -f "$SUP_NEXT" "$SUP"
  parent_args="$(ps -p "$PPID" -o args= 2>/dev/null || true)"
  if [ "${CORTEX_DEVBRIDGE_SUPERVISOR_V2:-0}" != 1 ] && printf '%s' "$parent_args" | grep -Fq "$ROOT/supervisor.sh"; then
    old_parent="$PPID"
    command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
    nohup "$SUP" >> "$ROOT/supervisor.stdout.log" 2>> "$ROOT/supervisor.stderr.log" < /dev/null &
    echo $! > "$ROOT/supervisor.pid"
    kill "$old_parent" 2>/dev/null || true
  fi
else
  rm -f "$SUP_NEXT"
fi
exit $rc
