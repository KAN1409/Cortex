package com.kareem.cortex;

import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public final class CortexTextUi {
    private CortexTextUi(){}

    public static boolean isArabicDominant(String text){
        if(text==null||text.isEmpty())return false;
        int ar=0,latin=0;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(isArabic(c))ar++;
            else if(isLatin(c))latin++;
        }
        return ar>0&&ar>=latin;
    }

    public static String readable(String raw){
        return LanguageBlockFormatter.format(raw==null?"":raw);
    }

    public static void setReadable(TextView view,String raw){
        if(view==null)return;
        String shown=readable(raw);
        view.setText(shown);
        applyDirection(view,shown);
        view.setLineSpacing(0f,1.16f);
    }

    public static void setPlain(TextView view,String raw){
        if(view==null)return;
        String shown=raw==null?"":raw;
        view.setText(shown);
        applyDirection(view,shown);
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

    private static boolean isArabic(char c){
        return (c>=0x0600&&c<=0x06ff)||(c>=0x0750&&c<=0x077f)||(c>=0x08a0&&c<=0x08ff)||(c>=0xfb50&&c<=0xfdff)||(c>=0xfe70&&c<=0xfeff);
    }

    private static boolean isLatin(char c){
        return (c>='A'&&c<='Z')||(c>='a'&&c<='z');
    }
}
