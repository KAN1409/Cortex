package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;

public class GeminiSettingsActivity extends Activity {
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);show();}
    private void show(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(16),dp(22),dp(10));
        TextView note=new TextView(this);note.setText("Gemini is the automatic Deep Brain that re-ranks meaningful Cortex Situations and proposes grounded next actions. If Gemini is blocked, Pulse can still show local detections, but the model-ranked priority layer is missing. The API key is encrypted with Android Keystore.");note.setTextSize(15);box.addView(note);
        EditText keyField=new EditText(this);keyField.setHint("Paste Gemini API key");keyField.setSingleLine(true);keyField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(keyField);
        EditText modelField=new EditText(this);modelField.setHint("Brain generation model");modelField.setSingleLine(true);modelField.setText(GeminiModelConfig.generationModel(this));modelField.setSelection(modelField.length());box.addView(modelField);
        CheckBox autoBrain=new CheckBox(this);autoBrain.setText("Use Gemini as automatic Cortex brain");autoBrain.setChecked(CognitiveAutoReasoningSettingsV4.enabled(this));box.addView(autoBrain);
        TextView autoNote=new TextView(this);autoNote.setText("Only meaningful fresh deadlines, risks, commitments, waiting states, events, or high-attention changes trigger normal reasoning. After a successful real transport test, Cortex also requests one current-Pulse Gemini baseline so older/manual Deep Brain history cannot leave today's screen permanently unranked by Gemini. Only cloud-eligible Cortex context is included.");autoNote.setTextSize(12);autoNote.setPadding(0,dp(2),0,dp(8));box.addView(autoNote);
        TextView health=new TextView(this);health.setText(GeminiKeyStore.has(this)?"Deep Brain transport: not tested yet":"Deep Brain transport: BLOCKED · API key not configured");health.setTextSize(12);health.setPadding(0,dp(6),0,dp(6));box.addView(health);
        Button test=new Button(this);test.setText("Run real Gemini Deep Brain test");test.setAllCaps(false);box.addView(test);
        boolean configured=GeminiKeyStore.has(this);
        test.setEnabled(configured);test.setOnClickListener(v->{test.setEnabled(false);test.setText("Testing production Gemini route…");health.setText("Sending a synthetic structured-output request. The health request itself contains no personal Cortex data.");new Thread(()->{GeminiCognitiveReasoningProviderV4.HealthReport r=GeminiCognitiveReasoningProviderV4.healthCheck(getApplicationContext());try{VaultDb db=new VaultDb(getApplicationContext());if(r.ok)DiagnosticsLog.info(db,"GeminiDeepBrain","health_check","pass",0,0,0,0,0,r.latencyMs,new org.json.JSONObject().put("model",r.model).put("http_code",r.httpCode));else DiagnosticsLog.warn(db,"GeminiDeepBrain","health_check","fail","GEMINI_DEEP_BRAIN_HEALTH",0,0,0,0,0,new org.json.JSONObject().put("model",r.model).put("http_code",r.httpCode).put("status",r.status));db.close();}catch(Throwable ignored){}runOnUiThread(()->{if(isFinishing()||isDestroyed())return;String suffix="";if(r.ok){CognitiveAutoReasoningSettingsV4.markProviderHealthy(this);if(autoBrain.isChecked()){CognitiveReasoningOrchestratorV4.scheduleBaseline(this,"gemini_health_check_passed");suffix="\nCurrent-Pulse re-rank: "+CognitiveAutoReasoningSettingsV4.lastPipelineState(this)+" · "+CognitiveAutoReasoningSettingsV4.lastPipelineReason(this);}else{CognitiveAutoReasoningSettingsV4.markPipeline(this,"READY","health_pass_auto_unchecked","gemini_health_check_passed");suffix="\nAutomatic brain is unchecked; transport is healthy but no ranking pass was requested.";}}health.setText(r.human()+suffix);test.setText("Run real Gemini Deep Brain test again");test.setEnabled(true);});},"CortexGeminiHealth").start();});
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(configured?"Automatic Deep Brain · Gemini":"Enable automatic Deep Brain").setView(box).setCancelable(false)
                .setNegativeButton("Close",(d,w)->finish()).setPositiveButton("Save settings",null);
        if(configured)builder.setNeutralButton("Remove key",null);
        AlertDialog d=builder.create();
        d.setOnShowListener(v->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
                String key=keyField.getText().toString().trim(),model=modelField.getText().toString().trim();
                if(!configured&&key.isEmpty()){keyField.setError("API key required");return;}
                if(!GeminiModelConfig.setGenerationModel(this,model)){modelField.setError("Use a valid Gemini model id");return;}
                try{if(!key.isEmpty())GeminiKeyStore.save(this,key);CognitiveAutoReasoningSettingsV4.setEnabled(this,autoBrain.isChecked());if(GeminiKeyStore.has(this)){VisualIntelligenceScheduler.kick(this);if(autoBrain.isChecked())CognitiveReasoningOrchestratorV4.schedule(this,"settings_enabled");}Toast.makeText(this,"Gemini Deep Brain settings saved",Toast.LENGTH_SHORT).show();d.dismiss();finish();}
                catch(Exception ex){Toast.makeText(this,"Could not save key: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
            });
            if(configured)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{GeminiKeyStore.clear(this);CognitiveAutoReasoningSettingsV4.markPipeline(this,"BLOCKED","gemini_key_removed","settings");Toast.makeText(this,"Gemini key removed",Toast.LENGTH_SHORT).show();d.dismiss();finish();});
        });
        d.show();
    }
}
