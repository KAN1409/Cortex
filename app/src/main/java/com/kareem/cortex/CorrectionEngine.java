package com.kareem.cortex;

import android.content.Context;
import java.util.*;

public final class CorrectionEngine {
    private CorrectionEngine(){}
    // Applies only user-approved exact replacements to the display transcript.
    // Raw provider output remains untouched in AudioStore diagnostics.
    public static String apply(Context ctx,String text){
        if(text==null||text.isEmpty())return text==null?"":text;
        VaultDb db=new VaultDb(ctx);String out=text;
        try{
            for(String[] r:FeatureStore.futureCorrections(db,"extracted_text")){
                String from=r[0],to=r[1];
                if(from.length()<2||from.length()>120)continue;
                out=out.replace(from,to);
            }
        }catch(Exception ignored){}finally{try{db.close();}catch(Exception ignored){}}
        return out;
    }
}
