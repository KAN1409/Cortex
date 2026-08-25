package com.kareem.cortex;

import android.content.Context;

/** Runtime model selection for the OpenRouter Brain route. */
public final class OpenRouterModelConfig {
    private static final String PREFS="cortex_model_config";
    private static final String KEY="openrouter_brain_model";
    public static final String DEFAULT_MODEL="stealth/ox-alpha";

    private OpenRouterModelConfig(){}

    public static String generationModel(Context context){
        String value=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,DEFAULT_MODEL);
        return valid(value)?value.trim():DEFAULT_MODEL;
    }

    public static boolean setGenerationModel(Context context,String model){
        String value=model==null?"":model.trim();
        if(value.isEmpty())value=DEFAULT_MODEL;
        if(!valid(value))return false;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,value).apply();
        return true;
    }

    public static boolean isOxAlpha(Context context){return DEFAULT_MODEL.equals(generationModel(context));}

    private static boolean valid(String value){
        if(value==null)return false;
        String x=value.trim();
        return x.length()>=3&&x.length()<=180&&x.indexOf('/')>0&&!x.matches(".*\\s+.*");
    }
}
