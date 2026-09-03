from pathlib import Path

p=Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s=p.read_text()

# Navigation state so Android Back never exits from an inner PromptDeck screen.
needle='''  LinearLayout root; EditText context,finalPrompt;'''
replacement='''  LinearLayout root; EditText context,finalPrompt;\n  String page="home"; Group currentGroup=null; String contextDraft="";'''
if needle not in s: raise SystemExit('state insertion point not found')
s=s.replace(needle,replacement,1)

# Track current page and parent category.
repls={
'''  void home(){''':'''  void home(){\n    page="home"; currentGroup=null;''',
'''  void group(Group g){''':'''  void group(Group g){\n    page="group"; currentGroup=g;''',
'''  void detail(Cmd c,Group g){''':'''  void detail(Cmd c,Group g){\n    page="detail"; currentGroup=g;''',
'''  void stack(){''':'''  void stack(){\n    if(context!=null)contextDraft=context.getText().toString();\n    page="stack";''',
'''  void addMore(){''':'''  void addMore(){\n    page="addMore";''',
'''  void build(){''':'''  void build(){\n    page="build";''',
'''  void library(){''':'''  void library(){\n    page="library";''',
'''  void customDetail(Cmd c){''':'''  void customDetail(Cmd c){\n    page="customDetail";'''
}
for old,new in repls.items():
    if old not in s: raise SystemExit('missing page hook: '+old)
    s=s.replace(old,new,1)

# System back follows the app hierarchy instead of finishing the Activity.
insert_before='''  void load(){'''
nav_method='''  @Override public void onBackPressed(){\n    if("stack".equals(page)){home();return;}\n    if("addMore".equals(page)||"build".equals(page)){stack();return;}\n    if("group".equals(page)||"library".equals(page)){home();return;}\n    if("detail".equals(page)){if(currentGroup!=null)group(currentGroup);else home();return;}\n    if("customDetail".equals(page)){library();return;}\n    super.onBackPressed();\n  }\n\n'''
if insert_before not in s: raise SystemExit('load insertion point not found')
s=s.replace(insert_before,nav_method+insert_before,1)

# Subtle satin palette layered on top of the GitHub-inspired colors.
const='''static final int TEXT=Color.rgb(230,237,243), MUTED=Color.rgb(125,133,144), ACCENT=Color.rgb(88,166,255), SUCCESS=Color.rgb(63,185,80);'''
const_new=const+'''\n  static final int SATIN_TOP=Color.rgb(30,36,45), SATIN_BOTTOM=Color.rgb(20,25,32), SATIN_EDGE=Color.rgb(56,64,74);'''
if const not in s: raise SystemExit('palette insertion point not found')
s=s.replace(const,const_new,1)

# Let elevation shadows breathe beyond card bounds.
s=s.replace('''root=vbox(); root.setPadding(dp(16),dp(10),dp(16),dp(28)); sv.addView(root); setContentView(sv);''',
'''root=vbox(); root.setPadding(dp(16),dp(10),dp(16),dp(28));root.setClipChildren(false);root.setClipToPadding(false);sv.setClipToPadding(false); sv.addView(root); setContentView(sv);''',1)

# Add a visible compact Back control in the top bar of stack/build/add-more pages.
old_top='''    LinearLayout top=hbox(); top.setGravity(Gravity.CENTER_VERTICAL);\n    TextView brand=text("PromptDeck",14,true,TEXT); brand.setLetterSpacing(0f); top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));'''
new_top='''    LinearLayout top=hbox(); top.setGravity(Gravity.CENTER_VERTICAL);\n    if("Prompt Stack".equals(title)||"Add more commands".equals(title)||"Final Prompt".equals(title)){\n      Button back=navBack("‹  Back");back.setOnClickListener(v->{if("Prompt Stack".equals(title))home();else stack();});\n      LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(36));bp.setMargins(0,0,dp(10),0);top.addView(back,bp);\n    }\n    TextView brand=text("PromptDeck",14,true,TEXT); brand.setLetterSpacing(0f); top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));'''
if old_top not in s: raise SystemExit('top bar hook not found')
s=s.replace(old_top,new_top,1)

