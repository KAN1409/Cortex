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

    /**
     * Arabic-dominant Cortex text uses a forced RTL paragraph base. FIRST_STRONG is intentionally
     * not used here: medical/product answers often begin with a Latin drug name, dose, model name,
     * URL or other technical token even though the sentence and reading order are Arabic.
     * MixedBidiText still isolates embedded Latin runs, so English stays readable inside RTL text.
     */
    public static void applyDirection(TextView view,String text){
        boolean rtl=isArabicDominant(text);
        if(rtl){
            view.setTextDirection(View.TEXT_DIRECTION_RTL);
            view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            view.setGravity(Gravity.END|Gravity.TOP);
            view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }else{
            view.setTextDirection(View.TEXT_DIRECTION_LTR);
            view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            view.setGravity(Gravity.START|Gravity.TOP);
            view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }
    }
}
