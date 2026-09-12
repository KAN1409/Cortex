package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.util.ArrayList;

/** Persistent source registry for Storage Access Framework archive roots. */
public final class WorkVaultSourceStore {
    private WorkVaultSourceStore(){}

    public static long addOrTouch(VaultDb vault, Uri treeUri, String displayName){
        if(vault==null||treeUri==null)return 0;
        SQLiteDatabase db=vault.getWritableDatabase();
        WorkVaultSchema.ensure(db);
        long now=System.currentTimeMillis();
        String uri=treeUri.toString();
        Cursor c=db.rawQuery("SELECT id FROM work_sources WHERE tree_uri=? LIMIT 1",new String[]{uri});
        long id=c.moveToFirst()?c.getLong(0):0;c.close();
        ContentValues v=new ContentValues();
        v.put("display_name",clean(displayName,treeUri.getLastPathSegment()));
        v.put("state","active");
        v.put("last_error","");
        v.put("updated_at",now);
        if(id>0){db.update("work_sources",v,"id=?",new String[]{String.valueOf(id)});return id;}
        v.put("tree_uri",uri);v.put("created_at",now);v.put("last_scan_at",0);
        return db.insertOrThrow("work_sources",null,v);
    }

    public static ArrayList<Source> active(VaultDb vault){
        ArrayList<Source> out=new ArrayList<>();
        SQLiteDatabase db=vault.getReadableDatabase();WorkVaultSchema.ensure(db);
        Cursor c=db.rawQuery("SELECT id,tree_uri,display_name,state,last_scan_at,last_error FROM work_sources WHERE state='active' ORDER BY updated_at DESC",null);
        try{while(c.moveToNext())out.add(new Source(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getString(5)));}
        finally{c.close();}
        return out;
    }

    public static void scanFinished(VaultDb vault,long sourceId,String error){
        ContentValues v=new ContentValues();long now=System.currentTimeMillis();
        v.put("last_scan_at",now);v.put("last_error",error==null?"":error);v.put("updated_at",now);
        vault.getWritableDatabase().update("work_sources",v,"id=?",new String[]{String.valueOf(sourceId)});
    }

    public static final class Source{
        public final long id,lastScanAt;public final String treeUri,displayName,state,lastError;
        Source(long id,String treeUri,String displayName,String state,long lastScanAt,String lastError){this.id=id;this.treeUri=n(treeUri);this.displayName=n(displayName);this.state=n(state);this.lastScanAt=lastScanAt;this.lastError=n(lastError);}
    }
    private static String clean(String a,String b){String x=n(a);if(!x.isEmpty())return x;x=n(b);return x.isEmpty()?"Work archive":x;}
    private static String n(String s){return s==null?"":s.trim();}
}
