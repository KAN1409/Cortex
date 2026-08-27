package com.kareem.cortex;

import android.content.Context;
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

/** V5: teacher and student are judged from the exact same deterministic cognitive packet. */
@RunWith(AndroidJUnit4.class)
public class CognitivePacketDifferentialV5Test {
    @Test public void samePacketTeacherStudentDifferential() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        VaultDb vault=new VaultDb(ctx);SQLiteDatabase db=vault.getWritableDatabase();CognitiveSchema.ensure(db);CognitiveAdjudicationStore.ensure(db);
        String question="Given my complete current Cortex state, what is useful now, what belongs together, what changed or became stale, and what should Cortex do next?";
        CognitivePacketBuilder.Packet p=CognitivePacketBuilder.build(vault,question);
        long rowId=CognitiveAdjudicationStore.savePacket(db,p.json);
        JSONObject student=CognitivePacketStudentAdapter.decide(p.json);
        CognitiveDecisionContract.Validation sv=CognitiveDecisionContract.validate(student.toString(),p.validRefs);
        CognitiveAdjudicationStore.saveStudent(db,rowId,student.toString(),sv);

        String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
        File out=new File(ctx.getExternalFilesDir(null),"self-user-test/CognitivePacketDifferentialV5_"+stamp);
        if(!out.mkdirs()&&!out.isDirectory())throw new IllegalStateException("cannot create V5 output");
        write(new File(out,"cognitive_packet.json"),p.json.toString(2));
        write(new File(out,"teacher_prompt.txt"),CognitiveDecisionContract.teacherPrompt(p.json));
        write(new File(out,"student_decision.json"),student.toString(2));
        JSONObject validation=new JSONObject().put("valid",sv.valid()).put("errors",new JSONArray(sv.errors));
        write(new File(out,"student_validation.json"),validation.toString(2));
        JSONObject manifest=new JSONObject()
                .put("schema","CORTEX_COGNITIVE_DIFFERENTIAL_V5")
                .put("generated_at",System.currentTimeMillis())
                .put("adjudication_row_id",rowId)
                .put("packet_id",p.json.optString("packet_id"))
                .put("same_packet",true)
                .put("full_fidelity",true)
                .put("redaction",false)
                .put("teacher_status","pending_chatgpt")
                .put("student_valid",sv.valid())
                .put("purpose","ChatGPT must decide from cognitive_packet.json before evaluating student_decision.json. Differences should be classified as missed link, stale state, wrong lifecycle, missed priority, false priority, missing action, unsafe action, hallucinated fact, or weak explanation.");
        write(new File(out,"README.json"),manifest.toString(2));
        write(new File(out,"README.md"),"# Cortex V5 — Same-Packet Cognitive Differential\n\n`cognitive_packet.json` is the single evidence/state input for both teacher and student. Build the ChatGPT teacher decision from that packet first. Only then inspect `student_decision.json`. This isolates cognitive differences from retrieval/input differences. Full fidelity is intentional; no sensitive-field redaction is performed.\n");
    }

    private static void write(File f,String s)throws Exception{File p=f.getParentFile();if(p!=null&&!p.exists())p.mkdirs();try(FileWriter w=new FileWriter(f)){w.write(s==null?"":s);}}
}
