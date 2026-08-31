package com.kareem.cortex.prime.capture.voice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground microphone capture. The model layer never participates in recording or persistence. */
public final class PrimeVoiceRecordingService extends Service {
    public static final String ACTION_START = "com.kareem.cortex.prime.action.START_VOICE_CAPTURE";
    public static final String ACTION_STOP = "com.kareem.cortex.prime.action.STOP_VOICE_CAPTURE";

    private static final String CHANNEL_ID = "cortex_prime_voice_capture";
    private static final int NOTIFICATION_ID = 4201;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private volatile boolean recording;
    private AudioRecord recorder;
    private File stagingFile;
    private long startedAtEpochMs;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) stopRecording();
        else startRecording();
        return START_NOT_STICKY;
    }

    private synchronized void startRecording() {
        if (recording) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Preparing voice evidence…"));

        int minBuffer = AudioRecord.getMinBufferSize(WavPcm16.SAMPLE_RATE, CHANNEL, ENCODING);
        if (minBuffer <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        int bufferSize = Math.max(minBuffer, WavPcm16.SAMPLE_RATE);
        AudioRecord candidate = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                WavPcm16.SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize
        );
        if (candidate.getState() != AudioRecord.STATE_INITIALIZED) {
            candidate.release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }

        try {
            startedAtEpochMs = System.currentTimeMillis();
            stagingFile = PendingVoiceFile.create(this, startedAtEpochMs);
        } catch (Exception e) {
            candidate.release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }

        recorder = candidate;
        recording = true;
        candidate.startRecording();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification("Recording. Tap Stop when finished."));
        writer.execute(() -> writePcm(candidate, stagingFile, bufferSize));
    }

    private void writePcm(AudioRecord audioRecord, File file, int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(WavPcm16.HEADER_BYTES);
            while (recording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) raf.write(buffer, 0, read);
            }
        } catch (Exception ignored) {
            // The pending file remains recoverable. Do not erase it on capture failure.
        }
    }

    private synchronized void stopRecording() {
        if (!recording) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        recording = false;
        AudioRecord active = recorder;
        recorder = null;
        try { if (active != null) active.stop(); } catch (RuntimeException ignored) {}

        File file = stagingFile;
        long occurredAt = startedAtEpochMs;
        stagingFile = null;
        startedAtEpochMs = 0L;
        writer.execute(() -> {
            try {
                if (active != null) active.release();
                if (WavPcm16.hasAudio(file)) VoiceEvidenceCommitter.commit(this, file, occurredAt);
                else if (file != null) file.delete();
            } catch (Exception ignored) {
                // Preserve pending bytes for later recovery.
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID,
                "Cortex Prime voice capture",
                NotificationManager.IMPORTANCE_LOW
        ));
    }

    private Notification notification(String text) {
        Intent stopIntent = new Intent(this, PrimeVoiceRecordingService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Cortex Prime voice evidence")
                .setContentText(text)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .build();
    }

    @Override
    public void onDestroy() {
        recording = false;
        try { if (recorder != null) recorder.stop(); } catch (RuntimeException ignored) {}
        if (recorder != null) recorder.release();
        recorder = null;
        writer.shutdownNow();
        super.onDestroy();
    }
}
