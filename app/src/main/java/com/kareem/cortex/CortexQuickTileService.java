package com.kareem.cortex;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Everywhere Cortex: one-tap Quick Settings entry into voice capture. */
public class CortexQuickTileService extends TileService {
    @Override public void onStartListening(){super.onStartListening();Tile t=getQsTile();if(t!=null){t.setLabel("Cortex Voice");t.setState(Tile.STATE_INACTIVE);t.updateTile();}}
    @Override public void onClick(){super.onClick();Intent i=new Intent(this,CaptureActivity.class);i.putExtra("mode","voice");i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);try{if(Build.VERSION.SDK_INT>=34){PendingIntent p=PendingIntent.getActivity(this,41021,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);startActivityAndCollapse(p);}else startActivityAndCollapse(i);}catch(Throwable ignored){try{startActivity(i);}catch(Throwable ignoredAgain){}}}
}
