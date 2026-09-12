package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Conservative cleanup for superseded parse history and stale derived associations. Never removes an active version. */
public final class WorkVaultVersionGc {
    public static final String VERSION="work_vault_version_gc_003";
    public static final int DEFAULT_PREVIOUS_TO_KEEP=2;
    private WorkVaultVersionGc(){}

    public static Result collectSource(SQLiteDatabase db,long sourceId){
        return collectSource(db,sourceId,DEFAULT_PREVIOUS_TO_KEEP);
    }

    public static Result collectSource(SQLiteDatabase db,long sourceId,int previousToKeep){
        Result out=new Result();
        if(db==null||sourceId<=0)return out;
        WorkVaultIndexSchema.ensure(db);WorkDocumentProfileStore.ensure(db);
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

        // Derived associations are operational projections, not retained parse history. Keep only rows
        // whose endpoints still resolve to the authoritative active version after GC/re-indexing.
        db.beginTransaction();
        try{
            out.profilesDeleted=pruneStaleProfiles(db,sourceId);
            out.linksDeleted=pruneStaleProcurementLinks(db);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return out;
    }

    private static int pruneStaleProfiles(SQLiteDatabase db,long sourceId){
        String where="file_id IN (SELECT id FROM work_files WHERE source_id=?) AND ("+
                "version_id<=0 OR NOT EXISTS ("+
                "SELECT 1 FROM work_files f JOIN work_file_versions v ON v.id=f.active_version_id AND v.file_id=f.id "+
                "WHERE f.id=work_document_profiles.file_id AND f.active_version_id>0 AND work_document_profiles.version_id=f.active_version_id))";
        return db.delete("work_document_profiles",where,new String[]{String.valueOf(sourceId)});
    }

    private static int pruneStaleProcurementLinks(SQLiteDatabase db){
        String where=staleEndpointWhere("from")+" OR "+staleEndpointWhere("to")+
                " OR (source_file_id>0 AND NOT EXISTS (SELECT 1 FROM work_files sf WHERE sf.id=work_procurement_links.source_file_id))";
        return db.delete("work_procurement_links",where,null);
    }

    static String staleEndpointWhere(String side){
        String kind=side+"_kind",id=side+"_id";
        return "("+
                "("+kind+"='REF' AND NOT EXISTS (SELECT 1 FROM work_procurement_refs r JOIN work_files f ON f.id=r.file_id WHERE r.id=work_procurement_links."+id+" AND f.active_version_id>0 AND r.version_id=f.active_version_id)) OR "+
                "("+kind+"='FOLLOWUP' AND NOT EXISTS (SELECT 1 FROM work_followup_records u JOIN work_files f ON f.id=u.file_id WHERE u.id=work_procurement_links."+id+" AND f.active_version_id>0 AND u.version_id=f.active_version_id)) OR "+
                "("+kind+"='PRICE' AND NOT EXISTS (SELECT 1 FROM work_price_records p JOIN work_files f ON f.id=p.file_id WHERE p.id=work_procurement_links."+id+" AND f.active_version_id>0 AND p.version_id=f.active_version_id)) OR "+
                "("+kind+"='FILE' AND NOT EXISTS (SELECT 1 FROM work_files f WHERE f.id=work_procurement_links."+id+" AND f.active_version_id>0))"+
                ")";
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
        public int filesCleaned,versionsDeleted,chunksDeleted,profilesDeleted,linksDeleted;
    }
}
