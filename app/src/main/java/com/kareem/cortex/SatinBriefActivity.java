package com.kareem.cortex;

import android.content.Intent;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** Legacy Satin presentation base. All visual tokens delegate to the locked CortexUi system. */
public class SatinBriefActivity extends PremiumHomeActivity {
    static final int CANVAS=CortexUi.BG,SURFACE=CortexUi.SURFACE,SURFACE_HI=CortexUi.SURFACE_2,INSET=CortexUi.BG;
    static final int PRIMARY=CortexUi.TEXT,SECONDARY=CortexUi.MUTED,MUTED2=CortexUi.FAINT;
    static final int RED=CortexUi.RED,AMBER=CortexUi.ORANGE,VIOLET=CortexUi.YELLOW,INFO=CortexUi.GREEN;
    final Handler ui=new Handler(Looper.getMainLooper());final ArrayList<KnowledgeItem> audioItems=new ArrayList<>();
    MediaPlayer player;boolean prepared=false;int audioIndex=0;CortexRingButton playRing;CortexScrubberView scrub;TextView audioTitle,audioSub,timeNow,timeEnd;Runnable playbackTick;

    @Override void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CANVAS);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(10),dp(18),dp(24));sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));systemHeader();
        TextView loading=label("BUILDING CURRENT BRIEF…",9,MUTED2);loading.setPadding(dp(4),dp(24),0,dp(10));content.addView(loading);
        addSatinNav(root);setContentView(root);CortexUi.fitSystemBars(this,root);
    }

    void systemHeader(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(7),dp(8),dp(4),dp(10));
        View dot=new View(this);dot.setBackground(CortexUi.round(this,RED,Color.TRANSPARENT,999));row.addView(dot,new LinearLayout.LayoutParams(dp(8),dp(8)));
        TextView cortex=label("C O R T E X",14,PRIMARY);cortex.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.setMargins(dp(12),0,0,0);row.addView(cortex,cp);
        View divider=new View(this);divider.setBackgroundColor(Color.argb(75,255,255,255));LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(1),dp(28));dpv.setMargins(dp(12),0,dp(12),0);row.addView(divider,dpv);
        TextView sys=label("SYSTEM",10,SECONDARY);row.addView(sys,new LinearLayout.LayoutParams(0,dp(40),1));
        CortexRingButton settings=new CortexRingButton(this);settings.setProgress(0f);settings.setGlyph(CortexRingButton.Glyph.RECORD);settings.setAccent(INFO);settings.setOnClickListener(v->{try{startActivity(new Intent(this,SettingsActivity.class));}catch(Throwable ignored){}});row.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        content.addView(row);
    }

    @Override void render(PrimeBriefStore.Snapshot s){
        if(destroyed||content==null)return;while(content.getChildCount()>1)content.removeViewAt(1);collectAudio(s);content.addView(signalCard(s),margins(0,dp(8),0,0));if(!audioItems.isEmpty())content.addView(audioCard(s),margins(0,dp(12),0,0));
        if(!s.actions.isEmpty())derivedSection("NEEDS YOU",RED,s.actions,"action",4);
        if(!s.waiting.isEmpty())derivedSection("WAITING",AMBER,s.waiting,"waiting",3);
        if(!s.decisions.isEmpty())derivedSection("DECISIONS",VIOLET,s.decisions,"decision",3);
        if(!s.worthKnowing.isEmpty())derivedSection("WORTH KNOWING",INFO,s.worthKnowing,"info",3);
        if(!s.changes.isEmpty())derivedSection("CHANGED & EVOLVING",INFO,s.changes,"change",3);
        if(!s.reviews.isEmpty()){sectionTitle("NEEDS REVIEW",INFO);LinearLayout r=satinCard(18);r.setGravity(Gravity.CENTER_VERTICAL);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(dp(14),dp(12),dp(12),dp(12));BriefGlyphView g=new BriefGlyphView(this,"review",INFO);r.addView(g,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView t=CortexUi.plain(this,s.reviews.size()+" item"+(s.reviews.size()==1?"":"s")+" need your review",13,PRIMARY);CortexUi.medium(t);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(10),0,0,0);r.addView(t,tp);TextView chip=statusChip("REVIEW",INFO);r.addView(chip,new LinearLayout.LayoutParams(-2,dp(32)));r.setOnClickListener(v->{try{startActivity(new Intent(this,ReviewQueueActivity.class));}catch(Throwable ignored){}});content.addView(r);}
        if(s.empty()){LinearLayout e=satinCard(22);e.setPadding(dp(17),dp(20),dp(17),dp(20));TextView h=CortexUi.plain(this,"Nothing needs you right now",18,PRIMARY);CortexUi.medium(h);e.addView(h);TextView b=CortexUi.text(this,"Cortex is still listening. New captures and derived intelligence will surface here when they need attention.",12,SECONDARY);b.setPadding(0,dp(6),0,0);e.addView(b);content.addView(e,margins(0,dp(14),0,0));}
        content.addView(promptDock(),margins(0,dp(16),0,dp(8)));
    }

    View signalCard(PrimeBriefStore.Snapshot s){
        LinearLayout card=satinCard(22);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(15),dp(15),dp(13),dp(15));
        BriefGlyphView wave=new BriefGlyphView(this,"wave",RED);card.addView(wave,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams txp=new LinearLayout.LayoutParams(0,-2,1);txp.setMargins(dp(12),0,dp(8),0);card.addView(text,txp);
        TextView h=label("AUDIO BRIEFING SIGNAL",10,RED);h.setTypeface(Typeface.create("monospace",Typeface.BOLD));text.addView(h);
        int attention=s.actions.size()+s.waiting.size()+s.decisions.size();TextView b=CortexUi.text(this,attention>0?"Cortex has "+attention+" current signal"+(attention==1?"":"s")+" that may need you.":"Voice transcript & context stream active.",13,PRIMARY);b.setPadding(0,dp(5),0,0);text.addView(b);
        TextView meta=label("TAP DAILY  ·  HOLD WEEKLY",8,MUTED2);meta.setPadding(0,dp(7),0,0);text.addView(meta);
        TextView live=statusChip("●  LIVE",RED);card.addView(live,new LinearLayout.LayoutParams(-2,dp(34)));card.setOnClickListener(v->showComposed(false));card.setOnLongClickListener(v->{showComposed(true);return true;});return card;
    }

    View audioCard(PrimeBriefStore.Snapshot s){
        LinearLayout card=satinCard(24);card.setPadding(dp(15),dp(15),dp(15),dp(13));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);BriefGlyphView source=new BriefGlyphView(this,"waveRing",RED);source.setBackground(CortexUi.round(this,INSET,Color.argb(20,255,255,255),18));top.addView(source,new LinearLayout.LayoutParams(dp(66),dp(66)));
        LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tbp=new LinearLayout.LayoutParams(0,-2,1);tbp.setMargins(dp(14),0,dp(8),0);top.addView(titleBox,tbp);
        audioTitle=CortexUi.plain(this,"Voice recording",20,PRIMARY);CortexUi.medium(audioTitle);audioTitle.setMaxLines(1);titleBox.addView(audioTitle);
        audioSub=CortexUi.plain(this,"Voice note",11,SECONDARY);audioSub.setPadding(0,dp(6),0,0);titleBox.addView(audioSub);TextView voice=statusChip("VOICE",RED);top.addView(voice,new LinearLayout.LayoutParams(-2,dp(34)));card.addView(top);
        LinearLayout seekRow=new LinearLayout(this);seekRow.setGravity(Gravity.CENTER_VERTICAL);seekRow.setPadding(0,dp(15),0,0);timeNow=label("00:00",10,SECONDARY);timeNow.setGravity(Gravity.CENTER_VERTICAL);seekRow.addView(timeNow,new LinearLayout.LayoutParams(dp(48),dp(40)));scrub=new CortexScrubberView(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(40),1);sp.setMargins(dp(4),0,dp(4),0);seekRow.addView(scrub,sp);timeEnd=label("00:00",10,SECONDARY);timeEnd.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);seekRow.addView(timeEnd,new LinearLayout.LayoutParams(dp(48),dp(40)));card.addView(seekRow);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setPadding(0,dp(4),0,dp(5));CortexRingButton prev=new CortexRingButton(this);prev.setGlyph(CortexRingButton.Glyph.PREVIOUS);prev.setAccent(INFO);prev.setOnClickListener(v->selectAudio(audioIndex-1));controls.addView(prev,new LinearLayout.LayoutParams(dp(74),dp(74)));playRing=new CortexRingButton(this);playRing.setGlyph(CortexRingButton.Glyph.PLAY);playRing.setProgress(0);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(100),dp(100));pp.setMargins(dp(24),0,dp(24),0);controls.addView(playRing,pp);playRing.setOnClickListener(v->togglePlayback());CortexRingButton next=new CortexRingButton(this);next.setGlyph(CortexRingButton.Glyph.NEXT);next.setAccent(INFO);next.setOnClickListener(v->selectAudio(audioIndex+1));controls.addView(next,new LinearLayout.LayoutParams(dp(74),dp(74)));card.addView(controls);
        scrub.setListener((fraction,finished)->{if(player==null||!prepared)return;int d=player.getDuration(),pos=Math.max(0,Math.min(d,(int)(d*fraction)));timeNow.setText(fmt(pos));playRing.setProgress(fraction);if(finished)try{player.seekTo(pos);}catch(Throwable ignored){}});
        View line=new View(this);line.setBackgroundColor(Color.argb(24,255,255,255));card.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout insights=new LinearLayout(this);insights.setGravity(Gravity.CENTER_VERTICAL);insights.setPadding(0,dp(10),0,0);insights.addView(insight("⌁",Math.max(1,s.worthKnowing.size())+" key points",RED),new LinearLayout.LayoutParams(0,dp(28),1));insights.addView(insight("◇",s.decisions.size()+" decisions",VIOLET),new LinearLayout.LayoutParams(0,dp(28),1));insights.addView(insight("↗",s.actions.size()+" actions",RED),new LinearLayout.LayoutParams(0,dp(28),1));card.addView(insights);
        ui.post(()->selectAudio(Math.min(audioIndex,audioItems.size()-1)));return card;
    }

    TextView insight(String glyph,String text,int color){TextView v=CortexUi.plain(this,glyph+"  "+text,9,SECONDARY);v.setGravity(Gravity.CENTER);return v;}

    void derivedSection(String title,int color,List<PrimeBriefStore.Item> xs,String glyph,int limit){sectionTitle(title,color);LinearLayout box=satinCard(19);box.setPadding(0,0,0,0);int n=Math.min(limit,xs.size());for(int i=0;i<n;i++){PrimeBriefStore.Item x=xs.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(13),dp(11),dp(11),dp(11));BriefGlyphView icon=new BriefGlyphView(this,glyph,color);row.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(10),0,dp(8),0);row.addView(txt,tp);String tt=x.title==null||x.title.trim().isEmpty()?friendlyFallback(x.kind):x.title.trim();TextView h=CortexUi.text(this,clipLocal(tt,80),13,PRIMARY);CortexUi.medium(h);h.setMaxLines(2);txt.addView(h);String body=x.body==null?"":x.body.trim();TextView m=CortexUi.plain(this,(body.isEmpty()?ageLocal(x.updatedAt):clipLocal(body,90)+"  ·  "+ageLocal(x.updatedAt)),10,SECONDARY);m.setMaxLines(1);m.setPadding(0,dp(4),0,0);txt.addView(m);TextView chip=statusChip(chipLabel(x.kind),color);row.addView(chip,new LinearLayout.LayoutParams(-2,dp(32)));row.setOnClickListener(v->derivedDetail(x));box.addView(row);if(i<n-1){View d=new View(this);d.setBackgroundColor(Color.argb(22,255,255,255));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(dp(13),0,dp(13),0);box.addView(d,lp);}}
        content.addView(box);if(xs.size()>n){TextView more=label("+ "+(xs.size()-n)+" MORE",8,MUTED2);more.setGravity(Gravity.RIGHT);more.setPadding(0,dp(6),dp(4),0);content.addView(more);}
    }

    void sectionTitle(String title,int color){TextView h=label(title,10,color);h.setTypeface(Typeface.create("monospace",Typeface.BOLD));h.setPadding(dp(2),dp(16),0,dp(7));content.addView(h);}

    View promptDock(){
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER_VERTICAL);dock.setPadding(dp(10),dp(6),dp(6),dp(6));dock.setBackground(CortexUi.round(this,INSET,Color.argb(24,255,255,255),22));BriefGlyphView nodes=new BriefGlyphView(this,"nodes",RED);dock.addView(nodes,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView ask=CortexUi.plain(this,"Ask Cortex about this briefing…",12,SECONDARY);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1);ap.setMargins(dp(8),0,dp(4),0);ask.setGravity(Gravity.CENTER_VERTICAL);dock.addView(ask,ap);CortexRingButton record=new CortexRingButton(this);record.setGlyph(CortexRingButton.Glyph.RECORD);record.setProgress(0f);dock.addView(record,new LinearLayout.LayoutParams(dp(58),dp(58)));
        View.OnClickListener brain=v->{long id=audioItems.isEmpty()?0:audioItems.get(Math.max(0,Math.min(audioIndex,audioItems.size()-1))).id;CortexActionExecutor.openBrain(this,id,"Analyze my current Cortex Brief. Connect the audio/context with what needs me, what is waiting, decisions, and useful next actions. Be concise and executable.");};ask.setOnClickListener(brain);nodes.setOnClickListener(brain);
        record.setOnClickListener(v->{try{Intent i=new Intent(this,SatinCaptureActivity.class);i.putExtra("mode","voice");startActivity(i);}catch(Throwable ignored){}});return dock;
    }

    void collectAudio(PrimeBriefStore.Snapshot s){audioItems.clear();HashSet<Long> seen=new HashSet<>();for(KnowledgeItem k:s.recent)if(addAudio(k,seen)&&audioItems.size()>=8)break;if(audioItems.isEmpty())try{for(KnowledgeItem k:db.lexicalSearch("",60))if(addAudio(k,seen)&&audioItems.size()>=8)break;}catch(Throwable ignored){}if(audioIndex>=audioItems.size())audioIndex=0;}
    boolean addAudio(KnowledgeItem k,Set<Long> seen){if(k==null||!"AUDIO".equalsIgnoreCase(k.type)||k.attachmentPath==null||k.attachmentPath.isEmpty()||seen.contains(k.id))return false;try{if(!new File(k.attachmentPath).exists())return false;}catch(Throwable e){return false;}seen.add(k.id);audioItems.add(k);return true;}

    void selectAudio(int index){if(audioItems.isEmpty())return;if(index<0)index=audioItems.size()-1;if(index>=audioItems.size())index=0;audioIndex=index;KnowledgeItem k=audioItems.get(index);releasePlayer();if(audioTitle!=null)audioTitle.setText(k.title==null||k.title.trim().isEmpty()?"Voice recording":clipLocal(k.title.trim(),46));if(audioSub!=null)audioSub.setText("Voice note  ·  "+ageLocal(k.createdAt)+(k.status==null||k.status.isEmpty()?"":"\n"+statusLine(k.status)));try{player=new MediaPlayer();player.setDataSource(k.attachmentPath);player.prepare();prepared=true;int d=player.getDuration();timeNow.setText("00:00");timeEnd.setText(fmt(d));scrub.setProgress(0);playRing.setProgress(0);playRing.setGlyph(CortexRingButton.Glyph.PLAY);player.setOnCompletionListener(mp->{playRing.setGlyph(CortexRingButton.Glyph.PLAY);playRing.setProgress(1f);scrub.setProgress(1f);timeNow.setText(fmt(mp.getDuration()));});}catch(Throwable e){prepared=false;if(audioSub!=null)audioSub.setText("Audio unavailable  ·  "+ageLocal(k.createdAt));}}

    void togglePlayback(){if(player==null||!prepared)return;try{if(player.isPlaying()){player.pause();playRing.setGlyph(CortexRingButton.Glyph.PLAY);}else{if(player.getCurrentPosition()>=Math.max(0,player.getDuration()-50))player.seekTo(0);player.start();playRing.setGlyph(CortexRingButton.Glyph.PAUSE);startPlaybackTick();}}catch(Throwable ignored){}}
    void startPlaybackTick(){if(playbackTick==null)playbackTick=new Runnable(){public void run(){if(player==null||!prepared||destroyed)return;try{int d=Math.max(1,player.getDuration()),p=player.getCurrentPosition();float f=Math.max(0f,Math.min(1f,p/(float)d));playRing.setProgress(f);scrub.setProgress(f);timeNow.setText(fmt(p));if(player.isPlaying())ui.postDelayed(this,120);}catch(Throwable ignored){}}};ui.removeCallbacks(playbackTick);ui.post(playbackTick);}
    void releasePlayer(){if(playbackTick!=null)ui.removeCallbacks(playbackTick);prepared=false;if(player!=null){try{player.stop();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;}}
    @Override protected void onDestroy(){releasePlayer();super.onDestroy();}

    LinearLayout satinCard(int radius){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setBackground(CortexUi.gradient(this,SURFACE_HI,SURFACE,Color.argb(22,255,255,255),radius));if(Build.VERSION.SDK_INT>=21)v.setElevation(dp(7));return v;}
    TextView label(String s,int size,int color){TextView v=CortexUi.plain(this,s,size,color);v.setTypeface(Typeface.create("monospace",Typeface.NORMAL));if(Build.VERSION.SDK_INT>=21)v.setLetterSpacing(.10f);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    TextView statusChip(String text,int color){TextView v=label(text,9,color);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setBackground(CortexUi.round(this,Color.argb(10,Color.red(color),Color.green(color),Color.blue(color)),Color.argb(105,Color.red(color),Color.green(color),Color.blue(color)),12));return v;}
    LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(l,t,r,b);return p;}

    void addSatinNav(LinearLayout root){LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER);bar.setPadding(dp(8),dp(4),dp(8),dp(7));bar.setBackground(CortexUi.round(this,Color.rgb(8,9,12),Color.argb(20,255,255,255),20));addNav(bar,"INPUT",MUTED2,InputActivity.class);addNav(bar,"BRIEF",RED,null);addNav(bar,"PEOPLE",MUTED2,PeopleProjectsActivity.class);addNav(bar,"BRAIN",MUTED2,AskCortexActivity.class);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.setMargins(dp(12),dp(4),dp(12),dp(8));root.addView(bar,p);}
    void addNav(LinearLayout bar,String text,int color,Class<?> target){TextView v=label(text,8,color);v.setGravity(Gravity.CENTER);if(target==null)v.setBackground(CortexUi.round(this,Color.argb(22,255,42,36),Color.TRANSPARENT,13));else v.setOnClickListener(x->{try{Intent i=new Intent(this,target);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}catch(Throwable ignored){}});bar.addView(v,new LinearLayout.LayoutParams(0,-1,1));}

    static String fmt(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.US,"%02d:%02d",sec/60,sec%60);}String statusLine(String s){if("analyzed".equalsIgnoreCase(s))return"Transcribed & analyzed";if("analyzing".equalsIgnoreCase(s)||"queued".equalsIgnoreCase(s))return"Cortex is analyzing";return s.replace('_',' ');}String chipLabel(String kind){String k=kind==null?"":kind.toUpperCase(Locale.US);if("ACTION".equals(k))return"ACTION";if("WAITING".equals(k))return"WAITING";if("DECISION".equals(k))return"DECISION";return"INFO";}String friendlyFallback(String k){if(k==null)return"Cortex signal";return k.replace('_',' ').toLowerCase(Locale.US);}String clipLocal(String s,int n){if(s==null)return"";String x=s.replace('\n',' ').trim();return x.length()<=n?x:x.substring(0,Math.max(0,n-1))+"…";}String ageLocal(long t){long d=Math.max(0,System.currentTimeMillis()-t),m=d/60000;if(m<1)return"now";if(m<60)return m+"m ago";long h=m/60;if(h<24)return h+"h ago";long days=h/24;if(days<7)return days+"d ago";return new SimpleDateFormat("d MMM",Locale.US).format(new Date(t));}

    static final class BriefGlyphView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),dot=new Paint(Paint.ANTI_ALIAS_FLAG);final String kind;final float d;final int accent;
        BriefGlyphView(android.content.Context c,String k,int color){super(c);kind=k;accent=color;d=getResources().getDisplayMetrics().density;p.setColor(Color.rgb(207,211,219));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f*d);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);dot.setColor(color);dot.setStyle(Paint.Style.FILL);setClickable(true);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2,s=Math.min(w,h)*.28f;Path q=new Path();if(kind.startsWith("wave")){for(int i=-2;i<=2;i++){float x=cx+i*s*.38f,hh=s*(i==0?1f:(Math.abs(i)==1?.72f:.45f));c.drawLine(x,cy-hh,x,cy+hh,p);}if("waveRing".equals(kind)){RectF r=new RectF(cx-s*1.5f,cy-s*1.5f,cx+s*1.5f,cy+s*1.5f);Paint a=new Paint(p);a.setColor(accent);a.setStrokeWidth(2*d);c.drawArc(r,-90,225,false,a);}}else if("action".equals(kind)){c.drawRect(cx-s,cy-s,cx+s*.7f,cy+s*.7f,p);c.drawLine(cx,cy,cx+s,cy-s,p);c.drawLine(cx+s*.45f,cy-s,cx+s,cy-s,p);c.drawLine(cx+s,cy-s,cx+s,cy-s*.45f,p);}else if("waiting".equals(kind)){RectF r=new RectF(cx-s,cy-s,cx+s,cy+s);c.drawArc(r,-70,300,false,p);c.drawLine(cx,cy,cx,cy-s*.55f,p);c.drawLine(cx,cy,cx+s*.45f,cy,p);}else if("decision".equals(kind)){c.drawCircle(cx,cy+s*.65f,s*.18f,p);c.drawCircle(cx-s*.65f,cy-s*.65f,s*.18f,p);c.drawCircle(cx+s*.65f,cy-s*.65f,s*.18f,p);c.drawLine(cx,cy+s*.47f,cx,cy,p);c.drawLine(cx,cy,cx-s*.65f,cy-s*.47f,p);c.drawLine(cx,cy,cx+s*.65f,cy-s*.47f,p);}else if("nodes".equals(kind)){for(int i=0;i<7;i++){double a=i*Math.PI*2/7;c.drawCircle(cx+(float)Math.cos(a)*s*.75f,cy+(float)Math.sin(a)*s*.75f,s*.10f,p);}c.drawCircle(cx,cy,s*.13f,p);}else{c.drawRect(cx-s*.8f,cy-s,cx+s*.55f,cy+s,p);for(int i=-1;i<=1;i++)c.drawLine(cx-s*.45f,cy+i*s*.45f,cx+s*.25f,cy+i*s*.45f,p);}c.drawCircle(cx+s*1.08f,cy-s*1.02f,2.4f*d,dot);}
    }
}
