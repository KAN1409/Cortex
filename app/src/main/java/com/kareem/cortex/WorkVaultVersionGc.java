package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Conservative cleanup for superseded parse history. Never removes an active version. */
public final class WorkVaultVersionGc {
    public static final String VERSION="work_vault_version_gc_002";
    public static final int DEFAULT_PREVIOUS_TO_KEEP=2;
    private WorkVaultVersionGc(){}

    public static Result collectSource(SQLiteDatabase db,long sourceId){
        return collectSource(db,sourceId,DEFAULT_PREVIOUS_TO_KEEP);
    }

    public static Result collectSource(SQLiteDatabase db,long sourceId,int previousToKeep){
        Result out=new Result();
        if(db==null||sourceId<=0)return out;
        WorkVaultIndexSchema.ensure(db);
        int keep=Math.max(0,previousToKeep);
        Cursor files=db.rawQuery("SELECT id,active_version_id FROM work_files WHERE source_id=? AND active_version_id>0 ORDER BY id",new String[]{String.valueOf(sourceId)});
        try{
            while(files.moveToNext()){
                long fileId=files.getLong(0),active=files.getLong(1);
                List<Long> victims=prunableVersionIds(db,fileId,active,keep);
                if(victims.isEmpty())continue;
                db.beginTransaction();
                try{
                    for(long versionId:victims){
                        out.chunksDeleted+=db.delete("work_chunks","version_id=?",new String[]{String.valueOf(versionId)});
                        out.versionsDeleted+=db.delete("work_file_versions","id=? AND file_id=? AND id<>?",new String[]{String.valueOf(versionId),String.valueOf(fileId),String.valueOf(active)});
                    }
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                out.filesCleaned++;
            }
        }finally{files.close();}
        return out;
    }

    static List<Long> prunableVersionIds(SQLiteDatabase db,long fileId,long activeVersionId,int previousToKeep){
        ArrayList<Long> out=new ArrayList<>();
        int keep=Math.max(0,previousToKeep),previousSeen=0;
        Cursor c=db.rawQuery("SELECT id FROM work_file_versions WHERE file_id=? ORDER BY parsed_at DESC,id DESC",new String[]{String.valueOf(fileId)});
        try{
            while(c.moveToNext()){
                long id=c.getLong(0);
                if(id==activeVersionId)continue;
                if(shouldPrunePreviousOrdinal(previousSeen,keep))out.add(id);
                previousSeen++;
            }
        }finally{c.close();}
        return out;
    }

    static boolean shouldPrunePreviousOrdinal(int zeroBasedPreviousOrdinal,int previousToKeep){
        return zeroBasedPreviousOrdinal>=Math.max(0,previousToKeep);
    }

    public static final class Result{
        public int filesCleaned,versionsDeleted,chunksDeleted;
    }
}
