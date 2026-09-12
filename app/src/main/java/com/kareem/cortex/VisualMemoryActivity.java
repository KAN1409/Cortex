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
    private static final int INITIAL_ITEM_LIMIT=24;
    private static final int SEARCH_ITEM_LIMIT=36;
    private static final int THUMBNAIL_MAX_PX=220;

    LinearLayout list;
    EditText search;
    TextView stats,state;
    TextView sync,ocr,semantic,refresh;
    final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cortex-visual-memory-ui");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    final ArrayList<Bitmap> bitmaps=new ArrayList<>();
    volatile boolean destroyed=false;
    boolean modelInstalled=false;
    int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);CortexUi.applyWindow(this);build();if(hasMediaPermission())loadAndMaybeSync();else requestMediaPermission();
    }

    @Override protected void onDestroy(){destroyed=true;io.shutdownNow();clearBitmaps();super.onDestroy();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);sv.setVerticalScrollBarEnabled(false);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        CortexUi.addBottomNav(this,root,"memory",null);setContentView(root);CortexUi.fitSystemBars(this,root);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(1),dp(8),dp(1),dp(10));
        LinearLayout ht=new LinearLayout(this);ht.setOrientation(LinearLayout.VERTICAL);head.addView(ht,new LinearLayout.LayoutParams(0,-2,1));
        ht.addView(CortexUi.eyebrow(this,"Cortex · Visual memory",CortexUi.GREEN));TextView title=CortexUi.plain(this,"Memory",32,CortexUi.TEXT);CortexUi.medium(title);title.setPadding(0,dp(1),0,0);ht.addView(title);
        TextView knowledge=CortexUi.chip(this,"Knowledge",CortexUi.LIME,false);knowledge.setOnClickListener(v->{try{startActivity(new Intent(this,KnowledgeExplorerActivity.class));}catch(Throwable ignored){}});head.addView(knowledge,new LinearLayout.LayoutParams(-2,dp(36)));body.addView(head);

        LinearLayout overview=CortexUi.card(this,26);overview.setPadding(dp(18),dp(17),dp(18),dp(16));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView eye=CortexUi.eyebrow(this,"Visual intelligence",CortexUi.GREEN);top.addView(eye,new LinearLayout.LayoutParams(0,-2,1));TextView privacy=CortexUi.chip(this,"Read-only originals",CortexUi.GREEN,false);top.addView(privacy,new LinearLayout.LayoutParams(-2,dp(29)));overview.addView(top);
        stats=CortexUi.plain(this,"Loading visual memory…",20,CortexUi.TEXT);CortexUi.medium(stats);stats.setPadding(0,dp(11),0,0);overview.addView(stats);
        state=CortexUi.text(this,"Preparing screenshot understanding and semantic retrieval.",10,CortexUi.MUTED);state.setPadding(0,dp(6),0,0);overview.addView(state);body.addView(overview,margins(0,5,0,0));

        search=new EditText(this);search.setHint("Search screenshots by words or meaning");search.setTextColor(CortexUi.TEXT);search.setHintTextColor(CortexUi.FAINT);search.setTextSize(14);search.setSingleLine(true);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setPadding(dp(16),0,dp(16),0);search.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER,18));search.setOnEditorActionListener((v,action,event)->{load(search.getText().toString());return true;});body.addView(search,marginsHeight(0,12,0,0,50));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(9),0,0);
        sync=action("Sync",CortexUi.LIME);ocr=action("OCR",CortexUi.GREEN);semantic=action("Semantic",CortexUi.ORANGE);addAction(actions,sync,0);addAction(actions,ocr,7);addAction(actions,semantic,7);body.addView(actions);
        sync.setOnClickListener(v->syncNow());ocr.setOnClickListener(v->{VisualMemoryRuntime.enqueueOcr(this);state.setText("OCR worker queued.");});semantic.setOnClickListener(v->semanticAction());

        refresh=CortexUi.action(this,"Refresh memory",CortexUi.MUTED,false);body.addView(refresh,marginsHeight(0,8,0,0,42));refresh.setOnClickListener(v->{VisualMemoryRuntime.enqueueCompletionMaintenance(this);load(search.getText().toString());});

        body.addView(CortexUi.section(this,"Visual evidence"));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
    }

    TextView action(String label,int color){return CortexUi.action(this,label,color,false);}
    void addAction(LinearLayout row,View v,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(left),0,0,0);row.addView(v,p);}
    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    LinearLayout.LayoutParams marginsHeight(int l,int t,int r,int b,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(h));p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}

    void loadAndMaybeSync(){
        VisualMemoryRuntime.enqueueCompletionMaintenance(this);load("");io.execute(()->{try{VisualMemoryStats s=VisualMemoryRuntime.stats(this);if(s.getPictures()==0)runOnUiThread(this::syncNow);}catch(Throwable ignored){}});
    }

    void syncNow(){
        if(!hasMediaPermission()){requestMediaPermission();return;}state.setText("Scanning MediaStore read-only…");sync.setEnabled(false);
        VisualMemoryRuntime.sync(this,new VisualMemoryRuntime.Callback(){
            public void success(String message){post(()->{sync.setEnabled(true);state.setText(message+" · recovery/indexing queued automatically.");load(search.getText().toString());});}
            public void failure(String message){post(()->{sync.setEnabled(true);state.setText("Sync failed safely: "+message);});}
        });
    }

    void semanticAction(){
        if(!modelInstalled){semantic.setEnabled(false);state.setText("Downloading official EmbeddingGemma model…");VisualMemoryRuntime.downloadSemanticModel(this,new VisualMemoryRuntime.Callback(){
            public void success(String message){post(()->{semantic.setEnabled(true);state.setText(message);load(search.getText().toString());});}
            public void failure(String message){post(()->{semantic.setEnabled(true);state.setText("Model download failed: "+message);});}
        });}
        else{VisualMemoryRuntime.enqueueSemantic(this,true);state.setText("Semantic repair/indexing queued. Failed items will be retried safely.");}
    }

    void load(String q){
        if(!hasMediaPermission()){requestMediaPermission();return;}final boolean searching=q!=null&&!q.trim().isEmpty();state.setText(searching?"Searching visual memory…":"Loading recent visual memory…");refresh.setEnabled(false);
        io.execute(()->{try{VisualMemoryStats s=VisualMemoryRuntime.stats(this);List<VisualMemoryItem> items=searching?VisualMemoryRuntime.search(this,q.trim(),SEARCH_ITEM_LIMIT):VisualMemoryRuntime.recent(this,INITIAL_ITEM_LIMIT);post(()->render(s,items,q));}catch(Throwable e){post(()->{refresh.setEnabled(true);state.setText("Visual memory stayed safe, but this view could not load: "+safe(e.getMessage()));});}});
    }

    void render(VisualMemoryStats s,List<VisualMemoryItem> items,String q){
        if(destroyed)return;list.removeAllViews();clearBitmaps();refresh.setEnabled(true);modelInstalled=s.getModelInstalled();
        stats.setText(s.getScreenshots()+" screenshots · "+s.getOcrReady()+" readable · "+s.getSemanticIndexed()+" searchable");
        String model=modelInstalled?"semantic model ready":"semantic model not installed";
        state.setText("Knowledge "+s.getKnowledgeDone()+" ready · "+s.getKnowledgePending()+" pending · OCR "+s.getOcrFailed()+" failed · Semantic "+s.getSemanticFailed()+" failed · "+model+((q==null||q.trim().isEmpty())?"\nShowing newest "+INITIAL_ITEM_LIMIT+" for fast launch. Search reaches older screenshots.":""));
        semantic.setText(modelInstalled?"Repair semantic":"Enable semantic");
        if(items==null||items.isEmpty()){
            LinearLayout empty=CortexUi.card(this,22);empty.setPadding(dp(18),dp(20),dp(18),dp(20));TextView eye=CortexUi.eyebrow(this,q==null||q.trim().isEmpty()?"Memory is empty":"No confident match",CortexUi.MUTED);empty.addView(eye);TextView h=CortexUi.plain(this,q==null||q.trim().isEmpty()?"Sync your screenshots":"Try a broader search",19,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(8),0,0);empty.addView(h);TextView b=CortexUi.text(this,q==null||q.trim().isEmpty()?"Cortex reads MediaStore without modifying your originals.":"Search uses lexical and semantic retrieval without changing the source image.",12,CortexUi.MUTED);b.setPadding(0,dp(6),0,0);empty.addView(b);list.addView(empty);return;
        }
        for(VisualMemoryItem item:items)list.addView(card(item),margins(0,0,0,10));
    }

    View card(VisualMemoryItem item){
        LinearLayout card=CortexUi.card(this,22);card.setPadding(dp(10),dp(10),dp(12),dp(11));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(CortexUi.round(this,CortexUi.SURFACE_3,CortexUi.BORDER_SOFT,15));Bitmap b=loadBitmap(item.getContentUri(),THUMBNAIL_MAX_PX);if(b!=null){bitmaps.add(b);image.setImageBitmap(b);}row.addView(image,new LinearLayout.LayoutParams(dp(116),dp(142)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(12),dp(1),0,0);row.addView(tx,tp);
        TextView meta=CortexUi.eyebrow(this,stamp(item.getCapturedAtMillis()),CortexUi.FAINT);tx.addView(meta);
        TextView title=CortexUi.text(this,clean(item.getDisplayName()),14,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(2);title.setPadding(0,dp(6),0,0);tx.addView(title);
        String preview=item.getOcrText();TextView p=CortexUi.text(this,preview==null||preview.trim().isEmpty()?"Waiting for readable text.":clip(preview.trim(),260),11,CortexUi.MUTED);p.setPadding(0,dp(7),0,0);p.setMaxLines(5);tx.addView(p);card.addView(row);

        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);chips.setGravity(Gravity.CENTER_VERTICAL);
        String ks=item.getKnowledgeState();int kc="DONE".equals(ks)?CortexUi.LIME:("BLOCKED".equals(ks)?CortexUi.YELLOW:("FAILED".equals(ks)?CortexUi.RED:CortexUi.MUTED));String kl="DONE".equals(ks)?"Knowledge ready":("SKIPPED".equals(ks)?"No learnable text":"Knowledge "+safe(ks));chips.addView(CortexUi.chip(this,kl,kc,true),chipParams());
        chips.addView(CortexUi.chip(this,"OCR "+shortState(item.getOcrState()),"DONE".equals(item.getOcrState())?CortexUi.GREEN:CortexUi.MUTED,false),chipParams());
        chips.addView(CortexUi.chip(this,"Semantic "+shortState(item.getSemanticState()),"DONE".equals(item.getSemanticState())?CortexUi.LIME:("FAILED".equals(item.getSemanticState())?CortexUi.RED:CortexUi.ORANGE),false),chipParams());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(34));cp.setMargins(0,dp(9),0,0);card.addView(chips,cp);
        if(!item.getKnowledgeEligible()){TextView provenance=CortexUi.plain(this,"Self-reference protected · excluded from learning",9,CortexUi.YELLOW);provenance.setPadding(dp(2),dp(7),0,0);card.addView(provenance);}
        CortexUi.pressable(this,card,CortexUi.velvet(this,22));card.setOnClickListener(v->openDetail(item));return card;
    }

    String shortState(String s){if(s==null||s.trim().isEmpty())return"—";if("DONE".equals(s))return"ready";if("FAILED".equals(s))return"failed";if("PENDING".equals(s))return"pending";return s.toLowerCase(Locale.ROOT).replace('_',' ');}
    LinearLayout.LayoutParams chipParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(30));p.setMargins(0,0,dp(6),0);return p;}

    void openDetail(VisualMemoryItem item){
        Intent i=new Intent(this,VisualMemoryDetailActivity.class);i.putExtra("uri",item.getContentUri());i.putExtra("name",item.getDisplayName());i.putExtra("time",item.getCapturedAtMillis());i.putExtra("ocr",item.getOcrText());i.putExtra("ocr_state",item.getOcrState());i.putExtra("semantic_state",item.getSemanticState());i.putExtra("semantic_error",item.getSemanticLastError());i.putExtra("media_id",item.getMediaId());i.putExtra("knowledge_state",item.getKnowledgeState());i.putExtra("origin",item.getOrigin());i.putExtra("self_score",item.getSelfReferenceScore());i.putExtra("derivation_depth",item.getDerivationDepth());i.putExtra("knowledge_eligible",item.getKnowledgeEligible());i.putExtra("provenance_reason",item.getProvenanceReason());startActivity(i);
    }

    Bitmap loadBitmap(String uri,int max){
        InputStream in=null;try{Uri u=Uri.parse(uri);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;in=getContentResolver().openInputStream(u);BitmapFactory.decodeStream(in,null,bounds);if(in!=null)in.close();in=null;if(bounds.outWidth<=0||bounds.outHeight<=0)return null;int sample=1;while(bounds.outWidth/sample>max||bounds.outHeight/sample>max*2)sample*=2;BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=Math.max(1,sample);o.inPreferredConfig=Bitmap.Config.RGB_565;o.inDither=true;in=getContentResolver().openInputStream(u);return BitmapFactory.decodeStream(in,null,o);}catch(Throwable ignored){return null;}finally{if(in!=null)try{in.close();}catch(Throwable ignored){}}
    }

    void clearBitmaps(){bitmaps.clear();}
    boolean hasMediaPermission(){if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    void requestMediaPermission(){if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},7101);else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},7101);if(state!=null)state.setText("Photo access is required to build Visual Memory. Originals remain read-only.");}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==7101&&hasMediaPermission())loadAndMaybeSync();}
    void post(Runnable r){if(destroyed)return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}
    String stamp(long t){return new SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(new Date(t));}
    String clean(String s){return s==null||s.trim().isEmpty()?"Screenshot":s.trim();}
    String clip(String s,int n){return s==null?"":(s.length()<=n?s:s.substring(0,n)+"…");}
    String safe(String s){return s==null?"":s;}
}
