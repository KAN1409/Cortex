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
 * Cortex product UI system.
 * Conversation-first, low-chrome, typographic hierarchy. Surfaces separate content;
 * outlines and elevation are deliberately restrained so product concepts carry the UI.
 */
public final class CortexUi {
    public static final int BG=Color.rgb(5,6,5), SURFACE=Color.rgb(15,16,15), SURFACE_2=Color.rgb(21,22,21), SURFACE_3=Color.rgb(28,29,28);
    public static final int TEXT=Color.rgb(246,247,243), MUTED=Color.rgb(165,167,162), FAINT=Color.rgb(101,104,99);
    public static final int LIME=Color.rgb(185,218,77), OLIVE=Color.rgb(126,145,55), GREEN=Color.rgb(111,202,86), YELLOW=Color.rgb(240,184,56), ORANGE=Color.rgb(242,154,49), RED=Color.rgb(239,92,73);
    public static final int ACCENT=LIME,SIGNAL=LIME,AMBER=YELLOW,SAGE=GREEN,INFO=GREEN,VIOLET=OLIVE,COPPER=ORANGE,CORAL=RED,GOLD=YELLOW;
    public static final int BORDER=Color.rgb(36,38,35), BORDER_SOFT=Color.rgb(25,27,24), HAIRLINE=Color.argb(26,220,230,210);
    private CortexUi(){}

    public static int dp(Activity a,int v){return(int)(v*a.getResources().getDisplayMetrics().density+.5f);}

