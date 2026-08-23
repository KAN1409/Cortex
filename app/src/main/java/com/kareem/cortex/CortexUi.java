package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Cortex v50 visual language.
 * Warm graphite + copper/coral, large hierarchy, quiet surfaces and modular cards.
 * Kept programmatic so the current Java UI can migrate screen-by-screen without a framework rewrite.
 */
public final class CortexUi {
    public static final int BG = Color.rgb(10,10,9);
    public static final int SURFACE = Color.rgb(23,22,20);
    public static final int SURFACE_2 = Color.rgb(31,29,26);
    public static final int SURFACE_3 = Color.rgb(39,36,32);
    public static final int TEXT = Color.rgb(246,241,233);
    public static final int MUTED = Color.rgb(163,156,146);
    public static final int FAINT = Color.rgb(111,106,99);
    public static final int COPPER = Color.rgb(204,132,83);
    public static final int CORAL = Color.rgb(234,111,88);
    public static final int GOLD = Color.rgb(218,174,91);
    public static final int SAGE = Color.rgb(146,183,145);
    public static final int BORDER = Color.rgb(50,47,43);
    public static final int BORDER_SOFT = Color.rgb(40,38,35);

    private CortexUi() {}

    public static int dp(Activity a, int v) {
        return (int)(v * a.getResources().getDisplayMetrics().density + .5f);
    }

    public static void applyWindow(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(0);
        }
    }

    public static GradientDrawable round(Activity a, int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(a, radius));
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(a, 1), stroke);
        return g;
    }

    public static GradientDrawable gradient(Activity a, int start, int end, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        g.setCornerRadius(dp(a, radius));
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(a, 1), stroke);
        return g;
    }

    public static View pressable(Activity a, View v, GradientDrawable base) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            RippleDrawable r = new RippleDrawable(ColorStateList.valueOf(Color.argb(42,255,255,255)), base, null);
            v.setBackground(r);
        } else v.setBackground(base);
        v.setClickable(true);
        v.setFocusable(true);
        v.setElevation(dp(a, 1));
        return v;
    }

    public static TextView text(Activity a, String s, int sp, int color) {
        TextView v = new TextView(a);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(0, 1.04f);
        CortexTextUi.setReadable(v, s == null ? "" : s);
        return v;
    }

    public static TextView plain(Activity a, String s, int sp, int color) {
        TextView v = new TextView(a);
        v.setTextSize(sp);
        v.setTextColor(color);
        CortexTextUi.setPlain(v, s == null ? "" : s);
        return v;
    }

    public static void medium(TextView v) { v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
    public static void bold(TextView v) { v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); }

    public static LinearLayout card(Activity a, int radius) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15));
        c.setBackground(round(a,SURFACE,BORDER_SOFT,radius));
        c.setElevation(dp(a,1));
        return c;
    }

    public static TextView chip(Activity a, String label, int color, boolean strong) {
        TextView v = plain(a,label,strong?11:10,color);
        if (strong) medium(v);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,11),0,dp(a,11),0);
        int fill = Color.argb(28,Color.red(color),Color.green(color),Color.blue(color));
        int stroke = Color.argb(88,Color.red(color),Color.green(color),Color.blue(color));
        v.setBackground(round(a,fill,stroke,999));
        return v;
    }

    public static TextView section(Activity a, String title) {
        TextView h = plain(a,title.toUpperCase(),11,MUTED);
        medium(h);
        h.setLetterSpacing(.08f);
        h.setPadding(0,dp(a,22),0,dp(a,10));
        return h;
    }

    public static TextView action(Activity a, String label, int color, boolean filled) {
        TextView b = plain(a,label,12,filled?BG:color);
        medium(b);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(a,14),0,dp(a,14),0);
        int fill = filled ? color : SURFACE_2;
        int stroke = filled ? color : BORDER;
        pressable(a,b,round(a,fill,stroke,15));
        return b;
    }

    public static void addBottomNav(Activity a, LinearLayout root, String selected, Runnable moreAction) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(a,5),dp(a,5),dp(a,5),dp(a,7));
        bar.setBackground(round(a,Color.rgb(17,16,15),BORDER_SOFT,22));
        addNav(a,bar,"⌂","Home","home",selected,()->{if(!"home".equals(selected))a.startActivity(new Intent(a,PremiumHomeActivity.class));});
        addNav(a,bar,"▤","Inbox","inbox",selected,()->{Intent i=new Intent(a,SmartInboxActivity.class);i.putExtra("mode","needs");a.startActivity(i);});
        addNav(a,bar,"✦","Ask","ask",selected,()->{if(!"ask".equals(selected))a.startActivity(new Intent(a,AskCortexActivity.class));});
        addNav(a,bar,"▣","Vault","vault",selected,()->{if(!"vault".equals(selected))a.startActivity(new Intent(a,VaultActivity.class));});
        addNav(a,bar,"•••","More","more",selected,moreAction);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(a,64));
        p.setMargins(dp(a,12),dp(a,6),dp(a,12),dp(a,10));
        root.addView(bar,p);
    }

    private static void addNav(Activity a, LinearLayout row, String icon, String label, String key, String selected, Runnable action) {
        boolean on = key.equals(selected);
        LinearLayout item = new LinearLayout(a);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(a,2),dp(a,5),dp(a,2),dp(a,3));
        if (on) item.setBackground(round(a,Color.rgb(37,31,25),Color.TRANSPARENT,17));
        TextView i = plain(a,icon,on?18:16,on?COPPER:MUTED); i.setGravity(Gravity.CENTER); if(on)medium(i);
        TextView l = plain(a,label,9,on?TEXT:MUTED); l.setGravity(Gravity.CENTER); if(on)medium(l);
        item.addView(i,new LinearLayout.LayoutParams(-1,dp(a,25)));
        item.addView(l,new LinearLayout.LayoutParams(-1,dp(a,17)));
        item.setOnClickListener(v->action.run());
        row.addView(item,new LinearLayout.LayoutParams(0,-1,1));
    }
}
