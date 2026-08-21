package com.kareem.cortex;

import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * Audio-file ASR for Cortex.
 *
 * v1.0.6 deliberately does NOT trust a single mixed-language recognizer pass.
 * The same PCM is decoded in overlapping time windows through ar-EG and en-US,
 * then the two time-aligned streams are merged. English-selected windows remain
 * Latin-script protected and never pass through Arabic transliteration/cleanup.
 */
public final class SystemAudioTranscriber {
    public interface Callback { void ok(TranscriptResult r); void fail(Exception e); }
    private SystemAudioTranscriber() {}

    private static final long WINDOW_MS = 2400;
    private static final long OVERLAP_MS = 700;
    private static final long STEP_MS = WINDOW_MS - OVERLAP_MS;

    private static final HashSet<String> ENGLISH_HINTS = new HashSet<>(Arrays.asList(
            "a","an","and","are","as","at","audio","backup","because","before","but","call","chat","chatgpt",
            "conversation","cortex","data","debug","do","document","english","file","for","from","have","hello",
            "i","image","in","is","it","later","memory","message","model","need","note","of","on","openai",
            "please","prompt","record","recording","result","search","send","screenshot","test","text","the",
            "this","to","transcribe","transcript","voice","want","we","with","you","arabic","english"
    ));

    static final class PcmSource {
        File file;
        int sampleRate = 16000;
        int channels = 1;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        long durationMs;
    }

    static final class PcmChunk {
        File file;
        long startMs, endMs;
    }

    static final class Candidate {
        String text = "";
        String language = "";
        float confidence = -1f;
        Exception error;
    }

    static final class WindowDecision {
        String text = "";
        String language = "";
        float confidence = 0f;
        double score = 0;
    }

    interface CandidateCallback { void done(Candidate c); }

    public static void transcribe(Context ctx, File audio, Callback cb) {
        if (Build.VERSION.SDK_INT < 33) {
            cb.fail(new UnsupportedOperationException("Audio-file transcription requires Android 13+"));
            return;
        }
        new Thread(() -> {
            try {
                PcmSource pcm = decode(audio, ctx.getCacheDir());
                ArrayList<PcmChunk> windows = makeWindows(pcm, ctx.getCacheDir());
                new Handler(Looper.getMainLooper()).post(() -> processWindow(
                        ctx.getApplicationContext(), pcm, windows, 0,
                        new TranscriptResult(), new LinkedHashSet<>(), cb));
            } catch (Exception e) {
                cb.fail(e);
            }
        }, "CortexDualDecoderPrep").start();
    }

