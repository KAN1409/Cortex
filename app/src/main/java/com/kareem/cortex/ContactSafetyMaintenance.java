package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Locale;

/** Idempotent cleanup for legacy contact false-actions. Contacts remain searchable context. */
public final class ContactSafetyMaintenance {
    public static final String VERSION="contact_safety_001";
    private ContactSafetyMaintenance(){}

    public static void run(VaultDb db){
        if(db==null)return;
        try{
            FeatureStore.ensure(db);SQLiteDatabase s=db.getWritableDatabase();
            int removed=s.delete("actions","item_id IN (SELECT id FROM knowledge_items WHERE type='CONTACT' AND source='contacts_sync')",null);

            ContentValues v=new ContentValues();v.put("bucket","Person");v.put("reviewed",1);v.put("attention_dismissed",1);v.put("snoozed_until",0);v.put("updated_at",System.currentTimeMillis());
            int quieted=s.update("smart_inbox",v,"manual_bucket=0 AND item_id IN (SELECT id FROM knowledge_items WHERE type='CONTACT' AND source='contacts_sync')",null);

            int duplicateVariants=countCanonicalDuplicateVariants(db);
            JSONObject m=new JSONObject();m.put("removed_false_actions",removed);m.put("quieted_contact_rows",quieted);m.put("canonical_duplicate_variants",duplicateVariants);m.put("policy",VERSION);
            DiagnosticsLog.info(db,"contacts","legacy_contact_cleanup","ok",0,0,0,0,0,0,m);
        }catch(Throwable e){DiagnosticsLog.error(db,"contacts","legacy_contact_cleanup",e,"CONTACT_CLEANUP_FAILED",0,0,0,0,0,null);}
    }

    private static int countCanonicalDuplicateVariants(VaultDb db){
        HashSet<String> seen=new HashSet<>(),dupes=new HashSet<>();
        Cursor c=db.getReadableDatabase().rawQuery("SELECT title,raw_text FROM knowledge_items WHERE type='CONTACT' AND source='contacts_sync'",null);
        while(c.moveToNext()){
            String name=nz(c.getString(0)).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT),raw=nz(c.getString(1));int i=raw.indexOf("Phone:");if(i<0)continue;String phone=raw.substring(i+6).trim().split("\\n",2)[0].trim();String canonical=PhoneNumberNormalizer.canonical(phone);if(canonical.isEmpty())continue;String key=name+"|"+canonical;if(!seen.add(key))dupes.add(key);
        }c.close();return dupes.size();
    }
    private static String nz(String s){return s==null?"":s.trim();}
}
