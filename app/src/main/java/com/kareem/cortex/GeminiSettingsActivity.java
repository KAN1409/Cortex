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
        TextView note=new TextView(this);note.setText("Gemini is a cloud model inside Cortex. It can power External/Combined answers and, when enabled below, act as the automatic Deep Brain for meaningful new Situations. Cortex still validates every returned ID/state/action before applying anything. The API key is encrypted with Android Keystore.");note.setTextSize(15);box.addView(note);
        EditText keyField=new EditText(this);keyField.setHint("Paste Gemini API key");keyField.setSingleLine(true);keyField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(keyField);
        EditText modelField=new EditText(this);modelField.setHint("Brain generation model");modelField.setSingleLine(true);modelField.setText(GeminiModelConfig.generationModel(this));modelField.setSelection(modelField.length());box.addView(modelField);
        CheckBox autoBrain=new CheckBox(this);autoBrain.setText("Use Gemini as automatic Cortex brain");autoBrain.setChecked(CognitiveAutoReasoningSettingsV4.enabled(this));box.addView(autoBrain);
        TextView autoNote=new TextView(this);autoNote.setText("Only meaningful fresh deadlines, risks, commitments, waiting states, events, or high-attention changes can trigger it. Work is coalesced safely, rate-limited, budgeted, and backed off after failures.");autoNote.setTextSize(12);autoNote.setPadding(0,dp(2),0,0);box.addView(autoNote);
        boolean configured=GeminiKeyStore.has(this);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(configured?"Gemini settings":"Enable Gemini").setView(box).setCancelable(false)
                .setNegativeButton("Close",(d,w)->finish()).setPositiveButton("Save settings",null);
        if(configured)builder.setNeutralButton("Remove key",null);
        AlertDialog d=builder.create();
        d.setOnShowListener(v->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
                String key=keyField.getText().toString().trim(),model=modelField.getText().toString().trim();
                if(!configured&&key.isEmpty()){keyField.setError("API key required");return;}
                if(!GeminiModelConfig.setGenerationModel(this,model)){modelField.setError("Use a valid Gemini model id");return;}
                try{if(!key.isEmpty())GeminiKeyStore.save(this,key);CognitiveAutoReasoningSettingsV4.setEnabled(this,autoBrain.isChecked());if(GeminiKeyStore.has(this)){VisualIntelligenceScheduler.kick(this);if(autoBrain.isChecked())CognitiveReasoningOrchestratorV4.schedule(this,"settings_enabled");}Toast.makeText(this,"Gemini settings saved",Toast.LENGTH_SHORT).show();d.dismiss();finish();}
                catch(Exception ex){Toast.makeText(this,"Could not save key: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
            });
            if(configured)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{GeminiKeyStore.clear(this);Toast.makeText(this,"Gemini key removed",Toast.LENGTH_SHORT).show();d.dismiss();finish();});
        });
        d.show();
    }
}