    private static PcmSource decode(File source, File cache) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(source.getAbsolutePath());
        int track = -1;
        MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
        }
        if (track < 0 || fmt == null) { ex.release(); throw new IOException("No audio track found"); }
        ex.selectTrack(track);
        String mime = fmt.getString(MediaFormat.KEY_MIME);
        if (mime == null) { ex.release(); throw new IOException("Unknown audio format"); }
        if (Build.VERSION.SDK_INT >= 24) fmt.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);

        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(fmt, null, null, 0);
        codec.start();
        File out = new File(cache, "cortex_pcm_" + System.nanoTime() + ".raw");
        FileOutputStream os = new FileOutputStream(out);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false, outputDone = false;
        int rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 16000;
        int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        long duration = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) / 1000 : 0;
        try {
            while (!outputDone) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(10000);
                    if (in >= 0) {
                        ByteBuffer b = codec.getInputBuffer(in);
                        int n = ex.readSampleData(b, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int index = codec.dequeueOutputBuffer(info, 10000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (Build.VERSION.SDK_INT >= 24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if (index >= 0) {
                    ByteBuffer b = codec.getOutputBuffer(index);
                    if (b != null && info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        b.position(info.offset); b.limit(info.offset + info.size);
                        byte[] bytes = new byte[info.size]; b.get(bytes); os.write(bytes);
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(index, false);
                }
            }
        } finally {
            try { os.close(); } catch (Exception ignored) {}
            try { codec.stop(); } catch (Exception ignored) {}
            codec.release(); ex.release();
        }
        PcmSource p = new PcmSource();
        p.file = out; p.sampleRate = rate; p.channels = channels; p.encoding = encoding; p.durationMs = duration;
        if (p.durationMs <= 0) {
            long bytesPerSec = (long) p.sampleRate * Math.max(1, p.channels) * 2L;
            p.durationMs = bytesPerSec > 0 ? p.file.length() * 1000L / bytesPerSec : 0;
        }
        return p;
    }

    private static ArrayList<PcmChunk> makeWindows(PcmSource pcm, File cache) throws Exception {
        byte[] all = readAll(pcm.file);
        ArrayList<PcmChunk> out = new ArrayList<>();
        long total = Math.max(1, pcm.durationMs);
        for (long start = 0; start < total; start += STEP_MS) {
            long end = Math.min(total, start + WINDOW_MS);
            out.add(writeChunk(pcm, cache, all, start, end));
            if (end >= total) break;
        }
        return out;
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream((int)Math.min(Integer.MAX_VALUE, f.length()));
        byte[] b = new byte[32768]; int n;
        try { while ((n = in.read(b)) > 0) out.write(b, 0, n); }
        finally { in.close(); }
        return out.toByteArray();
    }

    private static PcmChunk writeChunk(PcmSource pcm, File cache, byte[] all, long start, long end) throws Exception {
        long bps = (long) pcm.sampleRate * Math.max(1, pcm.channels) * 2L;
        int align = Math.max(2, pcm.channels * 2);
        long a = start * bps / 1000L, z = end * bps / 1000L;
        a -= a % align; z -= z % align;
        a = Math.max(0, Math.min(a, all.length)); z = Math.max(a, Math.min(z, all.length));
        File f = new File(cache, "cortex_dual_" + System.nanoTime() + ".raw");
        FileOutputStream os = new FileOutputStream(f);
        try { os.write(all, (int)a, (int)(z - a)); } finally { os.close(); }
        PcmChunk c = new PcmChunk(); c.file = f; c.startMs = start; c.endMs = end; return c;
    }

    private static void processWindow(Context ctx, PcmSource pcm, ArrayList<PcmChunk> windows, int index,
                                      TranscriptResult result, Set<String> languages, Callback cb) {
        if (index >= windows.size()) {
            pcm.file.delete();
            result.text = normalizeSpaces(result.text);
            result.durationMs = pcm.durationMs;
            result.engine = "android_speech_dual_decoder_time_merge";
            result.version = "2";
            result.language = languages.isEmpty() ? "mixed" : String.join("+", languages);
            if (result.text.isEmpty()) cb.fail(new IOException("Dual-decoder transcription produced no speech"));
            else cb.ok(result);
            return;
        }

        PcmChunk w = windows.get(index);
        recognizeFixed(ctx, pcm, w, "ar-EG", 0, ar ->
                recognizeFixed(ctx, pcm, w, "en-US", 0, en -> {
                    WindowDecision d = decide(ar, en);
                    w.file.delete();
                    if (d != null && !d.text.isEmpty()) {
                        String before = result.text;
                        result.text = mergeOverlap(result.text, d.text);
                        // Only add a segment when this window contributes new text after overlap removal.
                        if (!normalizeSpaces(before).equals(normalizeSpaces(result.text))) {
                            result.segments.add(new TranscriptResult.Segment(w.startMs, w.endMs, d.text, d.confidence));
                            languages.add(d.language);
                        }
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(() ->
                            processWindow(ctx, pcm, windows, index + 1, result, languages, cb), 90);
                }));
    }

    private static void recognizeFixed(Context ctx, PcmSource pcm, PcmChunk chunk, String language,
                                       int attempt, CandidateCallback cb) {
        Candidate out = new Candidate(); out.language = language;
        final SpeechRecognizer sr;
        try { sr = SpeechRecognizer.createSpeechRecognizer(ctx); }
        catch (Exception e) { out.error = e; cb.done(out); return; }
        final ParcelFileDescriptor pfd;
        try { pfd = ParcelFileDescriptor.open(chunk.file, ParcelFileDescriptor.MODE_READ_ONLY); }
        catch (Exception e) { sr.destroy(); out.error = e; cb.done(out); return; }
        final boolean[] finished = {false};

        RecognitionListener listener = new RecognitionListener() {
            void cleanup() { try { pfd.close(); } catch (Exception ignored) {} try { sr.destroy(); } catch (Exception ignored) {} }
            void finish(Bundle b) {
                if (finished[0]) return; finished[0] = true;
                ArrayList<String> xs = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                float[] scores = b == null ? null : b.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                if (xs != null && !xs.isEmpty()) {
                    int best = 0;
                    double bestScore = -999;
                    for (int i = 0; i < xs.size(); i++) {
                        String t = xs.get(i) == null ? "" : xs.get(i).trim();
                        float c = scores != null && i < scores.length ? scores[i] : -1f;
                        double s = "en-US".equals(language) ? englishCandidateScore(t, c) : arabicCandidateScore(t, c);
                        if (s > bestScore) { bestScore = s; best = i; }
                    }
                    out.text = xs.get(best) == null ? "" : xs.get(best).trim();
                    if (scores != null && best < scores.length) out.confidence = scores[best];
                }
                cleanup(); cb.done(out);
            }
            public void onReadyForSpeech(Bundle p) {}
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float r) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() {}
            public void onError(int error) {
                if (finished[0]) return; finished[0] = true; cleanup();
                if (attempt < 1 && retryable(error)) {
                    new Handler(Looper.getMainLooper()).postDelayed(() ->
                            recognizeFixed(ctx, pcm, chunk, language, attempt + 1, cb), 420);
                    return;
                }
                out.error = new IOException(errorName(error)); cb.done(out);
            }
            public void onResults(Bundle b) { finish(b); }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int t, Bundle b) {}
            @Override public void onSegmentResults(Bundle b) { finish(b); }
            @Override public void onEndOfSegmentedSession() { if (!finished[0]) finish(null); }
        };

        sr.setRecognitionListener(listener);
        Intent i = baseIntent(language, pcm, pfd);
        i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
        try { sr.startListening(i); }
        catch (Exception e) {
            try { pfd.close(); } catch (Exception ignored) {}
            try { sr.destroy(); } catch (Exception ignored) {}
            out.error = e; cb.done(out);
        }
    }

    private static Intent baseIntent(String language, PcmSource pcm, ParcelFileDescriptor pfd) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd);
        i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, pcm.channels);
        i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, pcm.encoding);
        i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, pcm.sampleRate);
        if (Build.VERSION.SDK_INT >= 33 && "en-US".equals(language)) {
            i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, new ArrayList<>(ENGLISH_HINTS));
        }
        return i;
    }

    private static WindowDecision decide(Candidate ar, Candidate en) {
        boolean aEmpty = ar == null || normalizeSpaces(ar.text).isEmpty();
        boolean eEmpty = en == null || normalizeSpaces(en.text).isEmpty();
        if (aEmpty && eEmpty) return null;
        if (aEmpty) return decision(en, "en-US", englishCandidateScore(en.text, en.confidence));
        if (eEmpty) return decision(ar, "ar-EG", arabicCandidateScore(ar.text, ar.confidence));

        double as = arabicCandidateScore(ar.text, ar.confidence);
        double es = englishCandidateScore(en.text, en.confidence);
        int englishHints = englishHintHits(en.text);
        double latin = latinRatio(en.text);
        double arabic = arabicRatio(ar.text);

        // Protected-English rule: a credible English hypothesis with real English lexical evidence
        // wins its aligned window even if the Arabic recognizer produced phonetic Arabic text.
        if (latin >= 0.72 && englishHints >= 1 && es >= as - 0.08)
            return decision(en, "en-US", es);

        // Strong language-specific evidence.
        if (es >= as + 0.13 && latin >= 0.62) return decision(en, "en-US", es);
        if (as >= es + 0.10 && arabic >= 0.58) return decision(ar, "ar-EG", as);

        // Hysteresis for ambiguous switch boundaries: dictionary-backed English wins only when
        // its acoustic confidence is not materially worse; otherwise preserve Arabic.
        if (englishHints >= 2 && confidence(en.confidence) + 0.05 >= confidence(ar.confidence))
            return decision(en, "en-US", es);
        return decision(ar, "ar-EG", as);
    }

    private static WindowDecision decision(Candidate c, String lang, double score) {
        WindowDecision d = new WindowDecision();
        d.text = normalizeSpaces(c.text);
        d.language = lang;
        d.confidence = c.confidence >= 0 ? c.confidence : (float)Math.max(0, Math.min(1, score));
        d.score = score;
        return d;
    }

    private static double englishCandidateScore(String text, float conf) {
        String t = normalizeSpaces(text);
        if (t.isEmpty()) return -2;
        double latin = latinRatio(t), arabic = arabicRatio(t);
        int hits = englishHintHits(t);
        int tokens = Math.max(1, tokenCount(t));
        double lexical = Math.min(1.0, hits / (double)Math.min(tokens, 4));
        return confidence(conf) * 0.56 + latin * 0.24 + lexical * 0.24 - arabic * 0.20;
    }

    private static double arabicCandidateScore(String text, float conf) {
        String t = normalizeSpaces(text);
        if (t.isEmpty()) return -2;
        double arabic = arabicRatio(t), latin = latinRatio(t);
        return confidence(conf) * 0.67 + arabic * 0.31 - latin * 0.12;
    }

    private static double confidence(float c) { return c >= 0 ? Math.max(0, Math.min(1, c)) : 0.50; }

    private static int englishHintHits(String text) {
        int n = 0;
        for (String raw : normalizeSpaces(text).toLowerCase(Locale.ROOT).split("\\s+")) {
            String x = raw.replaceAll("[^a-z0-9'-]", "");
            if (ENGLISH_HINTS.contains(x)) n++;
            else if (x.length() >= 4 && looksEnglishWord(x)) n++;
        }
        return n;
    }

    private static boolean looksEnglishWord(String s) {
        if (!s.matches("[a-z][a-z'-]+")) return false;
        int vowels = 0;
        for (int i = 0; i < s.length(); i++) if ("aeiouy".indexOf(s.charAt(i)) >= 0) vowels++;
        return vowels > 0 && vowels * 5 >= s.length();
    }

    private static int tokenCount(String s) {
        String t = normalizeSpaces(s); return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    private static double latinRatio(String s) { return scriptRatio(s, true); }
    private static double arabicRatio(String s) { return scriptRatio(s, false); }
    private static double scriptRatio(String s, boolean latin) {
        int wanted = 0, letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetter(ch)) {
                letters++;
                boolean isLatin = (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
                boolean isArabic = (ch >= '\u0600' && ch <= '\u06FF') || (ch >= '\u0750' && ch <= '\u077F') || (ch >= '\u08A0' && ch <= '\u08FF');
                if (latin ? isLatin : isArabic) wanted++;
            }
        }
        return letters == 0 ? 0 : wanted / (double)letters;
    }

    /** Pure merge hook used by regression tests. */
    static String mergeForTest(String arText, float arConfidence, String enText, float enConfidence) {
        Candidate ar = new Candidate(); ar.text = arText; ar.language = "ar-EG"; ar.confidence = arConfidence;
        Candidate en = new Candidate(); en.text = enText; en.language = "en-US"; en.confidence = enConfidence;
        WindowDecision d = decide(ar, en); return d == null ? "" : d.text;
    }

    private static String mergeOverlap(String existing, String next) {
        String a = normalizeSpaces(existing), b = normalizeSpaces(next);
        if (a.isEmpty()) return b; if (b.isEmpty()) return a;
        String[] aw = a.split("\\s+"), bw = b.split("\\s+");
        int max = Math.min(10, Math.min(aw.length, bw.length)), drop = 0;
        for (int n = max; n >= 1; n--) {
            boolean same = true;
            for (int i = 0; i < n; i++) {
                if (!normToken(aw[aw.length - n + i]).equals(normToken(bw[i]))) { same = false; break; }
            }
            if (same) { drop = n; break; }
        }
        StringBuilder out = new StringBuilder(a);
        for (int i = drop; i < bw.length; i++) { if (out.length() > 0) out.append(' '); out.append(bw[i]); }
        return normalizeSpaces(out.toString());
    }

    private static String normToken(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    private static String normalizeSpaces(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }

    private static boolean retryable(int e) {
        return e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                e == SpeechRecognizer.ERROR_SERVER || e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                e == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS;
    }

    private static String errorName(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Speech network timeout";
            case SpeechRecognizer.ERROR_NETWORK: return "Speech network unavailable";
            case SpeechRecognizer.ERROR_AUDIO: return "Speech audio error";
            case SpeechRecognizer.ERROR_SERVER: return "Speech server error";
            case SpeechRecognizer.ERROR_CLIENT: return "Speech client error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech detected";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No speech match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Speech recognizer busy";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Speech permission missing";
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS: return "Speech service busy";
            default: return "Speech recognition error " + e;
        }
    }
}
