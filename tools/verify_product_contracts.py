#!/usr/bin/env python3
"""Fail CI when Cortex product identity/release contracts drift."""
from __future__ import annotations

import argparse
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
BUILD = ROOT / "app/build.gradle"
ANDROID = "{http://schemas.android.com/apk/res/android}"

EXPECTED_APP_ID = "com.kareem.cortex"
EXPECTED_LABEL = "Cortex"
EXPECTED_LAUNCHER = ".NowActivity"
FORBIDDEN_EXPORTED_DIAGNOSTICS = {
    ".CortexEndToEndActivity",
    ".CortexAuditActivity",
    ".OcrTestActivity",
    ".CortexAsrLabActivity",
    ".CapabilityMatrixActivity",
    ".ExternalModelCheckActivity",
    ".RelevanceEvaluationActivity",
    ".EnvironmentActivity",
    ".CortexStatusActivity",
    ".CognitiveShadowActivity",
    ".CrashReportActivity",
}


def fail(message: str) -> None:
    raise SystemExit("CORTEX_CONTRACT_FAIL: " + message)


def text(path: pathlib.Path) -> str:
    if not path.is_file():
        fail(f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def one(pattern: str, source: str, label: str) -> str:
    match = re.search(pattern, source, re.MULTILINE)
    if not match:
        fail(f"could not resolve {label}")
    return match.group(1)


def launcher(root: ET.Element) -> str:
    app = root.find("application")
    if app is None:
        fail("manifest has no application element")
    launchers: list[str] = []
    for node in list(app):
        if node.tag not in {"activity", "activity-alias"}:
            continue
        name = node.attrib.get(ANDROID + "name", "")
        target = node.attrib.get(ANDROID + "targetActivity", name)
        for intent in node.findall("intent-filter"):
            actions = {x.attrib.get(ANDROID + "name", "") for x in intent.findall("action")}
            categories = {x.attrib.get(ANDROID + "name", "") for x in intent.findall("category")}
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
                launchers.append(target)
    if len(launchers) != 1:
        fail(f"expected exactly one launcher, found {launchers}")
    return launchers[0]


def verify_manifest() -> None:
    root = ET.parse(MANIFEST).getroot()
    app = root.find("application")
    if app is None:
        fail("manifest has no application")
    label = app.attrib.get(ANDROID + "label", "")
    if label != EXPECTED_LABEL:
        fail(f"visible app label drifted: {label!r}")
    resolved_launcher = launcher(root)
    if resolved_launcher != EXPECTED_LAUNCHER:
        fail(f"launcher drifted: {resolved_launcher}; expected {EXPECTED_LAUNCHER}")
    for activity in app.findall("activity"):
        name = activity.attrib.get(ANDROID + "name", "")
        if name not in FORBIDDEN_EXPORTED_DIAGNOSTICS:
            continue
        exported = activity.attrib.get(ANDROID + "exported", "false").lower() == "true"
        if exported:
            fail(f"internal diagnostic is externally exported: {name}")


def verify_build(expected_code: int | None, expected_name: str | None) -> None:
    source = text(BUILD)
    app_id = one(r"applicationId\s+['\"]([^'\"]+)['\"]", source, "applicationId")
    if app_id != EXPECTED_APP_ID:
        fail(f"applicationId drifted: {app_id}")
    if "storeFile file('cortex-debug.keystore')" not in source:
        fail("permanent Cortex signing keystore path is not configured")
    if "keyAlias 'androiddebugkey'" not in source:
        fail("permanent Cortex signing key alias drifted")
    if expected_code is not None:
        code = int(one(r"versionCode\s+(\d+)", source, "versionCode"))
        if code != expected_code:
            fail(f"versionCode={code}; expected {expected_code}")
    if expected_name is not None:
        name = one(r"versionName\s+['\"]([^'\"]+)['\"]", source, "versionName")
        if name != expected_name:
            fail(f"versionName={name!r}; expected {expected_name!r}")


def verify_identity_document() -> None:
    doc = text(ROOT / "docs/CORTEX_PRODUCT_IDENTITY_CONTRACT.md")
    required = [
        "`com.kareem.cortex`",
        "Visible app label: `Cortex`",
        "Main launcher: `NowActivity`",
    ]
    for token in required:
        if token not in doc:
            fail(f"identity document is stale; missing {token}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-version-code", type=int)
    parser.add_argument("--expected-version-name")
    args = parser.parse_args()
    verify_manifest()
    verify_build(args.expected_version_code, args.expected_version_name)
    verify_identity_document()
    print("CORTEX_CONTRACT_PASS: identity, launcher, signing config and diagnostic export boundaries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
