package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;

/** Runtime setup for Cortex's OpenRouter reasoning provider. */
public class OpenRouterSettingsActivity extends Activity {
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);show();}

    private void show(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(16),dp(22),dp(10));
        TextView note=new TextView(this);note.setText("OpenRouter is one Cortex Brain reasoning route. The default uses OpenRouter's free router instead of pinning Cortex to a temporary model id. Gemini and the local brain remain available according to routing and evidence policy. The API key is encrypted with Android Keystore, and a custom model can be selected without rebuilding Cortex.");note.setTextSize(15);box.addView(note);
        EditText keyField=new EditText(this);keyField.setHint("Paste OpenRouter API key");keyField.setSingleLine(true);keyField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(keyField);
        EditText modelField=new EditText(this);modelField.setHint("OpenRouter model id");modelField.setSingleLine(true);modelField.setText(OpenRouterModelConfig.generationModel(this));modelField.setSelection(modelField.length());box.addView(modelField);
        TextView modelNote=new TextView(this);modelNote.setText("Default: openrouter/free");modelNote.setTextSize(12);box.addView(modelNote);
        boolean configured=OpenRouterKeyStore.has(this);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(configured?"OpenRouter Brain settings":"Enable OpenRouter").setView(box).setCancelable(false).setNegativeButton("Close",(d,w)->finish()).setPositiveButton("Save settings",null);if(configured)builder.setNeutralButton("Remove key",null);AlertDialog d=builder.create();
        d.setOnShowListener(v->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String key=keyField.getText().toString().trim(),model=modelField.getText().toString().trim();if(!configured&&key.isEmpty()){keyField.setError("OpenRouter API key required");return;}if(!OpenRouterModelConfig.setGenerationModel(this,model)){modelField.setError("Use a valid OpenRouter model id, e.g. openrouter/free");return;}try{if(!key.isEmpty())OpenRouterKeyStore.save(this,key);Toast.makeText(this,"OpenRouter Brain settings saved",Toast.LENGTH_SHORT).show();d.dismiss();finish();}catch(Exception ex){Toast.makeText(this,"Could not save key: "+ex.getMessage(),Toast.LENGTH_LONG).show();}});if(configured)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{OpenRouterKeyStore.clear(this);Toast.makeText(this,"OpenRouter key removed",Toast.LENGTH_SHORT).show();d.dismiss();finish();});});d.show();
    }
}
