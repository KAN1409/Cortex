#!/data/data/com.termux/files/usr/bin/bash
set -u

CONTROL_BRANCH="${CORTEX_DEVBRIDGE_CONTROL_BRANCH:-infra/termux-dev-bridge-v1}"
ROOT="${CORTEX_DEVBRIDGE_HOME:-$HOME/.cortex-devbridge}"
CONTROL="$ROOT/control"
TARGET="$ROOT/agent-v3.runtime.sh"

[ -d "$CONTROL/.git" ] || { echo DEVBRIDGE_CONTROL_CLONE_MISSING >&2; exit 70; }
git -C "$CONTROL" show "origin/$CONTROL_BRANCH:scripts/devbridge-agent-v3.sh" > "$TARGET" 2>/dev/null || { echo DEVBRIDGE_V3_FETCH_FAILED >&2; exit 71; }

# Hot-patch bounded fresh-Cortex support into the current V3 runtime without requiring a manual
# re-bootstrap. Keep the source V3 contract intact while adding only the fresh package and robust
# signer/package-path inspection needed for fail-closed update checks on Android 16.
python - "$TARGET" <<'PYTRANSFORM' || { rm -f "$TARGET"; echo DEVBRIDGE_V3_PATCH_FAILED >&2; exit 73; }
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()

old_allow = "PKG_ALLOW='^(com\\.kareem\\.cortex|com\\.kareem\\.secondbrain)$'"
new_allow = "PKG_ALLOW='^(com\\.kareem\\.cortex|com\\.kareem\\.cortex\\.rebuild|com\\.kareem\\.secondbrain)$'"
if old_allow not in s:
    raise SystemExit('package allowlist marker missing')
s = s.replace(old_allow, new_allow, 1)

old_signer = r'''signer_sha(){
  local signer apk digest
  signer="$(apksigner_bin)"; [ -n "$signer" ] || return 1
  apk="$1"
  digest="$("$signer" verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1 | tr -d ':[:space:]' | tr 'A-F' 'a-f')"
  [ -n "$digest" ] || return 1
  printf '%s' "$digest"
}
'''
new_signer = r'''signer_sha(){
  local signer apk digest
  apk="$1"
  signer="$(apksigner_bin)"
  if [ -n "$signer" ]; then
    digest="$("$signer" verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1 | tr -d ':[:space:]' | tr 'A-F' 'a-f')"
    if [ -n "$digest" ]; then printf '%s' "$digest"; return 0; fi
  fi
  command -v python >/dev/null 2>&1 || return 1
  python - "$apk" <<'PYAPK'
import hashlib, struct, sys
p=sys.argv[1]
d=open(p,'rb').read()
e=d.rfind(b'PK\x05\x06', max(0, len(d)-65557))
if e < 0: raise SystemExit(2)
c=struct.unpack_from('<I', d, e+16)[0]
f=c-24
if f < 0 or d[f+8:f+24] != b'APK Sig Block 42': raise SystemExit(3)
sz=struct.unpack_from('<Q', d, f)[0]
s=c-(sz+8)
if s < 0 or struct.unpack_from('<Q', d, s)[0] != sz: raise SystemExit(4)
pairs={}; o=s+8
while o < f:
    n=struct.unpack_from('<Q', d, o)[0]; o += 8
    if n < 4 or o+n > f: break
    i=struct.unpack_from('<I', d, o)[0]
    pairs[i]=d[o+4:o+n]; o += n
def lp(b,p):
    n=struct.unpack_from('<I', b, p)[0]; p += 4
    if p+n > len(b): raise ValueError()
    return b[p:p+n], p+n
for i in (0x1b93ad61,0xf05368c0,0x7109871a):
    b=pairs.get(i)
    if not b: continue
    try:
        signers,_=lp(b,0)
        signer,_=lp(signers,0)
        signed,_=lp(signer,0)
        _,q=lp(signed,0)
        certs,_=lp(signed,q)
        cert,_=lp(certs,0)
        print(hashlib.sha256(cert).hexdigest(), end='')
        raise SystemExit(0)
    except (ValueError, struct.error):
        pass
raise SystemExit(5)
PYAPK
}
'''
if old_signer not in s:
    raise SystemExit('signer_sha marker missing')
s = s.replace(old_signer, new_signer, 1)

old_path = r'''  installed_path="$(rish -c "pm path '$pkg'" 2>/dev/null | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
  [ -n "$installed_path" ] || { echo INSTALLED_APK_NOT_FOUND; return 25; }
'''
new_path = r'''  installed_path="$(rish -c "/system/bin/pm path '$pkg'" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
  if [ -z "$installed_path" ]; then
    installed_path="$(rish -c "cmd package path '$pkg'" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n1)"
  fi
  if [ -z "$installed_path" ]; then
    code_path="$(rish -c "dumpsys package '$pkg'" 2>/dev/null | tr -d '\r' | sed -n 's/^[[:space:]]*codePath=//p' | head -n1)"
    [ -n "$code_path" ] && installed_path="${code_path%/}/base.apk"
  fi
  [ -n "$installed_path" ] || { echo INSTALLED_APK_NOT_FOUND; return 25; }
'''
if old_path not in s:
    raise SystemExit('installed path marker missing')
s = s.replace(old_path, new_path, 1)

p.write_text(s)
PYTRANSFORM

bash -n "$TARGET" >/dev/null 2>&1 || { rm -f "$TARGET"; echo DEVBRIDGE_V3_SYNTAX_FAILED >&2; exit 72; }
chmod 700 "$TARGET"
exec "$TARGET" "$@"
