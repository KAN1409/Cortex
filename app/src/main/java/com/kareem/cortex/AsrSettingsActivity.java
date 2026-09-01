package com.kareem.cortex;

import android.app.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public class AsrSettingsActivity extends Activity {
    LinearLayout root; TextView status;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();}

    void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);root.setPadding(dp(18),dp(8),dp(18),dp(20));
        root.addView(CortexUi.simpleHeader(this,"Voice transcription","Providers",v->finish()));
        TextView sub=CortexUi.text(this,"Gemini is primary. Groq Whisper is fallback. Keys stay encrypted with Android Keystore.",13,CortexUi.MUTED);sub.setPadding(dp(2),dp(15),dp(4),dp(16));root.addView(sub);
        status=CortexUi.text(this,"",13,CortexUi.TEXT);status.setPadding(dp(13),dp(12),dp(13),dp(12));status.setBackground(CortexUi.velvet(this,14));root.addView(status);
        TextView gemini=CortexUi.action(this,"Gemini API key",CortexUi.LIME,false);TextView groq=CortexUi.action(this,"Groq Whisper key",CortexUi.LIME,false);TextView done=CortexUi.action(this,"Done",CortexUi.LIME,true);
        gemini.setOnClickListener(v->editGemini());groq.setOnClickListener(v->editGroq());done.setOnClickListener(v->finish());
        add(root,gemini,12);add(root,groq,7);add(root,done,20);setContentView(root);
    }

    void refresh(){if(status==null)return;status.setText("Gemini  "+(GeminiKeyStore.has(this)?"Configured":"Missing")+"\nGroq     "+(GroqKeyStore.has(this)?"Configured":"Missing")+"\n\nAt least one provider is required for voice transcription.");}

    void editGemini(){
        final boolean configured=GeminiKeyStore.has(this);EditText e=secret("Paste Gemini API key");
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(configured?"Gemini API key":"Enable Gemini").setMessage("Primary Cortex transcription provider.").setView(e).setNegativeButton("Cancel",null).setPositiveButton(configured?"Replace":"Save",null);
        if(configured)b.setNeutralButton("Remove",null);AlertDialog d=b.create();d.setOnShowListener(x->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String key=e.getText().toString().trim();if(key.isEmpty()){e.setError("API key required");return;}try{GeminiKeyStore.save(this,key);d.dismiss();refresh();Toast.makeText(this,"Gemini enabled",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"Could not save Gemini key: "+ex.getMessage(),Toast.LENGTH_LONG).show();}});
            Button n=d.getButton(AlertDialog.BUTTON_NEUTRAL);if(n!=null)n.setOnClickListener(v->{GeminiKeyStore.clear(this);d.dismiss();refresh();Toast.makeText(this,"Gemini key removed",Toast.LENGTH_SHORT).show();});
        });d.show();
    }

    void editGroq(){
        final boolean configured=GroqKeyStore.has(this);EditText e=secret("Paste Groq API key");
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(configured?"Groq Whisper key":"Enable Groq Whisper").setMessage("Fallback provider if Gemini fails or is rejected by the quality gate.").setView(e).setNegativeButton("Cancel",null).setPositiveButton(configured?"Replace":"Save",null);
        if(configured)b.setNeutralButton("Remove",null);AlertDialog d=b.create();d.setOnShowListener(x->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String key=e.getText().toString().trim();if(key.isEmpty()){e.setError("API key required");return;}try{GroqKeyStore.save(this,key);d.dismiss();refresh();Toast.makeText(this,"Groq enabled",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"Could not save Groq key: "+ex.getMessage(),Toast.LENGTH_LONG).show();}});
            Button n=d.getButton(AlertDialog.BUTTON_NEUTRAL);if(n!=null)n.setOnClickListener(v->{GroqKeyStore.clear(this);d.dismiss();refresh();Toast.makeText(this,"Groq key removed",Toast.LENGTH_SHORT).show();});
        });d.show();
    }

    EditText secret(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextColor(CortexUi.TEXT);e.setHintTextColor(CortexUi.FAINT);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);e.setPadding(dp(12),dp(10),dp(12),dp(10));return e;}
    void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(-1,dp(48));x.setMargins(0,dp(top),0,0);p.addView(v,x);}
}
