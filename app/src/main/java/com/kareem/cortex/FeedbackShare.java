package com.kareem.cortex;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Locale;

public final class FeedbackShare {
    private static final String CHATGPT_PACKAGE="com.openai.chatgpt";
    private FeedbackShare(){}

    public static void send(Context context,KnowledgeItem item,File audio,String transcript,int rating,String feedback){
        if(context==null)return;
        if(audio==null||!audio.exists()){
            Toast.makeText(context,"Original audio file is missing",Toast.LENGTH_LONG).show();
            return;
        }
        String prompt=buildPrompt(item,audio,transcript,rating,feedback);
        try{
            Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".feedback.files",audio);
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.setPackage(CHATGPT_PACKAGE);
            send.putExtra(Intent.EXTRA_SUBJECT,"Cortex transcription feedback");
            send.putExtra(Intent.EXTRA_TEXT,prompt);
            send.putExtra(Intent.EXTRA_STREAM,uri);
            send.setClipData(ClipData.newRawUri("Cortex WAV",uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(!(context instanceof Activity))send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(send);
        }catch(Exception directError){
            try{
                ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Cortex transcription feedback",prompt));
                Intent launch=context.getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE);
                if(launch!=null){
                    if(!(context instanceof Activity))launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                    Toast.makeText(context,"Feedback prompt copied — attach the WAV in ChatGPT if needed",Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(context,"Feedback prompt copied. ChatGPT app was not found.",Toast.LENGTH_LONG).show();
                }
            }catch(Exception fallbackError){
                Toast.makeText(context,"Could not share transcription feedback: "+compact(fallbackError.getMessage()),Toast.LENGTH_LONG).show();
            }
        }
    }

    private static String buildPrompt(KnowledgeItem item,File audio,String transcript,int rating,String feedback){
        String current=clean(transcript);
        if(current.isEmpty())current="[no accepted transcript]";
        String diagnostic=clean(feedback);
        StringBuilder s=new StringBuilder();
        s.append("CORTEX_TRANSCRIPTION_FEEDBACK_V1\n\n");
        s.append("I am attaching the original WAV recorded by Cortex. Diagnose the transcription quality using the audio itself.\n\n");
        s.append("CURRENT CORTEX RESULT:\n").append(current).append("\n\n");
        s.append("AUDIO / ENGINE INFO:\n");
        s.append("Duration: ").append(formatDuration(durationMs(audio))).append("\n");
        s.append("Language: auto\n");
        s.append("Engine: ").append(inferEngine(diagnostic)).append("\n");
        s.append("File bytes: ").append(audio.length()).append("\n");
        if(item!=null)s.append("Status: ").append(clean(item.status)).append("\n");
        if(!diagnostic.isEmpty())s.append("Diagnostics: ").append(diagnostic).append("\n");
        s.append("\nUSER RATING: ").append(rating>0?rating+"/5":"not rated").append("\n");
        s.append("USER FEEDBACK:\n").append(diagnostic.isEmpty()?"No written feedback; infer issues only by comparing the audio and result.":diagnostic).append("\n\n");
        s.append("TASKS FOR CHATGPT / CODEX:\n");
        s.append("1. Transcribe the attached audio verbatim, preserving Egyptian Arabic and English code-switching.\n");
        s.append("2. Compare your transcript with the Cortex result and list omissions, substitutions, language mistakes, and punctuation issues.\n");
        s.append("3. Identify likely technical causes in the ASR pipeline.\n");
        s.append("4. Produce concrete implementation recommendations for the Cortex Android project.\n");
        s.append("5. Do not invent words that cannot be heard; mark uncertain audio as [unclear].\n");
        if(item!=null)s.append("\nCORTEX ITEM ID: ").append(item.id).append("\n");
        return s.toString();
    }

    private static String inferEngine(String diagnostic){
        String x=diagnostic==null?"":diagnostic.toLowerCase(Locale.US);
        if(x.contains("groq"))return "groq";
        if(x.contains("cohere"))return "cohere";
        if(x.contains("android fallback")||x.contains("speech recognition"))return "android_speech/fallback";
        return "unknown";
    }

    private static long durationMs(File audio){
        MediaMetadataRetriever m=new MediaMetadataRetriever();
        try{
            m.setDataSource(audio.getAbsolutePath());
            String d=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d==null?0:Long.parseLong(d);
        }catch(Exception ignored){return 0;}
        finally{try{m.release();}catch(Exception ignored){}}
    }

    private static String formatDuration(long ms){
        if(ms<=0)return "unknown";
        long sec=Math.round(ms/1000.0);
        return String.format(Locale.US,"%02d:%02d",sec/60,sec%60);
    }

    private static String clean(String s){return s==null?"":s.trim();}
    private static String compact(String s){String x=clean(s).replaceAll("\\s+"," ");return x.length()>200?x.substring(0,200)+"…":x;}
}
