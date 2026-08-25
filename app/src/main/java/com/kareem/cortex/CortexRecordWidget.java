package com.kareem.cortex;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.RemoteViews;

/** Home-screen control: Record when idle, Stop while the shared Cortex recorder is active. */
public final class CortexRecordWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)update(c,m,id);}
    @Override public void onEnabled(Context c){super.onEnabled(c);updateAll(c);}

    public static void updateAll(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);ComponentName n=new ComponentName(c,CortexRecordWidget.class);int[] ids=m.getAppWidgetIds(n);for(int id:ids)update(c,m,id);}

    private static void update(Context c,AppWidgetManager m,int id){
        boolean running=CortexRecordService.isRecording(c);long started=CortexRecordService.startedAt(c);RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.cortex_record_widget);
        v.setTextViewText(R.id.record_widget_state,running?"RECORDING":"READY");
        v.setTextColor(R.id.record_widget_state,running?0xFFFF2A24:0xFF9A9FAB);
        v.setViewVisibility(R.id.record_widget_record,running?View.GONE:View.VISIBLE);v.setViewVisibility(R.id.record_widget_stop,running?View.VISIBLE:View.GONE);v.setViewVisibility(R.id.record_widget_timer,running?View.VISIBLE:View.GONE);v.setViewVisibility(R.id.record_widget_hint,running?View.GONE:View.VISIBLE);
        if(running){long elapsed=Math.max(0,System.currentTimeMillis()-started);long base=SystemClock.elapsedRealtime()-elapsed;v.setChronometer(R.id.record_widget_timer,base,null,true);}else v.setChronometer(R.id.record_widget_timer,SystemClock.elapsedRealtime(),null,false);

        boolean permission=new AudioCapture().hasPermission(c);boolean asr=GroqKeyStore.has(c)||GeminiKeyStore.has(c);
        PendingIntent recordPi;
        if(permission&&asr){Intent start=new Intent(c,CortexRecordService.class).setAction(CortexRecordService.ACTION_START);recordPi=Build.VERSION.SDK_INT>=26?PendingIntent.getForegroundService(c,42001,start,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE):PendingIntent.getService(c,42001,start,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}else{Intent setup=new Intent(c,SatinCaptureActivity.class).putExtra("mode","voice").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);recordPi=PendingIntent.getActivity(c,42001,setup,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
        Intent stop=new Intent(c,CortexRecordService.class).setAction(CortexRecordService.ACTION_STOP);PendingIntent stopPi=PendingIntent.getService(c,42002,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(c,InputActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent openPi=PendingIntent.getActivity(c,42003,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.record_widget_record,recordPi);v.setOnClickPendingIntent(R.id.record_widget_stop,stopPi);v.setOnClickPendingIntent(R.id.record_widget_title,openPi);m.updateAppWidget(id,v);
    }
}
