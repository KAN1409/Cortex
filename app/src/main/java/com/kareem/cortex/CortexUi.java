package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Locked PRIME visual system from the approved preview.
 * Matte graphite shell, restrained tactile depth, warm semantic color only.
 * No blue, purple, glossy glass, mirrored reflections, or emoji icon drift.
 */
public final class CortexUi {
    public static final int BG = Color.rgb(7,7,8);
    public static final int SURFACE = Color.rgb(15,15,16);
    public static final int SURFACE_2 = Color.rgb(20,20,22);
    public static final int SURFACE_3 = Color.rgb(26,26,28);
    public static final int TEXT = Color.rgb(244,243,239);
    public static final int MUTED = Color.rgb(164,163,160);
    public static final int FAINT = Color.rgb(103,103,106);

    public static final int RED = Color.rgb(255,72,62);
    public static final int ORANGE = Color.rgb(255,146,42);
    public static final int YELLOW = Color.rgb(241,188,52);
    public static final int GREEN = Color.rgb(105,194,82);

    public static final int ACCENT = RED;
    public static final int SIGNAL = RED;
    public static final int AMBER = ORANGE;
    public static final int SAGE = GREEN;
    public static final int INFO = GREEN;
    public static final int VIOLET = YELLOW;

    public static final int BORDER = Color.rgb(45,45,48);
    public static final int BORDER_SOFT = Color.rgb(31,31,34);
    public static final int HAIRLINE = Color.argb(40,255,255,255);

    public static final int COPPER = ORANGE;
    public static final int CORAL = RED;
    public static final int GOLD = YELLOW;

    private CortexUi() {}

    public static int dp(Activity a,int v){return (int)(v*a.getResources().getDisplayMetrics().density+.5f);}

