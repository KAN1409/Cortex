from pathlib import Path
import re

p=Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s=p.read_text()

# Preserve display casing for imported slash commands while keeping lookup case-insensitive.
s=s.replace('return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","");',
            'return s.replaceAll("[^A-Za-z0-9_-]","");')
s=s.replace('Cmd find(String n){for(Cmd c:all)if(c.command.equals(n))return c;return null;}',
            'Cmd find(String n){for(Cmd c:all)if(c.command.equalsIgnoreCase(n))return c;return null;}')
s=s.replace('boolean has(String c,String csv){return Arrays.asList(csv.split(",")).contains(c);}',
            'boolean has(String c,String csv){return Arrays.asList(csv.toLowerCase(Locale.ROOT).split(",")).contains(c.toLowerCase(Locale.ROOT));}')

# Add a first-class Photo Editing & Image Generation category.
old_group='''    new Group("▦","Data & Formatting","تنظيم وعرض وتحويل البيانات","table","bullets","outline","format","json","csv","schema","template","prompt")\n  };'''
new_group='''    new Group("▦","Data & Formatting","تنظيم وعرض وتحويل البيانات","table","bullets","outline","format","json","csv","schema","template","prompt"),\n    new Group("◉","Photo Editing & Image Generation","ستايلات وتوجيهات جاهزة لتعديل الصور وتوليد المشاهد","NeonCity","GoldenHour","MiniWorld","Fog","LuxuryAd","LowAngleHero","VintageFilm","DroneView","Magazine","RainyNight","ProHeadshot","SnowWorld","DoubleExposure","OldMoney","StudioPro","Autumn","MovieScene")\n  };'''
if old_group not in s:
    raise SystemExit('group insertion point not found')
s=s.replace(old_group,new_group,1)

# Seed the 17 photo commands as built-ins after loading assets/custom commands.
load_re=re.compile(r'(  void load\(\)\{.*?\}\n)',re.S)
m=load_re.search(s)
if not m:
    raise SystemExit('load() not found')
load=m.group(1)
if 'seedPhotoCommands();' not in load:
    # Insert before the final brace of load().
    load_new=load[:-2]+'seedPhotoCommands();}\n'
    s=s[:m.start()]+load_new+s[m.end():]

insert_before='''  void base(String title,String sub,boolean showStack){'''
seed_method='''  void seedPhotoCommands(){
    String[][] defs=new String[][]{
      {"NeonCity","Cyberpunk night portrait"},{"GoldenHour","Cinematic sunset portrait"},{"MiniWorld","Miniature diorama"},{"Fog","Mysterious foggy portrait"},{"LuxuryAd","Luxury product advertisement"},{"LowAngleHero","Powerful hero photograph"},{"VintageFilm","Authentic 1990s photograph"},{"DroneView","Dramatic top-down photograph"},{"Magazine","Fashion editorial photograph"},{"RainyNight","Moody movie scene"},{"ProHeadshot","LinkedIn-ready headshot"},{"SnowWorld","Winter travel photograph"},{"DoubleExposure","Artistic poster portrait"},{"OldMoney","Luxury lifestyle portrait"},{"StudioPro","Professional studio portrait"},{"Autumn","Beautiful autumn portrait"},{"MovieScene","Cinematic movie still"}
    };
    for(String[] d:defs){if(find(d[0])!=null)continue;try{JSONObject o=new JSONObject();o.put("id",20000+all.size());o.put("command",d[0]);o.put("category","Photo Editing & Image Generation");o.put("description",photoDescription(d[1]));o.put("instruction","Use the /"+d[0]+" image direction: "+d[1]+". Apply this style faithfully to the user's image request while preserving any identity, subject, composition, or content constraints they provide.");all.add(new Cmd(o,false));}catch(Exception ignored){}}
  }

  String photoDescription(String shortText){
    return shortText+" — preset بصري جاهز يوجّه ChatGPT لنفس الستايل مع الحفاظ على تفاصيل طلب الصورة.";
  }

'''
if insert_before not in s:
    raise SystemExit('base() insertion point not found')
s=s.replace(insert_before,seed_method+insert_before,1)

# Include custom commands whose category matches the opened group.
needle='''    LinkedHashMap<String,ArrayList<Cmd>> subs=new LinkedHashMap<>();for(String n:g.names){Cmd c=find(n);if(c!=null){String s=subcat(c.command,g.title);if(!subs.containsKey(s))subs.put(s,new ArrayList<>());subs.get(s).add(c);}}'''
replacement='''    LinkedHashMap<String,ArrayList<Cmd>> subs=new LinkedHashMap<>();for(String n:g.names){Cmd c=find(n);if(c!=null){String sc=subcat(c.command,g.title);if(!subs.containsKey(sc))subs.put(sc,new ArrayList<>());subs.get(sc).add(c);}}for(Cmd c:all){if(!c.custom||!c.category.equalsIgnoreCase(g.title))continue;String sc=g.title.contains("Photo Editing")?"Imported Photo Prompts":"Custom";if(!subs.containsKey(sc))subs.put(sc,new ArrayList<>());subs.get(sc).add(c);}'''
if needle not in s:
    raise SystemExit('group map logic not found')
