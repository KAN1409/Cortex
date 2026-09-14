#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
PINS = {
    "actions/checkout@v4": "actions/checkout@11d5960a326750d5838078e36cf38b85af677262",
    "actions/checkout@v6": "actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803",
    "actions/setup-java@v4": "actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3",
    "actions/setup-java@v5": "actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961",
    "actions/setup-node@v4": "actions/setup-node@49933ea5288caeca8642d1e84afbd3f7d6820020",
    "actions/upload-artifact@v4": "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
    "android-actions/setup-android@v3": "android-actions/setup-android@9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407",
    "gradle/actions/setup-gradle@v4": "gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a",
    "gradle/actions/setup-gradle@v6": "gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb",
    "github/codeql-action/init@v3": "github/codeql-action/init@faaca9a8f6edddba5725ffe5adefdab6669a2eca",
    "github/codeql-action/analyze@v3": "github/codeql-action/analyze@faaca9a8f6edddba5725ffe5adefdab6669a2eca",
    "reactivecircus/android-emulator-runner@v2.38.0": "reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d",
}
sha_ref=re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)?@[0-9a-f]{40}$")
uses_ref=re.compile(r"^(\s*uses:\s*)([^\s#]+)(\s*(?:#.*)?)$")
changed=[];remaining=[]
for path in sorted([*WORKFLOWS.glob("*.yml"),*WORKFLOWS.glob("*.yaml")]):
    original=path.read_text(encoding="utf-8");text=original
    for mutable,pinned in PINS.items(): text=text.replace(mutable,pinned)
    if text!=original:
        path.write_text(text,encoding="utf-8");changed.append(str(path.relative_to(ROOT)))
    for number,line in enumerate(text.splitlines(),start=1):
        match=uses_ref.match(line)
        if not match: continue
        ref=match.group(2)
        if ref.startswith("./") or ref.startswith("docker://"): continue
        if not sha_ref.match(ref): remaining.append(f"{path.relative_to(ROOT)}:{number}:{ref}")
print(f"github_action_pin_changed_files={len(changed)}")
for path in changed: print(path)
if remaining:
    print("UNPINNED_ACTION_REFERENCES")
    for item in remaining: print(item)
    raise SystemExit(2)
print("github_action_pins=PASS")
