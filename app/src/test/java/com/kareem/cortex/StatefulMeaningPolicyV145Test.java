package com.kareem.cortex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class StatefulMeaningPolicyV145Test {

    @Test
    public void weatherIdentityIgnoresChangingForecastValues() {
        String a = StatefulMeaningPolicy.correlationKey(
                "WEATHER", "New Cairo · 21° · Clear", "", "com.google.android.googlequicksearchbox", 0L);
        String b = StatefulMeaningPolicy.correlationKey(
                "WEATHER", "New Cairo · 23° · Sunny", "", "com.google.android.googlequicksearchbox", 0L);

        assertEquals("Weather revisions for one location should share identity", a, b);
    }

    @Test
    public void batteryIdentityIgnoresChangingPercentage() {
        String a = StatefulMeaningPolicy.correlationKey(
                "BATTERY", "Battery 15%", "Battery low", "android", 0L);
        String b = StatefulMeaningPolicy.correlationKey(
                "BATTERY", "Battery 12%", "Battery low", "android", 0L);

        assertEquals("Battery revisions should share one device-state identity", a, b);
    }

    @Test
    public void backupIdentityIgnoresProgressText() {
        String a = StatefulMeaningPolicy.correlationKey(
                "BACKUP", "Backup in progress", "Backing up now", "com.google.android.apps.photos", 0L);
        String b = StatefulMeaningPolicy.correlationKey(
                "BACKUP", "Backup complete", "Backup complete", "com.google.android.apps.photos", 0L);

        assertEquals("Backup lifecycle revisions should share source identity", a, b);
    }

    @Test
    public void technicalStateUsesSourceInstanceWhenProvided() {
        String a = StatefulMeaningPolicy.correlationKey(
                "TECHNICAL_STATE", "Syncing", "Account A", "com.example.sync", 0L, 77L);
        String b = StatefulMeaningPolicy.correlationKey(
                "TECHNICAL_STATE", "Sync complete", "Account B", "com.example.sync", 0L, 77L);
        String c = StatefulMeaningPolicy.correlationKey(
                "TECHNICAL_STATE", "Sync complete", "Account B", "com.example.sync", 0L, 78L);

        assertEquals(a, b);
        assertNotEquals(a, c);
    }
}
