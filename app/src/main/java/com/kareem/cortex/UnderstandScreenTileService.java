package com.kareem.cortex;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import org.json.JSONObject;

/** Explicit user-triggered screen understanding. Never monitors the screen in the background. */
public final class UnderstandScreenTileService extends TileService {
    @Override public void onStartListening(){super.onStartListening();Tile t=getQsTile();if(t!=null){t.setLabel("Understand screen");t.setState(CortexScreenAccessibilityService.connected()?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);t.updateTile();}}
    @Override public void onClick(){super.onClick();CortexScreenAccessibilityService.Snapshot s=CortexScreenAccessibilityService.snapshot();if(s==null||!s.usable()){Toast.makeText(this,"Enable Cortex Screen Understanding first",Toast.LENGTH_SHORT).show();try{Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivityAndCollapse(i);}catch(Throwable ignored){}return;}new Thread(()->save(s),"CortexUnderstandScreen").start();}

    private void save(CortexScreenAccessibilityService.Snapshot s){VaultDb db=null;try{JSONObject m=new JSONObject();m.put("capture_kind","explicit_understand_screen");m.put("package",s.packageName);m.put("app_label",s.appLabel);m.put("captured_at",s.capturedAt);m.put("background_monitoring",false);String title=(s.appLabel==null||s.appLabel.trim().isEmpty()?s.packageName:s.appLabel)+" screen";String raw=s.text==null?"":s.text.trim();String fp=Fingerprint.text("screen|"+s.packageName+"|"+raw);db=new VaultDb(this);long id=db.insert("TEXT","screen_understand",title,raw,"Screen Context","screen,context,explicit", "",fp,m.toString());if(id<0)id=-id;if(id<=0){toast("Could not capture this screen");return;}AnalysisQueue.kick(this,null,null);openResult(id);}catch(Throwable e){toast("Could not understand this screen");}finally{if(db!=null)try{db.close();}catch(Throwable ignored){}}}
    private void openResult(long id){android.os.Handler h=new android.os.Handler(getMainLooper());h.post(()->{try{Intent i=new Intent(this,CaptureResultActivity.class);i.putExtra("item_id",id);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);if(Build.VERSION.SDK_INT>=34){PendingIntent p=PendingIntent.getActivity(this,(int)(42000+(id%1000)),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);startActivityAndCollapse(p);}else startActivityAndCollapse(i);}catch(Throwable e){toast("Screen captured. Open Brief to see the result.");}});}
    private void toast(String msg){new android.os.Handler(getMainLooper()).post(()->Toast.makeText(this,msg,Toast.LENGTH_SHORT).show());}
}
