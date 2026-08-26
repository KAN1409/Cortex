package com.kareem.cortex;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Deterministic audio fixture for the explicit experimental user-journey sandbox.
 *
 * This deliberately does NOT pretend to validate a live ASR provider. It creates a real WAV file
 * so capture/storage/queue/audio-result semantics can be exercised without touching the user's
 * microphone. AudioAnalyzer may substitute the declared transcript only while
 * CortexExperimentalTestMode is active and this exact metadata marker is present.
 */
public final class SyntheticAudioFixture {
    public static final String SOURCE="robot_synthetic_audio";
    public static final String MARKER="cortex_synthetic_asr_fixture_v1";
    public static final String TRANSCRIPT="Finally now هنجرب Cortex synthetic transcription with English وعربي مع بعض.";
    public static final String LANGUAGE="ar-en";
    public static final long DURATION_MS=3200L;
    private static final int RATE=16000;

    private SyntheticAudioFixture(){}

    public static File create(Context c)throws IOException{
        if(c==null||!CortexExperimentalTestMode.active(c))throw new SecurityException("Synthetic audio fixture is test-mode only");
        File dir=new File(c.getFilesDir(),"debug_exports/user_journey_inputs");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create fixture directory");
        File f=new File(dir,"robot_synthetic_voice.wav");
        long samples=(DURATION_MS*RATE)/1000L;long dataBytes=samples*2L;
        try(RandomAccessFile out=new RandomAccessFile(f,"rw")){
            out.setLength(0);writeHeader(out,dataBytes);
            for(long i=0;i<samples;i++){
                // Deterministic, low-amplitude two-tone signal. The generic suite validates the
                // pipeline contract, not acoustic recognition quality.
                double t=(double)i/(double)RATE;
                int sample=(int)(2200.0*Math.sin(2.0*Math.PI*440.0*t)+900.0*Math.sin(2.0*Math.PI*660.0*t));
                out.write(sample&255);out.write((sample>>8)&255);
            }
        }
        return f;
    }

    public static String metadata(File f)throws Exception{
        JSONObject m=new JSONObject();m.put("mime","audio/wav");m.put("bytes",f==null?0:f.length());m.put("recorded_at",System.currentTimeMillis());m.put("robot_test",true);m.put("synthetic_asr_fixture",MARKER);m.put("synthetic_transcript",TRANSCRIPT);m.put("synthetic_language",LANGUAGE);m.put("synthetic_duration_ms",DURATION_MS);m.put("live_provider_tested",false);return m.toString();
    }

    public static boolean matches(KnowledgeItem item){
        if(item==null||item.metadataJson==null||item.metadataJson.trim().isEmpty())return false;
        try{return MARKER.equals(new JSONObject(item.metadataJson).optString("synthetic_asr_fixture",""));}catch(Exception ignored){return false;}
    }

    private static void writeHeader(RandomAccessFile f,long data)throws IOException{
        int channels=1,bits=16;long byteRate=RATE*channels*bits/8;f.writeBytes("RIFF");le32(f,36+data);f.writeBytes("WAVEfmt ");le32(f,16);le16(f,1);le16(f,channels);le32(f,RATE);le32(f,byteRate);le16(f,channels*bits/8);le16(f,bits);f.writeBytes("data");le32(f,data);
    }
    private static void le16(RandomAccessFile f,long v)throws IOException{f.write((int)(v&255));f.write((int)((v>>8)&255));}
    private static void le32(RandomAccessFile f,long v)throws IOException{f.write((int)(v&255));f.write((int)((v>>8)&255));f.write((int)((v>>16)&255));f.write((int)((v>>24)&255));}
}
