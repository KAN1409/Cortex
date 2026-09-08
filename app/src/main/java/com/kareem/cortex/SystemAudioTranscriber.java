package com.kareem.cortex;

import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Zero-cost voice-note transcriber.
 *
 * Primary: Android on-device SpeechRecognizer when available.
 * Fallback: the device's default SpeechRecognizer (may use the phone's own network service,
 * but never requires a Cortex API key or user-billed API account).
 *
 * The source audio file is never modified or deleted. Only a temporary decoded PCM file is used.
 */
public final class SystemAudioTranscriber {
    public interface Callback{void ok(TranscriptResult r);void fail(Exception e);}
    public interface ModelCallback{void done(boolean ok,String detail);}
    private SystemAudioTranscriber(){}

    static final class PcmSource { File file; int sampleRate=16000,channels=1,encoding=AudioFormat.ENCODING_PCM_16BIT; long durationMs; }
    public static final class RuntimeStatus{
        public final boolean platform,onDevice;public final String detail;
        RuntimeStatus(boolean p,boolean o,String d){platform=p;onDevice=o;detail=d;}
    }

    public static RuntimeStatus runtimeStatus(Context ctx){
        try{
            boolean platform=SpeechRecognizer.isRecognitionAvailable(ctx);
            boolean local=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx);
            return new RuntimeStatus(platform,local,local?"On-device speech recognition available":(platform?"System speech recognition available; on-device recognizer not currently exposed":"No Android speech recognition service available"));
        }catch(Throwable e){return new RuntimeStatus(false,false,"Speech runtime check failed: "+safe(e));}
    }

    public static void transcribe(Context ctx,File audio,Callback cb){
        if(Build.VERSION.SDK_INT<33){cb.fail(new UnsupportedOperationException("Saved voice-note transcription requires Android 13+"));return;}
        if(audio==null||!audio.exists()||!audio.isFile()){cb.fail(new FileNotFoundException("Audio file is missing"));return;}
        final Context app=ctx.getApplicationContext();
        new Thread(()->{
            try{
                PcmSource pcm=decode(audio,app.getCacheDir());
                new Handler(Looper.getMainLooper()).post(()->startAttempt(app,pcm,true,cb));
            }catch(Exception e){cb.fail(e);}
        },"CortexAudioDecode").start();
    }

    /** Ask Android to prepare the on-device Arabic/English speech model. Safe to call from UI. */
    public static void requestOnDeviceModel(Context ctx,ModelCallback cb){
        final Context app=ctx.getApplicationContext();new Handler(Looper.getMainLooper()).post(()->{
            if(Build.VERSION.SDK_INT<31){done(cb,false,"On-device SpeechRecognizer requires Android 12+");return;}
            if(!SpeechRecognizer.isOnDeviceRecognitionAvailable(app)){done(cb,false,"Android reports no on-device speech recognizer on this runtime");return;}
            SpeechRecognizer sr=null;try{
                sr=SpeechRecognizer.createOnDeviceSpeechRecognizer(app);final SpeechRecognizer recognizer=sr;Intent request=languageIntent();
                if(Build.VERSION.SDK_INT>=34){
                    recognizer.triggerModelDownload(request,app.getMainExecutor(),new ModelDownloadListener(){
                        public void onProgress(int completedPercent){done(cb,true,"Downloading on-device speech model · "+completedPercent+"%");}
                        public void onSuccess(){try{recognizer.destroy();}catch(Throwable ignored){}done(cb,true,"On-device speech model ready");}
                        public void onScheduled(){try{recognizer.destroy();}catch(Throwable ignored){}done(cb,true,"On-device speech model download scheduled by Android");}
                        public void onError(int error){try{recognizer.destroy();}catch(Throwable ignored){}done(cb,false,"Android speech-model download error "+error);}
                    });
                }else if(Build.VERSION.SDK_INT>=33){
                    recognizer.triggerModelDownload(request);try{recognizer.destroy();}catch(Throwable ignored){}done(cb,true,"On-device speech model download requested from Android");
                }else{try{recognizer.destroy();}catch(Throwable ignored){}done(cb,true,"On-device speech recognizer is available");}
            }catch(Throwable e){if(sr!=null)try{sr.destroy();}catch(Throwable ignored){}done(cb,false,"Could not prepare on-device speech: "+safe(e));}
        });
    }

    private static void startAttempt(Context ctx,PcmSource pcm,boolean preferOnDevice,Callback cb){
        boolean useOnDevice=preferOnDevice&&Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx);
        if(!useOnDevice&&preferOnDevice){startAttempt(ctx,pcm,false,cb);return;}
        if(!useOnDevice&&!SpeechRecognizer.isRecognitionAvailable(ctx)){finishFailure(pcm,cb,new UnsupportedOperationException("No Android speech recognition service is available"));return;}

        final SpeechRecognizer sr;
        try{sr=useOnDevice?SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx):SpeechRecognizer.createSpeechRecognizer(ctx);}catch(Throwable e){if(useOnDevice){startAttempt(ctx,pcm,false,cb);return;}finishFailure(pcm,cb,new IOException("Could not create Android speech recognizer: "+safe(e),e));return;}

        final ParcelFileDescriptor pfd;
        try{pfd=ParcelFileDescriptor.open(pcm.file,ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception e){try{sr.destroy();}catch(Throwable ignored){}finishFailure(pcm,cb,e);return;}

        final boolean local=useOnDevice;final AtomicBoolean finished=new AtomicBoolean(false);final Handler main=new Handler(Looper.getMainLooper());
        final Runnable timeout=()->{
            if(!finished.compareAndSet(false,true))return;cleanupAttempt(sr,pfd);
            if(local)startAttempt(ctx,pcm,false,cb);else finishFailure(pcm,cb,new IOException("Android speech recognition timed out"));
        };
        main.postDelayed(timeout,local?90_000L:135_000L);

        TranscriptResult result=new TranscriptResult();result.durationMs=pcm.durationMs;result.engine=local?"android_on_device_asr":"android_system_asr";result.version="cortex-v64";StringBuilder text=new StringBuilder();final String[] detected={""};
        RecognitionListener listener=new RecognitionListener(){
            void append(Bundle b){
                ArrayList<String> xs=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(xs!=null&&!xs.isEmpty()){
                    String s=xs.get(0)==null?"":xs.get(0).trim();
                    if(!s.isEmpty()&&(text.length()==0||!sameTail(text.toString(),s))){if(text.length()>0)text.append(' ');text.append(s);}
                }
                if(Build.VERSION.SDK_INT>=34){try{ArrayList<RecognitionPart> parts=b.getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS);if(parts!=null)for(RecognitionPart p:parts){String raw=p.getFormattedText()!=null?p.getFormattedText():p.getRawText();if(raw!=null&&!raw.trim().isEmpty())result.segments.add(new TranscriptResult.Segment(p.getTimestampMillis(),p.getTimestampMillis(),raw.trim(),0));}}catch(Throwable ignored){}}
            }
            void complete(){
                if(!finished.compareAndSet(false,true))return;main.removeCallbacks(timeout);result.text=text.toString().replaceAll("\\s+"," ").trim();result.rawTranscript=result.text;result.providerMergedTranscript=result.text;result.language=detected[0].isEmpty()?"ar-EG+en-auto":detected[0];result.processedDurationMs=result.durationMs;result.coverage=result.durationMs>0?1.0:0.0;if(result.segments.isEmpty()&&!result.text.isEmpty())result.segments.add(new TranscriptResult.Segment(0,result.durationMs,result.text,0));cleanupAttempt(sr,pfd);
                if(result.text.isEmpty()){if(local)startAttempt(ctx,pcm,false,cb);else finishFailure(pcm,cb,new IOException("No speech recognized"));}
                else{pcm.file.delete();cb.ok(result);}
            }
            public void onReadyForSpeech(Bundle p){}public void onBeginningOfSpeech(){}public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}
            public void onError(int error){if(!finished.compareAndSet(false,true))return;main.removeCallbacks(timeout);cleanupAttempt(sr,pfd);if(local)startAttempt(ctx,pcm,false,cb);else finishFailure(pcm,cb,new IOException("Android speech recognition error "+error));}
            public void onResults(Bundle b){append(b);complete();}public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}
            @Override public void onSegmentResults(Bundle b){append(b);}
            @Override public void onEndOfSegmentedSession(){complete();}
            @Override public void onLanguageDetection(Bundle b){if(Build.VERSION.SDK_INT>=34){String x=b.getString(SpeechRecognizer.DETECTED_LANGUAGE);if(x!=null&&!x.trim().isEmpty())detected[0]=x.trim();}}
        };
        sr.setRecognitionListener(listener);
        Intent i=languageIntent();i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,pfd);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,pcm.channels);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,pcm.encoding);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,pcm.sampleRate);i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,RecognizerIntent.EXTRA_AUDIO_SOURCE);
        try{sr.startListening(i);}catch(Throwable e){main.removeCallbacks(timeout);if(finished.compareAndSet(false,true)){cleanupAttempt(sr,pfd);if(local)startAttempt(ctx,pcm,false,cb);else finishFailure(pcm,cb,new IOException("Could not start Android speech recognition: "+safe(e),e));}}
    }

    private static Intent languageIntent(){
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-EG");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        if(Build.VERSION.SDK_INT>=34){ArrayList<String> languages=new ArrayList<>(Arrays.asList("ar-EG","en-US","en-GB"));i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,true);i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,languages);i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,RecognizerIntent.LANGUAGE_SWITCH_BALANCED);i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,languages);i.putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING,true);i.putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE,true);}
        return i;
    }

    private static PcmSource decode(File source,File cache) throws Exception {
        MediaExtractor ex=new MediaExtractor();ex.setDataSource(source.getAbsolutePath());int track=-1;MediaFormat fmt=null;
        for(int i=0;i<ex.getTrackCount();i++){MediaFormat f=ex.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){track=i;fmt=f;break;}}
        if(track<0||fmt==null){ex.release();throw new IOException("No audio track found");}ex.selectTrack(track);String mime=fmt.getString(MediaFormat.KEY_MIME);if(mime==null){ex.release();throw new IOException("Unknown audio format");}if(Build.VERSION.SDK_INT>=24)fmt.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
        MediaCodec codec=MediaCodec.createDecoderByType(mime);codec.configure(fmt,null,null,0);codec.start();File out=new File(cache,"cortex_pcm_"+System.nanoTime()+".raw");FileOutputStream os=new FileOutputStream(out);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputDone=false,outputDone=false;int rate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)?fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE):16000;int channels=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;int encoding=AudioFormat.ENCODING_PCM_16BIT;long duration=fmt.containsKey(MediaFormat.KEY_DURATION)?fmt.getLong(MediaFormat.KEY_DURATION)/1000:0;
        try{while(!outputDone){if(!inputDone){int in=codec.dequeueInputBuffer(10000);if(in>=0){ByteBuffer b=codec.getInputBuffer(in);int n=ex.readSampleData(b,0);if(n<0){codec.queueInputBuffer(in,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{codec.queueInputBuffer(in,0,n,ex.getSampleTime(),0);ex.advance();}}}int index=codec.dequeueOutputBuffer(info,10000);if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat of=codec.getOutputFormat();if(of.containsKey(MediaFormat.KEY_SAMPLE_RATE))rate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE);if(of.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(Build.VERSION.SDK_INT>=24&&of.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=of.getInteger(MediaFormat.KEY_PCM_ENCODING);}else if(index>=0){ByteBuffer b=codec.getOutputBuffer(index);if(b!=null&&info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){b.position(info.offset);b.limit(info.offset+info.size);byte[] bytes=new byte[info.size];b.get(bytes);os.write(bytes);}outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);}}}finally{try{os.close();}catch(Exception ignored){}try{codec.stop();}catch(Exception ignored){}codec.release();ex.release();}
        PcmSource p=new PcmSource();p.file=out;p.sampleRate=rate;p.channels=channels;p.encoding=encoding;p.durationMs=duration;return p;
    }

    private static void cleanupAttempt(SpeechRecognizer sr,ParcelFileDescriptor pfd){try{pfd.close();}catch(Throwable ignored){}try{sr.cancel();}catch(Throwable ignored){}try{sr.destroy();}catch(Throwable ignored){}}
    private static void finishFailure(PcmSource pcm,Callback cb,Exception e){if(pcm!=null&&pcm.file!=null)pcm.file.delete();cb.fail(e);}
    private static boolean sameTail(String current,String candidate){String a=current.trim(),b=candidate.trim();if(a.equals(b)||a.endsWith(" "+b))return true;int max=Math.min(120,Math.min(a.length(),b.length()));return max>12&&a.substring(a.length()-max).equals(b.substring(b.length()-max));}
    private static void done(ModelCallback cb,boolean ok,String detail){if(cb!=null)try{cb.done(ok,detail);}catch(Throwable ignored){}}
    private static String safe(Throwable e){String m=e==null?"":e.getMessage();return m==null||m.trim().isEmpty()?(e==null?"unknown error":e.getClass().getSimpleName()):m.trim();}
}
