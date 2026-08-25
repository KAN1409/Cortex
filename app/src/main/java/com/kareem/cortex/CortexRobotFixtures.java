package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

/** Creates disposable but realistic data for experimental Cortex user journeys. */
public final class CortexRobotFixtures {
    private CortexRobotFixtures(){}

    public static void prepare(Context c)throws Exception{
        CortexExperimentalTestMode.set(c,true);
        c.deleteDatabase(VaultDb.robotDbName());
        resetSchemaReadyForSandbox();
        try(VaultDb db=new VaultDb(c)){
            CognitiveStore.ensure(db);HealthStore.ensure(db);PromptLibraryStore.ensure(db);ScreenshotLearning.ensure(db);VisualInsightStore.ensure(db);
            long text=memory(db,"TEXT","robot_fixture","Meeting follow-up","اتفقنا نراجع العرض يوم الخميس الساعة 3 مساء ونبعت النسخة النهائية قبل الاجتماع.","اتفقنا نراجع العرض يوم الخميس الساعة 3 مساء ونبعت النسخة النهائية قبل الاجتماع.","Work","meeting,followup");
            // Production voice capture is stored as AUDIO. Keep the synthetic fixture on the same
            // contract so the Vault Voice filter tests the app instead of a test-only type mismatch.
            long voice=memory(db,"AUDIO","robot_fixture","Voice note — mixed Arabic/English","Finally now هنجرب Cortex transcription with English وعربي مع بعض.","Finally now هنجرب Cortex transcription with English وعربي مع بعض.","Voice","voice,transcription");
            File img=createImage(c);long image=memory(db,"IMAGE","robot_fixture","Prescription image","Dexamethasone phosphate 8mg/2ml injection","Dexamethasone phosphate 8mg/2ml injection","Health","image,medical");
            ContentValues attach=new ContentValues();attach.put("attachment_path",img.getAbsolutePath());db.getWritableDatabase().update("knowledge_items",attach,"id=?",new String[]{String.valueOf(image)});

            long action=CognitiveStore.addDerived(db,"ACTION","Send revised proposal","Prepare the revised proposal before Thursday meeting.","open",0.94,92,Fingerprint.text("robot-action"),"{\"source\":\"robot_fixture\"}");
            long waiting=CognitiveStore.addDerived(db,"WAITING","Waiting for client feedback","Client feedback is expected before final issue.","open",0.88,78,Fingerprint.text("robot-waiting"),"{\"source\":\"robot_fixture\"}");
            long decision=CognitiveStore.addDerived(db,"DECISION","Choose final material","Compare the two approved material options before issue.","open",0.81,72,Fingerprint.text("robot-decision"),"{\"source\":\"robot_fixture\"}");
            CognitiveStore.linkChecked(db,"memory",text,"derived",action,"supports",0.95,"{\"synthetic\":true}");
            CognitiveStore.linkChecked(db,"memory",text,"derived",waiting,"supports",0.9,"{\"synthetic\":true}");
            CognitiveStore.linkChecked(db,"memory",text,"derived",decision,"supports",0.85,"{\"synthetic\":true}");

            // Match the same identity/project gates used by PeopleProjectsActivity. This makes the
            // fixture test the production query instead of a fake test-only rendering path.
            long person=entity(db,"PERSON","Robot Test Person","person|robot test person","{\"synthetic\":true,\"confirmed\":true,\"identity\":\"phone\"}");
            long project=entity(db,"PROJECT","Robot Test Project","project|robot test project","{\"synthetic\":true,\"created_from\":\"project_candidate\",\"confirmed\":true}");
            CognitiveStore.linkChecked(db,"memory",text,"entity",person,"mentions",0.9,"{\"synthetic\":true}");
            CognitiveStore.linkChecked(db,"memory",text,"entity",project,"project_context",0.9,"{\"synthetic\":true}");

            long now=System.currentTimeMillis();
            HealthStore.addMetric(db,"samsung_health","heart_rate",72,"bpm",now-60_000,now-60_000,"robot-hr","{\"synthetic\":true}");
            HealthStore.addMetric(db,"samsung_health","steps",6842,"count",now-3_600_000,now,"robot-steps","{\"synthetic\":true}");
            HealthStore.addMetric(db,"huawei_health","oxygen_saturation",98,"percent",now-120_000,now-120_000,"robot-spo2","{\"synthetic\":true}");
            HealthStore.addEvidence(db,"health_import","scan","robot-scan","Synthetic lab scan","LDL 205 mg/dL · Cholesterol 277 mg/dL",now-86_400_000,"{\"synthetic\":true}");
            HealthStore.linkKnowledgeEvidence(db,image,"scan","health_import");

            SQLiteDatabase s=db.getWritableDatabase();s.execSQL("UPDATE knowledge_items SET status='analyzed',updated_at=strftime('%s','now')*1000 WHERE source='robot_fixture'");
        }
    }

    public static void cleanup(Context c){
        try{c.deleteDatabase(VaultDb.robotDbName());}catch(Throwable ignored){}
        try{File f=new File(c.getFilesDir(),"robot_fixture.png");if(f.exists())f.delete();}catch(Throwable ignored){}
        CortexExperimentalTestMode.set(c,false);
        resetSchemaReadyForSandbox();
    }

    private static long memory(VaultDb db,String type,String source,String title,String raw,String extracted,String category,String tags){long id=db.insert(type,source,title,raw,category,tags,"",Fingerprint.text("robot|"+type+"|"+title),"{\"synthetic\":true,\"robot_test\":true}");if(id<0)id=-id;ContentValues v=new ContentValues();v.put("extracted_text",extracted);v.put("summary",extracted);v.put("status","analyzed");v.put("updated_at",System.currentTimeMillis());db.getWritableDatabase().update("knowledge_items",v,"id=?",new String[]{String.valueOf(id)});try{SemanticIndex.indexItem(db,id);}catch(Throwable ignored){}return id;}
    private static long entity(VaultDb db,String kind,String name,String key,String metadata){ContentValues v=new ContentValues();long now=System.currentTimeMillis();v.put("kind",kind);v.put("canonical_name",name);v.put("normalized_key",key);v.put("status","active");v.put("metadata_json",metadata);v.put("created_at",now);v.put("updated_at",now);return db.getWritableDatabase().insert("entity_nodes",null,v);}

    private static File createImage(Context c)throws Exception{File f=new File(c.getFilesDir(),"robot_fixture.png");Bitmap b=Bitmap.createBitmap(720,420,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(Color.rgb(244,244,240));Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(25,25,25));p.setTextSize(34);canvas.drawText("DEXAMETHASONE",44,100,p);p.setTextSize(28);canvas.drawText("8 mg / 2 ml",44,160,p);canvas.drawText("Robot test prescription fixture",44,230,p);try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();return f;}

    private static void resetSchemaReadyForSandbox(){try{Field f=CognitiveSchema.class.getDeclaredField("ready");f.setAccessible(true);f.setBoolean(null,false);}catch(Throwable ignored){}}
}
