package com.kareem.cortex.rebuild;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Provider credentials for Cortex perception and cognition. */
public final class AsrSettingsActivity extends Activity {
    private final int bg=Color.rgb(8,10,8),panel=Color.rgb(24,28,24),text=Color.rgb(244,246,242),muted=Color.rgb(164,171,163),accent=Color.rgb(143,226,67);
    private TextView status;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);} private void pad(View v,int x){v.setPadding(dp(x),dp(x),dp(x),dp(x));}

    @Override public void onCreate(Bundle b){super.onCreate(b);build();refresh();}
    @Override protected void onResume(){super.onResume();refresh();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);pad(root,20);
        TextView title=tv("Cortex providers",28,text);title.setTypeface(null,1);root.addView(title);
        TextView sub=tv("Brain: Groq GPT-OSS 120B primary with strict structured output; Gemini fallback. Photo perception: Groq Qwen Vision primary; Gemini fallback. Voice perception: Gemini 3.6 Flash primary with Groq Whisper fallback. Egyptian Arabic + English code-switching stays verbatim. Keys are encrypted with Android Keystore.",14,muted);sub.setPadding(0,dp(8),0,dp(18));root.addView(sub);
        status=tv("",14,text);root.addView(status);
        Button gemini=button("GEMINI KEY"),groq=button("GROQ KEY · BRAIN + VISION + WHISPER"),done=button("DONE");
        gemini.setOnClickListener(v->editGemini());groq.setOnClickListener(v->editGroq());done.setOnClickListener(v->finish());
        add(root,gemini,14);add(root,groq,8);add(root,done,24);setContentView(root);
    }

    private void refresh(){if(status==null)return;status.setText("Groq brain / photo vision / Whisper: "+(GroqKeyStore.has(this)?"ready ✓":"missing")+"\nGemini fallback / voice ASR: "+(GeminiKeyStore.has(this)?"ready ✓":"missing")+"\n\nEither provider can understand photos. Configure both for the best failover reliability. Obvious test/meta captures are handled locally without either provider.");}

    private void editGemini(){boolean configured=GeminiKeyStore.has(this);EditText e=secret("Paste Gemini API key");AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(configured?"Gemini key":"Enable Gemini").setMessage("Voice transcription primary plus fallback for Cortex Brain and photo vision.").setView(e).setNegativeButton("Cancel",null).setPositiveButton(configured?"Replace":"Save",null);if(configured)b.setNeutralButton("Remove",null);AlertDialog d=b.create();d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String key=e.getText().toString().trim();if(key.isEmpty()){e.setError("API key required");return;}try{GeminiKeyStore.save(this,key);d.dismiss();refresh();Toast.makeText(this,"Gemini enabled",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"Could not save Gemini key",Toast.LENGTH_LONG).show();}});Button n=d.getButton(AlertDialog.BUTTON_NEUTRAL);if(n!=null)n.setOnClickListener(v->{GeminiKeyStore.clear(this);d.dismiss();refresh();});});d.show();}
    private void editGroq(){boolean configured=GroqKeyStore.has(this);EditText e=secret("Paste Groq API key");AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(configured?"Groq key":"Enable Groq").setMessage("Primary Cortex Brain: GPT-OSS 120B. Primary photo vision: Qwen Vision. Also powers Whisper fallback for voice transcription.").setView(e).setNegativeButton("Cancel",null).setPositiveButton(configured?"Replace":"Save",null);if(configured)b.setNeutralButton("Remove",null);AlertDialog d=b.create();d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String key=e.getText().toString().trim();if(key.isEmpty()){e.setError("API key required");return;}try{GroqKeyStore.save(this,key);d.dismiss();refresh();Toast.makeText(this,"Groq enabled",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,"Could not save Groq key",Toast.LENGTH_LONG).show();}});Button n=d.getButton(AlertDialog.BUTTON_NEUTRAL);if(n!=null)n.setOnClickListener(v->{GroqKeyStore.clear(this);d.dismiss();refresh();});});d.show();}

    private EditText secret(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextColor(text);e.setHintTextColor(muted);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);pad(e,12);return e;}
    private TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(text);b.setBackgroundColor(panel);return b;}
    private void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(-1,dp(54));x.setMargins(0,dp(top),0,0);p.addView(v,x);}
}
