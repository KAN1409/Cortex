package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;

public class GeminiSettingsActivity extends Activity {
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);show();}
    private void show(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(16),dp(22),dp(10));
        TextView note=new TextView(this);note.setText("Gemini 2.5 Flash joins the Groq ASR benchmark for Egyptian Arabic + English code-switching. The key is encrypted with Android Keystore.");note.setTextSize(16);box.addView(note);
        EditText e=new EditText(this);e.setHint("Paste Gemini API key");e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(e);
        boolean configured=GeminiKeyStore.has(this);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(configured?"Gemini transcription key":"Enable Gemini transcription").setView(box).setCancelable(false)
                .setNegativeButton("Close",(d,w)->finish()).setPositiveButton(configured?"Replace key":"Save key",null);
        if(configured)builder.setNeutralButton("Remove key",null);
        AlertDialog d=builder.create();
        d.setOnShowListener(v->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String key=e.getText().toString().trim();if(key.isEmpty()){e.setError("API key required");return;}try{GeminiKeyStore.save(this,key);Toast.makeText(this,"Gemini 2.5 Flash enabled",Toast.LENGTH_SHORT).show();d.dismiss();finish();}catch(Exception ex){Toast.makeText(this,"Could not save key: "+ex.getMessage(),Toast.LENGTH_LONG).show();}});
            if(configured)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{GeminiKeyStore.clear(this);Toast.makeText(this,"Gemini key removed",Toast.LENGTH_SHORT).show();d.dismiss();finish();});
        });
        d.show();
    }
}
