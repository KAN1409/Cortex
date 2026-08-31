package com.kareem.cortex.prime.capture.vision;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** Process-start hook that resumes bounded OCR backlog from immutable IMAGE evidence. */
public final class PrimeVisionInitProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        if (getContext() != null) {
            Thread worker = new Thread(
                    () -> ImagePerceptionProcessor.recoverUnprocessed(getContext().getApplicationContext()),
                    "prime-vision-recovery"
            );
            worker.setDaemon(true);
            worker.start();
        }
        return true;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
