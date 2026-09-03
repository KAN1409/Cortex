from pathlib import Path

p=Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s=p.read_text()

# Route Add more commands to a dedicated contextual picker instead of home.
old='''    Button more=ghost("＋  Add more commands");more.setOnClickListener(v->home());root.addView(more);'''
new='''    Button more=ghost("＋  Add more commands");more.setOnClickListener(v->addMore());root.addView(more);'''
if old not in s:
    raise SystemExit('Add more button hook not found')
s=s.replace(old,new,1)

# Insert contextual Add More screen before build().
needle='''  void build(){'''
insert='''  void addMore(){
    base("Add more commands","ابدأ بالاقتراحات المرتبطة بالـStack الحالي، أو اختار أي command من المكتبة.",false);

    ArrayList<Cmd> suggestions=suggestedForStack();
    if(!suggestions.isEmpty()){
      TextView sl=label("SUGGESTED / WORKS WELL WITH");root.addView(sl);
      HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
      LinearLayout chips=hbox();chips.setPadding(0,0,dp(4),0);
      for(Cmd c:suggestions){Button chip=relatedChip(c);chips.addView(chip);}
      scroll.addView(chips);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,0,0,dp(8));scroll.setLayoutParams(hp);root.addView(scroll);
      TextView hint=text("اقتراحات مبنية على الـcommands الموجودة عندك دلوقتي. دوس على أي واحدة لإضافتها فورًا.",12,false,MUTED);hint.setTextDirection(View.TEXT_DIRECTION_RTL);hint.setGravity(Gravity.END);hint.setPadding(0,0,0,dp(10));root.addView(hint);
    }

    root.addView(label("ALL COMMANDS"));
    for(Group g:groups){
      TextView gh=section(g.title,availableCount(g));root.addView(gh);
      LinearLayout block=vbox();block.setBackground(shape(SURFACE,BORDER,18));block.setPadding(0,dp(2),0,dp(2));
      int shown=0,total=availableCount(g);
      for(String n:g.names){
        Cmd c=find(n);if(c==null)continue;
        boolean divider=shown<total-1;View row=pickCommandRow(c,divider);block.addView(row);shown++;
      }
      root.addView(block);spacer(14);
    }

    ArrayList<Cmd> custom=new ArrayList<>();for(Cmd c:all)if(c.custom)custom.add(c);
    if(!custom.isEmpty()){
      root.addView(section("My custom prompts",custom.size()));
      LinearLayout block=vbox();block.setBackground(shape(SURFACE,BORDER,18));
      for(int i=0;i<custom.size();i++)block.addView(pickCommandRow(custom.get(i),i<custom.size()-1));
      root.addView(block);spacer(14);
    }

    Button done=primary("Done · Back to Prompt Stack");done.setOnClickListener(v->stack());root.addView(done);
  }

  int availableCount(Group g){int n=0;for(String name:g.names)if(find(name)!=null)n++;return n;}

  View pickCommandRow(Cmd c,boolean divider){
    LinearLayout wrap=vbox();wrap.setPadding(dp(16),dp(12),dp(14),dp(10));
    LinearLayout line=hbox();line.setGravity(Gravity.CENTER_VERTICAL);
    TextView name=text("/"+c.command,15,true,TEXT);name.setTextDirection(View.TEXT_DIRECTION_LTR);name.setGravity(Gravity.START);name.setSingleLine(true);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    TextView state=text(selected.contains(c)?"✓ ADDED":"＋ ADD",10,true,selected.contains(c)?SUCCESS:ACCENT);state.setLetterSpacing(.08f);line.addView(state);wrap.addView(line);
    TextView d=text(c.description,12,false,MUTED);d.setTextDirection(View.TEXT_DIRECTION_RTL);d.setGravity(Gravity.END);d.setPadding(0,dp(3),0,0);wrap.addView(d);
    wrap.setOnClickListener(v->{if(!selected.contains(c)){selected.add(c);toast("Added /"+c.command);addMore();}else{selected.remove(c);toast("Removed /"+c.command);addMore();}});
    if(divider){View sep=new View(this);sep.setBackgroundColor(BORDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(10),0,0);wrap.addView(sep,lp);}return wrap;
  }

  ArrayList<Cmd> suggestedForStack(){
    LinkedHashSet<String> names=new LinkedHashSet<>();
    for(Cmd current:selected)for(String n:relatedNames(current.command))if(!n.equals(current.command))names.add(n);
    ArrayList<Cmd> out=new ArrayList<>();for(String n:names){Cmd c=find(n);if(c!=null&&!selected.contains(c))out.add(c);}return out;
  }

  void build(){'''
if needle not in s:
    raise SystemExit('build insertion point not found')
s=s.replace(needle,insert,1)

p.write_text(s)
print('PromptDeck RC4 contextual Add More picker applied')
