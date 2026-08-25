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

/** Unified Cortex Velvet shell: warm multicolor semantics, tactile depth and raised controls. */
public final class CortexUi {
    // Warm velvet palette. Colors are semantic and deliberately mixed across a dark tactile shell.
    public static final int BG = Color.rgb(8,7,6);
    public static final int SURFACE = Color.rgb(18,16,14);
    public static final int SURFACE_2 = Color.rgb(27,23,19);
    public static final int SURFACE_3 = Color.rgb(36,31,25);
    public static final int TEXT = Color.rgb(255,248,236);
    public static final int MUTED = Color.rgb(194,183,166);
    public static final int FAINT = Color.rgb(126,115,100);

    public static final int ORANGE = Color.rgb(255,138,52);     // capture / playback / active interaction
    public static final int YELLOW = Color.rgb(246,201,69);    // waiting / reminders / attention
    public static final int GREEN = Color.rgb(105,211,145);    // people / confirmed / useful / complete
    public static final int RED = Color.rgb(255,85,77);        // Brain / recording / urgent / destructive
    public static final int ACCENT = ORANGE;
    public static final int AMBER = YELLOW;
    public static final int SAGE = GREEN;
    public static final int SIGNAL = RED;
    public static final int INFO = GREEN;
    // Kept as a compatibility semantic name; visually decisions now live in the warm orange family.
    public static final int VIOLET = ORANGE;

    public static final int BORDER = Color.rgb(54,46,37);
    public static final int BORDER_SOFT = Color.rgb(33,28,23);

    // Legacy aliases retained so old surfaces inherit the new system without visual drift.
    public static final int COPPER = ORANGE;
    public static final int CORAL = RED;
    public static final int GOLD = YELLOW;

    private CortexUi() {}

    public static int dp(Activity a,int v){return (int)(v*a.getResources().getDisplayMetrics().density+.5f);}

