package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ChatGptDebugReview {
    private static final String CHANNEL="cortex_voice_debug";
    private static final String CHATGPT_PACKAGE="com.openai.chatgpt";
    private ChatGptDebugReview(){}

    public static long latestVoiceId(VaultDb db){
        Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM knowledge_items WHERE type='AUDIO' ORDER BY created_at DESC LIMIT 1",null);
        long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;
    }

    public static void notifyReady(Context ctx,long itemId){
        try{
            ensureChannel(ctx);
            if(Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
            KnowledgeItem k=new VaultDb(ctx).getById(itemId);if(k==null)return;
            Intent i=new Intent(ctx,DebugReviewActivity.class);i.putExtra("item_id",itemId);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi=PendingIntent.getActivity(ctx,(int)(itemId%100000),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            String body="Tap to send the original audio + Cortex transcript to ChatGPT for an independent bilingual review.";
            NotificationCompat.Builder b=new NotificationCompat.Builder(ctx,CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("Cortex voice debug ready")
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH);
            ((NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(720000+(int)(itemId%10000),b.build());
        }catch(Exception ignored){}
    }

    private static void ensureChannel(Context ctx){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm.getNotificationChannel(CHANNEL)==null){NotificationChannel ch=new NotificationChannel(CHANNEL,"Cortex Voice Debug",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("Temporary notifications for comparing Cortex voice transcription with ChatGPT.");nm.createNotificationChannel(ch);}
    }

    public static void share(Context ctx,VaultDb db,long itemId) throws Exception {
        KnowledgeItem k=db.getById(itemId);if(k==null)throw new IllegalArgumentException("Voice memory not found");
        if(!"AUDIO".equals(k.type))throw new IllegalArgumentException("Selected memory is not audio");
        if(k.attachmentPath==null||k.attachmentPath.isEmpty())throw new IllegalArgumentException("Original audio path is missing");
        File audio=new File(k.attachmentPath);if(!audio.exists())throw new IllegalArgumentException("Original audio file no longer exists");
        Uri uri=FileProvider.getUriForFile(ctx,ctx.getPackageName()+".debugfiles",audio);
        Intent send=new Intent(Intent.ACTION_SEND);send.setType("audio/*");send.putExtra(Intent.EXTRA_STREAM,uri);send.putExtra(Intent.EXTRA_TEXT,buildPrompt(db,k));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("Cortex original voice",uri));
        if(isInstalled(ctx,CHATGPT_PACKAGE)){send.setPackage(CHATGPT_PACKAGE);ctx.startActivity(send);}else ctx.startActivity(Intent.createChooser(send,"Review voice debug in ChatGPT"));
    }

    private static boolean isInstalled(Context ctx,String pkg){try{ctx.getPackageManager().getPackageInfo(pkg,0);return true;}catch(Exception e){return false;}}

    private static String buildPrompt(VaultDb db,KnowledgeItem k){
        StringBuilder p=new StringBuilder();
        p.append("CORTEX_VOICE_DEBUG_REVIEW_V1\n\n");
        p.append("The attached audio is the ORIGINAL recording. This is a debugging review.\n");
        p.append("IMPORTANT ORDER:\n1) Listen to the audio and create your OWN verbatim transcript first. Preserve Arabic and English exactly as spoken, including code-switching. Do not normalize English words into Arabic.\n2) Only after your independent transcript is complete, compare it with the Cortex transcript below.\n3) Return: (A) independent transcript, (B) Cortex transcript, (C) exact omissions/substitutions/language-switch errors, (D) approximate timestamps when possible, (E) likely ASR failure mode, (F) concrete fix recommendation for Cortex.\n4) Be especially strict about Arabic -> English -> Arabic switches.\n\n");
        p.append("--- CORTEX RESULT (DO NOT USE AS THE SOURCE TRANSCRIPT) ---\n");
        p.append("Memory ID: ").append(k.id).append('\n');
        p.append("Captured: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(k.createdAt))).append('\n');
        p.append("Status: ").append(k.status).append('\n');
        if(k.analysisError!=null&&!k.analysisError.trim().isEmpty())p.append("Error: ").append(k.analysisError).append('\n');
        p.append(AudioStore.info(db,k.id)).append('\n');
        p.append("Cortex transcript:\n").append(k.extractedText==null||k.extractedText.trim().isEmpty()?"<none>":k.extractedText.trim()).append("\n\n");
        ArrayList<String> seg=AudioStore.segments(db,k.id);if(!seg.isEmpty()){p.append("Cortex timed segments:\n");for(String s:seg)p.append(s).append('\n');}
        p.append("--- END CORTEX RESULT ---\n");
        return p.toString();
    }
}
