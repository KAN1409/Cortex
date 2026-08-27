package com.kareem.cortex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** V3 exports the same cognitive world Cortex sees, then records student answers for teacher adjudication. */
@RunWith(AndroidJUnit4.class)
public class TeacherStudentCognitiveDifferentialTest {
  private Context ctx; private VaultDb vault; private File out;
  private static final String[] TABLES={"knowledge_items","entities","actions","relations","raw_signals","signal_threads","derived_items","entity_nodes","entity_aliases","source_links","feedback_events","relevance_evaluations","ai_jobs","model_runs"};

  @Test public void teacherStudentCognitiveDifferential() throws Exception {
    ctx=InstrumentationRegistry.getInstrumentation().getTargetContext(); vault=new VaultDb(ctx); CognitiveSchema.ensure(vault.getWritableDatabase());
    String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
    out=new File(ctx.getExternalFilesDir(null),"self-user-test/TeacherStudentCognitiveDifferential_"+stamp); if(!out.mkdirs()&&!out.isDirectory())throw new IllegalStateException("cannot create V3 output");
    exportDatabase(); runStudentCases(); writeManifest();
  }

  private void exportDatabase() throws Exception { SQLiteDatabase db=vault.getReadableDatabase(); for(String t:TABLES) exportTable(db,t); }
  private void exportTable(SQLiteDatabase db,String table) throws Exception {
    File f=new File(out,table+".jsonl"); try(FileWriter w=new FileWriter(f)){Cursor c=null;try{c=db.rawQuery("SELECT * FROM "+table,null);String[] cols=c.getColumnNames();while(c.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<cols.length;i++){String k=cols[i];if(secretField(k)){o.put(k,"[REDACTED]");continue;}switch(c.getType(i)){case Cursor.FIELD_TYPE_INTEGER:o.put(k,c.getLong(i));break;case Cursor.FIELD_TYPE_FLOAT:o.put(k,c.getDouble(i));break;case Cursor.FIELD_TYPE_BLOB:o.put(k,"[BLOB "+c.getBlob(i).length+" bytes]");break;case Cursor.FIELD_TYPE_NULL:o.put(k,JSONObject.NULL);break;default:o.put(k,redact(c.getString(i)));}}w.write(o.toString());w.write('\n');}}finally{if(c!=null)c.close();}}
  }
  private boolean secretField(String k){String x=k==null?"":k.toLowerCase(Locale.US);return x.contains("api_key")||x.contains("token")||x.contains("password")||x.contains("secret")||x.contains("authorization");}
  private String redact(String s){if(s==null)return null;String x=s.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._-]+","$1[REDACTED]");x=x.replaceAll("(?i)(api[_ -]?key[\\\"':= ]+)[A-Za-z0-9._-]+","$1[REDACTED]");return x;}

  private void runStudentCases() throws Exception {
    JSONArray cases=new JSONArray(); String[] qs={
      "What needs my attention right now? Prioritize situations, not memories, and recommend the best next move.",
      "What ongoing situations or episodes are visible in my data? Group related evidence together.",
      "What work or project threads are still open, even if Cortex has not confirmed a project name?",
      "What upcoming deadlines, appointments, or reminders matter most and why?",
      "Which retrieved items are probably noise or context rather than actions?"
    };
    for(String q:qs){JSONObject o=new JSONObject();o.put("question",q);long start=System.currentTimeMillis();try{LocalAskRouter.Result r=LocalAskRouter.ask(ctx,vault,q);o.put("job_id",r.jobId).put("provider",r.provider).put("source_mode",r.sourceMode).put("answer",r.answer==null?"":r.answer).put("grounded_draft",r.grounded==null?"":r.grounded.answer).put("duration_ms",System.currentTimeMillis()-start);JSONArray src=new JSONArray();if(r.grounded!=null)for(SemanticHit h:r.grounded.sources){JSONObject s=new JSONObject();s.put("item_id",h.item==null?0:h.item.id).put("score",h.score);if(h.item!=null)s.put("title",h.item.title).put("type",h.item.type).put("source",h.item.source);src.put(s);}o.put("retrieved_evidence",src);JSONArray loops=new JSONArray();if(r.grounded!=null)for(String x:r.grounded.openLoops)loops.put(x);o.put("open_loops",loops);JSONArray dec=new JSONArray();if(r.grounded!=null)for(String x:r.grounded.decisions)dec.put(x);o.put("decisions",dec);}catch(Throwable t){o.put("error",t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));}cases.put(o);}
    write(new File(out,"student_cases.json"),new JSONObject().put("schema","CORTEX_TEACHER_STUDENT_V3").put("cases",cases).toString(2));
  }
  private void writeManifest() throws Exception {JSONObject m=new JSONObject().put("schema","CORTEX_TEACHER_STUDENT_COGNITIVE_DIFFERENTIAL_V3").put("generated_at",System.currentTimeMillis()).put("purpose","Export the cognitive world and Cortex student outputs so ChatGPT can independently construct teacher situations and compare retrieval, linking, temporal reasoning, suppression, prioritization and synthesis.").put("tables",new JSONArray(TABLES));write(new File(out,"README.json"),m.toString(2));write(new File(out,"README.md"),"# Cortex V3 — Teacher/Student Cognitive Differential\n\nThis folder contains the same structured cognitive data available to Cortex plus fixed student runs. Send the combined ZIP to ChatGPT. ChatGPT should independently build a teacher world model from the exported evidence first, then compare it against student_cases.json. Secrets are redacted and binary embeddings are not exported.\n");}
  private static void write(File f,String s)throws Exception{try(FileWriter w=new FileWriter(f)){w.write(s==null?"":s);}}
}
