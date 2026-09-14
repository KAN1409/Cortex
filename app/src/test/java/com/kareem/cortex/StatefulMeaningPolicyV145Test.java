package com.kareem.cortex;

import org.junit.Test;
import static org.junit.Assert.*;

public class StatefulMeaningPolicyV145Test {
    @Test public void weatherLocationIsStableAcrossForecastChanges(){
        String a=StatefulMeaningPolicy.correlationKey("notification_event","21° in New Cairo City","Feels like 24° · Clear","com.weather",1000,11);
        String b=StatefulMeaningPolicy.correlationKey("notification_event","23° in New Cairo City","Partly cloudy","com.weather",2000,12);
        assertEquals(a,b);
        assertTrue(a.startsWith("weather|"));
    }

    @Test public void batteryStateIsStableAcrossPercentageChanges(){
        String a=StatefulMeaningPolicy.correlationKey("notification_event","Battery power 15%","1 h 35 m left","com.android.systemui",1000,21);
        String b=StatefulMeaningPolicy.correlationKey("notification_event","Battery power 9%","45 m remaining","com.android.systemui",2000,21);
        assertEquals(a,b);
        assertTrue(a.startsWith("device_state|battery|"));
    }

    @Test public void backupStateIsStableForSameSource(){
        String a=StatefulMeaningPolicy.correlationKey("notification_event","Backup in progress","Preparing backup…","com.google.android.apps.photos",1000,31);
        String b=StatefulMeaningPolicy.correlationKey("notification_event","Backup in progress","Backing up 42 items","com.google.android.apps.photos",2000,31);
        assertEquals(a,b);
        assertTrue(a.startsWith("device_state|backup|"));
    }

    @Test public void technicalStateUsesSourceInstanceIdentity(){
        String a=StatefulMeaningPolicy.correlationKey("technical_state","app.apk","10%","com.android.chrome",1000,77);
        String b=StatefulMeaningPolicy.correlationKey("technical_state","app.apk","90%","com.android.chrome",2000,77);
        String c=StatefulMeaningPolicy.correlationKey("technical_state","other.apk","90%","com.android.chrome",2000,78);
        assertEquals(a,b);
        assertNotEquals(a,c);
    }
}
