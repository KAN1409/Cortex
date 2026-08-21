package com.kareem.cortex;

import java.io.*;
import java.util.*;

/** Lightweight local energy-VAD for Cortex's PCM16 mono WAV recordings. */
public final class WavSpeechChunker {
    private WavSpeechChunker(){}

    public static final class Chunk {
        public final File file; public final long startMs,endMs;
        Chunk(File f,long s,long e){file=f;startMs=s;endMs=e;}
    }
    static final class WavData {
        int sampleRate,channels,bits; byte[] pcm; long durationMs;
    }

    public static ArrayList<Chunk> split(File wav,File cacheDir)throws Exception{
        WavData w=readWav(wav);
        if(w.channels!=1||w.bits!=16)throw new IOException("Cortex VAD requires PCM16 mono WAV");
        short[] samples=toShorts(w.pcm);
        ArrayList<long[]> ranges=detectRanges(samples,w.sampleRate);
        ArrayList<Chunk> out=new ArrayList<>();
        int index=0;
        for(long[] r:ranges){
            long s=Math.max(0,r[0]),e=Math.min(w.durationMs,r[1]);
            if(e<=s)continue;
            File f=new File(cacheDir,"cortex_vad_"+System.nanoTime()+"_"+(index++)+".wav");
            writeChunk(f,samples,w.sampleRate,s,e);
            out.add(new Chunk(f,s,e));
        }
        return out;
    }

    public static long durationMs(File wav)throws Exception{return readWav(wav).durationMs;}

    static ArrayList<long[]> detectRanges(short[] samples,int sampleRate){
        ArrayList<long[]> out=new ArrayList<>();
        if(samples==null||samples.length==0||sampleRate<=0)return out;
        int frame=Math.max(1,sampleRate/50); // 20 ms
        int frames=(samples.length+frame-1)/frame;
        double[] rms=new double[frames];
        for(int i=0;i<frames;i++){
            int a=i*frame,z=Math.min(samples.length,a+frame);double sum=0;
            for(int j=a;j<z;j++){double v=samples[j];sum+=v*v;}
            rms[i]=Math.sqrt(sum/Math.max(1,z-a));
        }
        double[] sorted=rms.clone();Arrays.sort(sorted);
        int p=(int)Math.floor((sorted.length-1)*0.22);double noise=sorted[Math.max(0,p)];
        double threshold=Math.max(420.0,noise*2.35+90.0);

        boolean[] hot=new boolean[frames];for(int i=0;i<frames;i++)hot[i]=rms[i]>=threshold;
        // Bridge tiny 1-2 frame holes inside speech.
        for(int i=1;i<frames-1;i++)if(!hot[i]&&hot[i-1]&&hot[i+1])hot[i]=true;
        for(int i=1;i<frames-2;i++)if(!hot[i]&&!hot[i+1]&&hot[i-1]&&hot[i+2]){hot[i]=hot[i+1]=true;}

        int enter=2,exit=11,pre=6,post=6; // 40ms enter, 220ms hangover, 120ms padding
        int run=0,sil=0,start=-1;
        for(int i=0;i<frames;i++){
            if(hot[i]){run++;sil=0;if(start<0&&run>=enter)start=Math.max(0,i-enter+1-pre);}
            else {run=0;if(start>=0){sil++;if(sil>=exit){int end=Math.min(frames,i-exit+1+post);addRange(out,start,end,frame,sampleRate);start=-1;sil=0;}}}
        }
        if(start>=0)addRange(out,start,frames,frame,sampleRate);

        // Merge pauses <=180ms, then cap very long continuous speech at ~3.4s.
        ArrayList<long[]> merged=new ArrayList<>();
        for(long[] r:out){
            if(merged.isEmpty())merged.add(r);
            else {long[] last=merged.get(merged.size()-1);if(r[0]-last[1]<=180)last[1]=r[1];else merged.add(r);}
        }
        ArrayList<long[]> capped=new ArrayList<>();
        for(long[] r:merged){
            long s=r[0],e=r[1];
            while(e-s>3400){long cut=s+3000;capped.add(new long[]{s,cut});s=Math.max(cut-140,s+1);}
            if(e-s>=220)capped.add(new long[]{s,e});
        }
        return capped;
    }