    public static void applyWindow(Activity a){
        Window w=a.getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=29)w.setNavigationBarContrastEnforced(false);
        if(Build.VERSION.SDK_INT>=23)w.getDecorView().setSystemUiVisibility(0);
    }

    public static void fitSystemBars(Activity a,View root){
        final int pl=root.getPaddingLeft(),pt=root.getPaddingTop(),pr=root.getPaddingRight(),pb=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v,in)->{
            int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=in.getInsets(WindowInsets.Type.systemBars());l=x.left;t=x.top;r=x.right;b=x.bottom;}
            else{l=in.getSystemWindowInsetLeft();t=in.getSystemWindowInsetTop();r=in.getSystemWindowInsetRight();b=in.getSystemWindowInsetBottom();}
            v.setPadding(pl+l,pt+t,pr+r,pb+b);return in;
        });
        root.requestApplyInsets();
    }

    public static GradientDrawable round(Activity a,int fill,int stroke,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(a,radius));
        if(stroke!=Color.TRANSPARENT)g.setStroke(dp(a,1),stroke);return g;
    }
    public static GradientDrawable gradient(Activity a,int start,int end,int stroke,int radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{start,end});g.setCornerRadius(dp(a,radius));
        if(stroke!=Color.TRANSPARENT)g.setStroke(dp(a,1),stroke);return g;
    }

    /** Quiet surfaces: no decorative border-ception. */
    public static GradientDrawable velvet(Activity a,int radius){return round(a,SURFACE,Color.TRANSPARENT,radius);}
    public static GradientDrawable matte(Activity a,int radius){return round(a,SURFACE_2,Color.TRANSPARENT,radius);}
    public static View divider(Activity a){View v=new View(a);v.setBackgroundColor(BORDER_SOFT);return v;}

    public static <T extends View>T raised(Activity a,T v,int e){
        if(Build.VERSION.SDK_INT>=21){v.setElevation(dp(a,Math.min(1,Math.max(0,e))));v.setTranslationZ(0);}return v;
    }
    public static CortexGlyphView glyph(Activity a,String k,int c,boolean d){return new CortexGlyphView(a,k,c,d);}

    public static View pressable(Activity a,View v,GradientDrawable base){
        if(Build.VERSION.SDK_INT>=21)v.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(28,185,218,77)),base,null));else v.setBackground(base);
        v.setClickable(true);v.setFocusable(true);
        v.setOnTouchListener((x,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x.setAlpha(.86f);}
            else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){x.setAlpha(1f);}
            return false;
        });
        return v;
    }

    public static TextView text(Activity a,String s,int sp,int c){
        TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(c);v.setLineSpacing(0,1.08f);CortexTextUi.setReadable(v,s==null?"":s);return v;
    }
    public static TextView plain(Activity a,String s,int sp,int c){
        TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(c);CortexTextUi.setPlain(v,s==null?"":s);return v;
    }
    public static void medium(TextView v){v.setTypeface(Typeface.create("sans-serif-medium",0));}
    public static void bold(TextView v){v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));}

    public static LinearLayout card(Activity a,int r){
        LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,14),dp(a,13),dp(a,14),dp(a,13));c.setBackground(velvet(a,Math.min(r,18)));return c;
    }

    public static TextView chip(Activity a,String label,int color,boolean strong){
        TextView v=plain(a,label,strong?10:10,strong?TEXT:MUTED);if(strong)medium(v);v.setGravity(Gravity.CENTER);v.setPadding(dp(a,11),0,dp(a,11),0);
        int wash=Color.argb(strong?24:12,Color.red(color),Color.green(color),Color.blue(color));
        v.setBackground(round(a,wash,Color.TRANSPARENT,999));return v;
    }

    public static TextView section(Activity a,String s){
        TextView h=plain(a,s,11,MUTED);medium(h);h.setPadding(0,dp(a,20),0,dp(a,8));return h;
    }

    public static TextView action(Activity a,String label,int color,boolean filled){
        TextView b=plain(a,label,12,filled?BG:color);medium(b);b.setGravity(Gravity.CENTER);b.setPadding(dp(a,14),0,dp(a,14),0);
        int fill=filled?color:SURFACE_2;
        pressable(a,b,round(a,fill,Color.TRANSPARENT,13));return b;
    }

    public static LinearLayout simpleHeader(Activity a,String title,String subtitle,View.OnClickListener action){
        LinearLayout row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,4),dp(a,6),0,dp(a,7));
        LinearLayout tx=new LinearLayout(a);tx.setOrientation(LinearLayout.VERTICAL);TextView h=plain(a,title,20,TEXT);medium(h);tx.addView(h);
        if(subtitle!=null&&!subtitle.trim().isEmpty()){TextView s=plain(a,subtitle,11,MUTED);s.setPadding(0,dp(a,2),0,0);tx.addView(s);}
        row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){TextView more=plain(a,"⋮",24,MUTED);more.setGravity(Gravity.CENTER);more.setOnClickListener(action);row.addView(more,new LinearLayout.LayoutParams(dp(a,42),dp(a,42)));}
        return row;
    }

    public static int semanticFor(String k){
        if(k==null)return LIME;k=k.toLowerCase();if(k.contains("urgent")||k.contains("review"))return RED;if(k.contains("wait")||k.contains("remind"))return YELLOW;if(k.contains("input")||k.contains("capture")||k.contains("play"))return ORANGE;if(k.contains("people")||k.contains("project")||k.contains("useful")||k.contains("complete"))return LIME;return OLIVE;
    }

    /** Compact app-wide navigation. No oversized hero control competing with screen content. */
    public static void addBottomNav(Activity a,LinearLayout root,String selected,Runnable ignored){
        String cur=primeKey(selected);LinearLayout bar=new LinearLayout(a);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER);bar.setPadding(dp(a,7),dp(a,4),dp(a,7),dp(a,4));bar.setBackground(round(a,SURFACE,Color.TRANSPARENT,20));
        addNav(a,bar,"input","Input",cur,InputActivity.class);
        addNav(a,bar,"brief","Brief",cur,ProposalBriefActivity.class);
        addCenter(a,bar,cur);
        addNav(a,bar,"people","People",cur,ProposalPeopleProjectsActivity.class);
        addNav(a,bar,"brain","Brain",cur,ProposalAskCortexActivity.class);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,64));p.setMargins(dp(a,12),dp(a,4),dp(a,12),dp(a,8));root.addView(bar,p);fitSystemBars(a,root);
    }

    private static void addCenter(Activity a,LinearLayout row,String cur){
        TextView plus=plain(a,"+",29,BG);plus.setGravity(Gravity.CENTER);plus.setTypeface(Typeface.create("sans-serif-light",0));
        pressable(a,plus,round(a,LIME,Color.TRANSPARENT,999));
        plus.setOnClickListener(v->a.startActivity(new Intent(a,ProposalCaptureActivity.class)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,48),.90f);p.setMargins(dp(a,7),0,dp(a,7),0);row.addView(plus,p);
    }

    private static String primeKey(String s){
        if("home".equals(s)||"focus".equals(s)||"brief".equals(s))return"brief";
        if("vault".equals(s)||"people".equals(s))return"people";
        if("ask".equals(s)||"brain".equals(s))return"brain";
        if("input".equals(s))return"input";return s==null?"":s;
    }

    private static void addNav(Activity a,LinearLayout row,String key,String label,String selected,Class<?> cls){
        boolean on=key.equals(selected);LinearLayout item=new LinearLayout(a);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(a,2),dp(a,3),dp(a,2),0);
        item.setBackground(round(a,on?Color.argb(18,185,218,77):Color.TRANSPARENT,Color.TRANSPARENT,14));
        CortexGlyphView icon=glyph(a,key,on?LIME:MUTED,on);item.addView(icon,new LinearLayout.LayoutParams(dp(a,28),dp(a,28)));
        TextView l=plain(a,label,8,on?LIME:MUTED);l.setGravity(Gravity.CENTER);l.setMaxLines(1);if(on)medium(l);item.addView(l,new LinearLayout.LayoutParams(-1,dp(a,17)));
        item.setOnClickListener(v->{if(!on)a.startActivity(new Intent(a,cls).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));});row.addView(item,new LinearLayout.LayoutParams(0,-1,1));
    }

    private static int mix(int a,int b,float q){q=Math.max(0,Math.min(1,q));float p=1-q;return Color.rgb((int)(Color.red(a)*p+Color.red(b)*q),(int)(Color.green(a)*p+Color.green(b)*q),(int)(Color.blue(a)*p+Color.blue(b)*q));}
}
