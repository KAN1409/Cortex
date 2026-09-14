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
WORKFLOW = ROOT / ".github/workflows/android-build.yml"
NAVIGATION = ROOT / "app/src/main/java/com/kareem/cortex/CortexNavigation.java"
PRODUCTION_ACCEPTANCE = ROOT / "app/src/androidTest/java/com/kareem/cortex/CortexPublishAcceptanceTest.java"
PRODUCTION_SECONDARY_ACCEPTANCE = ROOT / "app/src/androidTest/java/com/kareem/cortex/CortexProductionSecondaryAcceptanceTest.java"
INTERNAL_DIAGNOSTIC_ACCEPTANCE = ROOT / "app/src/androidTest/java/com/kareem/cortex/CortexInternalDiagnosticCoverageTest.java"
ANDROID = "{http://schemas.android.com/apk/res/android}"

EXPECTED_APP_ID = "com.kareem.cortex"
EXPECTED_LABEL = "Cortex"
EXPECTED_LAUNCHER = ".CortexShellActivity"
EXPECTED_V146_BRANCH = "v146/hard-explicit-request-boundary"
EXPECTED_V146_VERSION_CODE = 146
EXPECTED_V146_VERSION_NAME = "2.34.0-v146-ui-action-truth"
FORBIDDEN_EXPORTED_DIAGNOSTICS = {
    ".CortexEndToEndActivity",
    ".CortexAuditActivity",
    ".OcrTestActivity",
    ".CortexAsrLabActivity",
    ".CapabilityMatrixActivity",
    ".ExternalModelCheckActivity",
    ".RelevanceEvaluationActivity",
    ".EnvironmentActivity",
    ".CognitiveShadowActivity",
    ".CrashReportActivity",
}
PRODUCTION_SECONDARY_INTERNAL_ONLY = {".SettingsActivity", ".CortexStatusActivity"}
EXPECTED_LEGACY_ALIASES = {
    ".NowActivity": ".CortexShellActivity",
    ".SatinBriefActivity": ".ProposalBriefActivity",
    ".CortexOrbBriefActivity": ".ProposalBriefActivity",
    ".PremiumHomeActivity": ".ProposalBriefActivity",
    ".CaptureActivity": ".ProposalCaptureActivity",
    ".SatinCaptureActivity": ".ProposalCaptureActivity",
    ".PeopleProjectsActivity": ".ProposalPeopleProjectsActivity",
    ".AskCortexActivity": ".ProposalAskCortexActivity",
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

    activity_by_name = {x.attrib.get(ANDROID + "name", ""): x for x in app.findall("activity")}
    for name in FORBIDDEN_EXPORTED_DIAGNOSTICS | PRODUCTION_SECONDARY_INTERNAL_ONLY:
        activity = activity_by_name.get(name)
        if activity is None:
            fail(f"required activity missing from manifest: {name}")
        exported = activity.attrib.get(ANDROID + "exported", "false").lower() == "true"
        if exported:
            fail(f"internal-only activity is externally exported: {name}")

    aliases = {
        x.attrib.get(ANDROID + "name", ""): x.attrib.get(ANDROID + "targetActivity", "")
        for x in app.findall("activity-alias")
    }
    for alias, target in EXPECTED_LEGACY_ALIASES.items():
        actual = aliases.get(alias)
        if actual != target:
            fail(f"legacy surface alias drifted: {alias} -> {actual!r}; expected {target}")
        alias_node = next(x for x in app.findall("activity-alias") if x.attrib.get(ANDROID + "name", "") == alias)
        if alias_node.attrib.get(ANDROID + "exported", "false").lower() == "true":
            fail(f"legacy compatibility alias must stay internal: {alias}")


def verify_build(expected_code: int | None, expected_name: str | None) -> None:
    source = text(BUILD)
    app_id = one(r"applicationId\s+['\"]([^'\"]+)['\"]", source, "applicationId")
    if app_id != EXPECTED_APP_ID:
        fail(f"applicationId drifted: {app_id}")
    if "def cortexDebugKeystore = file('cortex-debug.keystore')" not in source:
        fail("permanent Cortex signing keystore path variable drifted")
    if not re.search(r"signingConfigs\s*\{.*?debug\s*\{.*?storeFile\s+cortexDebugKeystore", source, re.DOTALL):
        fail("debug signing config no longer uses the permanent Cortex keystore")
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
        "Main launcher: `CortexShellActivity`",
        "CortexInternalDiagnosticCoverageTest",
    ]
    for token in required:
        if token not in doc:
            fail(f"identity document is stale; missing {token}")


def verify_release_workflow() -> None:
    workflow = text(WORKFLOW)
    if EXPECTED_V146_BRANCH not in workflow:
        fail("v146 branch is not a first-class Android CI trigger")
    if f"versionCode {EXPECTED_V146_VERSION_CODE}" not in workflow:
        fail("v146 CI versionCode stamp drifted")
    if EXPECTED_V146_VERSION_NAME not in workflow:
        fail("v146 CI versionName stamp drifted")
    if "python tools/verify_product_contracts.py" not in workflow:
        fail("Android CI no longer enforces the product identity contract")
    if ":app:assembleDebugAndroidTest" not in workflow:
        fail("instrumentation accessibility contracts are no longer compiled in CI")


def verify_navigation_contract() -> None:
    navigation = text(NAVIGATION)
    required = [
        "CortexShellActivity.class",
        "EXTRA_DESTINATION_ID",
        "EXTRA_OPEN_DOCK",
        "openDock(Activity from)",
        "CortexStatusActivity.class",
    ]
    for token in required:
        if token not in navigation:
            fail(f"canonical shell navigation drifted; missing {token}")


def verify_acceptance_split() -> None:
    production = text(PRODUCTION_ACCEPTANCE)
    secondary = text(PRODUCTION_SECONDARY_ACCEPTANCE)
    internal = text(INTERNAL_DIAGNOSTIC_ACCEPTANCE)
    if "CortexDestinationRegistry.primary()" not in production:
        fail("production surface acceptance is not derived from canonical primary destinations")
    if "test02_everyDeclaredUserSurfaceRenders" in production:
        fail("legacy every-manifest-activity production acceptance philosophy returned")
    for activity in FORBIDDEN_EXPORTED_DIAGNOSTICS:
        simple = activity.removeprefix(".")
        if simple in production or simple in secondary:
            fail(f"internal diagnostic leaked into production acceptance: {simple}")
        if simple not in internal:
            fail(f"internal diagnostic lost runtime coverage: {simple}")
    for simple in ("SettingsActivity", "CortexStatusActivity"):
        if simple not in secondary:
            fail(f"production secondary user surface lost acceptance coverage: {simple}")
        if simple in internal:
            fail(f"production secondary user surface incorrectly classified as diagnostic: {simple}")
    if "assertFalse(\"Internal diagnostic must not be exported:" not in internal:
        fail("internal diagnostic runtime suite no longer asserts non-exported status")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-version-code", type=int)
    parser.add_argument("--expected-version-name")
    args = parser.parse_args()
    verify_manifest()
    verify_build(args.expected_version_code, args.expected_version_name)
    verify_identity_document()
    verify_release_workflow()
    verify_navigation_contract()
    verify_acceptance_split()
    print("CORTEX_CONTRACT_PASS: identity, shell launcher, signing, v146 release, legacy aliases, user health, diagnostic isolation and acceptance boundaries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
