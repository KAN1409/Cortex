package com.kareem.cortex;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Data, privacy and integration controls only. This is not a feature catalog. */
public class FeatureHubActivity extends Activity {
    static final int REQ_CAL=501,REQ_CONTACTS=502,REQ_RESTORE=503,REQ_BACKUP=504,REQ_FILE=505;
    VaultDb db;LinearLayout body;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);FeatureStore.ensure(db);build();}

    void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(14),dp(20),dp(26));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));TextView h=CortexUi.plain(this,"Data & integrations",27,CortexUi.TEXT);CortexUi.medium(h);head.addView(h,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);
        body.addView(CortexUi.section(this,"Data"));row("Backup & restore","Export or restore your Cortex archive",this::backupRestore);row("Import file","Choose a file from Android Files or Drive",this::pickFile);
        body.addView(CortexUi.section(this,"Privacy"));row("Privacy controls","Choose how each source may be processed or used by cloud AI",this::showPrivacy);row("Notification access","Manage notification capture permission",this::notificationMemory);
        body.addView(CortexUi.section(this,"Integrations"));row("Calendar","Import Android Calendar events",this::syncCalendar);row("Contacts","Import Android contacts",this::syncContacts);
        setContentView(root);CortexUi.fitSystemBars(this,root);}

    void row(String title,String sub,Runnable action){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(2),dp(14),dp(2),dp(14));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);TextView t=CortexUi.plain(this,title,15,CortexUi.TEXT);CortexUi.medium(t);text.addView(t);TextView s=CortexUi.text(this,sub,11,CortexUi.MUTED);s.setPadding(0,dp(3),0,0);text.addView(s);c.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView go=CortexUi.plain(this,"›",25,CortexUi.MUTED);go.setGravity(Gravity.CENTER);c.addView(go,new LinearLayout.LayoutParams(dp(32),dp(44)));CortexUi.pressable(this,c,CortexUi.round(this,android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT,12));c.setOnClickListener(v->action.run());body.addView(c);body.addView(CortexUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));}

    void showPrivacy(){String[] sources={"audio","notifications","images","files","contacts","calendar"};String[] labels=new String[sources.length];for(int i=0;i<sources.length;i++)labels[i]=cap(sources[i])+" — "+PrivacyPolicy.label(PrivacyPolicy.mode(this,sources[i]));new AlertDialog.Builder(this).setTitle("Privacy controls").setItems(labels,(d,w)->choosePrivacy(sources[w])).setNegativeButton("Close",null).show();}
    void choosePrivacy(String source){String[] opts={"AI allowed","Local only","Never collect"};String[] vals={PrivacyPolicy.AI_ALLOWED,PrivacyPolicy.LOCAL_ONLY,PrivacyPolicy.NEVER};new AlertDialog.Builder(this).setTitle(cap(source)).setSingleChoiceItems(opts,index(vals,PrivacyPolicy.mode(this,source)),null).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{AlertDialog a=(AlertDialog)d;int pos=a.getListView().getCheckedItemPosition();if(pos>=0){PrivacyPolicy.set(this,source,vals[pos]);Toast.makeText(this,opts[pos],Toast.LENGTH_SHORT).show();}}).show();}
    int index(String[] x,String v){for(int i=0;i<x.length;i++)if(x[i].equals(v))return i;return 0;}

    void notificationMemory(){String mode=PrivacyPolicy.mode(this,"notifications");new AlertDialog.Builder(this).setTitle("Notification access").setMessage("Current privacy mode: "+PrivacyPolicy.label(mode)).setNegativeButton("Close",null).setNeutralButton("Privacy",(d,w)->showPrivacy()).setPositiveButton("Open Android settings",(d,w)->{try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(Exception e){Toast.makeText(this,"Could not open settings",Toast.LENGTH_LONG).show();}}).show();}

    void backupRestore(){String[] opts={"Export backup","Restore backup"};new AlertDialog.Builder(this).setTitle("Backup & restore").setItems(opts,(d,w)->{if(w==0)startBackup();else startRestore();}).setNegativeButton("Close",null).show();}
    void startBackup(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"Cortex-backup-"+new SimpleDateFormat("yyyy-MM-dd-HHmm",Locale.US).format(new Date())+".zip");startActivityForResult(i,REQ_BACKUP);}
    void startRestore(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");startActivityForResult(i,REQ_RESTORE);}

    void syncCalendar(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},REQ_CAL);return;}doCalendar();}
    void syncContacts(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},REQ_CONTACTS);return;}doContacts();}
    void doCalendar(){Toast.makeText(this,"Importing calendar…",Toast.LENGTH_SHORT).show();new Thread(()->{try{int n=IntegrationEngine.importCalendar(this,db);runOnUiThread(()->Toast.makeText(this,n+" events imported",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Calendar import failed",Toast.LENGTH_LONG).show());}}).start();}
    void doContacts(){Toast.makeText(this,"Importing contacts…",Toast.LENGTH_SHORT).show();new Thread(()->{try{int n=IntegrationEngine.importContacts(this,db);runOnUiThread(()->Toast.makeText(this,n+" contacts imported",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Contacts import failed",Toast.LENGTH_LONG).show());}}).start();}
    void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,REQ_FILE);}

    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(g.length==0||g[0]!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Permission not granted",Toast.LENGTH_SHORT).show();return;}if(req==REQ_CAL)doCalendar();else if(req==REQ_CONTACTS)doContacts();}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();if(req==REQ_BACKUP){Toast.makeText(this,"Creating backup…",Toast.LENGTH_SHORT).show();new Thread(()->{try{int n=BackupExporter.write(this,db,u);runOnUiThread(()->Toast.makeText(this,"Backup complete • "+n+" memories",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Backup failed",Toast.LENGTH_LONG).show());}}).start();}else if(req==REQ_RESTORE){Toast.makeText(this,"Restoring backup…",Toast.LENGTH_SHORT).show();new Thread(()->{try{int n=BackupImporter.restore(this,db,u);runOnUiThread(()->Toast.makeText(this,"Restore complete • "+n+" added",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Restore failed",Toast.LENGTH_LONG).show());}}).start();}else if(req==REQ_FILE){Intent share=new Intent(Intent.ACTION_SEND);String mime=getContentResolver().getType(u);share.setType(mime==null?"application/octet-stream":mime);share.putExtra(Intent.EXTRA_STREAM,u);share.setClipData(ClipData.newUri(getContentResolver(),"Cortex import",u));share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);int n=new ShareImporter(this,db).importIntent(share);if(n>0){Toast.makeText(this,"File imported",Toast.LENGTH_SHORT).show();AnalysisQueue.kick(this,db,()->{});}else Toast.makeText(this,"Could not import file or duplicate",Toast.LENGTH_LONG).show();}}

    String cap(String s){return s==null||s.isEmpty()?"":Character.toUpperCase(s.charAt(0))+s.substring(1);}
}
