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

/** Unified Cortex shell: quiet surfaces, one accent, safe insets and one navigation model. */
public final class CortexUi {
    public static final int BG = Color.rgb(8,9,12);
    public static final int SURFACE = Color.rgb(18,20,25);
    public static final int SURFACE_2 = Color.rgb(25,28,34);
    public static final int SURFACE_3 = Color.rgb(31,35,42);
    public static final int TEXT = Color.rgb(244,246,250);
    public static final int MUTED = Color.rgb(158,166,178);
    public static final int FAINT = Color.rgb(99,106,117);
    public static final int ACCENT = Color.rgb(126,158,255);
    public static final int COPPER = ACCENT;
    public static final int CORAL = ACCENT;
    public static final int GOLD = ACCENT;
    public static final int SAGE = Color.rgb(141,194,159);
    public static final int BORDER = Color.rgb(40,44,52);
    public static final int BORDER_SOFT = Color.rgb(25,28,34);

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
    public static View divider(Activity a){View v=new View(a);v.setBackgroundColor(BORDER_SOFT);return v;}

    public static View pressable(Activity a,View v,GradientDrawable base){if(Build.VERSION.SDK_INT>=21)v.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(30,255,255,255)),base,null));else v.setBackground(base);v.setClickable(true);v.setFocusable(true);return v;}
    public static TextView text(Activity a,String s,int sp,int color){TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(color);v.setLineSpacing(0,1.05f);CortexTextUi.setReadable(v,s==null?"":s);return v;}
    public static TextView plain(Activity a,String s,int sp,int color){TextView v=new TextView(a);v.setTextSize(sp);v.setTextColor(color);CortexTextUi.setPlain(v,s==null?"":s);return v;}
    public static void medium(TextView v){v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
    public static void bold(TextView v){v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));}

    public static LinearLayout card(Activity a,int radius){LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15));c.setBackground(round(a,SURFACE,Color.TRANSPARENT,radius));return c;}
    public static TextView chip(Activity a,String label,int color,boolean strong){TextView v=plain(a,label,strong?11:10,color);if(strong)medium(v);v.setGravity(Gravity.CENTER);v.setPadding(dp(a,11),0,dp(a,11),0);int fill=Color.argb(strong?24:12,Color.red(color),Color.green(color),Color.blue(color));int stroke=strong?Color.argb(52,Color.red(color),Color.green(color),Color.blue(color)):Color.TRANSPARENT;v.setBackground(round(a,fill,stroke,999));return v;}
    public static TextView section(Activity a,String title){TextView h=plain(a,title,11,MUTED);medium(h);h.setPadding(0,dp(a,22),0,dp(a,9));return h;}
    public static TextView action(Activity a,String label,int color,boolean filled){TextView b=plain(a,label,12,filled?Color.rgb(8,9,12):color);medium(b);b.setGravity(Gravity.CENTER);b.setPadding(dp(a,14),0,dp(a,14),0);pressable(a,b,round(a,filled?color:Color.TRANSPARENT,filled?Color.TRANSPARENT:BORDER_SOFT,15));return b;}

    public static void addBottomNav(Activity a,LinearLayout root,String selected,Runnable ignoredMoreAction){
        LinearLayout bar=new LinearLayout(a);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER);bar.setPadding(dp(a,6),dp(a,5),dp(a,6),dp(a,5));bar.setBackground(round(a,Color.rgb(14,16,20),Color.TRANSPARENT,22));
        addNav(a,bar,"home","Home",selected,PremiumHomeActivity.class);
        addNav(a,bar,"focus","Focus",selected,SmartInboxActivity.class);
        addCapture(a,bar);
        addNav(a,bar,"vault","Vault",selected,VaultActivity.class);
        addNav(a,bar,"ask","Ask",selected,AskCortexActivity.class);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,66));p.setMargins(dp(a,14),dp(a,7),dp(a,14),dp(a,10));root.addView(bar,p);fitSystemBars(a,root);
    }

    private static void addCapture(Activity a,LinearLayout row){LinearLayout item=new LinearLayout(a);item.setGravity(Gravity.CENTER);TextView plus=plain(a,"+",25,Color.rgb(8,9,12));medium(plus);plus.setGravity(Gravity.CENTER);pressable(a,plus,round(a,ACCENT,Color.TRANSPARENT,999));plus.setOnClickListener(v->a.startActivity(new Intent(a,CaptureActivity.class)));item.addView(plus,new LinearLayout.LayoutParams(dp(a,46),dp(a,46)));row.addView(item,new LinearLayout.LayoutParams(dp(a,62),-1));}

    private static void addNav(Activity a,LinearLayout row,String key,String label,String selected,Class<?> cls){
        boolean on=key.equals(selected);LinearLayout item=new LinearLayout(a);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(dp(a,2),dp(a,5),dp(a,2),dp(a,3));
        if(on)item.setBackground(round(a,SURFACE_2,Color.TRANSPARENT,16));
        NavIcon icon=new NavIcon(a,key,on?ACCENT:MUTED);item.addView(icon,new LinearLayout.LayoutParams(dp(a,27),dp(a,25)));
        TextView l=plain(a,label,9,on?TEXT:MUTED);l.setGravity(Gravity.CENTER);if(on)medium(l);item.addView(l,new LinearLayout.LayoutParams(-1,dp(a,18)));
        item.setOnClickListener(v->{if(on)return;Intent i=new Intent(a,cls);i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);a.startActivity(i);});
        row.addView(item,new LinearLayout.LayoutParams(0,-1,1));
    }

    private static final class NavIcon extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final String key;
        NavIcon(Activity a,String k,int color){super(a);key=k;p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(a,1.65f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
        static float dp(Activity a,float v){return v*a.getResources().getDisplayMetrics().density;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;
            if("home".equals(key)){Path q=new Path();q.moveTo(cx-w*.28f,cy);q.lineTo(cx,cy-h*.29f);q.lineTo(cx+w*.28f,cy);q.lineTo(cx+w*.22f,cy+h*.27f);q.lineTo(cx-w*.22f,cy+h*.27f);q.close();c.drawPath(q,p);}
            else if("focus".equals(key)){c.drawLine(cx-w*.24f,cy-h*.21f,cx+w*.18f,cy-h*.21f,p);c.drawLine(cx-w*.24f,cy,cx+w*.26f,cy,p);c.drawLine(cx-w*.24f,cy+h*.21f,cx+w*.10f,cy+h*.21f,p);p.setStyle(Paint.Style.FILL);c.drawCircle(cx+w*.27f,cy-h*.21f,p.getStrokeWidth()*1.15f,p);p.setStyle(Paint.Style.STROKE);}
            else if("vault".equals(key)){c.drawRoundRect(cx-w*.24f,cy-h*.28f,cx+w*.24f,cy+h*.28f,w*.07f,w*.07f,p);c.drawLine(cx-w*.11f,cy-h*.07f,cx+w*.11f,cy-h*.07f,p);c.drawLine(cx-w*.11f,cy+h*.09f,cx+w*.05f,cy+h*.09f,p);}
            else{Path q=new Path();q.moveTo(cx,cy-h*.29f);q.lineTo(cx+w*.08f,cy-h*.08f);q.lineTo(cx+w*.28f,cy);q.lineTo(cx+w*.08f,cy+h*.08f);q.lineTo(cx,cy+h*.29f);q.lineTo(cx-w*.08f,cy+h*.08f);q.lineTo(cx-w*.28f,cy);q.lineTo(cx-w*.08f,cy-h*.08f);q.close();c.drawPath(q,p);}
        }
    }
}
