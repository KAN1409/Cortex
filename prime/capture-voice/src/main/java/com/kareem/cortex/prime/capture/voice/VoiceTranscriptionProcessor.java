package com.kareem.cortex.prime.capture.voice;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.kareem.cortex.prime.evidence.EvidenceRecord;
import com.kareem.cortex.prime.evidence.EvidenceSource;
import com.kareem.cortex.prime.evidence.EvidenceSqliteStore;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Android RecognitionService adapter for immutable voice evidence.
 * Transcripts are appended as derived TEXT evidence and never overwrite the WAV parent.
 */
public final class VoiceTranscriptionProcessor {
    private VoiceTranscriptionProcessor() {}

    public static void transcribe(Context context, EvidenceRecord parent) {
        if (context == null || parent == null || parent.source != EvidenceSource.VOICE) return;
        Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT < 33) {
            appendStatus(app, parent, "UNSUPPORTED", "AUDIO_SOURCE_REQUIRES_API_33");
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> startOnMain(app, parent));
    }

    public static void recoverUntranscribed(Context context) {
        Context app = context.getApplicationContext();
        List<EvidenceRecord> recent;
        try (EvidenceSqliteStore store = new EvidenceSqliteStore(app)) {
            recent = store.recent(500);
        } catch (RuntimeException failure) {
            return;
        }
        Set<String> completedParents = new HashSet<>();
        List<EvidenceRecord> voice = new ArrayList<>();
        for (EvidenceRecord record : recent) {
            String parentId = parentId(record.rawPayloadJson);
            if (!parentId.isEmpty() && record.source == EvidenceSource.TEXT && !record.rawText.trim().isEmpty()) {
                completedParents.add(parentId);
            }
            if (record.source == EvidenceSource.VOICE) voice.add(record);
        }
        for (EvidenceRecord record : voice) {
            if (!completedParents.contains(record.id)) {
                transcribe(app, record);
                break; // one recognizer session at a time; next launch/recovery continues backlog.
            }
        }
    }

    private static void startOnMain(Context app, EvidenceRecord parent) {
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            appendStatus(app, parent, "RETRYABLE", "NO_RECOGNITION_SERVICE");
            return;
        }
        String sha = shaFrom(parent.sourceRef);
        if (sha.isEmpty()) {
            appendStatus(app, parent, "FAILED", "MISSING_AUDIO_ASSET_REF");
            return;
        }

        Uri audioUri = new Uri.Builder()
                .scheme("content")
                .authority(app.getPackageName() + ".voiceassets")
                .appendPath("pcm")
                .appendPath(sha)
                .build();
        grantRecognitionServices(app, audioUri);

        SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(app);
        recognizer.setRecognitionListener(new RecognitionListener() {
            private boolean terminal;

            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onError(int error) {
                if (terminal) return;
                terminal = true;
                appendStatus(app, parent, retryable(error) ? "RETRYABLE" : "FAILED", "SPEECH_ERROR_" + error);
                recognizer.destroy();
            }

            @Override
            public void onResults(Bundle results) {
                if (terminal) return;
                terminal = true;
                ArrayList<String> matches = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String transcript = matches == null || matches.isEmpty() ? "" : matches.get(0).trim();
                if (transcript.isEmpty()) appendStatus(app, parent, "COMPLETE_NO_SPEECH", "EMPTY_TRANSCRIPT");
                else appendTranscript(app, parent, transcript);
                recognizer.destroy();
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audioUri);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, WavPcm16.CHANNELS);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, WavPcm16.SAMPLE_RATE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        recognizer.startListening(intent);
    }

    private static void appendTranscript(Context context, EvidenceRecord parent, String transcript) {
        String id = "ev_transcript_" + sha256(parent.id + "\n" + transcript).substring(0, 24);
        JSONObject payload = new JSONObject();
        try {
            payload.put("schema", "CORTEX_PRIME_DERIVED_TRANSCRIPT_V1");
            payload.put("status", "COMPLETE");
            payload.put("parent_evidence_id", parent.id);
            payload.put("processor", "ANDROID_RECOGNITION_SERVICE_AUDIO_SOURCE");
            payload.put("derived", true);
            payload.put("immutable_parent", true);
        } catch (Exception ignored) {}
        append(context, new EvidenceRecord(
                id,
                EvidenceSource.TEXT,
                System.currentTimeMillis(),
                transcript,
                "derived-from:" + parent.id,
                payload.toString()
        ));
    }

    private static void appendStatus(Context context, EvidenceRecord parent, String status, String reason) {
        String normalized = status == null ? "UNKNOWN" : status.toUpperCase(Locale.ROOT);
        String id = "ev_asr_status_" + sha256(parent.id + "\n" + normalized + "\n" + reason).substring(0, 20);
        JSONObject payload = new JSONObject();
        try {
            payload.put("schema", "CORTEX_PRIME_DERIVED_TRANSCRIPT_STATUS_V1");
            payload.put("status", normalized);
            payload.put("reason", reason == null ? "" : reason);
            payload.put("parent_evidence_id", parent.id);
            payload.put("derived", true);
            payload.put("immutable_parent", true);
        } catch (Exception ignored) {}
        append(context, new EvidenceRecord(
                id,
                EvidenceSource.TEXT,
                System.currentTimeMillis(),
                "Voice transcription " + normalized.replace('_', ' ').toLowerCase(Locale.ROOT),
                "derived-from:" + parent.id,
                payload.toString()
        ));
    }

    private static void append(Context context, EvidenceRecord record) {
        try (EvidenceSqliteStore store = new EvidenceSqliteStore(context)) {
            store.append(record);
        } catch (RuntimeException ignored) {}
    }

    private static void grantRecognitionServices(Context context, Uri uri) {
        Intent serviceIntent = new Intent(RecognitionService.SERVICE_INTERFACE);
        List<ResolveInfo> services = context.getPackageManager().queryIntentServices(serviceIntent, PackageManager.MATCH_ALL);
        for (ResolveInfo info : services) {
            if (info.serviceInfo == null) continue;
            try {
                context.grantUriPermission(info.serviceInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (RuntimeException ignored) {}
        }
    }

    private static boolean retryable(int error) {
        return error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_SERVER
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
                || error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS;
    }

    private static String shaFrom(String sourceRef) {
        String prefix = "asset://sha256/";
        if (sourceRef == null || !sourceRef.startsWith(prefix)) return "";
        String sha = sourceRef.substring(prefix.length()).toLowerCase(Locale.ROOT);
        return sha.matches("[0-9a-f]{64}") ? sha : "";
    }

    private static String parentId(String payload) {
        if (payload == null || payload.isEmpty()) return "";
        try {
            return new JSONObject(payload).optString("parent_evidence_id", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
