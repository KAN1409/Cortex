package com.kareem.cortex;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class FeatureHubActivity extends Activity {
    static final int REQ_CAL=501,REQ_CONTACTS=502,REQ_RESTORE=503,REQ_BACKUP=504,REQ_FILE=505;
    VaultDb db;LinearLayout feed;TextView status;
    int bg=Color.rgb(16,17,20),panel=Color.rgb(24,26,31),text=Color.rgb(243,244,246),muted=Color.rgb(165,168,176),accent=Color.rgb(143,169,255),ok=Color.rgb(143,220,170);
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}void pad(View v,int x){v.setPadding(dp(x),dp(x),dp(x),dp(x));}
    TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setTextIsSelectable(true);return v;}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setTextColor(text);b.setBackgroundColor(panel);return b;}

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new VaultDb(this);FeatureStore.ensure(db);build();refresh();}
    @Override protected void onResume(){super.onResume();if(db!=null)refresh();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);pad(root,16);
        TextView title=tv("CORTEX",28,text);title.setTypeface(null,1);root.addView(title);root.addView(tv("Your second brain • capture → understand → recall → follow up",14,muted));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setPadding(0,dp(12),0,0);Button brain=button("BRAIN"),capture=button("CAPTURE"),inbox=button("INBOX"),brief=button("BRIEF");addEq(top,brain,0);addEq(top,capture,6);addEq(top,inbox,6);addEq(top,brief,6);root.addView(top);
        status=tv("",12,muted);status.setPadding(0,dp(10),0,dp(8));root.addView(status);
        ScrollView sv=new ScrollView(this);feed=new LinearLayout(this);feed.setOrientation(LinearLayout.VERTICAL);sv.addView(feed);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        brain.setOnClickListener(v->startActivity(new Intent(this,BrainActivity.class)));capture.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));inbox.setOnClickListener(v->showInbox());brief.setOnClickListener(v->showBriefChoice());
    }
    void addEq(LinearLayout r,View v,int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(m),0,0,0);r.addView(v,p);}

    void refresh(){
        int items=db.lexicalSearch("",10000).size(),loops=SecondBrainEngine.openLoops(db,1000).size(),inbox=FeatureStore.inbox(db,200).size();status.setText(items+" memories  •  "+loops+" open loops  •  "+inbox+" inbox");feed.removeAllViews();
        section("20-FEATURE CORTEX");
        addFeature(1,"Universal Capture","Text, links, files, screenshots, images, audio, Android share sheet and imports.",()->startActivity(new Intent(this,MainActivity.class)));
        addFeature(2,"Automatic Understanding","OCR, transcription, summaries, tags, entities, actions and semantic indexing.",()->startActivity(new Intent(this,MainActivity.class)));
        addFeature(3,"Ask Cortex","Grounded answers from your saved memory with source memories.",()->startActivity(new Intent(this,BrainActivity.class)));
        addFeature(4,"Follow-up Brain / Needs Attention","Open loops, failed items, waiting states and resurfacing.",()->startActivity(new Intent(this,BrainActivity.class)));
        addFeature(5,"Reminders & Dates","Schedule reminders from open loops and keep the source memory attached.",()->startActivity(new Intent(this,BrainActivity.class)));
        addFeature(6,"Timeline / Daily Memory","Chronological memory timeline across all captured sources.",()->startActivity(new Intent(this,BrainActivity.class)));
        addFeature(7,"Notification Memory","Collect allowed Android notifications into Cortex with sensitive tagging.",this::notificationMemory);
        addFeature(8,"People & Relationship Memory","People graph built from contacts/entities and related memories.",()->startActivity(new Intent(this,BrainActivity.class)));
        addFeature(9,"Projects & Context Packs","Project/topic context grouping and related memory packs.",()->startActivity(new Intent(this,MainActivity.class)));
        addFeature(10,"Prompt / AI Result Library","Store prompt + input + result + rating bundles.",()->startActivity(new Intent(this,MainActivity.class)));
        addFeature(11,"Screenshot & Image Intelligence","Local OCR and structured vision fields for shared images.",()->startActivity(new Intent(this,MainActivity.class)));
        addFeature(12,"Memory Connections","Semantic related memories, graph nodes and explicit relations.",()->startActivity(new Intent(this,BrainActivity.class)));
        addFeature(13,"Smart Inbox","Auto-bucket new memories into Action, Project, Person, Decision, Waiting or Reference.",this::showInbox);
        addFeature(14,"Memory-like Search","Hybrid lexical + semantic recall without needing exact wording.",()->startActivity(new Intent(this,MainActivity.class)));
        addFeature(15,"Daily / Weekly Brief","What happened, what needs attention and what is still open.",this::showBriefChoice);
        addFeature(16,"Corrections = Learning","Correct stored text and optionally teach Cortex exact future display replacements.",this::showCorrections);
        addFeature(17,"Local-first Privacy Controls","Choose AI allowed, local only or never collect by source.",this::showPrivacy);
        addFeature(18,"Backup / Restore / Export","Portable ZIP backup with database, JSONL and attachments, plus restore.",this::backupRestore);
        addFeature(19,"Integrations","Android Calendar, Contacts, Drive/file picker and share-sheet ingestion.",this::showIntegrations);
        addFeature(20,"Proactive Cortex","Resurface forgotten items and open loops with proactive notifications.",()->startActivity(new Intent(this,BrainActivity.class)));
    }

    void section(String s){TextView h=tv(s,11,accent);h.setTypeface(null,1);h.setPadding(0,dp(10),0,dp(7));feed.addView(h);}
    void addFeature(int n,String title,String body,Runnable action){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackgroundResource(R.drawable.rounded_panel);pad(c,12);c.setClickable(true);c.setOnClickListener(v->action.run());TextView t=tv(n+". "+title,15,text);t.setTypeface(null,1);c.addView(t);TextView b=tv(body,12,muted);b.setPadding(0,dp(4),0,0);c.addView(b);TextView live=tv("● LIVE",10,ok);live.setPadding(0,dp(6),0,0);c.addView(live);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));feed.addView(c,p);}

    void showInbox(){
        ArrayList<FeatureStore.InboxEntry> xs=FeatureStore.inbox(db,100);ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);pad(box,14);sv.addView(box);
        if(xs.isEmpty())box.addView(tv("Inbox clear. New captures will appear here until reviewed.",14,muted));
        for(FeatureStore.InboxEntry e:xs){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackgroundResource(R.drawable.rounded_panel);pad(c,11);TextView t=tv((e.pinned?"★ ":"")+e.item.title,14,text);t.setTypeface(null,1);c.addView(t);c.addView(tv(e.bucket+"  •  "+e.item.category+"  •  "+fmt(e.item.createdAt),11,muted));String p=!empty(e.item.summary)?e.item.summary:(!empty(e.item.extractedText)?e.item.extractedText:e.item.rawText);if(p==null)p="";if(p.length()>260)p=p.substring(0,260)+"…";c.addView(tv(LanguageBlockFormatter.format(p),12,text));LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);Button review=button("REVIEWED"),pin=button(e.pinned?"UNPIN":"PIN");r.addView(review,new LinearLayout.LayoutParams(0,dp(40),1));LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(0,dp(40),1);q.setMargins(dp(6),0,0,0);r.addView(pin,q);review.setOnClickListener(v->{FeatureStore.review(db,e.item.id,true);Toast.makeText(this,"Removed from inbox",Toast.LENGTH_SHORT).show();showInbox();});pin.setOnClickListener(v->{FeatureStore.pin(db,e.item.id,!e.pinned);Toast.makeText(this,e.pinned?"Unpinned":"Pinned",Toast.LENGTH_SHORT).show();showInbox();});c.addView(r);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(8));box.addView(c,cp);}
        new AlertDialog.Builder(this).setTitle("Smart Inbox • "+xs.size()).setView(sv).setNegativeButton("Close",null).show();
    }

    void showBriefChoice(){String[] x={"Daily brief — last 24 hours","Weekly brief — last 7 days"};new AlertDialog.Builder(this).setTitle("Cortex Brief").setItems(x,(d,w)->showBrief(w==0?BriefEngine.daily(db):BriefEngine.weekly(db))).setNegativeButton("Close",null).show();}
    void showBrief(String body){TextView t=tv(LanguageBlockFormatter.format(body),14,text);t.setPadding(dp(14),dp(8),dp(14),dp(8));t.setTextIsSelectable(true);new AlertDialog.Builder(this).setTitle("Memory Brief").setView(t).setNegativeButton("Close",null).setPositiveButton("COPY",(d,w)->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Cortex brief",body));Toast.makeText(this,"Brief copied",Toast.LENGTH_SHORT).show();}).show();}

    void showCorrections(){ArrayList<KnowledgeItem> items=db.lexicalSearch("",80);if(items.isEmpty()){Toast.makeText(this,"No memories to correct yet",Toast.LENGTH_SHORT).show();return;}String[] labels=new String[items.size()];for(int i=0;i<items.size();i++)labels[i]=items.get(i).title+"  •  "+fmt(items.get(i).createdAt);new AlertDialog.Builder(this).setTitle("Choose memory to correct").setItems(labels,(d,w)->chooseCorrectionField(items.get(w))).setNegativeButton("Close",null).show();}
    void chooseCorrectionField(KnowledgeItem k){String[] fields={"Extracted / transcript text","Summary","Title"};new AlertDialog.Builder(this).setTitle(k.title).setItems(fields,(d,w)->editCorrection(k,w==0?"extracted_text":w==1?"summary":"title")).setNegativeButton("Cancel",null).show();}
    void editCorrection(KnowledgeItem k,String field){String original="title".equals(field)?k.title:"summary".equals(field)?k.summary:k.extractedText;EditText e=new EditText(this);e.setText(original);e.setMinLines(7);e.setGravity(Gravity.TOP);CheckBox learn=new CheckBox(this);learn.setText("Learn this exact replacement for future display text");learn.setChecked("extracted_text".equals(field));LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);pad(box,14);box.addView(e);box.addView(learn);AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Correct "+field.replace('_',' ')).setView(box).setNegativeButton("Cancel",null).setPositiveButton("SAVE",null).create();dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String corrected=e.getText().toString().trim();if(corrected.isEmpty()){e.setError("Cannot be empty");return;}android.content.ContentValues cv=new android.content.ContentValues();cv.put(field,corrected);cv.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("knowledge_items",cv,"id=?",new String[]{String.valueOf(k.id)});FeatureStore.saveCorrection(db,k.id,field,original,corrected,learn.isChecked());try{SemanticIndex.indexItem(db,k.id);}catch(Exception ignored){}dlg.dismiss();Toast.makeText(this,"Correction saved and indexed",Toast.LENGTH_SHORT).show();refresh();}));dlg.show();}

    void showPrivacy(){String[] sources={"audio","notifications","images","files"};String[] labels=new String[sources.length];for(int i=0;i<sources.length;i++)labels[i]=cap(sources[i])+" — "+PrivacyPolicy.label(PrivacyPolicy.mode(this,sources[i]));new AlertDialog.Builder(this).setTitle("Privacy controls").setMessage("AI allowed = cloud processing may be used. Local only = keep processing on-device. Never collect = ignore that source where supported.").setItems(labels,(d,w)->choosePrivacy(sources[w])).setNegativeButton("Close",null).show();}
    void choosePrivacy(String source){String[] opts={"AI allowed","Local only","Never collect"};String[] vals={PrivacyPolicy.AI_ALLOWED,PrivacyPolicy.LOCAL_ONLY,PrivacyPolicy.NEVER};new AlertDialog.Builder(this).setTitle(cap(source)).setSingleChoiceItems(opts,index(vals,PrivacyPolicy.mode(this,source)),null).setNegativeButton("Cancel",null).setPositiveButton("SAVE",(d,w)->{AlertDialog a=(AlertDialog)d;int pos=a.getListView().getCheckedItemPosition();if(pos>=0){PrivacyPolicy.set(this,source,vals[pos]);Toast.makeText(this,cap(source)+": "+opts[pos],Toast.LENGTH_SHORT).show();}}).show();}
    int index(String[] x,String v){for(int i=0;i<x.length;i++)if(x[i].equals(v))return i;return 0;}

    void notificationMemory(){String mode=PrivacyPolicy.mode(this,"notifications");String msg="Privacy: "+PrivacyPolicy.label(mode)+"\n\nAndroid requires you to enable Notification access for Cortex. Cortex ignores its own notifications and tags likely OTP/verification content as sensitive/local-only.";new AlertDialog.Builder(this).setTitle("Notification Memory").setMessage(msg).setNegativeButton("Close",null).setNeutralButton("Privacy",(d,w)->showPrivacy()).setPositiveButton("OPEN NOTIFICATION ACCESS",(d,w)->{try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(Exception e){Toast.makeText(this,"Could not open settings",Toast.LENGTH_LONG).show();}}).show();}

    void backupRestore(){String[] opts={"Export Cortex backup ZIP","Restore Cortex backup ZIP"};new AlertDialog.Builder(this).setTitle("Backup / Restore").setItems(opts,(d,w)->{if(w==0)startBackup();else startRestore();}).setNegativeButton("Close",null).show();}
    void startBackup(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"Cortex-backup-"+new SimpleDateFormat("yyyy-MM-dd-HHmm",Locale.US).format(new Date())+".zip");startActivityForResult(i,REQ_BACKUP);}
    void startRestore(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");startActivityForResult(i,REQ_RESTORE);}

    void showIntegrations(){String[] opts={"Sync Android Calendar","Sync Android Contacts","Import file from Drive / Files","How Gmail / apps connect"};new AlertDialog.Builder(this).setTitle("Integrations").setMessage("Calendar and Contacts are explicit one-tap local imports. Drive/Files uses Android's document picker. Gmail and other apps can share directly to Cortex through the share sheet.").setItems(opts,(d,w)->{if(w==0)syncCalendar();else if(w==1)syncContacts();else if(w==2)pickFile();else new AlertDialog.Builder(this).setTitle("Gmail and other apps").setMessage("Open the item in Gmail, Drive, browser, gallery or any app → Share → Cortex. The original text/file is copied into Cortex and analyzed/indexed locally where possible.").setPositiveButton("OK",null).show();}).setNegativeButton("Close",null).show();}
    void syncCalendar(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},REQ_CAL);return;}doCalendar();}
    void syncContacts(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},REQ_CONTACTS);return;}doContacts();}
    void doCalendar(){Toast.makeText(this,"Importing calendar…",Toast.LENGTH_SHORT).show();new Thread(()->{try{int n=IntegrationEngine.importCalendar(this,db);runOnUiThread(()->{Toast.makeText(this,n+" calendar events imported",Toast.LENGTH_LONG).show();refresh();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Calendar import failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();}
    void doContacts(){Toast.makeText(this,"Importing contacts…",Toast.LENGTH_SHORT).show();new Thread(()->{try{int n=IntegrationEngine.importContacts(this,db);runOnUiThread(()->{Toast.makeText(this,n+" contacts imported",Toast.LENGTH_LONG).show();refresh();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Contacts import failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();}
    void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,REQ_FILE);}

    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(g.length==0||g[0]!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Permission not granted",Toast.LENGTH_SHORT).show();return;}if(req==REQ_CAL)doCalendar();else if(req==REQ_CONTACTS)doContacts();}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(req==REQ_BACKUP){Toast.makeText(this,"Creating backup…",Toast.LENGTH_SHORT).show();new Thread(()->{try{int n=BackupExporter.write(this,db,u);runOnUiThread(()->Toast.makeText(this,"Backup complete • "+n+" memories",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Backup failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();}else if(req==REQ_RESTORE){Toast.makeText(this,"Restoring backup…",Toast.LENGTH_SHORT).show();new Thread(()->{try{int n=BackupImporter.restore(this,db,u);runOnUiThread(()->{Toast.makeText(this,"Restore complete • "+n+" memories added",Toast.LENGTH_LONG).show();refresh();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Restore failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();}else if(req==REQ_FILE){Intent share=new Intent(Intent.ACTION_SEND);share.setType(getContentResolver().getType(u)==null?"application/octet-stream":getContentResolver().getType(u));share.putExtra(Intent.EXTRA_STREAM,u);share.setClipData(ClipData.newUri(getContentResolver(),"Cortex import",u));share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);int n=new ShareImporter(this,db).importIntent(share);if(n>0){Toast.makeText(this,"File imported to Cortex",Toast.LENGTH_SHORT).show();AnalysisQueue.kick(this,db,()->runOnUiThread(this::refresh));}else Toast.makeText(this,"Could not import file or duplicate",Toast.LENGTH_LONG).show();}}

    String cap(String s){return s==null||s.isEmpty()?"":Character.toUpperCase(s.charAt(0))+s.substring(1);}boolean empty(String s){return s==null||s.trim().isEmpty();}String fmt(long ms){return new SimpleDateFormat("dd MMM • HH:mm",Locale.getDefault()).format(new Date(ms));}
}
