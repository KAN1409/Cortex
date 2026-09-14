#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
changed=[]

def read(path): return (ROOT/path).read_text(encoding='utf-8')
def write(path,text):
    p=ROOT/path; old=p.read_text(encoding='utf-8')
    if old!=text:
        p.write_text(text,encoding='utf-8'); changed.append(path)

def replace(path,old,new,required=True):
    text=read(path)
    if old in text: write(path,text.replace(old,new))
    elif new not in text and required: raise SystemExit(f'missing repair pattern in {path}: {old[:100]!r}')

# Manifest: API 34 screen capture permission, intentional special-access declarations,
# and remove an unnecessary exported widget helper surface.
manifest='app/src/main/AndroidManifest.xml'
m=read(manifest)
m=m.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">','<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">')
if 'android.permission.DETECT_SCREEN_CAPTURE' not in m:
    m=m.replace('    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n','    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.DETECT_SCREEN_CAPTURE" />\n')
m=m.replace('    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />','    <!-- Required by PhoneUsageAccess; user grants Usage Access explicitly in Android Settings. -->\n    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />')
m=m.replace('    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />','    <!-- Accessibility/phone-context features resolve arbitrary observed app package identities. -->\n    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" tools:ignore="QueryAllPackagesPermission" />')
m=m.replace('<activity android:name=".PinRecordWidgetActivity" android:exported="true" android:label="Add Cortex Voice widget" />','<activity android:name=".PinRecordWidgetActivity" android:exported="false" android:label="Add Cortex Voice widget" />')
write(manifest,m)

# Lint WrongConstant: replace legacy numeric Typeface style values with named constants.
for p in (ROOT/'app/src/main/java').rglob('*.java'):
    s=p.read_text(encoding='utf-8')
    n=s.replace('.setTypeface(null,1)', '.setTypeface(null,android.graphics.Typeface.BOLD)')
    n=n.replace('Typeface.create("sans-serif-medium",0)', 'Typeface.create("sans-serif-medium",Typeface.NORMAL)')
    n=n.replace('Typeface.create("sans-serif-light",0)', 'Typeface.create("sans-serif-light",Typeface.NORMAL)')
    if n!=s:
        p.write_text(n,encoding='utf-8'); changed.append(str(p.relative_to(ROOT)))

# Persist only valid URI permission constants that were actually granted.
replace('app/src/main/java/com/kareem/cortex/CaptureActivity.java',
        'if((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,take&Intent.FLAG_GRANT_READ_URI_PERMISSION);',
        'if((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0&&(take&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);')
replace('app/src/main/java/com/kareem/cortex/WorkVaultActivity.java',
        'Uri uri=data.getData();int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{getContentResolver().takePersistableUriPermission(uri,flags);}catch(Throwable ignored){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignoredAgain){}}',
        'Uri uri=data.getData();int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&(flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);else if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);else if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Throwable ignored){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignoredAgain){}}')

# TileService: API 34+ uses PendingIntent; below 34 the deprecated Intent overload is
# deliberately isolated in one annotated compatibility helper.
def tile(path,replacements):
    s=read(path)
    if 'import android.annotation.SuppressLint;' not in s:
        s=s.replace('package com.kareem.cortex;\n\n','package com.kareem.cortex;\n\nimport android.annotation.SuppressLint;\n')
    for old,new in replacements:
        if old in s: s=s.replace(old,new)
        elif new not in s: raise SystemExit(f'missing tile repair pattern in {path}: {old}')
    helper='\n    @SuppressLint("StartActivityAndCollapseDeprecated")\n    private void startLegacyAndCollapse(Intent intent){startActivityAndCollapse(intent);}\n'
    if 'private void startLegacyAndCollapse(Intent intent)' not in s:
        pos=s.rfind('\n}')
        if pos<0: raise SystemExit(f'cannot place helper in {path}')
        s=s[:pos]+helper+s[pos:]
    write(path,s)

tile('app/src/main/java/com/kareem/cortex/CortexQuickTileService.java', [('else startActivityAndCollapse(i);','else startLegacyAndCollapse(i);')])
tile('app/src/main/java/com/kareem/cortex/UnderstandScreenTileService.java', [
    ('startActivityAndCollapse(i);}catch(Throwable ignored){}return;', 'startLegacyAndCollapse(i);}catch(Throwable ignored){}return;'),
    ('else startActivityAndCollapse(i);}catch(Throwable e)', 'else startLegacyAndCollapse(i);}catch(Throwable e)')])

# Contract verifier launcher parser must not let a self-closing activity consume the next
# activity's intent-filter body.
contract='tools/verify_product_contracts.py'
replace(contract,
        'pattern = r"<(activity|activity-alias)\\s+([^>]*)>(.*?)</\\1\\s*>"',
        'pattern = r"<(activity|activity-alias)\\s+([^>]*?)(?<!/)>(.*?)</\\1\\s*>"')

print(f'v146_mechanical_repair_changed_files={len(set(changed))}')
for x in sorted(set(changed)): print(x)