    public static void applyWindow(Activity a){
        Window w=a.getWindow();w.setStatusBarColor(BG);w.setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=29)w.setNavigationBarContrastEnforced(false);
        if(Build.VERSION.SDK_INT>=23)w.getDecorView().setSystemUiVisibility(0);
    }

    public static void fitSystemBars(Activity a,View root){
        final int pl=root.getPaddingLeft(),pt=root.getPaddingTop(),pr=root.getPaddingRight(),pb=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int l=0,t=0,r=0,b=0;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=insets.getInsets(WindowInsets.Type.systemBars());l=x.left;t=x.top;r=x.right;b=x.bottom;}
            else{l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();}
            v.setPadding(pl+l,pt+t,pr+r,pb+b);return insets;
        });root.requestApplyInsets();
    }

    public static GradientDrawable round(Activity a,int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(a,radius));if(stroke!=Color.TRANSPARENT)g.setStroke(dp(a,1),stroke);return g;}
    public static GradientDrawable gradient(Activity a,int start,int end,int stroke,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{start,end});g.setCornerRadius(dp(a,radius));if(stroke!=Color.TRANSPARENT)g.setStroke(dp(a,1),stroke);return g;}
    public static GradientDrawable velvet(Activity a,int radius){return gradient(a,SURFACE_2,SURFACE,HAIRLINE,radius);}
    public static GradientDrawable matte(Activity a,int radius){return round(a,SURFACE,HAIRLINE,radius);}
    public static View divider(Activity a){View v=new View(a);v.setBackgroundColor(BORDER_SOFT);return v;}

    public static <T extends View> T raised(Activity a,T v,int elevationDp){if(Build.VERSION.SDK_INT>=21){v.setElevation(dp(a,elevationDp));v.setTranslationZ(0f);}return v;}
    public static CortexGlyphView glyph(Activity a,String kind,int color,boolean dot){CortexGlyphView g=new CortexGlyphView(a,kind,color,dot);raised(a,g,4);return g;}

    public static View pressable(Activity a,View v,GradientDrawable base){
        if(Build.VERSION.SDK_INT>=21)v.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(18,255,255,255)),base,null));else v.setBackground(base);
        v.setClickable(true);v.setFocusable(true);raised(a,v,3);final int slop=ViewConfiguration.get(a).getScaledTouchSlop();
        v.setOnTouchListener(new View.OnTouchListener(){float downX,downY;boolean moved;@Override public boolean onTouch(View x,MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();moved=false;if(Build.VERSION.SDK_INT>=21){x.setTranslationZ(-dp(a,1));x.setScaleX(.992f);x.setScaleY(.992f);}break;case MotionEvent.ACTION_MOVE:if(Math.abs(e.getX()-downX)>slop||Math.abs(e.getY()-downY)>slop)moved=true;break;case MotionEvent.ACTION_UP:if(Build.VERSION.SDK_INT>=21){x.setTranslationZ(0f);x.setScaleX(1f);x.setScaleY(1f);}if(!moved&&e.getX()>=0&&e.getX()<=x.getWidth()&&e.getY()>=0&&e.getY()<=x.getHeight())CortexHaptics.press(x);break;case MotionEvent.ACTION_CANCEL:if(Build.VERSION.SDK_INT>=21){x.setTranslationZ(0f);x.setScaleX(1f);x.setScaleY(1f);}break;}return false;}});return v;
    }

    public static TextView text(Activity a,String s,int sp,int color){TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(color);v.setLineSpacing(0,1.05f);CortexTextUi.setReadable(v,s==null?"":s);return v;}
    public static TextView plain(Activity a,String s,int sp,int color){TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(color);CortexTextUi.setPlain(v,s==null?"":s);return v;}
    public static void medium(TextView v){v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
    public static void bold(TextView v){v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));}

    public static LinearLayout card(Activity a,int radius){LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15));c.setBackground(velvet(a,radius));raised(a,c,4);return c;}
    public static TextView chip(Activity a,String label,int color,boolean strong){TextView v=plain(a,label,strong?11:10,color);if(strong)medium(v);v.setGravity(Gravity.CENTER);v.setPadding(dp(a,11),0,dp(a,11),0);int fill=Color.argb(strong?18:10,Color.red(color),Color.green(color),Color.blue(color));int stroke=Color.argb(strong?86:58,Color.red(color),Color.green(color),Color.blue(color));v.setBackground(round(a,fill,stroke,999));raised(a,v,strong?3:2);return v;}
    public static TextView section(Activity a,String title){TextView h=plain(a,title,11,MUTED);medium(h);h.setPadding(0,dp(a,22),0,dp(a,9));return h;}
    public static TextView action(Activity a,String label,int color,boolean filled){TextView b=plain(a,label,12,color);medium(b);b.setGravity(Gravity.CENTER);b.setPadding(dp(a,14),0,dp(a,14),0);int wash=Color.argb(filled?22:8,Color.red(color),Color.green(color),Color.blue(color));int stroke=Color.argb(filled?110:64,Color.red(color),Color.green(color),Color.blue(color));pressable(a,b,round(a,wash,stroke,15));raised(a,b,filled?5:3);return b;}

    public static int semanticFor(String key){if(key==null)return RED;String k=key.toLowerCase();if(k.contains("wait")||k.contains("remind"))return ORANGE;if(k.contains("decision"))return YELLOW;if(k.contains("people")||k.contains("project")||k.contains("info")||k.contains("useful")||k.contains("complete"))return GREEN;if(k.contains("input")||k.contains("capture")||k.contains("play")||k.contains("change"))return ORANGE;return RED;}

    /** Lightweight fixed navigation: a quiet hairline + four destinations, not a floating card stack. */
    public static void addBottomNav(Activity a,LinearLayout root,String selected,Runnable ignoredMoreAction){
        String current=primeKey(selected);View line=divider(a);root.addView(line,new LinearLayout.LayoutParams(-1,dp(a,1)));LinearLayout bar=new LinearLayout(a);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER);bar.setPadding(dp(a,8),dp(a,3),dp(a,8),dp(a,4));bar.setBackgroundColor(BG);
        addNav(a,bar,"input","Input",current,InputActivity.class);addNav(a,bar,"brief","Brief",current,ProposalBriefActivity.class);addNav(a,bar,"people","People / Projects",current,ProposalPeopleProjectsActivity.class);addNav(a,bar,"brain","Brain",current,ProposalAskCortexActivity.class);
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(a,57)));fitSystemBars(a,root);
    }
    private static String primeKey(String s){if("home".equals(s)||"focus".equals(s)||"brief".equals(s))return"brief";if("vault".equals(s)||"people".equals(s))return"people";if("ask".equals(s)||"brain".equals(s))return"brain";if("input".equals(s))return"input";return s==null?"":s;}
    private static int navColor(String key){if("input".equals(key))return ORANGE;if("brief".equals(key))return RED;if("people".equals(key))return GREEN;return YELLOW;}

    private static void addNav(Activity a,LinearLayout row,String key,String label,String selected,Class<?> cls){
        boolean on=key.equals(selected);int semantic=navColor(key),idle=mix(semantic,FAINT,.84f);LinearLayout item=new LinearLayout(a);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(a,2),dp(a,2),dp(a,2),0);item.setBackgroundColor(Color.TRANSPARENT);
        CortexGlyphView icon=glyph(a,key,on?semantic:idle,on);if(!on&&Build.VERSION.SDK_INT>=21)icon.setElevation(0);item.addView(icon,new LinearLayout.LayoutParams(dp(a,27),dp(a,27)));TextView l=plain(a,label,8,on?TEXT:FAINT);l.setGravity(Gravity.CENTER);l.setMaxLines(1);if(on)medium(l);item.addView(l,new LinearLayout.LayoutParams(-1,dp(a,17)));
        item.setOnClickListener(v->{CortexHaptics.press(v);if(on)return;Intent i=new Intent(a,cls);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);a.startActivity(i);});row.addView(item,new LinearLayout.LayoutParams(0,-1,1));
    }

    private static int mix(int a,int b,float amountOfB){float q=Math.max(0f,Math.min(1f,amountOfB)),p=1f-q;return Color.rgb((int)(Color.red(a)*p+Color.red(b)*q),(int)(Color.green(a)*p+Color.green(b)*q),(int)(Color.blue(a)*p+Color.blue(b)*q));}
}
