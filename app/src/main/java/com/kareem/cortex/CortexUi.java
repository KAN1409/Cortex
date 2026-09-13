package com.kareem.cortex;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cortex premium AMOLED design system: true-black canvas, neutral graphite surfaces, localized semantic light only. */
public final class CortexUi {
    public static final int BG=Color.rgb(2,2,2), SURFACE=Color.rgb(8,8,8), SURFACE_2=Color.rgb(14,14,14), SURFACE_3=Color.rgb(20,20,20);
    public static final int TEXT=Color.rgb(247,247,243), MUTED=Color.rgb(166,166,161), FAINT=Color.rgb(94,94,91);
    public static final int LIME=Color.rgb(190,221,82), OLIVE=Color.rgb(126,146,55), GREEN=Color.rgb(111,202,86), YELLOW=Color.rgb(240,184,56), ORANGE=Color.rgb(242,154,49), RED=Color.rgb(239,92,73);
    public static final int ACCENT=LIME,SIGNAL=LIME,AMBER=YELLOW,SAGE=GREEN,INFO=GREEN,VIOLET=OLIVE,COPPER=ORANGE,CORAL=RED,GOLD=YELLOW;
    public static final int BORDER=Color.rgb(45,45,45), BORDER_SOFT=Color.rgb(28,28,28), HAIRLINE=Color.argb(42,235,235,228);
    public static final int R_HERO=30,R_CARD=22,R_ROW=18,R_CONTROL=16;
    private CortexUi(){}
    public static int dp(Activity a,int v){return(int)(v*a.getResources().getDisplayMetrics().density+.5f);}

    public static void applyWindow(Activity a){
        Window w=a.getWindow();w.setStatusBarColor(BG);w.setNavigationBarColor(BG);if(Build.VERSION.SDK_INT>=29)w.setNavigationBarContrastEnforced(false);if(Build.VERSION.SDK_INT>=23)w.getDecorView().setSystemUiVisibility(0);
        try{a.overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}catch(Throwable ignored){}
        // Reference rule: no full-screen green wash. Accent light belongs only to active/local components.
        try{com.kareem.cortex.visualmemory.ScreenshotCaptureProvenance.register(a);}catch(Throwable ignored){}
    }

