package com.kareem.cortex;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import java.io.*;

public final class AudioCapture {
    private static final int RATE=16000;
    private AudioRecord record; private Thread thread; private volatile boolean running; private RandomAccessFile out; private File file; private long pcmBytes;

    public boolean hasPermission(Context c){return android.os.Build.VERSION.SDK_INT<23||c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}

    public File start(Context ctx) throws Exception {
        if(running)throw new IllegalStateException("Already recording");
        File dir=new File(ctx.getFilesDir(),"audio");if(!dir.exists())dir.mkdirs();file=new File(dir,"voice_"+System.currentTimeMillis()+".wav");
        int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);int buffer=Math.max(min,8192);
        record=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer);
        if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone initialization failed");
        out=new RandomAccessFile(file,"rw");writeHeader(out,0);pcmBytes=0;running=true;record.startRecording();
        thread=new Thread(()->{byte[] b=new byte[buffer];try{while(running){int n=record.read(b,0,b.length);if(n>0){out.write(b,0,n);pcmBytes+=n;}}}catch(Exception ignored){}},"CortexVoiceRecorder");thread.start();return file;
    }

    public File stop() throws Exception {
        if(!running)return file;running=false;try{record.stop();}catch(Exception ignored){}if(thread!=null)try{thread.join(1200);}catch(InterruptedException ignored){}
        try{record.release();}catch(Exception ignored){}record=null;if(out!=null){out.seek(0);writeHeader(out,pcmBytes);out.close();out=null;}return file;
    }
    public boolean isRunning(){return running;}

    private static void writeHeader(RandomAccessFile f,long data) throws IOException {
        int channels=1,bits=16;long byteRate=RATE*channels*bits/8;f.writeBytes("RIFF");le32(f,36+data);f.writeBytes("WAVEfmt ");le32(f,16);le16(f,1);le16(f,channels);le32(f,RATE);le32(f,byteRate);le16(f,channels*bits/8);le16(f,bits);f.writeBytes("data");le32(f,data);
    }
    private static void le16(RandomAccessFile f,long v)throws IOException{f.write((int)(v&255));f.write((int)((v>>8)&255));}
    private static void le32(RandomAccessFile f,long v)throws IOException{f.write((int)(v&255));f.write((int)((v>>8)&255));f.write((int)((v>>16)&255));f.write((int)((v>>24)&255));}
}
