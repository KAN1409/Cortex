package com.kareem.cortex;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Android's own on-device recognizer used only as a shadow benchmark candidate.
 * API 33+ can accept a provided PCM stream via EXTRA_AUDIO_SOURCE, so Cortex can score
 * the immutable historical WAV rather than opening the microphone again.
 */
public final class AndroidOnDeviceAsrCandidate implements AsrCandidate {
    @Override public String id(){return "android_on_device_speech";}
    @Override public String displayName(){return "Android On-device Speech";}

    @Override public boolean isReady(Context context){
        return Build.VERSION.SDK_INT>=33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
    }

    @Override public void transcribe(Context context, File sourceWav, Callback callback){
        final long started=android.os.SystemClock.elapsedRealtime();
        if(!isReady(context)){
            callback.fail(new IllegalStateException("Android on-device speech recognition is unavailable"),0);return;
        }
        new Handler(Looper.getMainLooper()).post(()->start(context,sourceWav,started,callback));
    }

    @TargetApi(33)
    private void start(Context context, File sourceWav, long started, Callback callback){
        SpeechRecognizer recognizer=null;
        ParcelFileDescriptor readFd=null;
        ParcelFileDescriptor writeFd=null;
        try{
            WavData wav=WavData.read(sourceWav);
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            readFd=pipe[0];writeFd=pipe[1];
            final ParcelFileDescriptor finalRead=readFd;
            final ParcelFileDescriptor finalWrite=writeFd;
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            final SpeechRecognizer sr=recognizer;
            final boolean[] finished={false};

            recognizer.setRecognitionListener(new RecognitionListener(){
                private void failOnce(Exception e){
                    if(finished[0])return;finished[0]=true;
                    closeQuietly(finalRead);closeQuietly(finalWrite);try{sr.destroy();}catch(Exception ignored){}
                    callback.fail(e,android.os.SystemClock.elapsedRealtime()-started);
                }
                @Override public void onReadyForSpeech(Bundle params){}
                @Override public void onBeginningOfSpeech(){}
                @Override public void onRmsChanged(float rmsdB){}
                @Override public void onBufferReceived(byte[] buffer){}
                @Override public void onEndOfSpeech(){}
                @Override public void onError(int error){failOnce(new IllegalStateException("Android on-device ASR error "+error));}
                @Override public void onResults(Bundle results){
                    if(finished[0])return;
                    ArrayList<String> texts=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(texts==null||texts.isEmpty()||texts.get(0)==null||texts.get(0).trim().isEmpty()){
                        failOnce(new IllegalStateException("Android on-device ASR returned no transcript"));return;
                    }
                    finished[0]=true;
                    TranscriptResult out=new TranscriptResult();
                    out.text=texts.get(0).trim();out.language="ar-EG+en-US-auto-switch";
                    out.engine="android_on_device_speech";out.version=String.valueOf(Build.VERSION.SDK_INT);
                    out.durationMs=wav.durationMs();
                    closeQuietly(finalRead);closeQuietly(finalWrite);try{sr.destroy();}catch(Exception ignored){}
                    callback.ok(out,android.os.SystemClock.elapsedRealtime()-started);
                }
                @Override public void onPartialResults(Bundle partialResults){}
                @Override public void onEvent(int eventType, Bundle params){}
            });

            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-EG");
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,readFd);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,wav.channels);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,wav.sampleRate);
            if(Build.VERSION.SDK_INT>=34){
                ArrayList<String> langs=new ArrayList<>(Arrays.asList("ar-EG","en-US"));
                intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,true);
                intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,langs);
                intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,RecognizerIntent.LANGUAGE_SWITCH_BALANCED);
                intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,langs);
            }
            recognizer.startListening(intent);

            final ParcelFileDescriptor writer=writeFd;
            new Thread(()->streamPcm(sourceWav,wav,writer),"cortex-android-asr-pcm").start();
        }catch(Exception e){
            closeQuietly(readFd);closeQuietly(writeFd);if(recognizer!=null)try{recognizer.destroy();}catch(Exception ignored){}
            callback.fail(e,android.os.SystemClock.elapsedRealtime()-started);
        }
    }

    private static void streamPcm(File file,WavData wav,ParcelFileDescriptor writeFd){
        try(FileInputStream in=new FileInputStream(file);OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(writeFd)){
            long skip=wav.dataOffset;while(skip>0){long n=in.skip(skip);if(n<=0)break;skip-=n;}
            byte[] buf=new byte[8192];long left=wav.dataLength;
            while(left>0){int n=in.read(buf,0,(int)Math.min(buf.length,left));if(n<0)break;out.write(buf,0,n);left-=n;}
            out.flush();
        }catch(Exception ignored){}
    }

    private static void closeQuietly(ParcelFileDescriptor p){if(p!=null)try{p.close();}catch(Exception ignored){}}

    private static final class WavData{
        final long dataOffset,dataLength;final int sampleRate,channels,bitsPerSample;
        WavData(long o,long l,int r,int c,int b){dataOffset=o;dataLength=l;sampleRate=r;channels=c;bitsPerSample=b;}
        long durationMs(){long bytesPerSec=(long)sampleRate*channels*(bitsPerSample/8);return bytesPerSec<=0?0:(dataLength*1000L)/bytesPerSec;}
        static WavData read(File f)throws Exception{
            try(RandomAccessFile r=new RandomAccessFile(f,"r")){
                if(r.length()<44)throw new IllegalArgumentException("WAV is too small");
                byte[] id=new byte[4];r.readFully(id);if(!"RIFF".equals(new String(id,"US-ASCII")))throw new IllegalArgumentException("Not RIFF WAV");
                r.skipBytes(4);r.readFully(id);if(!"WAVE".equals(new String(id,"US-ASCII")))throw new IllegalArgumentException("Not WAVE audio");
                int rate=16000,channels=1,bits=16;long dataOffset=-1,dataLength=-1;
                while(r.getFilePointer()+8<=r.length()){
                    r.readFully(id);String chunk=new String(id,"US-ASCII");long len=readU32LE(r);long start=r.getFilePointer();
                    if("fmt ".equals(chunk)&&len>=16){int format=readU16LE(r);channels=readU16LE(r);rate=(int)readU32LE(r);r.skipBytes(6);bits=readU16LE(r);if(format!=1)throw new IllegalArgumentException("Only PCM WAV is supported");}
                    else if("data".equals(chunk)){dataOffset=start;dataLength=Math.min(len,r.length()-start);break;}
                    long next=start+len+(len&1);r.seek(Math.min(next,r.length()));
                }
                if(dataOffset<0||dataLength<=0)throw new IllegalArgumentException("WAV data chunk missing");
                if(bits!=16)throw new IllegalArgumentException("Cortex benchmark expects PCM16 WAV");
                return new WavData(dataOffset,dataLength,rate,channels,bits);
            }
        }
        static int readU16LE(RandomAccessFile r)throws Exception{return r.readUnsignedByte()|(r.readUnsignedByte()<<8);}
        static long readU32LE(RandomAccessFile r)throws Exception{return (long)r.readUnsignedByte()|((long)r.readUnsignedByte()<<8)|((long)r.readUnsignedByte()<<16)|((long)r.readUnsignedByte()<<24);}
    }
}