    public static void fitSystemBars(Activity a,View root){final int pl=root.getPaddingLeft(),pt=root.getPaddingTop(),pr=root.getPaddingRight(),pb=root.getPaddingBottom();root.setOnApplyWindowInsetsListener((v,in)->{int l,t,r,b;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=in.getInsets(WindowInsets.Type.systemBars());l=x.left;t=x.top;r=x.right;b=x.bottom;}else{l=in.getSystemWindowInsetLeft();t=in.getSystemWindowInsetTop();r=in.getSystemWindowInsetRight();b=in.getSystemWindowInsetBottom();}v.setPadding(pl+l,pt+t,pr+r,pb+b);return in;});root.requestApplyInsets();}
    public static GradientDrawable round(Activity a,int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(a,radius));if(stroke!=Color.TRANSPARENT)g.setStroke(dp(a,1),stroke);return g;}
    public static GradientDrawable gradient(Activity a,int start,int end,int stroke,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{start,end});g.setCornerRadius(dp(a,radius));if(stroke!=Color.TRANSPARENT)g.setStroke(dp(a,1),stroke);return g;}
    public static GradientDrawable velvet(Activity a,int radius){return gradient(a,SURFACE_3,SURFACE,HAIRLINE,radius);}public static GradientDrawable matte(Activity a,int radius){return round(a,SURFACE_2,HAIRLINE,radius);}public static View divider(Activity a){View v=new View(a);v.setBackgroundColor(BORDER_SOFT);return v;}
    public static <T extends View>T raised(Activity a,T v,int e){if(Build.VERSION.SDK_INT>=21){v.setElevation(dp(a,e));v.setTranslationZ(0);}return v;}public static CortexGlyphView glyph(Activity a,String k,int c,boolean d){CortexGlyphView g=new CortexGlyphView(a,k,c,d);raised(a,g,1);return g;}

    public static View pressable(Activity a,View v,GradientDrawable base){
        if(Build.VERSION.SDK_INT>=21)v.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(22,190,221,82)),base,null));else v.setBackground(base);v.setClickable(true);v.setFocusable(true);raised(a,v,1);
        v.setOnTouchListener((x,e)->{if(!CortexMotion.allowed(a))return false;int act=e.getActionMasked();if(act==MotionEvent.ACTION_DOWN){x.animate().cancel();x.animate().scaleX(.985f).scaleY(.985f).translationZ(-dp(a,1)).setDuration(90).start();}else if(act==MotionEvent.ACTION_UP||act==MotionEvent.ACTION_CANCEL){x.animate().cancel();x.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(150).start();}return false;});return v;
    }

    public static TextView text(Activity a,String s,int sp,int c){TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(c);v.setLineSpacing(0,1.12f);v.setIncludeFontPadding(false);CortexTextUi.setReadable(v,s==null?"":s);return v;}
    public static TextView plain(Activity a,String s,int sp,int c){TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(c);v.setIncludeFontPadding(false);CortexTextUi.setPlain(v,s==null?"":s);return v;}
    public static void medium(TextView v){v.setTypeface(Typeface.create("sans-serif-medium",0));}public static void bold(TextView v){v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));}
    public static LinearLayout card(Activity a,int r){LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,17),dp(a,16),dp(a,17),dp(a,16));c.setBackground(velvet(a,r));raised(a,c,1);return c;}

    public static TextView chip(Activity a,String label,int color,boolean strong){TextView v=plain(a,label,strong?11:10,color);if(strong)medium(v);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(a,28));v.setPadding(dp(a,12),0,dp(a,12),0);int fill=Color.argb(strong?18:7,Color.red(color),Color.green(color),Color.blue(color));int stroke=Color.argb(strong?82:42,Color.red(color),Color.green(color),Color.blue(color));v.setBackground(round(a,fill,stroke,999));return raised(a,v,strong?1:0);}
    public static TextView section(Activity a,String s){TextView h=plain(a,s,11,MUTED);medium(h);if(Build.VERSION.SDK_INT>=21)h.setLetterSpacing(.035f);h.setPadding(dp(a,1),dp(a,24),0,dp(a,10));return h;}
    public static TextView action(Activity a,String label,int color,boolean filled){TextView b=plain(a,label,12,color);medium(b);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(a,44));b.setPadding(dp(a,15),0,dp(a,15),0);int wash=Color.argb(filled?28:6,Color.red(color),Color.green(color),Color.blue(color));int stroke=Color.argb(filled?110:48,Color.red(color),Color.green(color),Color.blue(color));pressable(a,b,round(a,wash,stroke,R_CONTROL));return b;}
    public static TextView eyebrow(Activity a,String label,int color){TextView v=plain(a,label==null?"":label.toUpperCase(),9,color);medium(v);if(Build.VERSION.SDK_INT>=21)v.setLetterSpacing(.13f);return v;}
    public static LinearLayout metric(Activity a,String value,String label,int color){LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));box.setBackground(round(a,Color.argb(5,255,255,255),BORDER_SOFT,R_CONTROL));TextView v=plain(a,value,20,color);medium(v);box.addView(v);TextView l=plain(a,label,9,MUTED);if(Build.VERSION.SDK_INT>=21)l.setLetterSpacing(.04f);l.setPadding(0,dp(a,3),0,0);box.addView(l);return box;}

    public static LinearLayout header(Activity a,String eyebrow,String title,int accent,String actionLabel,View.OnClickListener action){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,1),dp(a,8),dp(a,1),dp(a,11));LinearLayout titles=new LinearLayout(a);titles.setOrientation(LinearLayout.VERTICAL);row.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        titles.addView(eyebrow(a,eyebrow,accent));TextView h=plain(a,title,34,TEXT);medium(h);h.setPadding(0,dp(a,2),0,0);titles.addView(h);
        if(actionLabel!=null&&!actionLabel.trim().isEmpty()){TextView b=chip(a,actionLabel,MUTED,false);if(action!=null)b.setOnClickListener(action);row.addView(b,new LinearLayout.LayoutParams(-2,dp(a,36)));}
        return row;
    }

    public static LinearLayout statusRow(Activity a,String title,String detail,String icon,int color){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));row.addView(glyph(a,icon,color,true),new LinearLayout.LayoutParams(dp(a,40),dp(a,40)));LinearLayout tx=new LinearLayout(a);tx.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(a,11),0,0,0);row.addView(tx,p);TextView h=plain(a,title,13,TEXT);medium(h);tx.addView(h);TextView d=text(a,detail,10,MUTED);d.setPadding(0,dp(a,3),0,0);tx.addView(d);return row;
    }

    public static int semanticFor(String k){if(k==null)return LIME;k=k.toLowerCase();if(k.contains("urgent")||k.contains("review")||k.contains("failed")||k.contains("error"))return RED;if(k.contains("wait")||k.contains("remind")||k.contains("process"))return YELLOW;if(k.contains("input")||k.contains("capture")||k.contains("play"))return ORANGE;if(k.contains("people")||k.contains("project")||k.contains("useful")||k.contains("complete")||k.contains("ready"))return LIME;return OLIVE;}

    public static void addBottomNav(Activity a,LinearLayout root,String selected,Runnable ignored){
        String cur=navKey(selected);LinearLayout bar=new LinearLayout(a);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(a,7),dp(a,6),dp(a,7),dp(a,6));bar.setBackground(matte(a,25));raised(a,bar,7);
        addNav(a,bar,"now","Now",cur,NowActivity.class);addNav(a,bar,"memory","Memory",cur,VisualMemoryActivity.class);addCenter(a,bar);addNav(a,bar,"work","Work",cur,WorkWorkspaceActivity.class);addNav(a,bar,"capture","Capture",cur,CaptureHubActivity.class);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,72));p.setMargins(dp(a,14),dp(a,5),dp(a,14),dp(a,10));root.addView(bar,p);if(CortexMotion.allowed(a)){bar.setAlpha(.80f);bar.setTranslationY(dp(a,7));bar.animate().alpha(1f).translationY(0).setDuration(280).start();}fitSystemBars(a,root);
    }
    private static void addCenter(Activity a,LinearLayout row){TextView plus=plain(a,"+",34,BG);plus.setGravity(Gravity.CENTER);plus.setTypeface(Typeface.create("sans-serif-light",0));GradientDrawable base=round(a,LIME,Color.rgb(211,233,130),999);pressable(a,plus,base);raised(a,plus,8);plus.setContentDescription("Capture something");plus.setOnClickListener(v->{CortexMotion.haptic(v,true);if(CortexMotion.allowed(a))v.animate().rotation(45f).scaleX(.94f).scaleY(.94f).setDuration(130).withEndAction(()->{CortexNavigation.openInput(a);v.setRotation(0f);v.setScaleX(1f);v.setScaleY(1f);}).start();else CortexNavigation.openInput(a);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(a,58),dp(a,58));p.setMargins(dp(a,5),0,dp(a,5),0);row.addView(plus,p);}
    private static String navKey(String s){if("home".equals(s)||"focus".equals(s)||"now".equals(s))return"now";if("brief".equals(s)||"picbrain".equals(s)||"visualmemory".equals(s)||"memory".equals(s))return"memory";if("work".equals(s)||"workvault".equals(s)||"archive".equals(s))return"work";if("vault".equals(s)||"people".equals(s)||"capture".equals(s)||"input".equals(s))return"capture";if("ask".equals(s)||"brain".equals(s))return"now";return s==null?"":s;}
    private static void addNav(Activity a,LinearLayout row,String key,String label,String selected,Class<?> cls){
        boolean on=key.equals(selected);int idle=mix(TEXT,MUTED,.72f);String icon="now".equals(key)?"bolt":("capture".equals(key)?"phone":("memory".equals(key)?"photo":("work".equals(key)?"project":key)));
        LinearLayout item=new LinearLayout(a);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(a,3),dp(a,3),dp(a,3),dp(a,1));item.setBackground(round(a,on?Color.argb(8,190,221,82):Color.TRANSPARENT,on?Color.argb(24,190,221,82):Color.TRANSPARENT,17));CortexGlyphView glyph=glyph(a,icon,on?LIME:idle,on);item.addView(glyph,new LinearLayout.LayoutParams(dp(a,31),dp(a,31)));TextView l=plain(a,label,8,on?LIME:MUTED);l.setGravity(Gravity.CENTER);l.setMaxLines(1);if(on)medium(l);item.addView(l,new LinearLayout.LayoutParams(-1,dp(a,17)));item.setContentDescription(label);
        if(on&&CortexMotion.allowed(a)){item.setScaleX(.96f);item.setScaleY(.96f);item.animate().scaleX(1f).scaleY(1f).setDuration(220).start();}
        item.setOnClickListener(v->{if(on)return;CortexMotion.haptic(v,false);if(CortexMotion.allowed(a))v.animate().scaleX(.94f).scaleY(.94f).setDuration(80).withEndAction(()->CortexNavigation.openPrimary(a,cls)).start();else CortexNavigation.openPrimary(a,cls);});row.addView(item,new LinearLayout.LayoutParams(0,-1,1));
    }
    private static int mix(int a,int b,float q){q=Math.max(0,Math.min(1,q));float p=1-q;return Color.rgb((int)(Color.red(a)*p+Color.red(b)*q),(int)(Color.green(a)*p+Color.green(b)*q),(int)(Color.blue(a)*p+Color.blue(b)*q));}
}
