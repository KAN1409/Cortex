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
        TextView note=new TextView(this);note.setText("Gemini powers configured cloud routes. The API key is encrypted with Android Keystore. Brain's External/Combined generation model can be changed at runtime without rebuilding Cortex.");note.setTextSize(15);box.addView(note);
        EditText keyField=new EditText(this);keyField.setHint("Paste Gemini API key");keyField.setSingleLine(true);keyField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(keyField);
        EditText modelField=new EditText(this);modelField.setHint("Brain generation model");modelField.setSingleLine(true);modelField.setText(GeminiModelConfig.generationModel(this));modelField.setSelection(modelField.length());box.addView(modelField);
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
                try{if(!key.isEmpty())GeminiKeyStore.save(this,key);Toast.makeText(this,"Gemini settings saved",Toast.LENGTH_SHORT).show();d.dismiss();finish();}
                catch(Exception ex){Toast.makeText(this,"Could not save key: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
            });
            if(configured)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{GeminiKeyStore.clear(this);Toast.makeText(this,"Gemini key removed",Toast.LENGTH_SHORT).show();d.dismiss();finish();});
        });
        d.show();
    }
}
