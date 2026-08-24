package com.kareem.cortex;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.regex.Pattern;

/** Runtime Gemini generation-model selection for Brain + Visual Intelligence. */
public final class GeminiModelConfig {
    private static final String PREF="cortex_gemini_model_config";
    private static final String KEY="generation_model";
    public static final String DEFAULT_GENERATION_MODEL="gemini-3.6-flash";
    private static final Pattern SAFE=Pattern.compile("[A-Za-z0-9._-]{3,80}");
    private GeminiModelConfig(){}

    public static String generationModel(Context c){
        String x=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,DEFAULT_GENERATION_MODEL);
        return valid(x)?x.trim():DEFAULT_GENERATION_MODEL;
    }

    public static boolean setGenerationModel(Context c,String model){
        String x=model==null?"":model.trim();if(!valid(x))return false;
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,x).apply();return true;
    }

    public static void resetGenerationModel(Context c){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(KEY).apply();}
    private static boolean valid(String x){return x!=null&&SAFE.matcher(x.trim()).matches();}
}
