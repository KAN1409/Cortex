package com.kareem.cortex;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class CortexTeacherImportActivity extends Activity {
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        Intent i=getIntent();
        String text=i==null?"":i.getStringExtra(Intent.EXTRA_TEXT);
        CortexChatGptAppTeacher.ImportResult r=CortexChatGptAppTeacher.importText(this,text);
        Toast.makeText(this,r.ok?"ChatGPT policy imported":"Policy import failed",Toast.LENGTH_LONG).show();
        Intent open=new Intent(this,ChatGptTeacherActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(open);
        finish();
    }
}
