package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** U1 Visual Memory: image-first browser over Cortex screenshot evidence and existing understanding. */
public final class VisualMemoryActivity extends Activity {
    VaultDb db;
    LinearLayout content;
    EditText search;
    TextView stats;
    final ArrayList<Bitmap> bitmaps=new ArrayList<>();
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        db=new VaultDb(getApplicationContext());
        build();
        render("");
    }

    @Override protected void onDestroy(){
        for(Bitmap b:bitmaps)try{if(b!=null&&!b.isRecycled())b.recycle();}catch(Throwable ignored){}
        bitmaps.clear();
        if(db!=null)try{db.close();}catch(Throwable ignored){}
        super.onDestroy();
    }

    void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CortexUi.BG);

        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(10),dp(18),dp(28));
        sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        CortexUi.addBottomNav(this,root,"capture",null);
        setContentView(root);

        LinearLayout head=new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));

        LinearLayout ht=new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextView title=CortexUi.plain(this,"Visual Memory",28,CortexUi.TEXT);
        CortexUi.medium(title);
        ht.addView(title);
        ht.addView(CortexUi.text(this,"Screenshots as evidence, OCR and Cortex understanding in one place.",11,CortexUi.MUTED));
        head.addView(ht,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(head);

        stats=CortexUi.plain(this,"",10,CortexUi.MUTED);
        stats.setPadding(dp(2),dp(8),0,dp(8));
        content.addView(stats);

        search=new EditText(this);
        search.setHint("Search screenshots, OCR or Cortex summaries");
        search.setTextColor(CortexUi.TEXT);
        search.setHintTextColor(CortexUi.FAINT);
        search.setSingleLine(true);
        search.setPadding(dp(14),0,dp(14),0);
        search.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER,16));
        search.setOnEditorActionListener((v,action,event)->{render(search.getText().toString());return true;});
        content.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView go=CortexUi.action(this,"Search visual memory",CortexUi.LIME,false);
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(44));gp.setMargins(0,dp(8),0,0);
        content.addView(go,gp);
        go.setOnClickListener(v->render(search.getText().toString()));

        content.addView(CortexUi.section(this,"Recent visual evidence"));
    }

    void render(String q){
        while(content.getChildCount()>5)content.removeViewAt(5);
        for(Bitmap b:bitmaps)try{if(b!=null&&!b.isRecycled())b.recycle();}catch(Throwable ignored){}
        bitmaps.clear();

        ArrayList<KnowledgeItem> all=db.captureSearch(q==null?"":q.trim(),240);
        ArrayList<KnowledgeItem> images=new ArrayList<>();
        int understood=0, withText=0;
        for(KnowledgeItem k:all){
            if(!"SCREENSHOT".equalsIgnoreCase(k.type)&&!"IMAGE".equalsIgnoreCase(k.type))continue;
            images.add(k);
            if("analyzed".equalsIgnoreCase(k.status))understood++;
            if(k.extractedText!=null&&!k.extractedText.trim().isEmpty())withText++;
        }
        stats.setText(images.size()+" images  •  OCR text "+withText+"  •  understood "+understood);
        content.addView(CortexUi.section(this,q==null||q.trim().isEmpty()?"Recent visual evidence":"Matches"));

        if(images.isEmpty()){
            TextView e=CortexUi.text(this,"No matching visual evidence yet.",12,CortexUi.MUTED);
            e.setPadding(0,dp(8),0,dp(18));content.addView(e);return;
        }
        for(KnowledgeItem k:images)content.addView(card(k),margins(0,0,0,10));
    }

    View card(KnowledgeItem k){
        LinearLayout card=CortexUi.card(this,20);
        card.setPadding(dp(10),dp(10),dp(10),dp(11));

        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.TOP);

        ImageView im=new ImageView(this);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        im.setBackground(CortexUi.round(this,CortexUi.SURFACE_3,CortexUi.BORDER_SOFT,14));
        Bitmap b=load(k.attachmentPath);
        if(b!=null){bitmaps.add(b);im.setImageBitmap(b);}
        row.addView(im,new LinearLayout.LayoutParams(dp(106),dp(106)));

        LinearLayout tx=new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(12),0,0,0);
        row.addView(tx,tp);

        TextView title=CortexUi.text(this,clean(k.title),14,CortexUi.TEXT);
        CortexUi.medium(title);title.setMaxLines(2);tx.addView(title);

        String state="analyzed".equalsIgnoreCase(k.status)?"UNDERSTOOD":friendly(k.status);
        TextView meta=CortexUi.plain(this,stamp(k.createdAt)+"  •  "+state,9,"analyzed".equalsIgnoreCase(k.status)?CortexUi.GREEN:CortexUi.MUTED);
        meta.setPadding(0,dp(5),0,0);tx.addView(meta);

        String summary=first(k.summary,k.extractedText,k.rawText);
        TextView s=CortexUi.text(this,clip(summary,260),11,CortexUi.MUTED);
        s.setPadding(0,dp(7),0,0);s.setMaxLines(5);tx.addView(s);

        card.addView(row);

        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);
        if(k.category!=null&&!k.category.trim().isEmpty())chips.addView(CortexUi.chip(this,k.category,CortexUi.LIME,false),chipParams());
        if(k.extractedText!=null&&!k.extractedText.trim().isEmpty())chips.addView(CortexUi.chip(this,"OCR",CortexUi.GREEN,false),chipParams());
        if("analyzed".equalsIgnoreCase(k.status))chips.addView(CortexUi.chip(this,"Cortex",CortexUi.ORANGE,false),chipParams());
        if(chips.getChildCount()>0){LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(34));cp.setMargins(0,dp(9),0,0);card.addView(chips,cp);}

        CortexUi.pressable(this,card,CortexUi.velvet(this,20));
        card.setOnClickListener(v->{Intent i=new Intent(this,VisualIntelligenceActivity.class);i.putExtra("item_id",k.id);startActivity(i);});
        return card;
    }

    LinearLayout.LayoutParams chipParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(30));p.setMargins(0,0,dp(6),0);return p;}
    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    Bitmap load(String path){try{File f=new File(path==null?"":path);return f.isFile()?SafeImageDecoder.decode(f,480,500000L):null;}catch(Throwable e){return null;}}
    String stamp(long t){return new SimpleDateFormat("dd MMM • HH:mm",Locale.getDefault()).format(new Date(t));}
    String clean(String s){return s==null||s.trim().isEmpty()?"Screenshot":s.trim();}
    String friendly(String s){return s==null||s.trim().isEmpty()?"CAPTURED":s.replace('_',' ').toUpperCase(Locale.ROOT);}
    String first(String...xs){for(String x:xs)if(x!=null&&!x.trim().isEmpty())return x.trim();return "No readable text yet.";}
    String clip(String s,int n){if(s==null)return"";return s.length()<=n?s:s.substring(0,n)+"…";}
}
