package com.kareem.cortex;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
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
    private static final int INITIAL_ITEM_LIMIT=24,SEARCH_ITEM_LIMIT=36,THUMBNAIL_MAX_PX=260;
    private static final String PERF_TAG="CortexVisualPerf";
    LinearLayout list;EditText search;TextView stats,state,sync,ocr,semantic,refresh;
    final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>(),r->{Thread t=new Thread(r,"cortex-visual-memory-ui");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    final ExecutorService thumbs=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"cortex-visual-thumbs");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    final LruCache<String,Bitmap> thumbCache=new LruCache<String,Bitmap>(12*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value==null?0:value.getAllocationByteCount();}};
    volatile boolean destroyed=false;volatile int loadGeneration=0;boolean modelInstalled=false;int dp(int x){return CortexUi.dp(this,x);}

    @Override public void onCreate(Bundle b){super.onCreate(b);CortexUi.applyWindow(this);build();if(hasMediaPermission())loadAndMaybeSync();else requestMediaPermission();}
    @Override protected void onDestroy(){destroyed=true;loadGeneration++;io.getQueue().clear();io.shutdownNow();thumbs.shutdownNow();thumbCache.evictAll();super.onDestroy();}

    void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CortexUi.BG);ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);sv.setVerticalScrollBarEnabled(false);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));CortexUi.addBottomNav(this,root,"memory",null);setContentView(root);CortexUi.fitSystemBars(this,root);
        body.addView(CortexUi.header(this,"CORTEX · VISUAL MEMORY","Memory",CortexUi.GREEN,"Knowledge",v->{try{startActivity(new Intent(this,KnowledgeExplorerActivity.class));}catch(Throwable ignored){}}));
        LinearLayout overview=CortexUi.card(this,CortexUi.R_HERO);overview.setPadding(dp(18),dp(17),dp(18),dp(16));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(CortexUi.eyebrow(this,"VISUAL INTELLIGENCE",CortexUi.GREEN),new LinearLayout.LayoutParams(0,-2,1));top.addView(CortexUi.chip(this,"Read-only originals",CortexUi.GREEN,false),new LinearLayout.LayoutParams(-2,dp(29)));overview.addView(top);stats=CortexUi.plain(this,"Loading visual memory…",20,CortexUi.TEXT);CortexUi.medium(stats);stats.setPadding(0,dp(11),0,0);overview.addView(stats);state=CortexUi.text(this,"Preparing screenshot understanding and semantic retrieval.",10,CortexUi.MUTED);state.setPadding(0,dp(6),0,0);overview.addView(state);body.addView(overview,margins(0,5,0,0));CortexMotion.hero(overview);

        search=new EditText(this);search.setHint("Search screenshots by words or meaning");search.setTextColor(CortexUi.TEXT);search.setHintTextColor(CortexUi.FAINT);search.setTextSize(14);search.setSingleLine(true);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setPadding(dp(16),0,dp(16),0);search.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,18));search.setOnFocusChangeListener((v,on)->{CortexMotion.focus(v,on);v.setBackground(CortexUi.round(this,CortexUi.SURFACE_2,on?Color.argb(110,190,221,82):CortexUi.BORDER_SOFT,18));});search.setOnEditorActionListener((v,action,event)->{load(search.getText().toString());return true;});body.addView(search,marginsHeight(0,12,0,0,50));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(9),0,0);sync=action("Sync",CortexUi.LIME);ocr=action("OCR",CortexUi.GREEN);semantic=action("Semantic",CortexUi.MUTED);addAction(actions,sync,0);addAction(actions,ocr,7);addAction(actions,semantic,7);body.addView(actions);sync.setOnClickListener(v->syncNow());ocr.setOnClickListener(v->{VisualMemoryRuntime.enqueueOcr(this);CortexMotion.softSwap(state,"OCR retry queued safely.");});semantic.setOnClickListener(v->semanticAction());
        refresh=CortexUi.action(this,"Refresh memory",CortexUi.MUTED,false);body.addView(refresh,marginsHeight(0,8,0,0,42));refresh.setOnClickListener(v->load(search.getText().toString()));body.addView(CortexUi.section(this,"Visual evidence"));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
    }

    TextView action(String label,int color){return CortexUi.action(this,label,color,false);}void addAction(LinearLayout row,View v,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(left),0,0,0);row.addView(v,p);}LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}LinearLayout.LayoutParams marginsHeight(int l,int t,int r,int b,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(h));p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}

    /** Paint first, schedule maintenance second. The first visible frame never waits behind repair work. */
    void loadAndMaybeSync(){
        load("");
        try{io.execute(()->{if(destroyed)return;try{int pictures=VisualMemoryRuntime.pictureCount(this);if(pictures==0)post(this::syncNow);else VisualMemoryRuntime.enqueueCompletionMaintenance(this);}catch(Throwable ignored){}});}catch(RejectedExecutionException ignored){}
    }

    void syncNow(){if(!hasMediaPermission()){requestMediaPermission();return;}CortexMotion.softSwap(state,"Scanning MediaStore read-only…");sync.setEnabled(false);VisualMemoryRuntime.sync(this,new VisualMemoryRuntime.Callback(){public void success(String message){post(()->{sync.setEnabled(true);CortexMotion.softSwap(state,message+" · indexing continues automatically.");load(search.getText().toString());});}public void failure(String message){post(()->{sync.setEnabled(true);state.setText("Sync failed safely: "+message);});}});}
    void semanticAction(){if(!modelInstalled){semantic.setEnabled(false);state.setText("Downloading official EmbeddingGemma model…");VisualMemoryRuntime.downloadSemanticModel(this,new VisualMemoryRuntime.Callback(){public void success(String message){post(()->{semantic.setEnabled(true);state.setText(message);load(search.getText().toString());});}public void failure(String message){post(()->{semantic.setEnabled(true);state.setText("Model download failed: "+message);});}});}else{VisualMemoryRuntime.enqueueSemantic(this,true);state.setText("Semantic repair queued. Only failed/recoverable items will be retried.");}}

    /** Latest request wins. Queued stale refreshes are discarded instead of serially blocking the UI. */
    void load(String q){
        if(!hasMediaPermission()){requestMediaPermission();return;}
        final String query=q==null?"":q.trim();final boolean searching=!query.isEmpty();final int generation=++loadGeneration;
        CortexMotion.softSwap(state,searching?"Searching by words and meaning…":"Loading recent visual memory…");refresh.setEnabled(false);io.getQueue().clear();
        try{io.execute(()->{
            if(destroyed||generation!=loadGeneration)return;long started=SystemClock.elapsedRealtime();
            try{
                VisualMemoryStats s=VisualMemoryRuntime.stats(this);long afterStats=SystemClock.elapsedRealtime();
                if(destroyed||generation!=loadGeneration)return;
                List<VisualMemoryItem> items=searching?VisualMemoryRuntime.search(this,query,SEARCH_ITEM_LIMIT):VisualMemoryRuntime.recent(this,INITIAL_ITEM_LIMIT);long afterItems=SystemClock.elapsedRealtime();
                if(destroyed||generation!=loadGeneration)return;
                post(()->{if(generation!=loadGeneration)return;render(s,items,query);android.util.Log.d(PERF_TAG,"load stats="+(afterStats-started)+"ms items="+(afterItems-afterStats)+"ms total="+(afterItems-started)+"ms query="+searching);});
            }catch(Throwable e){post(()->{if(generation!=loadGeneration)return;refresh.setEnabled(true);state.setText("Visual memory stayed safe, but this view could not load: "+safe(e.getMessage()));});}
        });}catch(RejectedExecutionException ignored){}
    }

    void render(VisualMemoryStats s,List<VisualMemoryItem> items,String q){
        if(destroyed)return;list.removeAllViews();refresh.setEnabled(true);modelInstalled=s.getModelInstalled();stats.setText(s.getScreenshots()+" screenshots · "+s.getOcrReady()+" readable · "+s.getSemanticIndexed()+" searchable");
        if(!modelInstalled){state.setText("Semantic search is optional and not installed yet. OCR memory remains available.");semantic.setText("Enable semantic");semantic.setTextColor(CortexUi.ORANGE);semantic.setEnabled(true);}else if(s.getSemanticFailed()>0){state.setText(s.getSemanticFailed()+" semantic item"+(s.getSemanticFailed()==1?" needs":"s need")+" repair · OCR failures "+s.getOcrFailed()+" · newest "+INITIAL_ITEM_LIMIT+" shown");semantic.setText("Repair semantic");semantic.setTextColor(CortexUi.YELLOW);semantic.setEnabled(true);CortexMotion.pulseOnce(semantic);}else if(s.getSemanticPending()>0){state.setText(s.getSemanticPending()+" screenshots are still indexing · OCR failures "+s.getOcrFailed());semantic.setText("Indexing "+s.getSemanticPending());semantic.setTextColor(CortexUi.YELLOW);semantic.setEnabled(false);CortexMotion.breathe(semantic);}else{state.setText("Visual memory is ready · "+s.getSemanticSkipped()+" intentionally skipped · "+s.getOcrFailed()+" OCR failures");semantic.setText("Semantic ready");semantic.setTextColor(CortexUi.GREEN);semantic.setEnabled(false);}
        if(items==null||items.isEmpty()){LinearLayout empty=CortexUi.card(this,22);empty.setPadding(dp(18),dp(20),dp(18),dp(20));empty.addView(CortexUi.eyebrow(this,q==null||q.trim().isEmpty()?"MEMORY IS EMPTY":"NO CONFIDENT MATCH",CortexUi.MUTED));TextView h=CortexUi.plain(this,q==null||q.trim().isEmpty()?"Sync your screenshots":"Try a broader search",19,CortexUi.TEXT);CortexUi.medium(h);h.setPadding(0,dp(8),0,0);empty.addView(h);TextView b=CortexUi.text(this,q==null||q.trim().isEmpty()?"Cortex reads MediaStore without modifying your originals.":"Search combines lexical and semantic retrieval without changing the source image.",12,CortexUi.MUTED);b.setPadding(0,dp(6),0,0);empty.addView(b);list.addView(empty);CortexMotion.enter(empty,0);return;}
        int i=0;for(VisualMemoryItem item:items){View c=card(item);list.addView(c,margins(0,0,0,9));CortexMotion.enter(c,Math.min(i++,5));}
    }

    View card(VisualMemoryItem item){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(10),dp(12),dp(10));CortexUi.pressable(this,card,CortexUi.round(this,CortexUi.SURFACE_2,CortexUi.BORDER_SOFT,21));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(CortexUi.round(this,CortexUi.SURFACE_3,CortexUi.BORDER_SOFT,14));row.addView(image,new LinearLayout.LayoutParams(dp(122),dp(150)));loadThumbnail(image,item.getContentUri());
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(12),dp(1),0,0);row.addView(tx,tp);tx.addView(CortexUi.eyebrow(this,stamp(item.getCapturedAtMillis()),CortexUi.FAINT));TextView title=CortexUi.text(this,friendlyTitle(item),14,CortexUi.TEXT);CortexUi.medium(title);title.setMaxLines(2);title.setPadding(0,dp(6),0,0);tx.addView(title);String preview=item.getOcrText();TextView p=CortexUi.text(this,preview==null||preview.trim().isEmpty()?"No readable text in this screenshot.":clip(preview.trim().replaceAll("\\s+"," "),220),10,CortexUi.MUTED);p.setPadding(0,dp(7),0,0);p.setMaxLines(4);tx.addView(p);String status=itemStatus(item);int color=statusColor(item);TextView chip=CortexUi.chip(this,status,color,!"Ready".equals(status));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(28));cp.setMargins(0,dp(8),0,0);tx.addView(chip,cp);card.addView(row);
        if(!item.getKnowledgeEligible()){TextView provenance=CortexUi.plain(this,"Protected from learning · source remains inspectable",9,CortexUi.YELLOW);provenance.setPadding(dp(2),dp(8),0,0);card.addView(provenance);}card.setOnClickListener(v->openDetail(item));return card;
    }

    void loadThumbnail(ImageView image,String uri){
        final String key=safe(uri);image.setTag(key);Bitmap hit=thumbCache.get(key);if(hit!=null){image.setImageBitmap(hit);image.setAlpha(1f);return;}image.setAlpha(.76f);
        if(key.isEmpty())return;
        try{thumbs.execute(()->{if(destroyed)return;Bitmap b=decodeBitmap(key,THUMBNAIL_MAX_PX);if(b==null||destroyed)return;thumbCache.put(key,b);post(()->{if(!key.equals(image.getTag()))return;image.setImageBitmap(b);image.animate().cancel();image.animate().alpha(1f).setDuration(150).start();});});}catch(RejectedExecutionException ignored){}
    }

    String itemStatus(VisualMemoryItem i){String k=safe(i.getKnowledgeState()),o=safe(i.getOcrState()),s=safe(i.getSemanticState());if("FAILED".equals(o)||"FAILED".equals(s)||"FAILED".equals(k))return"Needs repair";if("BLOCKED".equals(k)||!i.getKnowledgeEligible())return"Protected";if("PENDING".equals(o)||"NOT_PROCESSED".equals(o)||"PENDING".equals(s)||k.isEmpty())return"Processing";if("SKIPPED".equals(k))return"Evidence only";return"Ready";}
    int statusColor(VisualMemoryItem i){String x=itemStatus(i);if("Needs repair".equals(x))return CortexUi.YELLOW;if("Processing".equals(x))return CortexUi.ORANGE;if("Protected".equals(x))return CortexUi.YELLOW;if("Ready".equals(x))return CortexUi.GREEN;return CortexUi.OLIVE;}
    String friendlyTitle(VisualMemoryItem i){String n=clean(i.getDisplayName());String o=safe(i.getOcrText()).replaceAll("\\s+"," ").trim();if((n.toLowerCase(Locale.ROOT).startsWith("screenshot")||n.matches(".*\\d{8,}.*"))&&!o.isEmpty())return clip(o,72);return n;}
    void openDetail(VisualMemoryItem item){Intent i=new Intent(this,VisualMemoryDetailActivity.class);i.putExtra("uri",item.getContentUri());i.putExtra("name",item.getDisplayName());i.putExtra("time",item.getCapturedAtMillis());i.putExtra("ocr",item.getOcrText());i.putExtra("ocr_state",item.getOcrState());i.putExtra("semantic_state",item.getSemanticState());i.putExtra("semantic_error",item.getSemanticLastError());i.putExtra("media_id",item.getMediaId());i.putExtra("knowledge_state",item.getKnowledgeState());i.putExtra("origin",item.getOrigin());i.putExtra("self_score",item.getSelfReferenceScore());i.putExtra("derivation_depth",item.getDerivationDepth());i.putExtra("knowledge_eligible",item.getKnowledgeEligible());i.putExtra("provenance_reason",item.getProvenanceReason());startActivity(i);}

    Bitmap decodeBitmap(String uri,int max){InputStream in=null;try{Uri u=Uri.parse(uri);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;in=getContentResolver().openInputStream(u);BitmapFactory.decodeStream(in,null,bounds);if(in!=null)in.close();in=null;if(bounds.outWidth<=0||bounds.outHeight<=0)return null;int sample=1;while(bounds.outWidth/sample>max||bounds.outHeight/sample>max*2)sample*=2;BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=Math.max(1,sample);o.inPreferredConfig=Bitmap.Config.RGB_565;o.inDither=true;in=getContentResolver().openInputStream(u);return BitmapFactory.decodeStream(in,null,o);}catch(Throwable ignored){return null;}finally{if(in!=null)try{in.close();}catch(Throwable ignored){}}}
    boolean hasMediaPermission(){if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}void requestMediaPermission(){if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},7101);else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},7101);if(state!=null)state.setText("Photo access is required to build Visual Memory. Originals remain read-only.");}@Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==7101&&hasMediaPermission())loadAndMaybeSync();}
    void post(Runnable r){if(destroyed)return;runOnUiThread(()->{if(!destroyed&&!isFinishing()&&!isDestroyed())r.run();});}String stamp(long t){return new SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(new Date(t));}String clean(String s){return s==null||s.trim().isEmpty()?"Screenshot":s.trim();}String clip(String s,int n){return s==null?"":(s.length()<=n?s:s.substring(0,n)+"…");}String safe(String s){return s==null?"":s;}
}
