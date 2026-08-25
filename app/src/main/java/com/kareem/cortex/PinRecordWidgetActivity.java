package com.kareem.cortex;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.widget.Toast;

/** Small exported bridge used only to ask the launcher to pin the Cortex Voice widget. */
public final class PinRecordWidgetActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);try{AppWidgetManager m=AppWidgetManager.getInstance(this);if(android.os.Build.VERSION.SDK_INT>=26&&m.isRequestPinAppWidgetSupported()){boolean requested=m.requestPinAppWidget(new ComponentName(this,CortexRecordWidget.class),null,null);Toast.makeText(this,requested?"Confirm Cortex Voice on your home screen":"Launcher did not accept the widget pin request",Toast.LENGTH_LONG).show();}else Toast.makeText(this,"Open your launcher widget picker and choose Cortex Voice",Toast.LENGTH_LONG).show();}catch(Throwable e){Toast.makeText(this,"Could not request the Cortex Voice widget",Toast.LENGTH_LONG).show();}finish();}
}