# Preserve request/context while browsing additional commands.
s=s.replace('''context=input("اكتب هنا الموضوع أو النص اللي عاوز تطبق عليه الـcommands…",3);context.setMaxLines(8);context.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);root.addView(context);''',
'''context=input("اكتب هنا الموضوع أو النص اللي عاوز تطبق عليه الـcommands…",3);context.setMaxLines(8);context.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);context.setText(contextDraft);root.addView(context);''',1)
s=s.replace('''Button more=ghost("＋  Add more commands");more.setOnClickListener(v->addMore());root.addView(more);''',
'''Button more=ghost("＋  Add more commands");more.setOnClickListener(v->{contextDraft=context==null?contextDraft:context.getText().toString();addMore();});root.addView(more);''',1)

# Satin gradient helper and elevated card surfaces.
old_surface='''  LinearLayout surface(boolean compact){LinearLayout l=vbox();l.setPadding(dp(compact?16:16),dp(compact?14:14),dp(compact?16:16),dp(compact?14:14));l.setBackground(shape(SURFACE,BORDER,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));l.setLayoutParams(lp);return l;}\n  GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}'''
new_surface='''  LinearLayout surface(boolean compact){LinearLayout l=vbox();l.setPadding(dp(16),dp(14),dp(16),dp(14));l.setBackground(satinShape(SATIN_TOP,SATIN_BOTTOM,SATIN_EDGE,12));l.setElevation(dp(3));l.setTranslationZ(dp(1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(2),0,dp(10));l.setLayoutParams(lp);return l;}\n  GradientDrawable satinShape(int top,int bottom,int stroke,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}\n  GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}'''
if old_surface not in s: raise SystemExit('surface helper not found')
s=s.replace(old_surface,new_surface,1)

# Satin grouped command panels.
s=s.replace('block.setBackground(shape(SURFACE,BORDER,12));', 'block.setBackground(satinShape(SATIN_TOP,SATIN_BOTTOM,SATIN_EDGE,12));block.setElevation(dp(2));')

# Premium satin button treatment: blue primary has a mild highlight; secondary surfaces stay subtle.
old_btn='''  Button styledButton(String s,int fill,int stroke,int color){Button x=new Button(this);x.setText(s);x.setAllCaps(false);x.setTextColor(color);x.setTextSize(14);x.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));x.setBackground(shape(fill,stroke,9));x.setPadding(dp(12),dp(9),dp(12),dp(9));x.setMinHeight(dp(46));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));x.setLayoutParams(lp);return x;}'''
new_btn='''  Button styledButton(String s,int fill,int stroke,int color){Button x=new Button(this);x.setText(s);x.setAllCaps(false);x.setTextColor(color);x.setTextSize(14);x.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));if(fill==Color.rgb(31,111,235))x.setBackground(satinShape(Color.rgb(55,135,250),Color.rgb(28,103,219),stroke,10));else if(fill==SURFACE||fill==SURFACE2)x.setBackground(satinShape(Color.rgb(31,37,46),Color.rgb(22,27,34),stroke,10));else x.setBackground(shape(fill,stroke,10));x.setElevation(dp(fill==Color.rgb(31,111,235)?3:1));x.setPadding(dp(12),dp(9),dp(12),dp(9));x.setMinHeight(dp(46));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));x.setLayoutParams(lp);return x;}\n  Button navBack(String s){Button x=new Button(this);x.setText(s);x.setAllCaps(false);x.setTextColor(TEXT);x.setTextSize(12);x.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));x.setMinWidth(0);x.setMinHeight(0);x.setPadding(dp(10),0,dp(10),0);x.setBackground(satinShape(Color.rgb(29,35,43),Color.rgb(20,25,32),BORDER,9));x.setElevation(dp(2));return x;}'''
if old_btn not in s: raise SystemExit('styledButton helper not found')
s=s.replace(old_btn,new_btn,1)

# Satin input wells; still restrained so text remains the focus.
s=s.replace('x.setBackground(shape(BG,BORDER,9));','x.setBackground(satinShape(Color.rgb(17,22,29),Color.rgb(12,16,22),BORDER,10));x.setElevation(dp(1));')

p.write_text(s)
print('PromptDeck RC10 satin premium UI + back navigation patch applied')
