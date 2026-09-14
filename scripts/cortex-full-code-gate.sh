#!/usr/bin/env bash
set -uo pipefail

ROOT="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$ROOT"
mkdir -p audit
: > audit/gates.tsv
FAIL=0

record() {
  printf '%s\t%s\n' "$1" "$2" >> audit/gates.tsv
}

run_gate() {
  local name="$1"; shift
  local log="audit/${name//[^A-Za-z0-9_.-]/_}.log"
  printf '\n========== %s ==========\n' "$name"
  set +e
  "$@" 2>&1 | tee "$log"
  local rc=${PIPESTATUS[0]}
  set -e
  if [ "$rc" -eq 0 ]; then
    record "$name" PASS
    printf 'GATE PASS: %s\n' "$name"
  else
    record "$name" "FAIL:$rc"
    printf 'GATE FAIL: %s (rc=%s)\n' "$name" "$rc" >&2
    FAIL=$((FAIL+1))
  fi
}

set -e
python3 - <<'PY'
from pathlib import Path
import subprocess
extensions = {
    '.java':'java', '.kt':'kotlin', '.kts':'kotlin-script', '.xml':'xml',
    '.gradle':'gradle', '.properties':'properties', '.sh':'shell',
    '.yml':'yaml', '.yaml':'yaml', '.json':'json', '.ts':'typescript',
    '.js':'javascript', '.mjs':'javascript', '.cjs':'javascript',
    '.pro':'proguard', '.toml':'toml', '.py':'python'
}
root=Path('.')
files=subprocess.check_output(['git','ls-files'],text=True).splitlines()
rows=[]
for name in files:
    p=root/name; kind=extensions.get(p.suffix.lower())
    if not kind or not p.is_file(): continue
    try: data=p.read_bytes()
    except OSError: continue
    if b'\x00' in data[:4096]: continue
    lines=data.count(b'\n')+(1 if data and not data.endswith(b'\n') else 0)
    rows.append((name,lines,len(data),kind))
rows.sort()
Path('audit/manifest.tsv').write_text('path\tlines\tbytes\tkind\n'+''.join(f'{p}\t{l}\t{b}\t{k}\n' for p,l,b,k in rows))
Path('audit/coverage-summary.txt').write_text(f'tracked_code_config_files={len(rows)}\ntracked_code_config_lines={sum(r[1] for r in rows)}\ntracked_code_config_bytes={sum(r[2] for r in rows)}\n')
print(Path('audit/coverage-summary.txt').read_text(),end='')
PY

run_gate repo_contract_audit bash scripts/cortex-repo-audit.sh .
run_gate source_syntax python3 - <<'PY'
from pathlib import Path
import json, subprocess, sys, xml.etree.ElementTree as ET
bad=[]
for name in subprocess.check_output(['git','ls-files'],text=True).splitlines():
    p=Path(name)
    try:
        if p.suffix=='.json': json.loads(p.read_text(encoding='utf-8'))
        elif p.suffix=='.xml' and 'app/src/' in name: ET.parse(p)
    except Exception as e: bad.append(f'{name}: {e}')
if bad:
    print('\n'.join(bad),file=sys.stderr); raise SystemExit(1)
print('JSON/XML tracked source syntax: PASS')
PY
run_gate shell_syntax bash -c 'set -euo pipefail; while IFS= read -r f; do bash -n "$f"; done < <(git ls-files "*.sh")'
run_gate python_syntax bash -c 'set -euo pipefail; while IFS= read -r f; do python3 -m py_compile "$f"; done < <(git ls-files "*.py")'
run_gate whitespace_and_conflict_check git diff --check HEAD

if [ -f chatgpt-bridge/package.json ]; then
  run_gate bridge_install bash -c 'cd chatgpt-bridge && npm install --no-audit --no-fund --package-lock=false'
  run_gate bridge_typecheck bash -c 'cd chatgpt-bridge && npm run check'
fi

if command -v semgrep >/dev/null 2>&1; then
  run_gate semgrep_scan semgrep scan --config p/default --config p/security-audit --config p/secrets --metrics=off --json --output audit/semgrep.json app/src chatgpt-bridge/src scripts tools .github
  run_gate semgrep_error_gate python3 - <<'PY'
import json
from pathlib import Path
p=Path('audit/semgrep.json')
if not p.exists(): raise SystemExit('missing audit/semgrep.json')
results=json.loads(p.read_text()).get('results',[])
errors=[r for r in results if str(r.get('extra',{}).get('severity','')).upper()=='ERROR']
print(f'semgrep_findings={len(results)} error_findings={len(errors)}')
for r in errors[:200]:
    s=r.get('start',{}); print(f"ERROR {r.get('path')}:{s.get('line')} {r.get('check_id')} {r.get('extra',{}).get('message','')}")
raise SystemExit(1 if errors else 0)
PY
else
  record semgrep_scan MISSING; FAIL=$((FAIL+1))
fi

if command -v gradle >/dev/null 2>&1; then
  run_gate android_lint gradle :app:lintDebug --stacktrace
  run_gate unit_tests gradle :app:testDebugUnitTest --stacktrace
  run_gate java_compile gradle :app:compileDebugJavaWithJavac --stacktrace
  run_gate kotlin_compile gradle :app:compileDebugKotlin --stacktrace
  run_gate android_test_compile gradle :app:assembleDebugAndroidTest --stacktrace
else
  record gradle MISSING; FAIL=$((FAIL+1))
fi

printf '\n========== FULL CODE GATE SUMMARY ==========\n'
cat audit/coverage-summary.txt
cat audit/gates.tsv
if [ "$FAIL" -ne 0 ]; then
  printf 'CORTEX_FULL_CODE_GATE=FAIL failures=%s\n' "$FAIL" >&2
  exit 2
fi
printf 'CORTEX_FULL_CODE_GATE=PASS\n'