s=s.replace(needle,replacement,1)

# Photo-specific subcategories.
old_tail='''if(group.contains("Technical")){if(has(c,"rootcause,debug,fix,check"))return"Diagnose & Fix";return"Improve & Validate";}return"Structure & Convert";}'''
new_tail='''if(group.contains("Technical")){if(has(c,"rootcause,debug,fix,check"))return"Diagnose & Fix";return"Improve & Validate";}if(group.contains("Photo Editing")){if(has(c,"ProHeadshot,StudioPro,Magazine,OldMoney,LowAngleHero"))return"Portrait & Editorial";if(has(c,"NeonCity,GoldenHour,Fog,RainyNight,SnowWorld,Autumn,MovieScene"))return"Cinematic & Environment";if(has(c,"LuxuryAd,DroneView,VintageFilm"))return"Commercial & Camera Styles";return"Creative Effects";}return"Structure & Convert";}'''
if old_tail not in s:
    raise SystemExit('subcat tail not found')
s=s.replace(old_tail,new_tail,1)

# Add a bulk-paste entry in Prompt Library.
old_lib='''    View add=menuCard("＋","Add prompt","Create one custom command manually");add.setOnClickListener(v->showAdd());root.addView(add);View imp=menuCard("↓","Import pack","Import a .promptdeck.json library");'''
new_lib='''    View add=menuCard("＋","Add prompt","Create one custom command manually");add.setOnClickListener(v->showAdd());root.addView(add);View paste=menuCard("⌁","Paste command list","Paste lines like /NeonCity → Cyberpunk night portrait and PromptDeck separates them automatically");paste.setOnClickListener(v->showBulkPaste());root.addView(paste);View imp=menuCard("↓","Import pack","Import a .promptdeck.json library");'''
if old_lib not in s:
    raise SystemExit('library menu insertion point not found')
s=s.replace(old_lib,new_lib,1)

# Bulk-paste dialog and tolerant line parser.
showadd='''  void showAdd(){'''
bulk='''  void showBulkPaste(){
    LinearLayout box=vbox();box.setPadding(dp(18),dp(4),dp(18),0);
    EditText category=input("Category (default: Photo Editing & Image Generation)",1);category.setText("Photo Editing & Image Generation");
    EditText bulk=input("Paste command lines here…\\n\\n1. /NeonCity → Cyberpunk night portrait\\n2. /GoldenHour → Cinematic sunset portrait\\n\\nAlso accepts ->  —  :  |  =",12);
    box.addView(category);box.addView(bulk);
    AlertDialog d=new AlertDialog.Builder(this).setTitle("Smart paste commands").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Parse & add",null).create();
    d.setOnShowListener(z->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String cat=category.getText().toString().trim();if(cat.isEmpty())cat="Photo Editing & Image Generation";int[] result=parseBulkCommands(bulk.getText().toString(),cat);if(result[0]==0){toast("No command lines found. Use /Name → description");return;}saveCustom();d.dismiss();library();toast("Added "+result[0]+" prompts"+(result[1]>0?" • skipped "+result[1]:""));}));d.show();
  }

  int[] parseBulkCommands(String raw,String category){
    int added=0,skipped=0;String[] lines=raw.split("\\r?\\n");
    java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("^\\\\s*(?:\\\\d+[.)]\\\\s*)?/([A-Za-z0-9_-]+)\\\\s*(?:→|->|—|–|:|\\\\||=)\\\\s*(.+?)\\\\s*$");
    for(String line:lines){line=line.trim();if(line.isEmpty())continue;java.util.regex.Matcher m=pattern.matcher(line);if(!m.matches()){skipped++;continue;}String name=m.group(1).trim(),desc=m.group(2).trim();if(name.isEmpty()||desc.isEmpty()){skipped++;continue;}boolean duplicate=false;for(Cmd c:all)if(c.command.equalsIgnoreCase(name)){duplicate=true;break;}if(duplicate){skipped++;continue;}try{JSONObject o=new JSONObject();o.put("id",nextId());o.put("command",name);o.put("category",category);o.put("description",category.toLowerCase(Locale.ROOT).contains("photo")?photoDescription(desc):desc);o.put("instruction",category.toLowerCase(Locale.ROOT).contains("photo")?"Use the /"+name+" image direction: "+desc+". Apply it faithfully to the user's image request while preserving any identity, subject, composition, or content constraints they provide.":"Apply /"+name+": "+desc+". Follow this instruction as a named PromptDeck operator within the user's request.");all.add(new Cmd(o,true));added++;}catch(Exception e){skipped++;}}
    return new int[]{added,skipped};
  }

'''
if showadd not in s:
    raise SystemExit('showAdd insertion point not found')
s=s.replace(showadd,bulk+showadd,1)

p.write_text(s)
print('PromptDeck RC6 photo category + smart bulk paste applied')
