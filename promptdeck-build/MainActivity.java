package com.kareem.promptdeck;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
  static final int IMPORT_REQ=1001, EXPORT_REQ=1002;
  static final String PREFS="promptdeck", CUSTOM="custom_commands_v1";
  static final int BG=Color.rgb(11,12,14), SURFACE=Color.rgb(20,22,26), SURFACE2=Color.rgb(26,29,34), BORDER=Color.rgb(42,45,52);
  static final int TEXT=Color.rgb(244,241,234), MUTED=Color.rgb(158,160,166), ACCENT=Color.rgb(198,168,107), SUCCESS=Color.rgb(176,201,164);

  static class Cmd {
    int id; String command,category,description,instruction; boolean custom;
    Cmd(JSONObject o, boolean custom) throws JSONException {
      id=o.optInt("id",0); command=clean(o.optString("command","")); category=o.optString("category","Custom").trim();
      description=o.optString("description",o.optString("description_ar","")).trim(); instruction=o.optString("instruction","").trim(); this.custom=custom;
      if(command.isEmpty()||instruction.isEmpty()) throw new JSONException("command and instruction are required");
    }
    JSONObject json() throws JSONException { JSONObject o=new JSONObject();o.put("id",id);o.put("command",command);o.put("category",category);o.put("description",description);o.put("instruction",instruction);return o; }
    static String clean(String s){s=s==null?"":s.trim();while(s.startsWith("/"))s=s.substring(1);return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","");}
  }
  static class Group { String title,sub,icon; String[] names; Group(String icon,String title,String sub,String...names){this.icon=icon;this.title=title;this.sub=sub;this.names=names;} }

  final ArrayList<Cmd> all=new ArrayList<>(), selected=new ArrayList<>();
  LinearLayout root; EditText context,finalPrompt;

  final Group[] groups={
    new Group("✦","Writing & Rewriting","كتابة وإعادة صياغة وتحسين النص","rewrite","rephrase","paraphrase","polish","proofread","grammar","shorten","expand","simplify","clarify","humanize","professional","formal","casual","tone","translate","arabic"),
    new Group("◈","Thinking & Ideas","أفكار، نقد، زوايا جديدة واتخاذ قرار","brainstorm","ideas","angles","alternative","critique","challenge","devilsadvocate","blindspots","assumptions","firstprinciples","counterexample","socratic","tradeoffs","decision","recommend","rank"),
    new Group("⌕","Research & Analysis","بحث، تحقق، أدلة وفهم المعلومات","research","verify","sources","evidence","facts","deepdive","insights","trends","data","compare","proscons","summary","summarize","extract","classify"),
    new Group("◇","Planning & Execution","خطط، أولويات، خطوات وتنفيذ","plan","strategy","roadmap","checklist","todo","priority","workflow","timeline","action","requirements","constraints","risks","acceptance","spec","estimate"),
    new Group("◎","Learning & Study","شرح، تعلم، مراجعة واختبارات","eli5","explain","steps","examples","analogy","study","quiz","flashcards","teach","hint","test","review","memorize","mistakes"),
    new Group("▣","Work & Career","إيميلات، CV، مقابلات واجتماعات","email","reply","followup","request","apology","resume","coverletter","interview","meeting","minutes"),
    new Group("△","Content Creation","محتوى، Hooks، Scripts وSocial Media","hook","caption","script","carousel","reel","viral","cta","story","headline"),
    new Group("⚙","Problem Solving & Technical","تشخيص، إصلاح، اختبار وتحسين","rootcause","debug","fix","check","optimize","better","edgecases","refactor","tests","security","rubric","score"),
    new Group("▦","Data & Formatting","تنظيم وعرض وتحويل البيانات","table","bullets","outline","format","json","csv","schema","template","prompt")
  };

  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);load();home();}

  void load(){all.clear();try{JSONArray a=new JSONArray(readAsset("commands.json"));for(int i=0;i<a.length();i++)all.add(new Cmd(a.getJSONObject(i),false));JSONArray c=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(CUSTOM,"[]"));for(int i=0;i<c.length();i++)try{all.add(new Cmd(c.getJSONObject(i),true));}catch(Exception ignored){}}catch(Exception e){throw new RuntimeException(e);}}

  void base(String title,String sub,boolean showStack){
    ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
    root=vbox(); root.setPadding(dp(20),dp(14),dp(20),dp(40)); sv.addView(root); setContentView(sv);
    LinearLayout top=hbox(); top.setGravity(Gravity.CENTER_VERTICAL);
    TextView brand=text("PROMPTDECK",12,true,ACCENT); brand.setLetterSpacing(.18f); top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
    if(showStack){TextView stack=pill(selected.isEmpty()?"STACK":"STACK  "+selected.size());stack.setOnClickListener(v->stack());top.addView(stack);} root.addView(top);
    TextView ttl=text(title,31,true,TEXT); ttl.setPadding(0,dp(18),0,dp(4)); root.addView(ttl);
    if(sub!=null&&!sub.isEmpty()){TextView st=text(sub,15,false,MUTED);st.setLineSpacing(0,1.12f);st.setPadding(0,0,0,dp(18));root.addView(st);} }

  void home(){
    base("Choose a category","كل الأوامر متقسمة بشكل واضح. افتح أي قسم، اختار الـcommand، واقرأ استخدامه قبل ما تضيفه للـStack.",true);
    for(Group g:groups){View c=groupCard(g);c.setOnClickListener(v->group(g));root.addView(c);} spacer(8);
    View library=menuCard("＋","My Prompt Library","أضف أو استورد أو صدّر prompts خاصة بيك");library.setOnClickListener(v->library());root.addView(library);
    if(!selected.isEmpty()){spacer(10);Button compose=primary("Build prompt from "+selected.size()+" selected command"+(selected.size()==1?"":"s"));compose.setOnClickListener(v->stack());root.addView(compose);}
    TextView foot=text("120 built-in prompt operators  •  local library  •  no API required",11,false,MUTED);foot.setGravity(Gravity.CENTER);foot.setPadding(0,dp(24),0,0);root.addView(foot);
  }

  View groupCard(Group g){
    LinearLayout card=surface(false); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
    TextView icon=text(g.icon,22,true,ACCENT); icon.setGravity(Gravity.CENTER); GradientDrawable circle=shape(SURFACE2,ACCENT,22);circle.setStroke(dp(1),Color.rgb(55,50,42));icon.setBackground(circle);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(44),dp(44));ip.setMargins(0,0,dp(14),0);card.addView(icon,ip);
    LinearLayout copy=vbox();TextView title=text(g.title,17,true,TEXT);TextView sub=text(g.sub,13,false,MUTED);copy.addView(title);copy.addView(sub);card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("›",28,false,MUTED);card.addView(arrow);return card;
  }
  View menuCard(String icon,String title,String sub){
    LinearLayout card=surface(false);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);TextView ic=text(icon,23,false,ACCENT);ic.setGravity(Gravity.CENTER);card.addView(ic,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout copy=vbox();copy.addView(text(title,16,true,TEXT));copy.addView(text(sub,13,false,MUTED));card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));card.addView(text("›",28,false,MUTED));return card;
  }

  void group(Group g){
    base(g.title,g.sub,true);
    LinkedHashMap<String,ArrayList<Cmd>> subs=new LinkedHashMap<>();for(String n:g.names){Cmd c=find(n);if(c!=null){String s=subcat(c.command,g.title);if(!subs.containsKey(s))subs.put(s,new ArrayList<>());subs.get(s).add(c);}}
    for(Map.Entry<String,ArrayList<Cmd>> en:subs.entrySet()){
      root.addView(section(en.getKey(),en.getValue().size()));
      LinearLayout block=vbox(); block.setBackground(shape(SURFACE,BORDER,18)); block.setPadding(0,dp(2),0,dp(2));
      int index=0;for(Cmd c:en.getValue()){View row=commandRow(c,index++<en.getValue().size()-1);row.setOnClickListener(v->detail(c,g));block.addView(row);}root.addView(block);spacer(16);
    }
    Button back=ghost("←  Categories");back.setOnClickListener(v->home());root.addView(back);
  }

  View commandRow(Cmd c,boolean divider){
    LinearLayout wrap=vbox();wrap.setPadding(dp(16),dp(13),dp(14),dp(11));LinearLayout line=hbox();line.setGravity(Gravity.CENTER_VERTICAL);
    TextView name=text("/"+c.command,16,true,TEXT);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));if(selected.contains(c)){TextView tick=text("SELECTED",9,true,SUCCESS);tick.setLetterSpacing(.12f);line.addView(tick);}else line.addView(text("›",24,false,MUTED));wrap.addView(line);
    TextView d=text(c.description,13,false,MUTED);d.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);d.setPadding(0,dp(3),0,0);wrap.addView(d);
    if(divider){View v=new View(this);v.setBackgroundColor(BORDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(11),0,0);wrap.addView(v,lp);}return wrap;
  }

  void detail(Cmd c,Group g){
    base("/"+c.command,c.description,true);
    info("WHAT IT DOES",c.description);
    info("USE IT WHEN",useWhen(c));
    info("EXAMPLE","/"+c.command+"  "+example(c));
    info("INSTRUCTION SENT TO CHATGPT",c.instruction);
    String related=related(c.command);if(!related.isEmpty())info("WORKS WELL WITH",related);
    Button add=selected.contains(c)?secondary("✓  Added to Prompt Stack"):primary("＋  Add to Prompt Stack");add.setOnClickListener(v->{if(!selected.contains(c)){selected.add(c);toast("Added /"+c.command);}stack();});root.addView(add);
    Button back=ghost("←  "+g.title);back.setOnClickListener(v->group(g));root.addView(back);
  }

  void info(String label,String body){TextView l=text(label,10,true,ACCENT);l.setLetterSpacing(.14f);l.setPadding(0,dp(14),0,dp(6));root.addView(l);LinearLayout box=surface(true);TextView b=text(body,15,false,TEXT);b.setLineSpacing(0,1.18f);b.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);box.addView(b);root.addView(box);}

  void stack(){
    base("Prompt Stack",selected.isEmpty()?"لسه ما اخترتش أي commands.":"رتّب الخطوات. كل command هيبني على نتيجة اللي قبله.",false);
    if(selected.isEmpty()){LinearLayout empty=surface(true);TextView e=text("ابدأ من Categories واختار command أو أكتر.",15,false,MUTED);e.setGravity(Gravity.CENTER);e.setPadding(0,dp(28),0,dp(28));empty.addView(e);root.addView(empty);Button browse=primary("Browse categories");browse.setOnClickListener(v->home());root.addView(browse);return;}
    for(int i=0;i<selected.size();i++){final int k=i;Cmd c=selected.get(i);LinearLayout row=surface(false);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);TextView num=text(String.format(Locale.ROOT,"%02d",i+1),11,true,ACCENT);num.setLetterSpacing(.12f);row.addView(num,new LinearLayout.LayoutParams(dp(38),-2));LinearLayout copy=vbox();copy.addView(text("/"+c.command,15,true,TEXT));copy.addView(text(c.description,12,false,MUTED));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));Button up=mini("↑"),dn=mini("↓"),rm=mini("×");up.setOnClickListener(v->{if(k>0)Collections.swap(selected,k,k-1);stack();});dn.setOnClickListener(v->{if(k<selected.size()-1)Collections.swap(selected,k,k+1);stack();});rm.setOnClickListener(v->{selected.remove(k);stack();});row.addView(up);row.addView(dn);row.addView(rm);root.addView(row);}
    root.addView(label("YOUR REQUEST / CONTEXT"));context=input("اكتب هنا الموضوع أو النص اللي عاوز تطبق عليه الـcommands…",5);root.addView(context);
    Button build=primary("Build final prompt");build.setOnClickListener(v->build());root.addView(build);Button more=ghost("＋  Add more commands");more.setOnClickListener(v->home());root.addView(more);
  }

  void build(){
    String user=context==null?"":context.getText().toString().trim();base("Final Prompt","جاهز للنسخ أو الإرسال مباشرة إلى ChatGPT.",false);
    StringBuilder p=new StringBuilder("Help me with the request below by following these steps in order. Each step should build on the useful findings of the previous one.\n\n");for(int i=0;i<selected.size();i++)p.append(i+1).append(". ").append(selected.get(i).instruction).append('\n');if(!user.isEmpty())p.append("\nMy request / context:\n").append(user);p.append("\n\nGive me one clear, coherent final answer. Show useful conclusions and evidence, but do not expose private chain-of-thought.");
    finalPrompt=input("",11);finalPrompt.setText(p);finalPrompt.setTextSize(14);root.addView(finalPrompt);Button send=primary("Open in ChatGPT");send.setOnClickListener(v->send());root.addView(send);Button copy=secondary("Copy prompt");copy.setOnClickListener(v->copy());root.addView(copy);Button edit=ghost("←  Edit stack");edit.setOnClickListener(v->stack());root.addView(edit);
  }

  void library(){
    base("Prompt Library",all.size()+" prompts available  •  custom prompts stay on this device",true);
    View add=menuCard("＋","Add prompt","Create one custom command manually");add.setOnClickListener(v->showAdd());root.addView(add);View imp=menuCard("↓","Import pack","Import a .promptdeck.json library");imp.setOnClickListener(v->openImport());root.addView(imp);View exp=menuCard("↑","Export custom","Back up or move your custom prompts");exp.setOnClickListener(v->openExport());root.addView(exp);
    ArrayList<Cmd> custom=new ArrayList<>();for(Cmd c:all)if(c.custom)custom.add(c);if(!custom.isEmpty()){spacer(10);root.addView(section("My custom prompts",custom.size()));LinearLayout block=vbox();block.setBackground(shape(SURFACE,BORDER,18));for(int i=0;i<custom.size();i++){Cmd c=custom.get(i);View row=commandRow(c,i<custom.size()-1);row.setOnClickListener(v->customDetail(c));block.addView(row);}root.addView(block);}Button back=ghost("←  Categories");back.setOnClickListener(v->home());root.addView(back);
  }
  void customDetail(Cmd c){base("/"+c.command,c.description,true);info("INSTRUCTION SENT TO CHATGPT",c.instruction);Button add=selected.contains(c)?secondary("✓  Added to Prompt Stack"):primary("＋  Add to Prompt Stack");add.setOnClickListener(v->{if(!selected.contains(c))selected.add(c);stack();});root.addView(add);Button del=ghost("Delete custom prompt");del.setOnClickListener(v->confirmDelete(c));root.addView(del);}

  void showAdd(){
    LinearLayout x=vbox();x.setPadding(dp(18),dp(4),dp(18),0);EditText cmd=input("Command name, e.g. architectreview",1),cat=input("Category",1),desc=input("Short explanation",2),inst=input("Full instruction for ChatGPT",5);x.addView(cmd);x.addView(cat);x.addView(desc);x.addView(inst);
    AlertDialog d=new AlertDialog.Builder(this).setTitle("Add prompt").setView(x).setNegativeButton("Cancel",null).setPositiveButton("Add",null).create();d.setOnShowListener(z->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{JSONObject o=new JSONObject();o.put("id",nextId());o.put("command",cmd.getText());o.put("category",cat.getText());o.put("description",desc.getText());o.put("instruction",inst.getText());Cmd c=new Cmd(o,true);all.add(c);saveCustom();d.dismiss();library();toast("Prompt added");}catch(Exception e){toast("Name and instruction are required");}}));d.show();
  }
  void confirmDelete(Cmd c){new AlertDialog.Builder(this).setTitle("Delete /"+c.command+"?").setMessage("This removes the custom prompt from this device.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{all.remove(c);selected.remove(c);saveCustom();library();}).show();}

  String subcat(String c,String group){if(group.contains("Writing")){if(has(c,"rewrite,rephrase,paraphrase,polish,proofread,grammar"))return"Rewrite & Improve";if(has(c,"humanize,professional,formal,casual,tone"))return"Tone & Style";if(has(c,"shorten,expand,simplify,clarify"))return"Length & Clarity";return"Language & Translation";}if(group.contains("Thinking")){if(has(c,"brainstorm,ideas,angles,alternative"))return"Generate Ideas";if(has(c,"critique,challenge,devilsadvocate,blindspots,counterexample"))return"Challenge & Critique";return"Reason & Decide";}if(group.contains("Research")){if(has(c,"research,sources,evidence,verify,facts"))return"Research & Verification";if(has(c,"compare,proscons"))return"Compare";return"Analyze & Extract";}if(group.contains("Planning")){if(has(c,"plan,strategy,roadmap"))return"Strategy & Roadmap";if(has(c,"requirements,constraints,risks,acceptance,spec"))return"Define the Work";return"Tasks & Execution";}if(group.contains("Learning")){if(has(c,"eli5,explain,steps,examples,analogy,teach"))return"Understand & Learn";return"Practice & Remember";}if(group.contains("Work")){if(has(c,"email,reply,followup,request,apology"))return"Messages & Email";if(has(c,"resume,coverletter,interview"))return"Career";return"Meetings";}if(group.contains("Content")){if(has(c,"hook,headline,caption,cta,viral"))return"Attention & Engagement";return"Content Formats";}if(group.contains("Technical")){if(has(c,"rootcause,debug,fix,check"))return"Diagnose & Fix";return"Improve & Validate";}return"Structure & Convert";}
  String useWhen(Cmd c){String n=c.command;if(has(n,"compare,proscons,rank,recommend,decision"))return"عندك أكتر من اختيار وعاوز تفهم الفرق أو توصل لقرار.";if(has(n,"research,verify,sources,evidence,facts"))return"محتاج معلومة موثوقة أو عاوز تتأكد من ادعاء قبل ما تعتمد عليه.";if(has(n,"rewrite,rephrase,polish,proofread,grammar,humanize"))return"عندك نص موجود وعاوز تطلعه بشكل أحسن بدل ما تبدأ من الصفر.";if(has(n,"brainstorm,ideas,angles,alternative"))return"محتاج توسع مساحة الاختيارات وتطلع أفكار أو اتجاهات جديدة.";if(has(n,"debug,rootcause,fix,check,tests"))return"في مشكلة أو نتيجة غلط وعاوز تشخص السبب وتوصل لإصلاح قابل للاختبار.";if(has(n,"plan,strategy,roadmap,action,priority"))return"عندك هدف وعاوز تحوله لترتيب عملي واضح بدل كلام عام.";return"لما تكون محتاج الوظيفة دي كخطوة واضحة داخل طلب أكبر.";}
  String example(Cmd c){return"طبّق الأمر ده على الموضوع أو النص اللي هبعته، واديني نتيجة واضحة وعملية.";}
  String related(String n){if(has(n,"research,verify,sources,evidence"))return"/research   /verify   /sources   /evidence   /facts";if(has(n,"critique,challenge,blindspots,devilsadvocate"))return"/critique   /challenge   /blindspots   /improve";if(has(n,"rewrite,rephrase,polish,proofread"))return"/rewrite   /clarify   /polish   /professional";if(has(n,"plan,strategy,roadmap,action"))return"/strategy   /roadmap   /priority   /action";if(has(n,"debug,fix,check,tests"))return"/rootcause   /debug   /fix   /tests   /check";return"";}
  boolean has(String c,String csv){return Arrays.asList(csv.split(",")).contains(c);}Cmd find(String n){for(Cmd c:all)if(c.command.equals(n))return c;return null;}int nextId(){int m=10000;for(Cmd c:all)m=Math.max(m,c.id+1);return m;}

  void saveCustom(){JSONArray a=new JSONArray();for(Cmd c:all)if(c.custom)try{a.put(c.json());}catch(Exception ignored){}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(CUSTOM,a.toString()).apply();}
  void openImport(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,IMPORT_REQ);}void openExport(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"PromptDeck-custom.promptdeck.json");startActivityForResult(i,EXPORT_REQ);}
  @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{if(r==IMPORT_REQ)importPack(data.getData());else if(r==EXPORT_REQ)exportPack(data.getData());}catch(Exception e){toast("File error: "+e.getMessage());}}
  void importPack(Uri u)throws Exception{Object p=new JSONTokener(readUri(u)).nextValue();JSONArray a;if(p instanceof JSONArray)a=(JSONArray)p;else{JSONObject o=(JSONObject)p;a=o.has("commands")?o.getJSONArray("commands"):new JSONArray().put(o);}int n=0,skip=0;for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);o.put("id",nextId());Cmd c=new Cmd(o,true);boolean dup=false;for(Cmd z:all)if(z.command.equals(c.command)&&z.instruction.equals(c.instruction))dup=true;if(dup){skip++;continue;}all.add(c);n++;}catch(Exception ignored){skip++;}saveCustom();toast("Imported "+n+(skip>0?" • skipped "+skip:""));library();}
  void exportPack(Uri u)throws Exception{JSONObject p=new JSONObject();p.put("format","promptdeck-pack");p.put("version",1);p.put("name","PromptDeck custom prompts");JSONArray a=new JSONArray();for(Cmd c:all)if(c.custom)a.put(c.json());p.put("commands",a);OutputStream out=getContentResolver().openOutputStream(u,"w");if(out==null)throw new IOException("Can't open destination");out.write(p.toString(2).getBytes(StandardCharsets.UTF_8));out.close();toast("Custom prompts exported");}
  void copy(){if(finalPrompt==null)return;((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PromptDeck prompt",finalPrompt.getText()));toast("Prompt copied");}
  void send(){if(finalPrompt==null)return;Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,finalPrompt.getText().toString());i.setPackage("com.openai.chatgpt");try{startActivity(i);}catch(Exception e){i.setPackage(null);startActivity(Intent.createChooser(i,"Send prompt"));}}

  LinearLayout surface(boolean compact){LinearLayout l=vbox();l.setPadding(dp(compact?16:16),dp(compact?14:14),dp(compact?16:16),dp(compact?14:14));l.setBackground(shape(SURFACE,BORDER,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));l.setLayoutParams(lp);return l;}
  GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}
  TextView text(String s,int sp,boolean bold,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));else v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));return v;}
  TextView section(String s,int count){TextView v=text(s+"   "+count,11,true,MUTED);v.setAllCaps(true);v.setLetterSpacing(.11f);v.setPadding(dp(2),dp(6),0,dp(9));return v;}TextView label(String s){TextView v=text(s,10,true,ACCENT);v.setLetterSpacing(.14f);v.setPadding(0,dp(18),0,dp(7));return v;}
  TextView pill(String s){TextView v=text(s,10,true,ACCENT);v.setLetterSpacing(.12f);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),dp(7),dp(12),dp(7));v.setBackground(shape(SURFACE2,Color.rgb(64,56,42),22));return v;}
  Button styledButton(String s,int fill,int stroke,int color){Button x=new Button(this);x.setText(s);x.setAllCaps(false);x.setTextColor(color);x.setTextSize(14);x.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));x.setBackground(shape(fill,stroke,14));x.setPadding(dp(14),dp(11),dp(14),dp(11));x.setMinHeight(dp(50));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));x.setLayoutParams(lp);return x;}
  Button primary(String s){return styledButton(s,ACCENT,0,BG);}Button secondary(String s){return styledButton(s,SURFACE2,BORDER,TEXT);}Button ghost(String s){return styledButton(s,BG,BORDER,MUTED);}Button mini(String s){Button x=new Button(this);x.setText(s);x.setAllCaps(false);x.setTextColor(MUTED);x.setTextSize(14);x.setMinWidth(0);x.setMinHeight(0);x.setPadding(dp(8),dp(6),dp(8),dp(6));x.setBackground(shape(SURFACE2,BORDER,10));return x;}
  EditText input(String hint,int lines){EditText x=new EditText(this);x.setHint(hint);x.setHintTextColor(Color.rgb(105,108,115));x.setTextColor(TEXT);x.setTextSize(15);x.setMinLines(lines);x.setGravity(Gravity.TOP|Gravity.START);x.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);x.setPadding(dp(15),dp(13),dp(15),dp(13));x.setBackground(shape(SURFACE,BORDER,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));x.setLayoutParams(lp);return x;}
  LinearLayout vbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}LinearLayout hbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}void spacer(int h){Space s=new Space(this);root.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  String readAsset(String n)throws IOException{return slurp(getAssets().open(n));}String readUri(Uri u)throws IOException{return slurp(getContentResolver().openInputStream(u));}String slurp(InputStream in)throws IOException{if(in==null)throw new IOException("Can't open file");ByteArrayOutputStream o=new ByteArrayOutputStream();byte[]b=new byte[4096];int n;while((n=in.read(b))>0)o.write(b,0,n);in.close();return o.toString(StandardCharsets.UTF_8.name());}
}