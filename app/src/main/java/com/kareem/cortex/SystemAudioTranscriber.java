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
    static final class PcmChunk { File file; long startMs,endMs; }
    static final class Candidate { String text="",language="";float confidence=-1f;Exception error; }
    interface CandidateCallback { void done(Candidate c); }

    public static void transcribe(Context ctx,File audio,Callback cb){
        if(Build.VERSION.SDK_INT<33){cb.fail(new UnsupportedOperationException("Audio-file transcription requires Android 13+"));return;}
        new Thread(()->{try{PcmSource pcm=decode(audio,ctx.getCacheDir());new Handler(Looper.getMainLooper()).post(()->startAutoSwitch(ctx,pcm,cb));}catch(Exception e){cb.fail(e);}},"CortexAudioDecode").start();
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
        PcmSource p=new PcmSource();p.file=out;p.sampleRate=rate;p.channels=channels;p.encoding=encoding;p.durationMs=duration;
        if(p.durationMs<=0){long bytesPerSec=(long)p.sampleRate*Math.max(1,p.channels)*2L;p.durationMs=bytesPerSec>0?(p.file.length()*1000L/bytesPerSec):0;}
        return p;
    }

    private static void startAutoSwitch(Context ctx,PcmSource pcm,Callback cb){
        final SpeechRecognizer sr;try{sr=SpeechRecognizer.createSpeechRecognizer(ctx);}catch(Exception e){startBilingualFallback(ctx,pcm,cb,e,null);return;}
        final ParcelFileDescriptor pfd;try{pfd=ParcelFileDescriptor.open(pcm.file,ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception e){sr.destroy();startBilingualFallback(ctx,pcm,cb,e,null);return;}
        TranscriptResult result=new TranscriptResult();result.durationMs=pcm.durationMs;result.engine="android_speech_mixed";StringBuilder text=new StringBuilder();final boolean[] finished={false};final boolean[] switchProblem={false};final LinkedHashSet<String> detectedLanguages=new LinkedHashSet<>();
        RecognitionListener listener=new RecognitionListener(){
            void append(Bundle b){ArrayList<String> xs=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(xs!=null&&!xs.isEmpty()){String s=xs.get(0).trim();if(!s.isEmpty()&&(text.length()==0||!text.toString().endsWith(s))){if(text.length()>0)text.append(' ');text.append(s);}}}
            void cleanup(){try{pfd.close();}catch(Exception ignored){}try{sr.destroy();}catch(Exception ignored){}}
            void fallback(Exception why,TranscriptResult provisional){if(finished[0])return;finished[0]=true;cleanup();startBilingualFallback(ctx,pcm,cb,why,provisional);}
            void success(){if(finished[0])return;String out=text.toString().trim();if(switchProblem[0]){fallback(new IOException("Android language switch unavailable"),null);return;}if(out.isEmpty()){fallback(new IOException("No speech recognized in mixed-language mode"),null);return;}
                result.text=out;result.language=detectedLanguages.isEmpty()?"ar-EG+en-US":String.join("+",detectedLanguages);result.segments.add(new TranscriptResult.Segment(0,result.durationMs,out,0));
                // A successful recognizer result can still hide code-switching by transliterating everything into one script.
                // Audit mono-script results with the bilingual phrase-level pass and keep the richer transcript.
                if(isMonoScript(out)){fallback(new IOException("Mixed-language quality audit"),result);return;}
                finished[0]=true;cleanup();pcm.file.delete();cb.ok(result);
            }
            public void onReadyForSpeech(Bundle p){}public void onBeginningOfSpeech(){}public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}
            public void onError(int error){if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS||error==SpeechRecognizer.ERROR_AUDIO){if(finished[0])return;finished[0]=true;cleanup();pcm.file.delete();cb.fail(new IOException(errorName(error)));}else fallback(new IOException(errorName(error)),null);}
            public void onResults(Bundle b){append(b);success();}public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}
            @Override public void onSegmentResults(Bundle b){append(b);}
            @Override public void onEndOfSegmentedSession(){success();}
            @Override public void onLanguageDetection(Bundle b){if(Build.VERSION.SDK_INT>=34){String x=b.getString(SpeechRecognizer.DETECTED_LANGUAGE);if(x!=null&&!x.isEmpty())detectedLanguages.add(x);int sw=b.getInt(SpeechRecognizer.LANGUAGE_SWITCH_RESULT,SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED);if(sw==SpeechRecognizer.LANGUAGE_SWITCH_RESULT_FAILED||sw==SpeechRecognizer.LANGUAGE_SWITCH_RESULT_SKIPPED_NO_MODEL)switchProblem[0]=true;}}
        };
        sr.setRecognitionListener(listener);
        Intent i=baseIntent("ar-EG",pcm,pfd);i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,RecognizerIntent.EXTRA_AUDIO_SOURCE);
        if(Build.VERSION.SDK_INT>=34){i.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,RecognizerIntent.LANGUAGE_SWITCH_QUICK_RESPONSE);i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,new ArrayList<>(Arrays.asList("ar-EG","en-US")));i.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,new ArrayList<>(Arrays.asList("ar-EG","en-US")));}
        try{sr.startListening(i);}catch(Exception e){try{pfd.close();}catch(Exception ignored){}try{sr.destroy();}catch(Exception ignored){}startBilingualFallback(ctx,pcm,cb,e,null);}
    }

    private static void startBilingualFallback(Context ctx,PcmSource pcm,Callback cb,Exception original,TranscriptResult provisional){
        new Thread(()->{try{ArrayList<PcmChunk> chunks=makeSpeechChunks(pcm,ctx.getCacheDir());new Handler(Looper.getMainLooper()).post(()->processChunk(ctx,pcm,chunks,0,new TranscriptResult(),new LinkedHashSet<>(),cb,original,provisional));}catch(Exception e){pcm.file.delete();if(provisional!=null&&!provisional.text.trim().isEmpty())cb.ok(provisional);else cb.fail(new IOException("Mixed-language fallback failed: "+e.getMessage(),e));}},"CortexBilingualPrep").start();
    }

    // Phrase-level segmentation: find natural low-energy gaps and cut there. This prevents one language from owning an entire 7-second block.
    private static ArrayList<PcmChunk> makeSpeechChunks(PcmSource pcm,File cache) throws Exception {
        if(pcm.encoding!=AudioFormat.ENCODING_PCM_16BIT||pcm.channels<1)return makeFixedChunks(pcm,cache,3200,280);
        byte[] all=readAll(pcm.file);int frameSamples=Math.max(1,pcm.sampleRate/50); // 20 ms
        int frameBytes=frameSamples*pcm.channels*2;int frames=Math.max(1,all.length/frameBytes);double[] rms=new double[frames];
        double sum=0;for(int f=0;f<frames;f++){long ss=0;int n=0;int base=f*frameBytes;int end=Math.min(all.length,base+frameBytes);for(int p=base;p+1<end;p+=2*pcm.channels){short v=(short)((all[p]&255)|(all[p+1]<<8));ss+=(long)v*v;n++;}rms[f]=n==0?0:Math.sqrt(ss/(double)n);sum+=rms[f];}
        double avg=sum/frames,threshold=Math.max(180,avg*0.22);int silenceFrames=14; // ~280 ms
        ArrayList<Long> cuts=new ArrayList<>();cuts.add(0L);int quiet=0;for(int f=0;f<frames;f++){if(rms[f]<threshold)quiet++;else quiet=0;if(quiet==silenceFrames){long ms=Math.max(0,(f-silenceFrames/2)*20L);long last=cuts.get(cuts.size()-1);if(ms-last>=900)cuts.add(ms);}}
        long total=Math.max(1,pcm.durationMs);if(total-cuts.get(cuts.size()-1)<650&&cuts.size()>1)cuts.remove(cuts.size()-1);cuts.add(total);
        ArrayList<PcmChunk> out=new ArrayList<>();for(int i=0;i<cuts.size()-1;i++){long a=cuts.get(i),b=cuts.get(i+1);if(b-a<500)continue;while(b-a>4800){long x=a+3600;out.add(writeChunk(pcm,cache,all,a,Math.min(total,x+180)));a=x;}out.add(writeChunk(pcm,cache,all,Math.max(0,a-120),Math.min(total,b+120)));}
        if(out.isEmpty())return makeFixedChunks(pcm,cache,2800,240);return out;
    }

    private static ArrayList<PcmChunk> makeFixedChunks(PcmSource pcm,File cache,long chunkMs,long overlapMs) throws Exception {ArrayList<PcmChunk> out=new ArrayList<>();byte[] all=readAll(pcm.file);long total=Math.max(1,pcm.durationMs);for(long s=0;s<total;s+=Math.max(500,chunkMs-overlapMs)){long e=Math.min(total,s+chunkMs);out.add(writeChunk(pcm,cache,all,s,e));if(e>=total)break;}return out;}
    private static byte[] readAll(File f)throws Exception{FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream((int)Math.min(Integer.MAX_VALUE,f.length()));byte[] b=new byte[32768];int n;try{while((n=in.read(b))>0)out.write(b,0,n);}finally{in.close();}return out.toByteArray();}
    private static PcmChunk writeChunk(PcmSource pcm,File cache,byte[] all,long start,long end)throws Exception{long bps=(long)pcm.sampleRate*Math.max(1,pcm.channels)*2L;int align=Math.max(2,pcm.channels*2);long a=start*bps/1000L,z=end*bps/1000L;a-=a%align;z-=z%align;a=Math.max(0,Math.min(a,all.length));z=Math.max(a,Math.min(z,all.length));File f=new File(cache,"cortex_mix_"+System.nanoTime()+".raw");FileOutputStream os=new FileOutputStream(f);try{os.write(all,(int)a,(int)(z-a));}finally{os.close();}PcmChunk c=new PcmChunk();c.file=f;c.startMs=start;c.endMs=end;return c;}

    private static void processChunk(Context ctx,PcmSource pcm,ArrayList<PcmChunk> chunks,int index,TranscriptResult result,Set<String> languages,Callback cb,Exception original,TranscriptResult provisional){
        if(index>=chunks.size()){pcm.file.delete();result.text=result.text.trim();result.durationMs=pcm.durationMs;result.engine="android_speech_bilingual_phrase_audit";result.language=languages.isEmpty()?"mixed":String.join("+",languages);
            if(result.text.isEmpty()){if(provisional!=null&&!provisional.text.trim().isEmpty())cb.ok(provisional);else cb.fail(new IOException("Mixed Arabic/English transcription failed after fallback",original));return;}
            if(provisional!=null&&!provisional.text.trim().isEmpty()&&!preferAudit(result,provisional))cb.ok(provisional);else cb.ok(result);return;}
        PcmChunk chunk=chunks.get(index);recognizeFixed(ctx,pcm,chunk,"ar-EG",0,ar->recognizeFixed(ctx,pcm,chunk,"en-US",0,en->{Candidate best=choose(ar,en);chunk.file.delete();if(best!=null&&!best.text.trim().isEmpty()){String clean=best.text.trim();result.text=mergeOverlap(result.text,clean);result.segments.add(new TranscriptResult.Segment(chunk.startMs,chunk.endMs,clean,best.confidence));languages.add(best.language);}new Handler(Looper.getMainLooper()).postDelayed(()->processChunk(ctx,pcm,chunks,index+1,result,languages,cb,original,provisional),100);}));
    }

    private static void recognizeFixed(Context ctx,PcmSource pcm,PcmChunk chunk,String language,int attempt,CandidateCallback cb){
        Candidate out=new Candidate();out.language=language;final SpeechRecognizer sr;try{sr=SpeechRecognizer.createSpeechRecognizer(ctx);}catch(Exception e){out.error=e;cb.done(out);return;}
        final ParcelFileDescriptor pfd;try{pfd=ParcelFileDescriptor.open(chunk.file,ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception e){sr.destroy();out.error=e;cb.done(out);return;}final boolean[] finished={false};
        RecognitionListener listener=new RecognitionListener(){
            void cleanup(){try{pfd.close();}catch(Exception ignored){}try{sr.destroy();}catch(Exception ignored){}}
            void finish(Bundle b){if(finished[0])return;finished[0]=true;ArrayList<String> xs=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(xs!=null&&!xs.isEmpty())out.text=xs.get(0)==null?"":xs.get(0).trim();float[] scores=b==null?null:b.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);if(scores!=null&&scores.length>0)out.confidence=scores[0];cleanup();cb.done(out);}
            public void onReadyForSpeech(Bundle p){}public void onBeginningOfSpeech(){}public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}
            public void onError(int error){if(finished[0])return;finished[0]=true;cleanup();if(attempt<1&&retryable(error)){new Handler(Looper.getMainLooper()).postDelayed(()->recognizeFixed(ctx,pcm,chunk,language,attempt+1,cb),400);return;}out.error=new IOException(errorName(error));cb.done(out);}
            public void onResults(Bundle b){finish(b);}public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}@Override public void onSegmentResults(Bundle b){finish(b);}@Override public void onEndOfSegmentedSession(){if(!finished[0])finish(null);}
        };
        sr.setRecognitionListener(listener);Intent i=baseIntent(language,pcm,pfd);i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,RecognizerIntent.EXTRA_AUDIO_SOURCE);try{sr.startListening(i);}catch(Exception e){try{pfd.close();}catch(Exception ignored){}try{sr.destroy();}catch(Exception ignored){}out.error=e;cb.done(out);}
    }

    private static Intent baseIntent(String language,PcmSource pcm,ParcelFileDescriptor pfd){Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,language);i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,pfd);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,pcm.channels);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,pcm.encoding);i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,pcm.sampleRate);return i;}

    private static Candidate choose(Candidate ar,Candidate en){if((ar==null||ar.text.isEmpty())&&(en==null||en.text.isEmpty()))return null;if(ar==null||ar.text.isEmpty())return en;if(en==null||en.text.isEmpty())return ar;double as=score(ar,true),es=score(en,false);return es>as?en:ar;}
    private static double score(Candidate c,boolean arabic){double conf=c.confidence>=0?Math.max(0,Math.min(1,c.confidence)):0.50;int a=arabicCount(c.text),l=latinCount(c.text);int letters=Math.max(1,a+l);double fit=(arabic?a:l)/(double)letters;double mixedBonus=(a>0&&l>0)?0.12:0;return conf*0.62+fit*0.22+mixedBonus+Math.min(.04,c.text.length()/300.0);}
    private static boolean preferAudit(TranscriptResult audit,TranscriptResult provisional){boolean am=isMixedScript(audit.text),pm=isMixedScript(provisional.text);if(am&&!pm)return true;if(pm&&!am)return false;int auditWords=wordCount(audit.text),provWords=wordCount(provisional.text);return auditWords>=Math.max(2,(int)(provWords*0.75));}
    private static boolean isMonoScript(String s){int a=arabicCount(s),l=latinCount(s);return (a>0&&l==0)||(l>0&&a==0);}
    private static boolean isMixedScript(String s){return arabicCount(s)>0&&latinCount(s)>0;}
    private static int arabicCount(String s){int n=0;if(s!=null)for(int i=0;i<s.length();i++){char c=s.charAt(i);if((c>='\u0600'&&c<='\u06FF')||(c>='\u0750'&&c<='\u077F'))n++;}return n;}
    private static int latinCount(String s){int n=0;if(s!=null)for(int i=0;i<s.length();i++){char c=s.charAt(i);if((c>='A'&&c<='Z')||(c>='a'&&c<='z'))n++;}return n;}
    private static int wordCount(String s){String x=s==null?"":s.trim();return x.isEmpty()?0:x.split("\\s+").length;}
    private static String mergeOverlap(String existing,String next){String a=existing==null?"":existing.trim(),b=next==null?"":next.trim();if(a.isEmpty())return b;if(b.isEmpty())return a;String[] aw=a.split("\\s+"),bw=b.split("\\s+");int max=Math.min(8,Math.min(aw.length,bw.length)),drop=0;for(int n=max;n>=1;n--){boolean same=true;for(int i=0;i<n;i++)if(!normToken(aw[aw.length-n+i]).equals(normToken(bw[i]))){same=false;break;}if(same){drop=n;break;}}StringBuilder out=new StringBuilder(a);for(int i=drop;i<bw.length;i++){if(out.length()>0)out.append(' ');out.append(bw[i]);}return out.toString();}
    private static String normToken(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]","");}
    private static boolean retryable(int e){return e==SpeechRecognizer.ERROR_NETWORK||e==SpeechRecognizer.ERROR_NETWORK_TIMEOUT||e==SpeechRecognizer.ERROR_SERVER||e==SpeechRecognizer.ERROR_RECOGNIZER_BUSY||e==SpeechRecognizer.ERROR_TOO_MANY_REQUESTS;}
    private static String errorName(int e){switch(e){case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return "Speech network timeout";case SpeechRecognizer.ERROR_NETWORK:return "Speech network unavailable";case SpeechRecognizer.ERROR_AUDIO:return "Speech audio error";case SpeechRecognizer.ERROR_SERVER:return "Speech server error";case SpeechRecognizer.ERROR_CLIENT:return "Speech client error";case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:return "No speech detected";case SpeechRecognizer.ERROR_NO_MATCH:return "No speech match";case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:return "Speech recognizer busy";case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return "Speech permission missing";case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS:return "Speech service busy";default:return "Speech recognition error "+e;}}
}
