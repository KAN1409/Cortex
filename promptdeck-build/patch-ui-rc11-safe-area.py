from pathlib import Path

p=Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s=p.read_text()

# Android 15+ can lay app content edge-to-edge. Make every rebuilt screen respect
# the real status/navigation bar insets instead of relying on a guessed top margin.
old='''root=vbox(); root.setPadding(dp(16),dp(10),dp(16),dp(28));root.setClipChildren(false);root.setClipToPadding(false);sv.setClipToPadding(false); sv.addView(root); setContentView(sv);'''
new='''root=vbox(); root.setPadding(dp(16),dp(10),dp(16),dp(28));root.setClipChildren(false);root.setClipToPadding(false);sv.setClipToPadding(false); sv.addView(root); setContentView(sv);\n    sv.setOnApplyWindowInsetsListener((v,insets)->{\n      int top=insets.getSystemWindowInsetTop();\n      int bottom=insets.getSystemWindowInsetBottom();\n      root.setPadding(dp(16),top+dp(10),dp(16),bottom+dp(28));\n      return insets;\n    });\n    sv.requestApplyInsets();'''
if old not in s:
    raise SystemExit('base safe-area insertion point not found')
s=s.replace(old,new,1)

# Keep system-bar icon contrast appropriate for the dark UI.
old_create='''@Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);load();home();}'''
new_create='''@Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);load();home();}'''
if old_create in s:
    s=s.replace(old_create,new_create,1)

p.write_text(s)
print('PromptDeck RC11 safe-area/system-bars patch applied')
