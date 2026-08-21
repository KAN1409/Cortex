package com.kareem.cortex;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
            String body="Tap to open ChatGPT with the original audio + Cortex transcript for independent bilingual review.";
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

        String prompt=buildPrompt(db,k);
        Uri uri=FileProvider.getUriForFile(ctx,ctx.getPackageName()+".debugfiles",audio);
        try{ctx.grantUriPermission(CHATGPT_PACKAGE,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}

        String[] mimeAttempts={"audio/wav","audio/*","application/octet-stream","*/*"};
        Exception last=null;
        for(String mime:mimeAttempts){
            try{
                Intent send=buildDirectIntent(uri,prompt,mime);
                if(canResolve(ctx,send)){
                    ctx.startActivity(send);
                    return;
                }
            }catch(Exception e){last=e;}
        }

        // Last-resort diagnostic fallback: open ChatGPT directly and copy the exact prompt.
        // This intentionally avoids the Android share sheet so the user can tell us whether
        // this ChatGPT build exposes an attachment+text receiver at all.
        ClipboardManager cm=(ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Cortex voice debug prompt",prompt));
        Intent launch=ctx.getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE);
        if(launch!=null){
            ctx.startActivity(launch);
            Toast.makeText(ctx,"ChatGPT opened, but this build did not expose a direct audio+prompt share target. Debug prompt copied to clipboard.",Toast.LENGTH_LONG).show();
            return;
        }
        if(last!=null)throw new IllegalStateException("Could not hand off audio + prompt to ChatGPT",last);
        throw new IllegalStateException("ChatGPT app not available for direct debug handoff");
    }

    private static Intent buildDirectIntent(Uri uri,String prompt,String mime){
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setPackage(CHATGPT_PACKAGE);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM,uri);
        send.putExtra(Intent.EXTRA_TEXT,prompt);
        send.putExtra(Intent.EXTRA_SUBJECT,"Cortex bilingual voice transcription debug");
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData.Item item=new ClipData.Item(prompt,null,null,uri);
        send.setClipData(new ClipData("Cortex original voice + debug prompt",new String[]{"audio/wav","text/plain"},item));
        return send;
    }

    private static boolean canResolve(Context ctx,Intent i){
        try{
            ResolveInfo r=ctx.getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY);
            return r!=null;
        }catch(Exception e){return false;}
    }

    private static String buildPrompt(VaultDb db,KnowledgeItem k){
        StringBuilder p=new StringBuilder();
        p.append("CORTEX_VOICE_DEBUG_REVIEW_V2\n\n");
        p.append("The attached WAV is the ORIGINAL Cortex recording. Perform this as an independent ASR debugging review.\n\n");
        p.append("DO THIS IN THIS ORDER:\n");
        p.append("1) Listen to the attached audio FIRST and create your own verbatim transcript from the audio alone. Preserve Arabic and English exactly as spoken, including code-switching. Keep English words in English letters; do not transliterate them into Arabic.\n");
        p.append("2) Only AFTER finishing your independent transcript, read the Cortex result below.\n");
        p.append("3) Compare them and return:\n");
        p.append("   A. Independent transcript\n   B. Cortex transcript\n   C. Exact omissions, substitutions and language-switch errors\n   D. Approximate timestamps for every meaningful mismatch\n   E. Whether Cortex forced English speech into Arabic phonetic text\n   F. Likely ASR failure mode\n   G. A concrete engineering fix for Cortex mixed Arabic/English transcription\n");
        p.append("4) Be especially strict with Arabic -> English -> Arabic transitions. Treat the attached audio as ground truth, not the Cortex transcript.\n\n");
        p.append("--- CORTEX RESULT (REFERENCE ONLY; NOT GROUND TRUTH) ---\n");
        p.append("Memory ID: ").append(k.id).append('\n');
        p.append("Captured: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(k.createdAt))).append('\n');
        p.append("Status: ").append(k.status).append('\n');
        if(k.analysisError!=null&&!k.analysisError.trim().isEmpty())p.append("Error: ").append(k.analysisError).append('\n');
        String info=AudioStore.info(db,k.id);if(info!=null&&!info.trim().isEmpty())p.append(info).append('\n');
        p.append("Cortex transcript:\n").append(k.extractedText==null||k.extractedText.trim().isEmpty()?"<none>":k.extractedText.trim()).append("\n\n");
        ArrayList<String> seg=AudioStore.segments(db,k.id);if(!seg.isEmpty()){p.append("Cortex timed segments:\n");for(String s:seg)p.append(s).append('\n');}
        p.append("--- END CORTEX RESULT ---\n");
        return p.toString();
    }
}
