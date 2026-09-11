package com.kareem.cortex;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.kareem.cortex.visualmemory.VisualMemoryItem;
import com.kareem.cortex.visualmemory.VisualMemoryRuntime;
import com.kareem.cortex.visualmemory.VisualMemoryStats;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class VisualMemoryActivity extends Activity {
    LinearLayout list;
    EditText search;
    TextView stats, state;
    TextView sync, ocr, semantic, refresh;
    final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-visual-memory-ui");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    final ArrayList<Bitmap> bitmaps=new ArrayList<>();
    volatile boolean destroyed=false;
    boolean modelInstalled=false;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CortexUi.applyWindow(this);
        build();
        if(hasMediaPermission()) loadAndMaybeSync();
        else requestMediaPermission();
    }

    @Override protected void onDestroy(){
        destroyed=true;
        io.shutdownNow();
        clearBitmaps();
        super.onDestroy();
    }

    void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CortexUi.BG);

        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(10),dp(18),dp(28));
        sv.addView(body);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"capture",null);
        setContentView(root);

        LinearLayout head=new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=CortexUi.plain(this,"‹",34,CortexUi.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        LinearLayout ht=new LinearLayout(this);ht.setOrientation(LinearLayout.VERTICAL);
        TextView title=CortexUi.plain(this,"Visual Memory",28,CortexUi.TEXT);CortexUi.medium(title);ht.addView(title);
        ht.addView(CortexUi.text(this,"PicBrain inside Cortex — screenshots, OCR, semantic memory and read-only originals.",11,CortexUi.MUTED));
        head.addView(ht,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(head);

        LinearLayout overview=CortexUi.card(this,20);
        overview.setPadding(dp(14),dp(13),dp(14),dp(13));
        stats=CortexUi.text(this,"Loading visual memory…",12,CortexUi.TEXT);overview.addView(stats);
        state=CortexUi.text(this,"",10,CortexUi.MUTED);state.setPadding(0,dp(5),0,0);overview.addView(state);
        body.addView(overview,margins(0,8,0,0));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        sync=action("Sync",CortexUi.LIME);ocr=action("OCR",CortexUi.GREEN);semantic=action("Semantic",CortexUi.ORANGE);
        addAction(actions,sync,0);addAction(actions,ocr,7);addAction(actions,semantic,7);
        body.addView(actions,margins(0,9,0,0));
        sync.setOnClickListener(v->syncNow());
        ocr.setOnClickListener(v->{VisualMemoryRuntime.enqueueOcr(this);state.setText("OCR worker queued.");});
        semantic.setOnClickListener(v->semanticAction());

        search=new EditText(this);
        search.setHint("Search screenshots by words or meaning");
        search.setTextColor(CortexUi.TEXT);search.setHintTextColor(CortexUi.FAINT);
        search.setSingleLine(true);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(14),0,dp(14),0);
        search.setBackground(CortexUi.round(this,CortexUi.SURFACE,CortexUi.BORDER,16));
        search.setOnEditorActionListener((v,action,event)->{load(search.getText().toString());return true;});
        body.addView(search,margins(0,12,0,0));

        refresh=CortexUi.action(this,"Refresh visual memory",CortexUi.MUTED,false);
        body.addView(refresh,new LinearLayout.LayoutParams(-1,dp(44)));
        refresh.setOnClickListener(v->load(search.getText().toString()));

        body.addView(CortexUi.section(this,"Visual evidence"));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
    }

    TextView action(String label,int color){return CortexUi.action(this,label,color,false);}
    void addAction(LinearLayout row,View v,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(left),0,0,0);row.addView(v,p);}
    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}

    void loadAndMaybeSync(){
        load("");
        io.execute(()->{
            try{
                VisualMemoryStats s=VisualMemoryRuntime.stats(this);
                if(s.getPictures()==0) runOnUiThread(this::syncNow);
            }catch(Throwable ignored){}
        });
    }

    void syncNow(){
        if(!hasMediaPermission()){requestMediaPermission();return;}
        state.setText("Scanning MediaStore read-only…");
        sync.setEnabled(false);
        VisualMemoryRuntime.sync(this,new VisualMemoryRuntime.Callback(){
            public void success(String message){post(()->{sync.setEnabled(true);state.setText(message+" • OCR queued automatically.");load(search.getText().toString());});}
            public void failure(String message){post(()->{sync.setEnabled(true);state.setText("Sync failed safely: "+message);});}
        });
    }

    void semanticAction(){
        if(!modelInstalled){
            semantic.setEnabled(false);state.setText("Downloading official EmbeddingGemma model…");
            VisualMemoryRuntime.downloadSemanticModel(this,new VisualMemoryRuntime.Callback(){
                public void success(String message){post(()->{semantic.setEnabled(true);state.setText(message);load(search.getText().toString());});}
                public void failure(String message){post(()->{semantic.setEnabled(true);state.setText("Model download failed: "+message);});}
            });
        }else{
            VisualMemoryRuntime.enqueueSemantic(this,false);
            state.setText("Semantic indexing started. It will continue in the background.");
        }
    }

    void load(String q){
        if(!hasMediaPermission()){requestMediaPermission();return;}
        state.setText(q==null||q.trim().isEmpty()?"Loading screenshots…":"Searching visual memory…");
        refresh.setEnabled(false);
        io.execute(()->{
            try{
                VisualMemoryStats s=VisualMemoryRuntime.stats(this);
                List<VisualMemoryItem> items=(q==null||q.trim().isEmpty())
                    ?VisualMemoryRuntime.recent(this,180)
                    :VisualMemoryRuntime.search(this,q.trim(),180);
                post(()->render(s,items,q));
            }catch(Throwable e){
                post(()->{refresh.setEnabled(true);state.setText("Visual memory stayed safe, but this view could not load: "+safe(e.getMessage()));});
            }
        });
    }

    void render(VisualMemoryStats s,List<VisualMemoryItem> items,String q){
        if(destroyed)return;
        clearBitmaps();list.removeAllViews();refresh.setEnabled(true);
        modelInstalled=s.getModelInstalled();
        stats.setText("Pictures "+s.getPictures()+"  •  Screenshots "+s.getScreenshots()+"  •  OCR "+s.getOcrReady()+"/"+s.getScreenshots()+"  •  Semantic "+s.getSemanticIndexed()+"/"+s.getOcrReady());
        state.setText("OCR failed "+s.getOcrFailed()+"  •  Semantic failed "+s.getSemanticFailed()+"  •  "+(modelInstalled?"EmbeddingGemma ready":"Semantic model not installed"));
        semantic.setText(modelInstalled?"Start semantic index":"Download semantic model");
        if(items==null||items.isEmpty()){
            TextView empty=CortexUi.text(this,q==null||q.trim().isEmpty()?"No screenshots indexed yet. Tap Sync.":"No confident matches.",12,CortexUi.MUTED);
            empty.setPadding(0,dp(8),0,dp(20));list.addView(empty);return;
        }
        for(VisualMemoryItem item:items)list.addView(card(item),margins(0,0,0,10));
    }

    View card(VisualMemoryItem item){
        LinearLayout card=CortexUi.card(this,20);card.setPadding(dp(10),dp(10),dp(10),dp(11));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(CortexUi.round(this,CortexUi.SURFACE_3,CortexUi.BORDER_SOFT,14));
        Bitmap b=loadBitmap(item.getContentUri(),420);if(b!=null){bitmaps.add(b);image.setImageBitmap(b);}
        row.addView(image,new LinearLayout.LayoutParams(dp(108),dp(128)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(12),0,0,0);row.addView(tx,tp);
        TextView title=CortexUi.text(this,clean(item.getDisplayName()),13,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(2);tx.addView(title);
        TextView meta=CortexUi.plain(this,stamp(item.getCapturedAtMillis())+"  •  OCR "+item.getOcrState()+"  •  Semantic "+item.getSemanticState(),9,CortexUi.MUTED);meta.setPadding(0,dp(5),0,0);tx.addView(meta);
        String preview=item.getOcrText();TextView p=CortexUi.text(this,preview==null||preview.trim().isEmpty()?"No OCR text yet.":clip(preview.trim(),320),11,CortexUi.MUTED);p.setPadding(0,dp(7),0,0);p.setMaxLines(6);tx.addView(p);
        card.addView(row);
        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(CortexUi.chip(this,"OCR",item.getOcrState().equals("DONE")?CortexUi.GREEN:CortexUi.MUTED,false),chipParams());
        chips.addView(CortexUi.chip(this,"Semantic",item.getSemanticState().equals("DONE")?CortexUi.LIME:CortexUi.ORANGE,false),chipParams());
        if(!item.getKnowledgeEligible())chips.addView(CortexUi.chip(this,"Self • no learning",CortexUi.YELLOW,true),chipParams());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(34));cp.setMargins(0,dp(9),0,0);card.addView(chips,cp);
        CortexUi.pressable(this,card,CortexUi.velvet(this,20));
        card.setOnClickListener(v->openDetail(item));
        return card;
    }

    LinearLayout.LayoutParams chipParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(30));p.setMargins(0,0,dp(6),0);return p;}

    void openDetail(VisualMemoryItem item){
        Intent i=new Intent(this,VisualMemoryDetailActivity.class);
        i.putExtra("uri",item.getContentUri());i.putExtra("name",item.getDisplayName());i.putExtra("time",item.getCapturedAtMillis());
        i.putExtra("ocr",item.getOcrText());i.putExtra("ocr_state",item.getOcrState());i.putExtra("semantic_state",item.getSemanticState());i.putExtra("semantic_error",item.getSemanticLastError());
        i.putExtra("origin",item.getOrigin());i.putExtra("self_score",item.getSelfReferenceScore());i.putExtra("derivation_depth",item.getDerivationDepth());i.putExtra("knowledge_eligible",item.getKnowledgeEligible());i.putExtra("provenance_reason",item.getProvenanceReason());
        startActivity(i);
    }

    Bitmap loadBitmap(String uri,int max){
        InputStream in=null;
        try{
            Uri u=Uri.parse(uri);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            in=getContentResolver().openInputStream(u);BitmapFactory.decodeStream(in,null,bounds);if(in!=null)in.close();in=null;
            int sample=1;while(bounds.outWidth/sample>max*2||bounds.outHeight/sample>max*2)sample*=2;
            BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=Math.max(1,sample);
            in=getContentResolver().openInputStream(u);return BitmapFactory.decodeStream(in,null,o);
        }catch(Throwable ignored){return null;}
        finally{if(in!=null)try{in.close();}catch(Throwable ignored){}}
    }

    void clearBitmaps(){for(Bitmap b:bitmaps)try{if(b!=null&&!b.isRecycled())b.recycle();}catch(Throwable ignored){}bitmaps.clear();}
    boolean hasMediaPermission(){
        if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    void requestMediaPermission(){
        if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},7101);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},7101);
        state.setText("Photo access is required to build Visual Memory. Originals remain read-only.");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==7101&&hasMediaPermission())loadAndMaybeSync();}
    void post(Runnable r){if(destroyed)return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
    String stamp(long t){return new SimpleDateFormat("dd MMM • HH:mm",Locale.getDefault()).format(new Date(t));}
    String clean(String s){return s==null||s.trim().isEmpty()?"Screenshot":s.trim();}
    String clip(String s,int n){return s==null?"":(s.length()<=n?s:s.substring(0,n)+"…");}
    String safe(String s){return s==null?"":s;}
}
