package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import com.kareem.cortex.visualmemory.VisualKnowledgeReader;
import com.kareem.cortex.visualmemory.VisualKnowledgeSnapshot;

public final class VisualMemoryDetailActivity extends Activity {
    int dp(int x){return CortexUi.dp(this,x);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        build();
    }

    void build(){
        Intent i=getIntent();
        String uri=i.getStringExtra("uri");
        String name=i.getStringExtra("name");
        long time=i.getLongExtra("time",0);
        String ocr=i.getStringExtra("ocr");
        String ocrState=i.getStringExtra("ocr_state");
        String semanticState=i.getStringExtra("semantic_state");
        String semanticError=i.getStringExtra("semantic_error");
        String origin=i.getStringExtra("origin");
        float selfScore=i.getFloatExtra("self_score",0f);
        int derivationDepth=i.getIntExtra("derivation_depth",0);
        boolean knowledgeEligible=i.getBooleanExtra("knowledge_eligible",true);
        String provenanceReason=i.getStringExtra("provenance_reason");
        long mediaId=i.getLongExtra("media_id",0L);

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(10),dp(18),dp(28));
        sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"picbrain",null);setContentView(root);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        LinearLayout ht=new LinearLayout(this);ht.setOrientation(LinearLayout.VERTICAL);
        TextView title=CortexUi.text(this,name==null||name.trim().isEmpty()?"Screenshot":name,22,CortexUi.TEXT);CortexUi.medium(title);ht.addView(title);
        ht.addView(CortexUi.plain(this,time>0?new SimpleDateFormat("dd MMM yyyy • HH:mm",Locale.getDefault()).format(new Date(time)):"",10,CortexUi.MUTED));
        head.addView(ht,new LinearLayout.LayoutParams(0,-2,1));body.addView(head);

        ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER_SOFT,20));
        Bitmap bitmap=load(uri,1500);if(bitmap!=null)image.setImageBitmap(bitmap);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.setMargins(0,dp(10),0,0);body.addView(image,ip);

        body.addView(info("OCR STATUS",safe(ocrState),CortexUi.GREEN));
        body.addView(info("SEMANTIC STATUS",safe(semanticState)+(safe(semanticError).isEmpty()?"":"\n\nLast error: "+semanticError),CortexUi.LIME));
        body.addView(info("PROVENANCE",(knowledgeEligible?"Eligible for knowledge":"Blocked from new knowledge")+"\nOrigin: "+safe(origin)+"\nSelf-reference: "+Math.round(selfScore*100)+"%\nDerivation depth: "+derivationDepth+(safe(provenanceReason).isEmpty()?"":"\nReason: "+provenanceReason),knowledgeEligible?CortexUi.GREEN:CortexUi.YELLOW));
        LinearLayout understood=CortexUi.card(this,18);understood.setPadding(dp(14),dp(12),dp(14),dp(14));
        TextView understoodLabel=CortexUi.plain(this,"CORTEX UNDERSTOOD",10,CortexUi.LIME);CortexUi.medium(understoodLabel);understood.addView(understoodLabel);
        TextView understoodBody=CortexUi.text(this,knowledgeEligible?"Building structured information from this screenshot…":"Not promoted: this screenshot is Cortex-derived evidence.",12,CortexUi.TEXT);understoodBody.setPadding(0,dp(7),0,0);understood.addView(understoodBody);
        LinearLayout understoodWrap=new LinearLayout(this);understoodWrap.addView(understood,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams uwp=new LinearLayout.LayoutParams(-1,-2);uwp.setMargins(0,dp(9),0,0);understoodWrap.setLayoutParams(uwp);body.addView(understoodWrap);
        if(knowledgeEligible&&mediaId>0){
            Executors.newSingleThreadExecutor().execute(()->{
                VisualKnowledgeSnapshot k=VisualKnowledgeReader.read(this,mediaId);
                runOnUiThread(()->understoodBody.setText(formatKnowledge(k)));
            });
        }
        body.addView(info("TRANSCRIPTION",safe(ocr).isEmpty()?"No OCR text available yet.":ocr,CortexUi.TEXT));

        TextView original=CortexUi.action(this,"Open original image",CortexUi.ORANGE,false);
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(46));op.setMargins(0,dp(10),0,0);body.addView(original,op);
        original.setOnClickListener(v->{try{Intent open=new Intent(Intent.ACTION_VIEW,Uri.parse(uri));open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(open);}catch(Throwable e){Toast.makeText(this,"Could not open original",Toast.LENGTH_SHORT).show();}});
    }

    View info(String label,String value,int accent){
        LinearLayout card=CortexUi.card(this,18);card.setPadding(dp(14),dp(12),dp(14),dp(14));
        TextView l=CortexUi.plain(this,label,10,accent);CortexUi.medium(l);card.addView(l);
        TextView v=CortexUi.text(this,value,12,CortexUi.TEXT);v.setPadding(0,dp(7),0,0);v.setTextIsSelectable(true);card.addView(v);
        LinearLayout wrap=new LinearLayout(this);wrap.addView(card,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(9),0,0);wrap.setLayoutParams(p);return wrap;
    }

    Bitmap load(String uri,int max){
        InputStream in=null;
        try{
            Uri u=Uri.parse(uri);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            in=getContentResolver().openInputStream(u);BitmapFactory.decodeStream(in,null,bounds);if(in!=null)in.close();in=null;
            int sample=1;while(bounds.outWidth/sample>max*2||bounds.outHeight/sample>max*2)sample*=2;
            BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=Math.max(1,sample);
            in=getContentResolver().openInputStream(u);return BitmapFactory.decodeStream(in,null,o);
        }catch(Throwable e){return null;}
        finally{if(in!=null)try{in.close();}catch(Throwable ignored){}}
    }
    String formatKnowledge(VisualKnowledgeSnapshot k){
        if(k==null||k.getKnowledgeId()<=0)return "No structured information has been promoted yet.";
        StringBuilder s=new StringBuilder();
        if(!safe(k.getSummary()).isEmpty())s.append(k.getSummary());
        if(!safe(k.getCategory()).isEmpty())s.append(s.length()>0?"\n\n":"").append("Category: ").append(k.getCategory());
        if(k.getEntities()!=null&&!k.getEntities().isEmpty())s.append("\n\nInformation\n• ").append(String.join("\n• ",k.getEntities()));
        if(k.getActions()!=null&&!k.getActions().isEmpty())s.append("\n\nPossible actions\n• ").append(String.join("\n• ",k.getActions()));
        s.append("\n\nEvidence depth: ").append(k.getDerivationDepth());
        return s.toString();
    }
    String safe(String s){return s==null?"":s.trim();}
}
