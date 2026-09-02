package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Visible V1 product flow: Cortex prepares -> ChatGPT organizes -> user previews -> Cortex applies selected operations. */
public final class CortexOrganizerActivity extends Activity {
    static final String PREF="cortex_organizer_v1",KEY_REQUEST="last_request_id";
    VaultDb db;CortexOrganizerContractV1.PromptPack pack;CortexOrganizerContractV1.Plan plan;String expectedRequestId="";
    TextView packState,validationState,applyButton;EditText response;LinearLayout preview;final ArrayList<CheckBox> checks=new ArrayList<>();
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);CortexUi.applyWindow(this);db=new VaultDb(this);expectedRequestId=getSharedPreferences(PREF,MODE_PRIVATE).getString(KEY_REQUEST,"");build();preparePack();
        String incoming=getIntent()==null?"":getIntent().getStringExtra("cortex_organizer_response");if(incoming!=null&&!incoming.trim().isEmpty()){response.setText(incoming);validateResponse();}
    }
    @Override protected void onDestroy(){try{if(db!=null)db.close();}catch(Throwable ignored){}super.onDestroy();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);root.addView(header(),new LinearLayout.LayoutParams(-1,dp(72)));
        ScrollView sv=new ScrollView(this);sv.setClipToPadding(false);sv.setVerticalScrollBarEnabled(false);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),0,dp(18),dp(30));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        TextView intro=CortexUi.text(this,"ChatGPT does the thinking. Cortex only prepares your data, validates the returned organization plan, and applies the changes you choose.",12,CortexUi.MUTED);intro.setPadding(dp(2),dp(3),dp(4),dp(16));body.addView(intro);

        TextView prepareTitle=CortexUi.section(this,"1  PREPARE");body.addView(prepareTitle);
        LinearLayout prepare=CortexUi.card(this,17);prepare.setPadding(dp(14),dp(13),dp(14),dp(13));
        TextView h=CortexUi.plain(this,"What ChatGPT will receive",15,CortexUi.TEXT);CortexUi.medium(h);prepare.addView(h);
        TextView share=CortexUi.text(this,"Recent Cortex evidence as text and metadata, plus existing open follow-ups and project candidates. Raw attachments, live screen frames and API keys are not included.",11,CortexUi.MUTED);share.setPadding(0,dp(6),0,dp(10));prepare.addView(share);
        packState=CortexUi.plain(this,"Preparing local context…",10,CortexUi.MUTED);prepare.addView(packState);
        TextView open=CortexUi.action(this,"Open in ChatGPT",CortexUi.LIME,true);open.setOnClickListener(v->confirmOpen());LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(50));op.setMargins(0,dp(12),0,0);prepare.addView(open,op);body.addView(prepare,sectionGap());

        TextView returnTitle=CortexUi.section(this,"2  RETURN ORGANIZATION PLAN");body.addView(returnTitle);
        LinearLayout returned=CortexUi.card(this,17);returned.setPadding(dp(13),dp(12),dp(13),dp(13));
        TextView marker=CortexUi.plain(this,"CORTEX_ORGANIZER_RESPONSE_V1",10,CortexUi.LIME);CortexUi.medium(marker);returned.addView(marker);
        response=new EditText(this);response.setHint("Paste the structured response from ChatGPT here");response.setHintTextColor(Color.rgb(103,108,102));response.setTextColor(CortexUi.TEXT);response.setTextSize(11);response.setGravity(Gravity.TOP|Gravity.START);response.setMinLines(6);response.setMaxLines(14);response.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);response.setPadding(dp(11),dp(10),dp(11),dp(10));response.setBackground(CortexUi.round(this,Color.rgb(18,20,18),Color.rgb(54,58,52),12));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.setMargins(0,dp(10),0,dp(9));returned.addView(response,rp);
        LinearLayout actions=new LinearLayout(this);TextView paste=CortexUi.action(this,"Paste",CortexUi.MUTED,false);paste.setOnClickListener(v->pasteClipboard());actions.addView(paste,new LinearLayout.LayoutParams(0,dp(44),1));TextView validate=CortexUi.action(this,"Validate",CortexUi.LIME,false);validate.setOnClickListener(v->validateResponse());LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,dp(44),1);vp.setMargins(dp(8),0,0,0);actions.addView(validate,vp);returned.addView(actions);validationState=CortexUi.plain(this,"Nothing has been imported yet.",10,CortexUi.MUTED);validationState.setPadding(0,dp(9),0,0);returned.addView(validationState);body.addView(returned,sectionGap());

        TextView previewTitle=CortexUi.section(this,"3  CHOOSE WHAT CHANGES");body.addView(previewTitle);
        preview=new LinearLayout(this);preview.setOrientation(LinearLayout.VERTICAL);TextView empty=CortexUi.text(this,"Validated organization operations will appear here. Nothing is selected automatically.",11,CortexUi.MUTED);empty.setPadding(dp(13),dp(12),dp(13),dp(12));empty.setBackground(CortexUi.round(this,Color.rgb(18,20,18),Color.rgb(47,51,46),14));preview.addView(empty);body.addView(preview,sectionGap());

        LinearLayout apply=CortexUi.card(this,17);apply.setPadding(dp(13),dp(12),dp(13),dp(13));TextView rule=CortexUi.text(this,"Source evidence is never deleted by this workflow. Only the changes you select are applied.",10,CortexUi.MUTED);apply.addView(rule);applyButton=CortexUi.action(this,"Apply selected (0)",CortexUi.LIME,true);applyButton.setEnabled(false);applyButton.setAlpha(.42f);applyButton.setOnClickListener(v->confirmApply());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(52));ap.setMargins(0,dp(10),0,0);apply.addView(applyButton,ap);body.addView(apply);

        setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    View header(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(8),dp(18),0);
        TextView back=CortexUi.action(this,"‹",CortexUi.MUTED,false);back.setTextSize(28);back.setOnClickListener(v->finish());row.addView(back,new LinearLayout.LayoutParams(dp(46),dp(46)));
        CortexLineIconView logo=new CortexLineIconView(this,"logo",CortexUi.LIME);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(30),dp(30));lp.setMargins(dp(12),0,0,0);row.addView(logo,lp);
        TextView title=CortexUi.plain(this,"Organize Cortex",22,CortexUi.TEXT);CortexUi.bold(title);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(8),0,0,0);row.addView(title,tp);return row;
    }

    LinearLayout.LayoutParams sectionGap(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(20));return p;}

    void preparePack(){
        try{pack=CortexOrganizerContractV1.build(db);packState.setText(pack.evidenceCount+" evidence items  •  "+pack.existingCount+" existing organization items  •  local until you open ChatGPT");}
        catch(Throwable e){pack=null;packState.setText("Could not prepare Cortex data safely");packState.setTextColor(CortexUi.RED);}
    }

    void confirmOpen(){
        if(pack==null)preparePack();if(pack==null){Toast.makeText(this,"Cortex could not prepare the organizer pack",Toast.LENGTH_LONG).show();return;}
        String msg=pack.evidenceCount+" evidence items\n"+pack.existingCount+" existing follow-ups / project candidates\n\nNo raw attachments\nNo live screen capture\nNo API keys\n\nChatGPT will be asked to return organization operations only.";
        new AlertDialog.Builder(this).setTitle("About to share with ChatGPT").setMessage(msg).setNegativeButton("Cancel",null).setPositiveButton("Open ChatGPT",(d,w)->openChatGPT()).show();
    }

    void openChatGPT(){
        try{
            if(pack==null)preparePack();if(pack==null)throw new IllegalStateException();expectedRequestId=pack.requestId;getSharedPreferences(PREF,MODE_PRIVATE).edit().putString(KEY_REQUEST,expectedRequestId).apply();packState.setText(pack.evidenceCount+" evidence items  •  waiting for ChatGPT response");
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_SUBJECT,"Organize Cortex data");send.putExtra(Intent.EXTRA_TEXT,pack.text);send.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
            Intent direct=new Intent(send);direct.setPackage("com.openai.chatgpt");try{startActivity(direct);}catch(Throwable missing){startActivity(Intent.createChooser(send,"Open organizer with"));}
        }catch(Throwable e){Toast.makeText(this,"Could not open ChatGPT safely",Toast.LENGTH_LONG).show();}
    }

    void pasteClipboard(){
        try{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm==null||!cm.hasPrimaryClip()||cm.getPrimaryClip().getItemCount()==0){Toast.makeText(this,"Clipboard is empty",Toast.LENGTH_SHORT).show();return;}CharSequence s=cm.getPrimaryClip().getItemAt(0).coerceToText(this);response.setText(s==null?"":s.toString());response.setSelection(response.length());validationState.setText("Pasted. Validate before anything can change.");}
        catch(Throwable e){Toast.makeText(this,"Could not read clipboard",Toast.LENGTH_SHORT).show();}
    }

    void validateResponse(){
        plan=null;checks.clear();try{
            plan=CortexOrganizerContractV1.parse(db,response.getText().toString(),expectedRequestId);validationState.setText(plan.operations.size()+" valid organization operation"+(plan.operations.size()==1?"":"s")+"  •  grounded in existing Cortex evidence");validationState.setTextColor(CortexUi.LIME);renderPreview();
        }catch(Throwable e){validationState.setText("Blocked. No data changed. "+safe(e));validationState.setTextColor(CortexUi.RED);preview.removeAllViews();TextView bad=CortexUi.text(this,"Cortex rejected this response because it was not a valid grounded organization plan.",11,CortexUi.MUTED);bad.setPadding(dp(13),dp(12),dp(13),dp(12));bad.setBackground(CortexUi.round(this,Color.rgb(20,20,20),Color.rgb(72,50,50),14));preview.addView(bad);refreshApply();}
    }

    void renderPreview(){
        preview.removeAllViews();checks.clear();if(plan==null)return;
        if(!plan.summary.isEmpty()){TextView summary=CortexUi.text(this,plan.summary,11,CortexUi.TEXT);summary.setPadding(dp(13),dp(11),dp(13),dp(11));summary.setBackground(CortexUi.round(this,Color.rgb(19,21,19),Color.rgb(50,55,49),14));preview.addView(summary,cardGap());}
        if(plan.operations.isEmpty()){TextView none=CortexUi.text(this,"ChatGPT found no organization changes that were clearly supported by the current Cortex data.",11,CortexUi.MUTED);none.setPadding(dp(13),dp(12),dp(13),dp(12));none.setBackground(CortexUi.round(this,Color.rgb(18,20,18),Color.rgb(47,51,46),14));preview.addView(none);refreshApply();return;}
        for(int i=0;i<plan.operations.size();i++)preview.addView(operationCard(plan.operations.get(i)),cardGap());refreshApply();
    }

    LinearLayout.LayoutParams cardGap(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));return p;}

    View operationCard(CortexOrganizerContractV1.Operation op){
        LinearLayout card=CortexUi.card(this,15);card.setPadding(dp(10),dp(10),dp(10),dp(10));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.TOP);
        CheckBox check=new CheckBox(this);check.setChecked(false);check.setOnCheckedChangeListener((b,c)->refreshApply());checks.add(check);top.addView(check,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams xp=new LinearLayout.LayoutParams(0,-2,1);xp.setMargins(dp(5),0,0,0);top.addView(tx,xp);
        TextView title=CortexUi.text(this,op.displayTitle(),13,CortexUi.TEXT);CortexUi.medium(title);tx.addView(title);
        TextView kind=CortexUi.plain(this,friendlyOp(op.op),9,CortexUi.LIME);kind.setPadding(0,dp(2),0,0);tx.addView(kind);card.addView(top);
        StringBuilder detail=new StringBuilder();if(op.tags.length>0)detail.append("Tags: ").append(join(op.tags," · ")).append("\n");if(!op.relation.isEmpty())detail.append("Relation: ").append(op.relation.replace('_',' ')).append("\n");if(!op.body.isEmpty())detail.append(op.body).append("\n");if(!op.reason.isEmpty())detail.append("Why: ").append(op.reason).append("\n");detail.append("Evidence: ").append(ids(op.evidenceIds));
        TextView d=CortexUi.text(this,detail.toString().trim(),10,CortexUi.MUTED);d.setPadding(dp(47),dp(4),dp(2),0);card.addView(d);return card;
    }

    void refreshApply(){int n=0;for(CheckBox c:checks)if(c.isChecked())n++;applyButton.setText("Apply selected ("+n+")");applyButton.setEnabled(plan!=null&&n>0);applyButton.setAlpha(plan!=null&&n>0?1f:.42f);}

    void confirmApply(){
        if(plan==null)return;int n=0;for(CheckBox c:checks)if(c.isChecked())n++;if(n==0)return;final int count=n;
        new AlertDialog.Builder(this).setTitle("Apply "+count+" selected change"+(count==1?"":"s")+"?").setMessage("Cortex will apply only these organization operations. Existing source evidence will not be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->applySelected()).show();
    }

    void applySelected(){
        if(plan==null)return;boolean[] selected=new boolean[checks.size()];for(int i=0;i<checks.size();i++)selected[i]=checks.get(i).isChecked();
        try{CortexOrganizerContractV1.ApplyResult r=CortexOrganizerContractV1.applySelected(db,plan,selected);getSharedPreferences(PREF,MODE_PRIVATE).edit().remove(KEY_REQUEST).apply();expectedRequestId="";new AlertDialog.Builder(this).setTitle("Cortex organized").setMessage(r.applied+" selected change"+(r.applied==1?"":"s")+" applied.\n\nYou can now see the result in Cortex Evidence / project and follow-up surfaces.").setPositiveButton("Done",(d,w)->{response.setText("");plan=null;checks.clear();renderEmpty();preparePack();}).show();}
        catch(Throwable e){Toast.makeText(this,"Nothing changed: "+safe(e),Toast.LENGTH_LONG).show();}
    }

    void renderEmpty(){preview.removeAllViews();TextView x=CortexUi.text(this,"Run another ChatGPT organization pass whenever new Cortex data needs sorting.",11,CortexUi.MUTED);x.setPadding(dp(13),dp(12),dp(13),dp(12));x.setBackground(CortexUi.round(this,Color.rgb(18,20,18),Color.rgb(47,51,46),14));preview.addView(x);refreshApply();}

    String friendlyOp(String op){if("TAG_EVIDENCE".equals(op))return "Retrieval tags";if("LINK_EVIDENCE".equals(op))return "Evidence relationship";if("CREATE_FOLLOW_UP".equals(op))return "Follow-up";if("CREATE_PROJECT_CANDIDATE".equals(op))return "Project grouping";return op;}
    String ids(long[] xs){StringBuilder b=new StringBuilder();for(long x:xs){if(b.length()>0)b.append(", ");b.append('#').append(x);}return b.toString();}
    String join(String[] xs,String sep){StringBuilder b=new StringBuilder();for(String x:xs){if(b.length()>0)b.append(sep);b.append(x);}return b.toString();}
    String safe(Throwable e){String s=e==null?"Unknown error":String.valueOf(e.getMessage());if(s==null||s.trim().isEmpty())s=e==null?"Unknown error":e.getClass().getSimpleName();return s.length()>180?s.substring(0,180):s;}
}
