package com.kareem.cortex;

import android.app.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;

public class AsrSettingsActivity extends Activity {
    LinearLayout root; TextView status;
    int bg=Color.rgb(16,17,20),panel=Color.rgb(24,26,31),text=Color.rgb(243,244,246),muted=Color.rgb(165,168,176),accent=Color.rgb(143,169,255);
    int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);} void pad(View v,int x){v.setPadding(dp(x),dp(x),dp(x),dp(x));}

    @Override public void onCreate(Bundle b){super.onCreate(b);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();}

    void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);pad(root,20);
        TextView title=tv("Cortex ASR Setup",26,text);title.setTypeface(null,1);root.addView(title);
        TextView sub=tv("Gemini 3.6 Flash is primary. Groq Whisper Large v3 is fallback. Keys are encrypted with Android Keystore.",14,muted);sub.setPadding(0,dp(8),0,dp(18));root.addView(sub);
        status=tv("",14,text);root.addView(status);
        Button gemini=button("GEMINI 3.6 FLASH KEY");Button groq=button("GROQ WHISPER KEY");Button done=button("DONE");
        gemini.setOnClickListener(v->editGemini());groq.setOnClickListener(v->editGroq());done.setOnClickListener(v->finish());
        add(root,gemini,14);add(root,groq,8);add(root,done,24);setContentView(root);
    }

    void refresh(){if(status==null)return;status.setText("Gemini: "+(GeminiKeyStore.has(this)?"configured ✓":"missing")+"\nGroq: "+(GroqKeyStore.has(this)?"configured ✓":"missing")+"\n\nAt least one provider is required for voice transcription.");}

    void editGemini(){
        final boolean configured=GeminiKeyStore.has(this);EditText e=secret("Paste Gemini API key");
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(configured?"Gemini 3.6 Flash key":"Enable Gemini 3.6 Flash").setMessage("Primary Cortex transcription provider.").setView(e).setNegativeButton("Cancel",null).setPositiveButton(configured?"Replace":"Save",null);
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

    EditText secret(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextColor(text);e.setHintTextColor(muted);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);pad(e,12);return e;}
    TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);return v;}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(text);b.setBackgroundColor(panel);return b;}
    void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(-1,dp(52));x.setMargins(0,dp(top),0,0);p.addView(v,x);}
}
