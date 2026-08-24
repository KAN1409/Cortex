package com.kareem.cortex;

import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import org.json.JSONObject;

/** Explicit user-triggered screen understanding. Never monitors the screen in the background. */
public final class UnderstandScreenTileService extends TileService {
    @Override public void onStartListening(){super.onStartListening();Tile t=getQsTile();if(t!=null){t.setLabel("Understand screen");t.setState(CortexScreenAccessibilityService.connected()?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);t.updateTile();}}

    @Override public void onClick(){
        super.onClick();CortexScreenAccessibilityService.Snapshot s=CortexScreenAccessibilityService.snapshot();
        if(s==null||!s.usable()){
            Toast.makeText(this,"Enable Cortex Screen Understanding first",Toast.LENGTH_SHORT).show();
            try{Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivityAndCollapse(i);}catch(Throwable ignored){}
            return;
        }
        new Thread(()->save(s),"CortexUnderstandScreen").start();
    }

    private void save(CortexScreenAccessibilityService.Snapshot s){
        VaultDb db=null;try{
            JSONObject m=new JSONObject();m.put("capture_kind","explicit_understand_screen");m.put("package",s.packageName);m.put("app_label",s.appLabel);m.put("captured_at",s.capturedAt);m.put("background_monitoring",false);
            MasterRelevanceFilter.Signal signal=new MasterRelevanceFilter.Signal("screen_context",s.packageName,s.appLabel+" screen",s.text,m.toString(),s.capturedAt,false);
            db=new VaultDb(this);long id=RawSignalStore.capture(db,signal);runOnUiThread(id>0?"Screen context captured":"Screen context already captured");
        }catch(Throwable e){runOnUiThread("Could not understand this screen");}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}
    }
    private void runOnUiThread(String msg){android.os.Handler h=new android.os.Handler(getMainLooper());h.post(()->Toast.makeText(this,msg,Toast.LENGTH_SHORT).show());}
}
