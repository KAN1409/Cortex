package com.kareem.cortex;

import android.app.*;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Capture result with locked matte premium shell and a model proposal pass for the exact result. */
public final class ProposalCaptureResultActivity extends CaptureResultActivity {
    private static final Set<String> LEGACY_SUGGESTIONS=new HashSet<>(Arrays.asList("Add to project","Create reminder draft","Deep research this product","Search this product online","Summarize + extract actions","Explain / diagnose this screen","Analyze this visual","Get deeper insight","Search online"));

    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(10),dp(18),dp(26));sv.addView(content);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));content.addView(header());state=CortexUi.plain(this,"Queued for analysis…",10,CortexUi.ORANGE);CortexUi.medium(state);state.setPadding(dp(2),dp(7),0,dp(12));content.addView(state);CortexUi.addBottomNav(this,root,"input",null);setContentView(root);
    }

    View header(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(5),dp(8),dp(2),dp(10));View dot=new View(this);dot.setBackground(CortexUi.round(this,CortexUi.RED,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));TextView c=CortexUi.plain(this,"C O R T E X",14,CortexUi.TEXT);CortexUi.bold(c);if(android.os.Build.VERSION.SDK_INT>=21)c.setLetterSpacing(.20f);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.setMargins(dp(12),0,0,0);row.addView(c,cp);View d=CortexUi.divider(this);LinearLayout.LayoutParams dv=new LinearLayout.LayoutParams(dp(1),dp(28));dv.setMargins(dp(12),0,dp(12),0);row.addView(d,dv);TextView sys=CortexUi.plain(this,"RESULT",10,CortexUi.MUTED);if(android.os.Build.VERSION.SDK_INT>=21)sys.setLetterSpacing(.10f);row.addView(sys,new LinearLayout.LayoutParams(0,dp(40),1));CortexGlyphView close=CortexUi.glyph(this,"check",CortexUi.GREEN,false);close.setOnClickListener(v->finish());row.addView(close,new LinearLayout.LayoutParams(dp(46),dp(46)));return row;}

    @Override String displayTitle(KnowledgeItem k,VisualInsightStore.Insight vi){
        if(k!=null&&"AUDIO".equals(k.type)){String t=TranscriptCorrectionStore.effectiveText(db,k);if(t!=null&&!t.trim().isEmpty()){String x=t.replaceAll("\\s+"," ").trim();return x.length()>180?x.substring(0,180)+"…":x;}}
        return super.displayTitle(k,vi);
    }

    @Override void render(KnowledgeItem k){
        super.render(k);hideLegacySuggestions(content);if(stopped||db==null||content==null||k==null||notReady(k.status))return;VisualInsightStore.Insight vi=null;if(isImageType(k.type))try{vi=VisualInsightStore.get(db,k.id);}catch(Throwable ignored){}
        if("AUDIO".equals(k.type))addTranscriptEditor(k);
        String understood=understanding(k,vi);StringBuilder result=new StringBuilder();if(understood!=null&&!understood.trim().isEmpty())result.append(understood.trim());if(k.summary!=null&&!k.summary.trim().isEmpty()&&!contains(result,k.summary)){if(result.length()>0)result.append("\n\n");result.append("Summary: ").append(k.summary.trim());}String raw="AUDIO".equals(k.type)?TranscriptCorrectionStore.effectiveText(db,k):(k.extractedText!=null&&!k.extractedText.trim().isEmpty()?k.extractedText:k.rawText);if(raw!=null&&!raw.trim().isEmpty()&&!contains(result,raw)){if(result.length()>0)result.append("\n\n");result.append("Evidence: ").append(proposalClip(raw,1200));}if(vi!=null&&vi.ready()&&vi.usefulnessReason!=null&&!vi.usefulnessReason.trim().isEmpty())result.append("\n\nWhy it may matter: ").append(proposalClip(vi.usefulnessReason,600));if(result.length()==0)return;
        LinearLayout host=CortexUi.card(this,18);host.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout lead=new LinearLayout(this);lead.setGravity(Gravity.CENTER_VERTICAL);String icon="AUDIO".equals(k.type)?"wave":isImageType(k.type)?"photo":"FILE".equals(k.type)?"file":"text";int color="AUDIO".equals(k.type)?CortexUi.RED:isImageType(k.type)?CortexUi.GREEN:"FILE".equals(k.type)?CortexUi.ORANGE:CortexUi.YELLOW;lead.addView(CortexUi.glyph(this,icon,color,true),new LinearLayout.LayoutParams(dp(38),dp(38)));TextView lh=CortexUi.plain(this,"USEFUL NEXT MOVES",9,color);CortexUi.medium(lh);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(8),0,0,0);lead.addView(lh,lp);host.addView(lead);
        ResultProposalEngine.Target target=new ResultProposalEngine.Target("Input / Capture result","capture_"+k.id,displayTitle(k,vi),result.toString(),k.id,k.type,ProposalUi.cloudAllowedForMemory(this,k));ProposalUi.attach(this,db,host,target);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,0);int at=findDirectSection("Teach Cortex");if(at<0)content.addView(host,p);else content.addView(host,at,p);
    }

    private void addTranscriptEditor(KnowledgeItem k){
        String transcript=TranscriptCorrectionStore.effectiveText(db,k);if(transcript==null||transcript.trim().isEmpty())return;int at=findDirectSection("What Cortex understood");if(at<0)return;
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(2),0,dp(7));TextView label=CortexUi.plain(this,TranscriptCorrectionStore.hasCorrection(db,k.id)?"Transcript • corrected":"Transcript",9,CortexUi.MUTED);CortexUi.medium(label);row.addView(label,new LinearLayout.LayoutParams(0,dp(38),1));TextView edit=CortexUi.chip(this,"Edit transcript",CortexUi.YELLOW,false);CortexUi.pressable(this,edit,CortexUi.round(this,Color.argb(8,241,188,52),Color.argb(70,241,188,52),999));row.addView(edit,new LinearLayout.LayoutParams(-2,dp(36)));edit.setOnClickListener(v->showTranscriptEditor(k,transcript));content.addView(row,at,new LinearLayout.LayoutParams(-1,-2));
    }

    private void showTranscriptEditor(KnowledgeItem k,String transcript){
        final EditText input=new EditText(this);input.setText(transcript);input.setTextColor(CortexUi.TEXT);input.setHintTextColor(CortexUi.FAINT);input.setTextSize(16);input.setGravity(Gravity.TOP|Gravity.START);input.setMinLines(4);input.setMaxLines(12);input.setSingleLine(false);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,16));input.setSelection(input.getText().length());
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(4),dp(6),dp(4),0);wrap.addView(input,new LinearLayout.LayoutParams(-1,-2));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Edit transcript").setMessage("Fix a word or sentence. Cortex keeps the original transcription and uses this corrected version as the working evidence.").setView(wrap).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String corrected=input.getText()==null?"":input.getText().toString().trim();if(corrected.isEmpty()){input.setError("Transcript cannot be empty");return;}try{if(TranscriptCorrectionStore.save(db,k,corrected)){CortexActionExecutor.recordFeedback(db,k.id,"transcript_correction","manual");Toast.makeText(this,"Transcript corrected",Toast.LENGTH_SHORT).show();dialog.dismiss();refresh();}else input.setError("Could not save correction");}catch(Throwable e){input.setError("Could not save correction");}}));dialog.show();
    }

    private int findDirectSection(String text){if(content==null)return-1;for(int i=0;i<content.getChildCount();i++){View v=content.getChildAt(i);if(v instanceof TextView&&text.equals(((TextView)v).getText().toString().trim()))return i;}return-1;}
    private static void hideLegacySuggestions(View v){if(v==null)return;if(v instanceof TextView){String s=((TextView)v).getText().toString().trim();if(LEGACY_SUGGESTIONS.contains(s))v.setVisibility(View.GONE);}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)hideLegacySuggestions(g.getChildAt(i));}}
    private static boolean notReady(String s){String x=s==null?"":s.trim().toLowerCase(Locale.ROOT);return x.isEmpty()||"pending".equals(x)||"queued".equals(x)||"analyzing".equals(x)||"processing".equals(x)||x.contains("failed");}
    private static boolean isImageType(String t){return "IMAGE".equals(t)||"SCREENSHOT".equals(t);}
    private static boolean contains(StringBuilder b,String s){if(s==null)return false;String x=s.replaceAll("\\s+"," ").trim();if(x.length()<24)return false;return b.toString().replaceAll("\\s+"," ").contains(x.substring(0,Math.min(120,x.length())));}
    private static String proposalClip(String s,int n){String x=s==null?"":s.replaceAll("\\s+"," ").trim();return x.length()>n?x.substring(0,n)+"…":x;}
}
