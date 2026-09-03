package com.kareem.promptdeck;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
  static final int IMPORT_REQ=1001, EXPORT_REQ=1002;
  static final String PREFS="promptdeck", CUSTOM="custom_commands_v1";
  static class Cmd {
    int id; String command,category,description,instruction; boolean custom;
    Cmd(JSONObject o, boolean custom) throws JSONException {
      id=o.optInt("id",0); command=clean(o.optString("command","")); category=o.optString("category","Custom").trim();
      if(category.isEmpty()) category="Custom"; description=o.optString("description",o.optString("description_ar","")).trim();
      instruction=o.optString("instruction","").trim(); this.custom=custom;
      if(command.isEmpty()||instruction.isEmpty()) throw new JSONException("command and instruction are required");
    }
    JSONObject json() throws JSONException { JSONObject o=new JSONObject(); o.put("id",id);o.put("command",command);o.put("category",category);o.put("description",description);o.put("instruction",instruction);return o; }
    static String clean(String s){ if(s==null)return ""; s=s.trim(); while(s.startsWith("/"))s=s.substring(1); return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]",""); }
  }

  final ArrayList<Cmd> all=new ArrayList<>(), selected=new ArrayList<>();
  LinearLayout list,chain,cats; EditText need,search,finalPrompt; TextView count,explain;
  String activeCategory="All";

  @Override public void onCreate(Bundle b){ super.onCreate(b); load(); buildUi(); acceptShared(getIntent()); }
  @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); setIntent(i); acceptShared(i); }

  void load(){ all.clear(); try{
    JSONArray a=new JSONArray(readAsset("commands.json")); for(int i=0;i<a.length();i++) all.add(new Cmd(a.getJSONObject(i),false));
    JSONArray c=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(CUSTOM,"[]"));
    for(int i=0;i<c.length();i++) try{all.add(new Cmd(c.getJSONObject(i),true));}catch(Exception ignored){}
  }catch(Exception e){ throw new RuntimeException(e); } }

  void buildUi(){
    ScrollView sv=new ScrollView(this); LinearLayout root=vbox(); root.setPadding(dp(16),dp(14),dp(16),dp(30)); sv.addView(root); setContentView(sv);
    TextView title=t("PromptDeck",30,true); root.addView(title); root.addView(t("Build prompt pipelines, explain them, then send the final prompt to ChatGPT.",14,false));
    LinearLayout actions=hbox(); Button add=b("+ Add prompt"), imp=b("Import JSON"), exp=b("Export custom");
    add.setOnClickListener(v->showAdd()); imp.setOnClickListener(v->openImport()); exp.setOnClickListener(v->openExport()); actions.addView(add);actions.addView(imp);actions.addView(exp);root.addView(actions);
    count=t("",12,false); root.addView(count); updateCount();

    root.addView(h("1  What do you need?")); need=e("Describe your goal or paste the text/context here…",5); root.addView(need); need.addTextChangedListener(watcher(()->updatePrompt()));
    root.addView(h("Recipes")); LinearLayout recipes=hbox(); addRecipe(recipes,"Research decision",new String[]{"research","compare","proscons","verify","recommend"}); addRecipe(recipes,"Improve an idea",new String[]{"critique","challenge","blindspots","improve"}); addRecipe(recipes,"Project plan",new String[]{"brainstorm","strategy","roadmap","priority","action"}); addRecipe(recipes,"Deep research",new String[]{"research","sources","verify","deepdive","insights"}); root.addView(recipes);

    root.addView(h("2  Choose prompt operators")); search=e("Search: research, rewrite, risks…",1); root.addView(search); search.addTextChangedListener(watcher(()->renderList()));
    HorizontalScrollView hsv=new HorizontalScrollView(this); cats=hbox(); hsv.addView(cats); root.addView(hsv); rebuildCats(); list=vbox(); root.addView(list); renderList();

    root.addView(h("3  Pipeline")); chain=vbox(); root.addView(chain); explain=t("Choose one or more operators. Order matters: each stage builds on the previous result.",13,false); root.addView(explain);
    LinearLayout controls=hbox(); Button clear=b("Clear"),copy=b("Copy Prompt"),send=b("Send to ChatGPT"); clear.setOnClickListener(v->{selected.clear();renderChain();updatePrompt();}); copy.setOnClickListener(v->copy()); send.setOnClickListener(v->send());controls.addView(clear);controls.addView(copy);controls.addView(send);root.addView(controls);
    root.addView(h("Final Prompt")); finalPrompt=e("Your composed prompt appears here…",10); finalPrompt.setTextIsSelectable(true); root.addView(finalPrompt); renderChain(); updatePrompt();
  }

  void rebuildCats(){ cats.removeAllViews(); LinkedHashSet<String> set=new LinkedHashSet<>(); set.add("All"); for(Cmd c:all)set.add(c.category); for(String s:set){Button x=b(s);x.setOnClickListener(v->{activeCategory=s;rebuildCats();renderList();});cats.addView(x);} }
  void renderList(){ if(list==null)return; list.removeAllViews(); String q=search==null?"":search.getText().toString().toLowerCase(Locale.ROOT).trim(); int shown=0;
    for(Cmd c:all){ if(!activeCategory.equals("All")&&!activeCategory.equals(c.category))continue; String blob=(c.command+" "+c.category+" "+c.description+" "+c.instruction).toLowerCase(Locale.ROOT); if(!q.isEmpty()&&!blob.contains(q))continue; shown++; LinearLayout row=vbox(); TextView name=t("/"+c.command+(c.custom?"  • custom":""),17,true); row.addView(name); if(!c.description.isEmpty())row.addView(t(c.description,13,false)); row.setPadding(dp(10),dp(8),dp(10),dp(8)); row.setOnClickListener(v->{selected.add(c);renderChain();updatePrompt();}); if(c.custom)row.setOnLongClickListener(v->{confirmDelete(c);return true;}); list.addView(row); }
    if(shown==0)list.addView(t("No matching prompt operators.",13,false)); }
  void renderChain(){ if(chain==null)return; chain.removeAllViews(); for(int i=0;i<selected.size();i++){ final int idx=i; Cmd c=selected.get(i); LinearLayout r=hbox(); r.addView(t((i+1)+". /"+c.command,15,true),new LinearLayout.LayoutParams(0,-2,1)); Button up=b("↑"),dn=b("↓"),rm=b("×"); up.setOnClickListener(v->{if(idx>0)Collections.swap(selected,idx,idx-1);renderChain();updatePrompt();});dn.setOnClickListener(v->{if(idx<selected.size()-1)Collections.swap(selected,idx,idx+1);renderChain();updatePrompt();});rm.setOnClickListener(v->{selected.remove(idx);renderChain();updatePrompt();});r.addView(up);r.addView(dn);r.addView(rm);chain.addView(r);} explain.setText(selected.isEmpty()?"Choose one or more operators. Order matters: each stage builds on the previous result.":"Pipeline: "+chainNames()); }
  String chainNames(){StringBuilder s=new StringBuilder();for(int i=0;i<selected.size();i++){if(i>0)s.append(" → ");s.append('/').append(selected.get(i).command);}return s.toString();}
  void updatePrompt(){ if(finalPrompt==null)return; String user=need==null?"":need.getText().toString().trim(); if(selected.isEmpty()){finalPrompt.setText(user);return;} StringBuilder p=new StringBuilder(); p.append("Work through the following instruction pipeline in order. Each stage should use and improve on the output or findings of the previous stage. Do not merely name the stages; actually perform them.\n\n"); for(int i=0;i<selected.size();i++){Cmd c=selected.get(i);p.append(i+1).append(". /").append(c.command).append(": ").append(c.instruction).append('\n');} if(!user.isEmpty())p.append("\nMy request / context:\n").append(user); p.append("\n\nReturn a coherent final answer that reflects the full pipeline, while preserving useful intermediate reasoning as concise conclusions rather than exposing private chain-of-thought."); finalPrompt.setText(p.toString()); }

  void addRecipe(LinearLayout box,String label,String[] names){Button x=b(label);x.setOnClickListener(v->{selected.clear();for(String n:names){Cmd c=find(n);if(c!=null)selected.add(c);}renderChain();updatePrompt();});box.addView(x);}
  Cmd find(String n){for(Cmd c:all)if(c.command.equals(n))return c;return null;}

  void showAdd(){ LinearLayout x=vbox();x.setPadding(dp(16),dp(4),dp(16),0);EditText cmd=e("Command name, e.g. architectreview",1),cat=e("Category",1),desc=e("Short explanation",2),inst=e("Full instruction that ChatGPT should follow",5);x.addView(cmd);x.addView(cat);x.addView(desc);x.addView(inst);AlertDialog d=new AlertDialog.Builder(this).setTitle("Add prompt operator").setView(x).setNegativeButton("Cancel",null).setPositiveButton("Add",null).create();d.setOnShowListener(z->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{JSONObject o=new JSONObject();o.put("id",nextId());o.put("command",cmd.getText().toString());o.put("category",cat.getText().toString());o.put("description",desc.getText().toString());o.put("instruction",inst.getText().toString());Cmd c=new Cmd(o,true);all.add(c);saveCustom();refresh();d.dismiss();toast("/"+c.command+" added");}catch(Exception ex){toast("Command and instruction are required. Use English letters/numbers for the command.");}}));d.show(); }
  int nextId(){int m=10000;for(Cmd c:all)m=Math.max(m,c.id+1);return m;}
  void confirmDelete(Cmd c){new AlertDialog.Builder(this).setTitle("Delete /"+c.command+"?").setMessage("This removes it from custom prompts on this device.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{all.remove(c);selected.remove(c);saveCustom();refresh();renderChain();updatePrompt();}).show();}
  void saveCustom(){JSONArray a=new JSONArray();for(Cmd c:all)if(c.custom)try{a.put(c.json());}catch(Exception ignored){}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(CUSTOM,a.toString()).apply();}
  void refresh(){rebuildCats();renderList();updateCount();}
  void updateCount(){if(count==null)return;int n=0;for(Cmd c:all)if(c.custom)n++;count.setText(all.size()+" prompt operators • "+n+" custom");}

  void openImport(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,IMPORT_REQ);}
  void openExport(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"PromptDeck-custom.promptdeck.json");startActivityForResult(i,EXPORT_REQ);}
  @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{if(r==IMPORT_REQ)importPack(data.getData());else if(r==EXPORT_REQ)exportPack(data.getData());}catch(Exception e){toast("File error: "+e.getMessage());}}
  void importPack(Uri u)throws Exception{Object parsed=new JSONTokener(readUri(u)).nextValue();JSONArray arr;if(parsed instanceof JSONArray)arr=(JSONArray)parsed;else if(parsed instanceof JSONObject){JSONObject o=(JSONObject)parsed;if(o.has("commands"))arr=o.getJSONArray("commands");else{arr=new JSONArray();arr.put(o);}}else throw new JSONException("Unsupported JSON");int added=0,skip=0;for(int i=0;i<arr.length();i++)try{JSONObject o=arr.getJSONObject(i);o.put("id",nextId());Cmd c=new Cmd(o,true);if(duplicate(c)){skip++;continue;}all.add(c);added++;}catch(Exception e){skip++;}saveCustom();refresh();toast("Imported "+added+" • skipped "+skip);}
  boolean duplicate(Cmd n){for(Cmd c:all)if(c.command.equals(n.command)&&c.instruction.equals(n.instruction))return true;return false;}
  void exportPack(Uri u)throws Exception{JSONObject p=new JSONObject();p.put("format","promptdeck-pack");p.put("version",1);p.put("name","PromptDeck custom prompts");JSONArray a=new JSONArray();for(Cmd c:all)if(c.custom)a.put(c.json());p.put("commands",a);OutputStream out=getContentResolver().openOutputStream(u,"w");if(out==null)throw new IOException("Can't open destination");out.write(p.toString(2).getBytes(StandardCharsets.UTF_8));out.close();toast("Custom prompts exported");}

  void copy(){String s=finalPrompt.getText().toString();if(s.trim().isEmpty()){toast("Nothing to copy");return;}((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PromptDeck prompt",s));toast("Prompt copied");}
  void send(){String s=finalPrompt.getText().toString();if(s.trim().isEmpty()){toast("Build a prompt first");return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,s);i.setPackage("com.openai.chatgpt");try{startActivity(i);}catch(Exception e){Intent g=new Intent(Intent.ACTION_SEND);g.setType("text/plain");g.putExtra(Intent.EXTRA_TEXT,s);startActivity(Intent.createChooser(g,"Send prompt"));}}
  void acceptShared(Intent i){if(i!=null&&need!=null&&Intent.ACTION_SEND.equals(i.getAction())&&"text/plain".equals(i.getType())){String s=i.getStringExtra(Intent.EXTRA_TEXT);if(s!=null&&!s.isEmpty())need.setText(s);}}

  String readAsset(String n)throws IOException{InputStream in=getAssets().open(n);return slurp(in);} String readUri(Uri u)throws IOException{InputStream in=getContentResolver().openInputStream(u);if(in==null)throw new IOException("Can't open file");return slurp(in);} String slurp(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);in.close();return out.toString(StandardCharsets.UTF_8.name());}
  LinearLayout vbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;} LinearLayout hbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  TextView t(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setPadding(dp(5),dp(5),dp(5),dp(5));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;} TextView h(String s){TextView v=t(s,18,true);v.setPadding(dp(2),dp(18),dp(2),dp(7));return v;}
  EditText e(String hint,int lines){EditText x=new EditText(this);x.setHint(hint);x.setMinLines(lines);x.setGravity(Gravity.TOP|Gravity.START);x.setPadding(dp(12),dp(10),dp(12),dp(10));return x;} Button b(String s){Button x=new Button(this);x.setText(s);x.setAllCaps(false);x.setTextSize(12);x.setMinWidth(0);x.setPadding(dp(10),dp(5),dp(10),dp(5));return x;}
  int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);} void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  TextWatcher watcher(final Runnable r){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){}public void afterTextChanged(Editable e){r.run();}};}
}