    private static void addRange(ArrayList<long[]> out,int sf,int ef,int frame,int rate){
        long s=(long)sf*frame*1000L/rate,e=(long)ef*frame*1000L/rate;
        if(e-s>=220)out.add(new long[]{s,e});
    }

    private static WavData readWav(File f)throws Exception{
        try(RandomAccessFile r=new RandomAccessFile(f,"r")){
            if(r.length()<44)throw new IOException("WAV too small");
            byte[] h=new byte[12];r.readFully(h);
            if(h[0]!='R'||h[1]!='I'||h[2]!='F'||h[3]!='F'||h[8]!='W'||h[9]!='A'||h[10]!='V'||h[11]!='E')throw new IOException("Invalid WAV header");
            int rate=0,ch=0,bits=0,format=0;long dataPos=-1,dataLen=-1;
            while(r.getFilePointer()+8<=r.length()){
                byte[] id=new byte[4];r.readFully(id);long len=readLe32(r);long pos=r.getFilePointer();String name=new String(id,"US-ASCII");
                if("fmt ".equals(name)&&len>=16){format=readLe16(r);ch=readLe16(r);rate=(int)readLe32(r);readLe32(r);readLe16(r);bits=readLe16(r);}
                else if("data".equals(name)){dataPos=pos;dataLen=Math.min(len,r.length()-pos);break;}
                long next=pos+len+(len&1);if(next>r.length())break;r.seek(next);
            }
            if(format!=1||dataPos<0||dataLen<=0||rate<=0)throw new IOException("Unsupported WAV format");
            if(dataLen>Integer.MAX_VALUE)throw new IOException("WAV too large");
            byte[] pcm=new byte[(int)dataLen];r.seek(dataPos);r.readFully(pcm);
            WavData w=new WavData();w.sampleRate=rate;w.channels=ch;w.bits=bits;w.pcm=pcm;
            long bps=(long)rate*Math.max(1,ch)*Math.max(1,bits/8);w.durationMs=bps<=0?0:dataLen*1000L/bps;return w;
        }
    }
    private static short[] toShorts(byte[] b){short[] s=new short[b.length/2];for(int i=0;i<s.length;i++){int lo=b[i*2]&255,hi=b[i*2+1];s[i]=(short)(lo|(hi<<8));}return s;}

    private static void writeChunk(File out,short[] all,int rate,long startMs,long endMs)throws Exception{
        int a=(int)Math.max(0,Math.min(all.length,startMs*rate/1000L));int z=(int)Math.max(a,Math.min(all.length,endMs*rate/1000L));long data=(long)(z-a)*2L;
        try(RandomAccessFile f=new RandomAccessFile(out,"rw")){f.setLength(0);f.writeBytes("RIFF");le32(f,36+data);f.writeBytes("WAVEfmt ");le32(f,16);le16(f,1);le16(f,1);le32(f,rate);le32(f,rate*2L);le16(f,2);le16(f,16);f.writeBytes("data");le32(f,data);for(int i=a;i<z;i++){short v=all[i];f.write(v&255);f.write((v>>8)&255);}}
    }
    private static int readLe16(RandomAccessFile f)throws IOException{return f.readUnsignedByte()|(f.readUnsignedByte()<<8);}
    private static long readLe32(RandomAccessFile f)throws IOException{return (long)f.readUnsignedByte()|((long)f.readUnsignedByte()<<8)|((long)f.readUnsignedByte()<<16)|((long)f.readUnsignedByte()<<24);}
    private static void le16(RandomAccessFile f,long v)throws IOException{f.write((int)(v&255));f.write((int)((v>>8)&255));}
    private static void le32(RandomAccessFile f,long v)throws IOException{f.write((int)(v&255));f.write((int)((v>>8)&255));f.write((int)((v>>16)&255));f.write((int)((v>>24)&255));}
}
