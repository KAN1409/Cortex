from pathlib import Path
import re

p=Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s=p.read_text()

# Slightly tighter page rhythm and title sizing for narrow phones.
s=s.replace('root.setPadding(dp(20),dp(14),dp(20),dp(40));','root.setPadding(dp(18),dp(12),dp(18),dp(34));')
s=s.replace('TextView ttl=text(title,31,true,TEXT); ttl.setPadding(0,dp(18),0,dp(4));','TextView ttl=text(title,28,true,TEXT); ttl.setPadding(0,dp(16),0,dp(4)); ttl.setTextDirection(View.TEXT_DIRECTION_LTR);')
s=s.replace('TextView st=text(sub,15,false,MUTED);st.setLineSpacing(0,1.12f);st.setPadding(0,0,0,dp(18));','TextView st=text(sub,14,false,MUTED);st.setLineSpacing(0,1.16f);st.setPadding(0,0,0,dp(16));st.setGravity(Gravity.START);')

# Command rows: explicitly separate English command direction from Arabic description.
s=s.replace('TextView name=text("/"+c.command,16,true,TEXT);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));',
'''TextView name=text("/"+c.command,15,true,TEXT);name.setTextDirection(View.TEXT_DIRECTION_LTR);name.setGravity(Gravity.START);name.setSingleLine(true);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));''')
s=s.replace('TextView d=text(c.description,13,false,MUTED);d.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);d.setPadding(0,dp(3),0,0);wrap.addView(d);',
'''TextView d=text(c.description,12,false,MUTED);d.setTextDirection(View.TEXT_DIRECTION_RTL);d.setGravity(Gravity.END);d.setPadding(0,dp(4),0,0);wrap.addView(d);''')

old_stack=re.compile(r'''  void stack\(\)\{\n    base\("Prompt Stack".*?\n  \}\n\n  void build\(\)\{''', re.S)
new_stack='''  void stack(){
    base("Prompt Stack",selected.isEmpty()?"لسه ما اخترتش أي commands.":"رتّب الخطوات. كل command هيبني على نتيجة اللي قبله.",false);
    if(selected.isEmpty()){
      LinearLayout empty=surface(true);TextView e=text("ابدأ من Categories واختار command أو أكتر.",14,false,MUTED);e.setGravity(Gravity.CENTER);e.setPadding(0,dp(24),0,dp(24));empty.addView(e);root.addView(empty);
      Button browse=primary("Browse categories");browse.setOnClickListener(v->home());root.addView(browse);return;
    }
    for(int i=0;i<selected.size();i++){
      final int k=i; Cmd c=selected.get(i);
      LinearLayout card=surface(false); card.setPadding(dp(15),dp(14),dp(15),dp(12));

      LinearLayout top=hbox(); top.setGravity(Gravity.CENTER_VERTICAL);
      TextView num=text(String.format(Locale.ROOT,"%02d",i+1),10,true,ACCENT);num.setLetterSpacing(.12f);num.setGravity(Gravity.CENTER);
      top.addView(num,new LinearLayout.LayoutParams(dp(36),dp(32)));
      TextView command=text("/"+c.command,16,true,TEXT);command.setTextDirection(View.TEXT_DIRECTION_LTR);command.setGravity(Gravity.START);command.setSingleLine(true);
      top.addView(command,new LinearLayout.LayoutParams(0,-2,1));
      card.addView(top);

      TextView desc=text(c.description,12,false,MUTED);desc.setTextDirection(View.TEXT_DIRECTION_RTL);desc.setGravity(Gravity.END);desc.setLineSpacing(0,1.12f);desc.setPadding(dp(36),dp(4),0,dp(10));card.addView(desc);

      LinearLayout controls=hbox();controls.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
      Button up=compactControl("↑  Up"),dn=compactControl("↓  Down"),rm=compactControl("×  Remove");
      up.setEnabled(k>0); up.setAlpha(k>0?1f:.35f); dn.setEnabled(k<selected.size()-1); dn.setAlpha(k<selected.size()-1?1f:.35f);
      up.setOnClickListener(v->{if(k>0)Collections.swap(selected,k,k-1);stack();});
      dn.setOnClickListener(v->{if(k<selected.size()-1)Collections.swap(selected,k,k+1);stack();});
      rm.setOnClickListener(v->{selected.remove(k);stack();});
      LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(38),1);cp.setMargins(dp(3),0,dp(3),0);
      controls.addView(up,cp);controls.addView(dn,new LinearLayout.LayoutParams(cp));controls.addView(rm,new LinearLayout.LayoutParams(cp));
      card.addView(controls);
      root.addView(card);
    }
    root.addView(label("YOUR REQUEST / CONTEXT"));
    context=input("اكتب هنا الموضوع أو النص اللي عاوز تطبق عليه الـcommands…",3);context.setMaxLines(8);context.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);root.addView(context);
    Button build=primary("Build final prompt");build.setOnClickListener(v->build());root.addView(build);
    Button more=ghost("＋  Add more commands");more.setOnClickListener(v->home());root.addView(more);
  }

  void build(){'''
s,n=old_stack.subn(new_stack,s,count=1)
if n!=1:
    raise SystemExit('stack() replacement failed')

# Add a compact control helper immediately after mini().
needle='''Button mini(String s){Button x=new Button(this);x.setText(s);x.setAllCaps(false);x.setTextColor(MUTED);x.setTextSize(14);x.setMinWidth(0);x.setMinHeight(0);x.setPadding(dp(8),dp(6),dp(8),dp(6));x.setBackground(shape(SURFACE2,BORDER,10));return x;}'''
replacement=needle+'''\n  Button compactControl(String s){Button x=new Button(this);x.setText(s);x.setAllCaps(false);x.setTextColor(MUTED);x.setTextSize(11);x.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));x.setMinWidth(0);x.setMinHeight(0);x.setPadding(dp(6),0,dp(6),0);x.setBackground(shape(SURFACE2,BORDER,10));return x;}'''
if needle not in s:
    raise SystemExit('mini helper not found')
s=s.replace(needle,replacement,1)

# Inputs should start compact and grow naturally.
s=s.replace('x.setMinLines(lines);x.setGravity(Gravity.TOP|Gravity.START);','x.setMinLines(lines);x.setGravity(Gravity.TOP|Gravity.START);x.setLineSpacing(0,1.12f);')

p.write_text(s)
print('PromptDeck RC2 responsive UI patch applied')
