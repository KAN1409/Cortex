package com.kareem.cortex;

import android.content.Intent;
import android.widget.Toast;

/** Final PRIME capture entry: Satin capture UI plus proposal-aware result routing. */
public final class ProposalCaptureActivity extends SatinCaptureActivity {
    @Override void showResult(long id){
        try{
            Intent i=new Intent(this,ProposalCaptureResultActivity.class);i.putExtra("item_id",id);startActivity(i);finish();
        }catch(Throwable e){
            Toast.makeText(this,"Captured successfully. Open Brief to see it.",Toast.LENGTH_LONG).show();
            try{startActivity(new Intent(this,PremiumHomeActivity.class));}catch(Throwable ignored){}finish();
        }
    }
}