    public static void applyWindow(Activity a){
        Window w=a.getWindow();
        w.setStatusBarColor(BG);w.setNavigationBarColor(BG);
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
        });
        root.requestApplyInsets();
    }

    public static GradientDrawable round(Activity a,int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(a,radius));if(stroke!=Color.TRANSPARENT)g.setStroke(dp(a,1),stroke);return g;}
    public static GradientDrawable gradient(Activity a,int start,int end,int stroke,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});g.setCornerRadius(dp(a,radius));if(stroke!=Color.TRANSPARENT)g.setStroke(dp(a,1),stroke);return g;}
    public static GradientDrawable velvet(Activity a,int radius){return gradient(a,SURFACE_3,SURFACE,Color.argb(42,255,232,196),radius);}
    public static View divider(Activity a){View v=new View(a);v.setBackgroundColor(BORDER_SOFT);return v;}

    /** Gives a view a soft raised plate with real elevation and a subtle tactile press motion. */
    public static <T extends View> T raised(Activity a,T v,int elevationDp){
        if(Build.VERSION.SDK_INT>=21){v.setElevation(dp(a,elevationDp));v.setTranslationZ(0f);}
        return v;
    }

    public static View pressable(Activity a,View v,GradientDrawable base){
        if(Build.VERSION.SDK_INT>=21)v.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(28,255,244,220)),base,null));else v.setBackground(base);
        v.setClickable(true);v.setFocusable(true);raised(a,v,3);
        v.setOnTouchListener((x,e)->{
            if(Build.VERSION.SDK_INT>=21){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)x.setTranslationZ(-dp(a,2));else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)x.setTranslationZ(0f);}
            return false;
        });
        return v;
    }
    public static TextView text(Activity a,String s,int sp,int color){TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(color);v.setLineSpacing(0,1.05f);CortexTextUi.setReadable(v,s==null?"":s);return v;}
    public static TextView plain(Activity a,String s,int sp,int color){TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(color);CortexTextUi.setPlain(v,s==null?"":s);return v;}
    public static void medium(TextView v){v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
    public static void bold(TextView v){v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));}

    public static LinearLayout card(Activity a,int radius){
        LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15));
        c.setBackground(velvet(a,radius));raised(a,c,5);return c;
    }
    public static TextView chip(Activity a,String label,int color,boolean strong){
        TextView v=plain(a,label,strong?11:10,color);if(strong)medium(v);v.setGravity(Gravity.CENTER);v.setPadding(dp(a,11),0,dp(a,11),0);
        int fill=Color.argb(strong?34:20,Color.red(color),Color.green(color),Color.blue(color));int stroke=Color.argb(strong?80:46,Color.red(color),Color.green(color),Color.blue(color));
        v.setBackground(gradient(a,Color.argb(strong?42:26,Color.red(color),Color.green(color),Color.blue(color)),fill,stroke,999));raised(a,v,strong?4:2);return v;
    }
    public static TextView section(Activity a,String title){TextView h=plain(a,title,11,MUTED);medium(h);h.setPadding(0,dp(a,22),0,dp(a,9));return h;}
    public static TextView action(Activity a,String label,int color,boolean filled){TextView b=plain(a,label,12,filled?BG:color);medium(b);b.setGravity(Gravity.CENTER);b.setPadding(dp(a,14),0,dp(a,14),0);pressable(a,b,filled?gradient(a,color,mix(color,BG,.20f),Color.TRANSPARENT,15):gradient(a,SURFACE_3,SURFACE_2,Color.argb(60,Color.red(color),Color.green(color),Color.blue(color)),15));raised(a,b,filled?6:3);return b;}

    /** PRIME surfaces only. Every tab owns a warm semantic color so the shell reads as a mixed palette, not one accent. */
    public static void addBottomNav(Activity a,LinearLayout root,String selected,Runnable ignoredMoreAction){
        String current=primeKey(selected);LinearLayout bar=new LinearLayout(a);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER);bar.setPadding(dp(a,6),dp(a,5),dp(a,6),dp(a,5));bar.setBackground(gradient(a,SURFACE_2,Color.rgb(12,10,9),Color.argb(38,255,226,190),22));raised(a,bar,8);
        addNav(a,bar,"input","Input",current,InputActivity.class);
        addNav(a,bar,"brief","Brief",current,ProposalBriefActivity.class);
        addNav(a,bar,"people","People / Projects",current,ProposalPeopleProjectsActivity.class);
        addNav(a,bar,"brain","Brain",current,ProposalAskCortexActivity.class);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,70));p.setMargins(dp(a,14),dp(a,7),dp(a,14),dp(a,10));root.addView(bar,p);fitSystemBars(a,root);
    }
    private static String primeKey(String s){if("home".equals(s)||"focus".equals(s)||"brief".equals(s))return"brief";if("vault".equals(s)||"people".equals(s))return"people";if("ask".equals(s)||"brain".equals(s))return"brain";if("input".equals(s))return"input";return s==null?"":s;}
    private static int navColor(String key){if("input".equals(key))return ORANGE;if("brief".equals(key))return YELLOW;if("people".equals(key))return GREEN;return RED;}

    private static void addNav(Activity a,LinearLayout row,String key,String label,String selected,Class<?> cls){
        boolean on=key.equals(selected);int semantic=navColor(key);int idle=mix(semantic,MUTED,.56f);
        LinearLayout item=new LinearLayout(a);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(a,3),dp(a,5),dp(a,3),dp(a,3));
        int fill=on?Color.argb(30,Color.red(semantic),Color.green(semantic),Color.blue(semantic)):Color.argb(90,28,24,20);
        int stroke=Color.argb(on?84:28,Color.red(semantic),Color.green(semantic),Color.blue(semantic));item.setBackground(gradient(a,SURFACE_3,fill,stroke,16));raised(a,item,on?5:2);
        NavIcon icon=new NavIcon(a,key,on?semantic:idle,semantic,on);item.addView(icon,new LinearLayout.LayoutParams(dp(a,31),dp(a,29)));
        TextView l=plain(a,label,8,on?TEXT:idle);l.setGravity(Gravity.CENTER);l.setMaxLines(1);if(on)medium(l);item.addView(l,new LinearLayout.LayoutParams(-1,dp(a,18)));
        item.setOnClickListener(v->{if(on)return;Intent i=new Intent(a,cls);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);a.startActivity(i);});
        row.addView(item,new LinearLayout.LayoutParams(0,-1,1));
    }

    private static int mix(int a,int b,float amountOfB){float q=Math.max(0f,Math.min(1f,amountOfB)),p=1f-q;return Color.rgb((int)(Color.red(a)*p+Color.red(b)*q),(int)(Color.green(a)*p+Color.green(b)*q),(int)(Color.blue(a)*p+Color.blue(b)*q));}

    private static final class NavIcon extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),plate=new Paint(Paint.ANTI_ALIAS_FLAG);final String key;final boolean active;
        NavIcon(Activity a,String k,int color,int semantic,boolean on){super(a);key=k;active=on;setLayerType(LAYER_TYPE_SOFTWARE,null);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(a,1.7f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);plate.setColor(on?Color.argb(34,Color.red(semantic),Color.green(semantic),Color.blue(semantic)):Color.rgb(22,19,16));plate.setStyle(Paint.Style.FILL);plate.setShadowLayer((on?7:4)*getResources().getDisplayMetrics().density,0,2*getResources().getDisplayMetrics().density,Color.argb(on?150:100,0,0,0));}
        static float dp(Activity a,float v){return v*a.getResources().getDisplayMetrics().density;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;c.drawRoundRect(w*.08f,h*.05f,w*.92f,h*.95f,w*.20f,w*.20f,plate);
            if("input".equals(key)){c.drawRoundRect(cx-w*.25f,cy-h*.26f,cx+w*.25f,cy+h*.26f,w*.08f,w*.08f,p);c.drawLine(cx-w*.13f,cy,cx+w*.13f,cy,p);c.drawLine(cx,cy-h*.14f,cx,cy+h*.14f,p);}
            else if("brief".equals(key)){c.drawLine(cx-w*.25f,cy-h*.20f,cx+w*.20f,cy-h*.20f,p);c.drawLine(cx-w*.25f,cy,cx+w*.27f,cy,p);c.drawLine(cx-w*.25f,cy+h*.20f,cx+w*.10f,cy+h*.20f,p);p.setStyle(Paint.Style.FILL);c.drawCircle(cx+w*.27f,cy-h*.20f,p.getStrokeWidth()*1.15f,p);p.setStyle(Paint.Style.STROKE);}
            else if("people".equals(key)){c.drawCircle(cx-w*.10f,cy-h*.13f,h*.12f,p);c.drawCircle(cx+w*.16f,cy-h*.08f,h*.09f,p);c.drawArc(cx-w*.27f,cy+h*.01f,cx+w*.08f,cy+h*.33f,190,160,false,p);c.drawArc(cx+w*.02f,cy+h*.05f,cx+w*.30f,cy+h*.29f,195,145,false,p);}
            else{Path q=new Path();q.moveTo(cx,cy-h*.29f);q.lineTo(cx+w*.08f,cy-h*.08f);q.lineTo(cx+w*.28f,cy);q.lineTo(cx+w*.08f,cy+h*.08f);q.lineTo(cx,cy+h*.29f);q.lineTo(cx-w*.08f,cy+h*.08f);q.lineTo(cx-w*.28f,cy);q.lineTo(cx-w*.08f,cy-h*.08f);q.close();c.drawPath(q,p);}
        }
    }
}
