from pathlib import Path

p=Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s=p.read_text()

# GitHub Mobile-inspired dark palette and tighter density.
s=s.replace('static final int BG=Color.rgb(11,12,14), SURFACE=Color.rgb(20,22,26), SURFACE2=Color.rgb(26,29,34), BORDER=Color.rgb(42,45,52);',
'''static final int BG=Color.rgb(13,17,23), SURFACE=Color.rgb(22,27,34), SURFACE2=Color.rgb(33,38,45), BORDER=Color.rgb(48,54,61);''')
s=s.replace('static final int TEXT=Color.rgb(244,241,234), MUTED=Color.rgb(158,160,166), ACCENT=Color.rgb(198,168,107), SUCCESS=Color.rgb(176,201,164);',
'''static final int TEXT=Color.rgb(230,237,243), MUTED=Color.rgb(125,133,144), ACCENT=Color.rgb(88,166,255), SUCCESS=Color.rgb(63,185,80);''')

# Screen rhythm: less luxury-card spacing, more GitHub list density.
s=s.replace('root.setPadding(dp(18),dp(12),dp(18),dp(34));','root.setPadding(dp(16),dp(10),dp(16),dp(28));')
s=s.replace('TextView ttl=text(title,28,true,TEXT); ttl.setPadding(0,dp(16),0,dp(4)); ttl.setTextDirection(View.TEXT_DIRECTION_LTR);',
'''TextView ttl=text(title,26,true,TEXT); ttl.setPadding(0,dp(14),0,dp(4)); ttl.setTextDirection(View.TEXT_DIRECTION_LTR);''')
s=s.replace('TextView st=text(sub,14,false,MUTED);st.setLineSpacing(0,1.16f);st.setPadding(0,0,0,dp(16));st.setGravity(Gravity.START);',
'''TextView st=text(sub,14,false,MUTED);st.setLineSpacing(0,1.14f);st.setPadding(0,0,0,dp(14));st.setGravity(Gravity.START);''')

# Brand becomes a simple GitHub-like top label, no spaced-out luxury wordmark.
s=s.replace('TextView brand=text("PROMPTDECK",12,true,ACCENT); brand.setLetterSpacing(.18f);',
'''TextView brand=text("PromptDeck",14,true,TEXT); brand.setLetterSpacing(0f);''')

# Cards and grouped lists: smaller radius, slightly tighter spacing.
s=s.replace('GradientDrawable circle=shape(SURFACE2,ACCENT,22);circle.setStroke(dp(1),Color.rgb(55,50,42));',
'''GradientDrawable circle=shape(SURFACE2,BORDER,20);circle.setStroke(dp(1),BORDER);''')
s=s.replace('LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(44),dp(44));','LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(40),dp(40));')
s=s.replace('block.setBackground(shape(SURFACE,BORDER,18));','block.setBackground(shape(SURFACE,BORDER,12));')
s=s.replace('wrap.setPadding(dp(16),dp(13),dp(14),dp(11));','wrap.setPadding(dp(14),dp(11),dp(12),dp(9));')
s=s.replace('wrap.setPadding(dp(16),dp(12),dp(14),dp(10));','wrap.setPadding(dp(14),dp(10),dp(12),dp(8));')

# Generic surfaces: GitHub-like low radius and compact vertical gaps.
s=s.replace('l.setBackground(shape(SURFACE,BORDER,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));',
'''l.setBackground(shape(SURFACE,BORDER,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));''')

# Section labels and stack pill.
s=s.replace('TextView v=text(s+"   "+count,11,true,MUTED);v.setAllCaps(true);v.setLetterSpacing(.11f);v.setPadding(dp(2),dp(6),0,dp(9));',
'''TextView v=text(s+"  ·  "+count,11,true,MUTED);v.setAllCaps(true);v.setLetterSpacing(.06f);v.setPadding(dp(2),dp(5),0,dp(8));''')
s=s.replace('v.setBackground(shape(SURFACE2,Color.rgb(64,56,42),22));','v.setBackground(shape(SURFACE2,BORDER,18));')

# Buttons: GitHub Mobile proportions, blue primary, subtle bordered secondary.
s=s.replace('x.setBackground(shape(fill,stroke,14));x.setPadding(dp(14),dp(11),dp(14),dp(11));x.setMinHeight(dp(50));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));',
'''x.setBackground(shape(fill,stroke,9));x.setPadding(dp(12),dp(9),dp(12),dp(9));x.setMinHeight(dp(46));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));''')
s=s.replace('Button primary(String s){return styledButton(s,ACCENT,0,BG);}',
'''Button primary(String s){return styledButton(s,Color.rgb(31,111,235),Color.rgb(56,139,253),Color.WHITE);}''')
s=s.replace('Button secondary(String s){return styledButton(s,SURFACE2,BORDER,TEXT);}',
'''Button secondary(String s){return styledButton(s,SURFACE2,BORDER,TEXT);}''')
s=s.replace('Button ghost(String s){return styledButton(s,BG,BORDER,MUTED);}',
'''Button ghost(String s){return styledButton(s,SURFACE,BORDER,TEXT);}''')

# Inputs: flatter GitHub field treatment.
s=s.replace('x.setBackground(shape(SURFACE,BORDER,16));','x.setBackground(shape(BG,BORDER,9));')

# Related chips and compact controls get GitHub-like small-radius pills.
s=s.replace('x.setBackground(shape(SURFACE2,added?Color.rgb(69,78,63):BORDER,18));',
'''x.setBackground(shape(SURFACE2,added?Color.rgb(35,134,54):BORDER,8));''')
s=s.replace('x.setBackground(shape(SURFACE2,Color.rgb(69,78,63),18));',
'''x.setBackground(shape(SURFACE2,Color.rgb(35,134,54),8));''')
s=s.replace('x.setBackground(shape(SURFACE2,BORDER,10));','x.setBackground(shape(SURFACE2,BORDER,8));')

# Prompt Stack cards slightly flatter and controls more compact.
s=s.replace('card.setPadding(dp(15),dp(14),dp(15),dp(12));','card.setPadding(dp(14),dp(12),dp(14),dp(10));')
s=s.replace('LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(38),1);','LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(36),1);')

# Add More page: use a GitHub-like list hierarchy and less vertical spacing.
s=s.replace('spacer(14);','spacer(10);')
s=s.replace('spacer(16);','spacer(12);')

p.write_text(s)
print('PromptDeck RC5 GitHub Mobile-inspired UI patch applied')
