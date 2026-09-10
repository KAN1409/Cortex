package com.kareem.cortex;

import android.content.Context;

/** Runtime model selection for the OpenRouter Brain route. */
public final class OpenRouterModelConfig {
    private static final String PREFS="cortex_model_config";
    private static final String KEY="openrouter_brain_model";
    private static final String RETIRED_OX_ALPHA="stealth/ox-alpha";
    /** OpenRouter's free router avoids pinning Cortex to a temporary/retired model id. */
    public static final String DEFAULT_MODEL="openrouter/free";

    private OpenRouterModelConfig(){}

    public static String generationModel(Context context){
        String value=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,DEFAULT_MODEL);
        String resolved=valid(value)?value.trim():DEFAULT_MODEL;
        if(RETIRED_OX_ALPHA.equalsIgnoreCase(resolved)){
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,DEFAULT_MODEL).apply();
            return DEFAULT_MODEL;
        }
        return resolved;
    }

    public static boolean setGenerationModel(Context context,String model){
        String value=model==null?"":model.trim();
        if(value.isEmpty()||RETIRED_OX_ALPHA.equalsIgnoreCase(value))value=DEFAULT_MODEL;
        if(!valid(value))return false;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,value).apply();
        return true;
    }

    /** Kept for source compatibility with older UI checks; the retired model is never selected. */
    public static boolean isOxAlpha(Context context){return false;}

    private static boolean valid(String value){
        if(value==null)return false;
        String x=value.trim();
        return x.length()>=3&&x.length()<=180&&x.indexOf('/')>0&&!x.matches(".*\\s+.*");
    }
}
