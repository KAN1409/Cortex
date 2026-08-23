package com.kareem.cortex;

import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Central TextView direction/rendering policy. Stored text is never modified here. */
public final class CortexTextUi {
    private CortexTextUi(){}

    public static boolean isArabicDominant(String text){return MixedBidiText.isArabicDominant(MixedBidiText.stripControls(text==null?"":text));}

    public static String readable(String raw){return MixedBidiText.format(raw==null?"":raw).toString();}

    public static void setReadable(TextView view,String raw){
        if(view==null)return;
        String clean=MixedBidiText.stripControls(raw==null?"":raw);
        view.setText(MixedBidiText.format(clean));
        applyDirection(view,clean);
        view.setLineSpacing(0f,1.16f);
    }

    public static void setPlain(TextView view,String raw){
        if(view==null)return;
        String clean=MixedBidiText.stripControls(raw==null?"":raw);
        view.setText(clean);
        applyDirection(view,clean);
    }

    public static void applyDirection(TextView view,String text){
        boolean rtl=isArabicDominant(text);
        if(rtl){
            view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            view.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }else{
            view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
            view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }
    }
}
