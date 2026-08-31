package com.kareem.cortex.prime.capture.voice;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** Read-only, grant-gated stream of immutable voice assets for Android RecognitionService. */
public final class PrimeAudioAssetProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public String getType(Uri uri) {
        return "audio/L16";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("Voice evidence provider is read-only");
        String sha = last(uri);
        if (!sha.matches("[0-9a-f]{64}")) throw new FileNotFoundException("Invalid asset id");
        File file = new File(getContext().getFilesDir(), "evidence-assets/audio/" + sha + ".wav");
        if (!file.isFile()) throw new FileNotFoundException("Voice asset not found");

        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException failure) {
            FileNotFoundException wrapped = new FileNotFoundException("Unable to open voice stream");
            wrapped.initCause(failure);
            throw wrapped;
        }
        Thread feeder = new Thread(() -> {
            try (FileInputStream in = new FileInputStream(file);
                 FileOutputStream out = new FileOutputStream(pipe[1].getFileDescriptor())) {
                long remainingHeader = WavPcm16.HEADER_BYTES;
                while (remainingHeader > 0) {
                    long skipped = in.skip(remainingHeader);
                    if (skipped <= 0) break;
                    remainingHeader -= skipped;
                }
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) if (read > 0) out.write(buffer, 0, read);
            } catch (Exception ignored) {
                // Reader observes EOF; immutable asset is never modified.
            } finally {
                try { pipe[1].close(); } catch (Exception ignored) {}
            }
        }, "prime-voice-asr-stream");
        feeder.setDaemon(true);
        feeder.start();
        return pipe[0];
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        String sha = last(uri);
        File file = new File(getContext().getFilesDir(), "evidence-assets/audio/" + sha + ".wav");
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(sha + ".pcm");
            else if (OpenableColumns.SIZE.equals(column)) row.add(Math.max(0L, file.length() - WavPcm16.HEADER_BYTES));
            else row.add(null);
        }
        return cursor;
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }

    private static String last(Uri uri) {
        String value = uri == null ? null : uri.getLastPathSegment();
        return value == null ? "" : value.toLowerCase();
    }
}
