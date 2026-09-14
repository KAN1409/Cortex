#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
changes = []

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def write(path, text):
    p = ROOT / path
    old = p.read_text(encoding="utf-8")
    if old != text:
        p.write_text(text, encoding="utf-8")
        changes.append(path)

def replace_required(path, old, new, *, count=None):
    text = read(path)
    found = text.count(old)
    if found == 0:
        if new in text:
            return
        raise SystemExit(f"expected repair pattern missing in {path}: {old[:120]!r}")
    if count is not None and found != count:
        raise SystemExit(f"unexpected repair pattern count in {path}: expected {count}, found {found}")
    write(path, text.replace(old, new))

# Android 14 screenshot callback requires the normal install-time permission.
manifest = "app/src/main/AndroidManifest.xml"
text = read(manifest)
text = text.replace(
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">',
)
if 'android.permission.DETECT_SCREEN_CAPTURE' not in text:
    text = text.replace(
        '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n',
        '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n'
        '    <uses-permission android:name="android.permission.DETECT_SCREEN_CAPTURE" />\n',
    )
# These two declarations are intentional special-access/package-visibility capabilities.
text = text.replace(
    '    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />',
    '    <!-- Required by PhoneUsageAccess; the user grants Usage Access explicitly in Android Settings. -->\n'
    '    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />',
)
text = text.replace(
    '    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />',
    '    <!-- Cortex accessibility/phone-context features resolve arbitrary observed app package identities. -->\n'
    '    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" tools:ignore="QueryAllPackagesPermission" />',
)
write(manifest, text)

# Replace integer style literals with the Android type-safe symbolic constant.
for p in (ROOT / "app/src/main/java").rglob("*.java"):
    s = p.read_text(encoding="utf-8")
    n = s.replace('.setTypeface(null,1)', '.setTypeface(null,android.graphics.Typeface.BOLD)')
    if n != s:
        p.write_text(n, encoding="utf-8")
        changes.append(str(p.relative_to(ROOT)))

replace_required(
    "app/src/main/java/com/kareem/cortex/CortexUi.java",
    'Typeface.create("sans-serif-medium",0)',
    'Typeface.create("sans-serif-medium",Typeface.NORMAL)',
    count=1,
)
replace_required(
    "app/src/main/java/com/kareem/cortex/CortexUi.java",
    'Typeface.create("sans-serif-light",0)',
    'Typeface.create("sans-serif-light",Typeface.NORMAL)',
    count=1,
)

# Persist only a URI grant that is actually present; pass an allowed constant, never a possibly-zero mask.
replace_required(
    "app/src/main/java/com/kareem/cortex/CaptureActivity.java",
    'if((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,take&Intent.FLAG_GRANT_READ_URI_PERMISSION);',
    'if((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0&&(take&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);',
    count=1,
)
replace_required(
    "app/src/main/java/com/kareem/cortex/WorkVaultActivity.java",
    'Uri uri=data.getData();int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{getContentResolver().takePersistableUriPermission(uri,flags);}catch(Throwable ignored){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignoredAgain){}}',
    'Uri uri=data.getData();int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&(flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);else if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);else if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Throwable ignored){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignoredAgain){}}',
    count=1,
)

# TileService requires PendingIntent on API 34+, while the legacy Intent overload is the only compatible path below 34.
def repair_tile(path, legacy_calls):
    s = read(path)
    if 'import android.annotation.SuppressLint;' not in s:
        s = s.replace('package com.kareem.cortex;\n\n', 'package com.kareem.cortex;\n\nimport android.annotation.SuppressLint;\n')
    for old, new in legacy_calls:
        if old in s:
            s = s.replace(old, new)
        elif new not in s:
            raise SystemExit(f"expected legacy tile call missing in {path}: {old}")
    helper = '\n    @SuppressLint("StartActivityAndCollapseDeprecated")\n    private void startLegacyAndCollapse(Intent intent){startActivityAndCollapse(intent);}\n'
    if 'private void startLegacyAndCollapse(Intent intent)' not in s:
        pos = s.rfind('\n}')
        if pos < 0:
            raise SystemExit(f"could not place legacy helper in {path}")
        s = s[:pos] + helper + s[pos:]
    write(path, s)

repair_tile(
    "app/src/main/java/com/kareem/cortex/CortexQuickTileService.java",
    [('else startActivityAndCollapse(i);', 'else startLegacyAndCollapse(i);')],
)
repair_tile(
    "app/src/main/java/com/kareem/cortex/UnderstandScreenTileService.java",
    [
        ('startActivityAndCollapse(i);}catch(Throwable ignored){}return;', 'startLegacyAndCollapse(i);}catch(Throwable ignored){}return;'),
        ('else startActivityAndCollapse(i);}catch(Throwable e)', 'else startLegacyAndCollapse(i);}catch(Throwable e)'),
    ],
)

# Use explicit registry rulesets so Semgrep can remain metrics-off/private.
gate = "scripts/cortex-full-code-gate.sh"
replace_required(
    gate,
    'semgrep scan --config auto --metrics=off --json --output audit/semgrep.json app/src chatgpt-bridge/src scripts .github',
    'semgrep scan --config p/default --config p/security-audit --config p/secrets --metrics=off --json --output audit/semgrep.json app/src chatgpt-bridge/src scripts .github',
    count=1,
)

print(f"mechanical_repair_changed_files={len(set(changes))}")
for path in sorted(set(changes)):
    print(path)
