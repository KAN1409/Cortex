package com.kareem.cortex;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class DebugReviewActivity extends Activity {
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        VaultDb db=new VaultDb(this);
        long id=getIntent()==null?0:getIntent().getLongExtra("item_id",0);
        if(id<=0)id=ChatGptDebugReview.latestVoiceId(db);
        if(id<=0){Toast.makeText(this,"No voice recording found in Cortex",Toast.LENGTH_LONG).show();finish();return;}
        try{ChatGptDebugReview.share(this,db,id);}catch(Exception e){Toast.makeText(this,"Could not open ChatGPT debug review: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        finish();
    }
}
