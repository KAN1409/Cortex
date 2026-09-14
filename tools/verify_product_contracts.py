#!/usr/bin/env python3
"""Fail CI when Cortex product identity/release contracts drift."""
from __future__ import annotations

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
BUILD = ROOT / "app/build.gradle"
WORKFLOW = ROOT / ".github/workflows/android-build.yml"
NAVIGATION = ROOT / "app/src/main/java/com/kareem/cortex/CortexNavigation.java"
PRODUCTION_ACCEPTANCE = ROOT / "app/src/androidTest/java/com/kareem/cortex/CortexPublishAcceptanceTest.java"
PRODUCTION_SECONDARY_ACCEPTANCE = ROOT / "app/src/androidTest/java/com/kareem/cortex/CortexProductionSecondaryAcceptanceTest.java"
INTERNAL_DIAGNOSTIC_ACCEPTANCE = ROOT / "app/src/androidTest/java/com/kareem/cortex/CortexInternalDiagnosticCoverageTest.java"

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
    data = path.read_text(encoding="utf-8")
    if len(data) > 2_000_000:
        fail(f"contract input unexpectedly large: {path.relative_to(ROOT)}")
    return data


def one(pattern: str, source: str, label: str) -> str:
    match = re.search(pattern, source, re.MULTILINE)
    if not match:
        fail(f"could not resolve {label}")
    return match.group(1)


def android_attrs(raw: str) -> dict[str, str]:
    """Parse the small quoted android:* attribute surface used by the checked-in manifest.

    Product CI only needs declarative manifest attributes and intent-filter literals; using a
    bounded textual parser avoids loading XML features (DTD/entities/external references) at all.
    """
    return dict(re.findall(r'\bandroid:([A-Za-z0-9_]+)\s*=\s*"([^"]*)"', raw))


def manifest_source() -> str:
    source = text(MANIFEST)
    lowered = source.lower()
    if "<!doctype" in lowered or "<!entity" in lowered:
        fail("manifest must not contain DTD/entity declarations")
    return source


def manifest_opening_tags(source: str, tag: str) -> list[dict[str, str]]:
    pattern = rf"<{re.escape(tag)}\s+([^>]*?)(?:/?>)"
    return [android_attrs(m.group(1)) for m in re.finditer(pattern, source, re.IGNORECASE | re.DOTALL)]


def launcher(source: str) -> str:
    launchers: list[str] = []
    pattern = r"<(activity|activity-alias)\s+([^>]*)>(.*?)</\1\s*>"
    for match in re.finditer(pattern, source, re.IGNORECASE | re.DOTALL):
        attrs = android_attrs(match.group(2))
        body = match.group(3)
        if "android.intent.action.MAIN" not in body or "android.intent.category.LAUNCHER" not in body:
            continue
        name = attrs.get("name", "")
        launchers.append(attrs.get("targetActivity", name))
    if len(launchers) != 1:
        fail(f"expected exactly one launcher, found {launchers}")
    return launchers[0]


def verify_manifest() -> None:
    source = manifest_source()
    app_match = re.search(r"<application\s+([^>]*)>", source, re.IGNORECASE | re.DOTALL)
    if app_match is None:
        fail("manifest has no application")
    app_attrs = android_attrs(app_match.group(1))
    label = app_attrs.get("label", "")
    if label != EXPECTED_LABEL:
        fail(f"visible app label drifted: {label!r}")
    resolved_launcher = launcher(source)
    if resolved_launcher != EXPECTED_LAUNCHER:
        fail(f"launcher drifted: {resolved_launcher}; expected {EXPECTED_LAUNCHER}")

    activities = manifest_opening_tags(source, "activity")
    activity_by_name = {attrs.get("name", ""): attrs for attrs in activities}
    for name in FORBIDDEN_EXPORTED_DIAGNOSTICS | PRODUCTION_SECONDARY_INTERNAL_ONLY:
        activity = activity_by_name.get(name)
        if activity is None:
            fail(f"required activity missing from manifest: {name}")
        if activity.get("exported", "false").lower() == "true":
            fail(f"internal-only activity is externally exported: {name}")

    aliases = manifest_opening_tags(source, "activity-alias")
    alias_by_name = {attrs.get("name", ""): attrs for attrs in aliases}
    for alias, target in EXPECTED_LEGACY_ALIASES.items():
        alias_node = alias_by_name.get(alias)
        if alias_node is None:
            fail(f"required legacy compatibility alias missing: {alias}")
        actual = alias_node.get("targetActivity", "")
        if actual != target:
            fail(f"legacy surface alias drifted: {alias} -> {actual!r}; expected {target}")
        if alias_node.get("exported", "false").lower() == "true":
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
    required = ["`com.kareem.cortex`", "Visible app label: `Cortex`", "Main launcher: `CortexShellActivity`", "CortexInternalDiagnosticCoverageTest"]
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
    required = ["CortexShellActivity.class", "EXTRA_DESTINATION_ID", "EXTRA_OPEN_DOCK", "openDock(Activity from)", "CortexStatusActivity.class"]
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
