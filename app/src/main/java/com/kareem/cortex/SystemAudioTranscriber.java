package com.kareem.cortex;

import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.*;
import java.io.*;
import java.nio.*;
import java.util.*;

public final class SystemAudioTranscriber {
    public interface Callback{void ok(TranscriptResult r);void fail(Exception e);}
    private SystemAudioTranscriber(){}

    static final class PcmSource { File file; int sampleRate=16000,channels=1,encoding=AudioFormat.ENCODING_PCM_16BIT; long durationMs; }

    public static void transcribe(Context ctx,File audio,Callback cb){
        if(Build.VERSION.SDK_INT<33){cb.fail(new UnsupportedOperationException("Audio-file transcription requires Android 13+"));return;}
        new Thread(()->{try{PcmSource pcm=decode(audio,ctx.getCacheDir());new Handler(Looper.getMainLooper()).post(()->startRecognizer(ctx,pcm,cb));}catch(Exception e){cb.fail(e);}},"CortexAudioDecode").start();
    }

    private static PcmSource decode(File source,File cache) throws Exception {
        MediaExtractor ex=new MediaExtractor();ex.setDataSource(source.getAbsolutePath());int track=-1;MediaFormat fmt=null;
        for(int i=0;i<ex.getTrackCount();i++){MediaFormat f=ex.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){track=i;fmt=f;break;}}
        if(track<0||fmt==null){ex.release();throw new IOException("No audio track found");}ex.selectTrack(track);
        String mime=fmt.getString(MediaFormat.KEY_MIME);if(mime==null){ex.release();throw new IOException("Unknown audio format");}
        if(Build.VERSION.SDK_INT>=24)fmt.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
        MediaCodec codec=MediaCodec.createDecoderByType(mime);codec.configure(fmt,null,null,0);codec.start();
        File out=new File(cache,"cortex_pcm_"+System.nanoTime()+".raw");FileOutputStream os=new FileOutputStream(out);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputDone=false,outputDone=false;int rate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)?fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE):16000;int channels=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;int encoding=AudioFormat.ENCODING_PCM_16BIT;long duration=fmt.containsKey(MediaFormat.KEY_DURATION)?fmt.getLong(MediaFormat.KEY_DURATION)/1000:0;
        try{
            while(!outputDone){
                if(!inputDone){int in=codec.dequeueInputBuffer(10000);if(in>=0){ByteBuffer b=codec.getInputBuffer(in);int n=ex.readSampleData(b,0);if(n<0){codec.queueInputBuffer(in,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{codec.queueInputBuffer(in,0,n,ex.getSampleTime(),0);ex.advance();}}}
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat of=codec.getOutputFormat();if(of.containsKey(MediaFormat.KEY_SAMPLE_RATE))rate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE);if(of.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(Build.VERSION.SDK_INT>=24&&of.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=of.getInteger(MediaFormat.KEY_PCM_ENCODING);}
                else if(index>=0){ByteBuffer b=codec.getOutputBuffer(index);if(b!=null&&info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){b.position(info.offset);b.limit(info.offset+info.size);byte[] bytes=new byte[info.size];b.get(bytes);os.write(bytes);}outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);}
            }
        }finally{try{os.close();}catch(Exception ignored){}try{codec.stop();}catch(Exception ignored){}codec.release();ex.release();}
        PcmSource p=new PcmSource();p.file=out;p.sampleRate=rate;p.channels=channels;p.encoding=encoding;p.durationMs=duration;return p;
    }

    private static void startRecognizer(Context ctx,PcmSource pcm,Callback cb){
        final SpeechRecognizer sr;try{sr=SpeechRecognizer.createSpeechRecognizer(ctx);}catch(Exception e){pcm.file.delete();cb.fail(e);return;}
        final ParcelFileDescriptor pfd;try{pfd=ParcelFileDescriptor.open(pcm.file,ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception e){sr.destroy();pcm.file.delete();cb.fail(e);return;}
        TranscriptResult result=new TranscriptResult();result.durationMs=pcm.durationMs;StringBuilder text=new StringBuilder();final boolean[] finished={false};
        RecognitionListener listener=new RecognitionListener(){
            String detected="";
            void append(Bundle b){
                ArrayList<String> xs=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(xs!=null&&!xs.isEmpty()){String s=xs.get(0).trim();if(!s.isEmpty()&&(text.length()==0||!text.toString().endsWith(s))){if(text.length()>0)text.append(' ');text.append(s);}}
                if(Build.VERSION.SDK_INT>=34){try{ArrayList<RecognitionPart> parts=b.getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS);if(parts!=null)for(RecognitionPart p:parts){String raw=p.getFormattedText()!=null?p.getFormattedText():p.getRawText();if(raw!=null&&!raw.trim().isEmpty())result.segments.add(new TranscriptResult.Segment(p.getTimestampMillis(),p.getTimestampMillis(),raw.trim(),0));}}catch(Exception ignored){}}
            }
            void done(){if(finished[0])return;finished[0]=true;result.text=text.toString().trim();result.language=detected;if(result.segments.isEmpty()&&!result.text.isEmpty())result.segments.add(new TranscriptResult.Segment(0,result.durationMs,result.text,0));cleanup();if(result.text.isEmpty())cb.fail(new IOException("No speech recognized"));else cb.ok(result);}
            void cleanup(){try{pfd.close();}catch(Exception ignored){}try{sr.destroy();}catch(Exception ignored){}pcm.file.delete();}
            public void onReadyForSpeech(Bundle p){}public void onBeginningOfSpeech(){}public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}
            public void onError(int error){if(finished[0])return;finished[0]=true;cleanup();cb.fail(new IOException("Speech recognition error "+error));}
            public void onResults(Bundle b){append(b);done();}
            public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}
            @Override public void onSegmentResults(Bundle b){append(b);}
            @Override public void onEndOfSegmentedSession(){done();}
            @Override public void onLanguageDetection(Bundle b){if(Build.VERSION.SDK_INT>=34){String x=b.getString(SpeechRecognizer.DETECTED_LANGUAGE);if(x!=null)detected=x;}}
        };
        sr.setRecognitionListener(listener);
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-EG");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,pfd);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,pcm.channels);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,pcm.encoding);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,pcm.sampleRate);i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,RecognizerIntent.EXTRA_AUDIO_SOURCE);
        if(Build.VERSION.SDK_INT>=34){i.putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING,true);i.putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE,true);i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,RecognizerIntent.LANGUAGE_SWITCH_BALANCED);i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,new ArrayList<>(Arrays.asList("ar-EG","en-US","en-GB")));}
        try{sr.startListening(i);}catch(Exception e){try{pfd.close();}catch(Exception ignored){}sr.destroy();pcm.file.delete();cb.fail(e);}
    }
}
