from pathlib import Path

p=Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s=p.read_text()

# 1) WORKS WELL WITH becomes an interactive related-command strip.
old='''    String related=related(c.command);if(!related.isEmpty())info("WORKS WELL WITH",related);'''
new='''    relatedActions(c.command);'''
if old not in s:
    raise SystemExit('detail related hook not found')
s=s.replace(old,new,1)

# Insert the interactive related section after info().
needle='''  void info(String label,String body){TextView l=text(label,10,true,ACCENT);l.setLetterSpacing(.14f);l.setPadding(0,dp(14),0,dp(6));root.addView(l);LinearLayout box=surface(true);TextView b=text(body,15,false,TEXT);b.setLineSpacing(0,1.18f);b.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);box.addView(b);root.addView(box);}\n'''
insert=needle+'''\n  void relatedActions(String command){
    String[] names=relatedNames(command);if(names.length==0)return;
    TextView l=text("WORKS WELL WITH",10,true,ACCENT);l.setLetterSpacing(.14f);l.setPadding(0,dp(14),0,dp(6));root.addView(l);
    HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(false);
    LinearLayout row=hbox();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,0,dp(4),0);
    for(String name:names){
      Cmd related=find(name);if(related==null||related.command.equals(command))continue;
      Button chip=relatedChip(related);row.addView(chip);
    }
    scroll.addView(row);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(0,0,0,dp(10));scroll.setLayoutParams(sp);root.addView(scroll);
    TextView hint=text("Tap a command to add it directly to your Prompt Stack.",11,false,MUTED);hint.setPadding(0,0,0,dp(6));root.addView(hint);
  }

  Button relatedChip(Cmd c){
    boolean added=selected.contains(c);Button x=new Button(this);x.setAllCaps(false);x.setText((added?"✓  ":"＋  ")+"/"+c.command);x.setTextColor(added?SUCCESS:TEXT);x.setTextSize(12);x.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));x.setTextDirection(View.TEXT_DIRECTION_LTR);x.setSingleLine(true);x.setMinWidth(0);x.setMinHeight(dp(40));x.setPadding(dp(12),0,dp(12),0);x.setBackground(shape(SURFACE2,added?Color.rgb(69,78,63):BORDER,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.setMargins(0,0,dp(8),0);x.setLayoutParams(lp);x.setOnClickListener(v->{if(!selected.contains(c)){selected.add(c);toast("Added /"+c.command);x.setText("✓  /"+c.command);x.setTextColor(SUCCESS);x.setBackground(shape(SURFACE2,Color.rgb(69,78,63),18));}else toast("/"+c.command+" is already in the stack");});return x;
  }
'''
if needle not in s:
    raise SystemExit('info() insertion point not found')
s=s.replace(needle,insert,1)

# Replace the old display-only related() helper with machine-readable names.
old_related='''  String related(String n){if(has(n,"research,verify,sources,evidence"))return"/research   /verify   /sources   /evidence   /facts";if(has(n,"critique,challenge,blindspots,devilsadvocate"))return"/critique   /challenge   /blindspots   /improve";if(has(n,"rewrite,rephrase,polish,proofread"))return"/rewrite   /clarify   /polish   /professional";if(has(n,"plan,strategy,roadmap,action"))return"/strategy   /roadmap   /priority   /action";if(has(n,"debug,fix,check,tests"))return"/rootcause   /debug   /fix   /tests   /check";return"";}'''
new_related='''  String[] relatedNames(String n){if(has(n,"research,verify,sources,evidence,facts"))return new String[]{"research","verify","sources","evidence","facts"};if(has(n,"critique,challenge,blindspots,devilsadvocate,improve"))return new String[]{"critique","challenge","blindspots","devilsadvocate","improve"};if(has(n,"rewrite,rephrase,polish,proofread,clarify,professional"))return new String[]{"rewrite","rephrase","clarify","polish","proofread","professional"};if(has(n,"plan,strategy,roadmap,action,priority"))return new String[]{"strategy","roadmap","priority","action"};if(has(n,"debug,rootcause,fix,check,tests"))return new String[]{"rootcause","debug","fix","tests","check"};if(has(n,"compare,proscons,rank,recommend,decision"))return new String[]{"compare","proscons","tradeoffs","rank","recommend"};if(has(n,"brainstorm,ideas,angles,alternative"))return new String[]{"brainstorm","angles","alternative","critique","rank"};return new String[0];}'''
if old_related not in s:
    raise SystemExit('related() helper not found')
s=s.replace(old_related,new_related,1)

# 2) Preserve the literal slash command in the generated prompt.
old_build='''    StringBuilder p=new StringBuilder("Help me with the request below by following these steps in order. Each step should build on the useful findings of the previous one.\\n\\n");for(int i=0;i<selected.size();i++)p.append(i+1).append(". ").append(selected.get(i).instruction).append('\\n');if(!user.isEmpty())p.append("\\nMy request / context:\\n").append(user);p.append("\\n\\nGive me one clear, coherent final answer. Show useful conclusions and evidence, but do not expose private chain-of-thought.");'''
new_build='''    StringBuilder p=new StringBuilder("Help me with the request below by following these steps in order. Each step should build on the useful findings of the previous one.\\n\\n");for(int i=0;i<selected.size();i++){Cmd c=selected.get(i);p.append(i+1).append(". /").append(c.command).append('\\n').append("   ").append(c.instruction).append('\\n');}if(!user.isEmpty())p.append("\\nMy request / context:\\n").append(user);p.append("\\n\\nGive me one clear, coherent final answer. Show useful conclusions and evidence, but do not expose private chain-of-thought.");'''
if old_build not in s:
    raise SystemExit('build prompt generator not found')
s=s.replace(old_build,new_build,1)

p.write_text(s)
print('PromptDeck RC3 interaction/prompt patch applied')
