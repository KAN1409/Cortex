package com.kareem.cortex;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
import java.util.Locale;

/** Fast lexical retrieval over Work Vault chunks. FTS5 is optional; callers retain a LIKE fallback. */
public final class WorkVaultFtsSearch {
    public static final String VERSION="work_vault_fts_search_001";
    private static volatile Boolean supported;
    private WorkVaultFtsSearch(){}

    /** Returns hit count, or -1 when FTS5 is unavailable/failed and caller should use fallback retrieval. */
    public static int append(SQLiteDatabase db,String query,List<WorkVaultSearch.Hit> out,int max){
        if(db==null||out==null||max<=0)return -1;
        String match=matchQuery(query);if(match.isEmpty())return 0;
        if(!ensure(db))return -1;
        int added=0;Cursor c=null;
        try{
            c=db.rawQuery(
                    "SELECT c.file_id,f.display_name,f.document_uri,c.chunk_text,c.sheet_name,c.page_number,c.slide_number,c.row_number,bm25(work_chunks_fts) "+
                    "FROM work_chunks_fts JOIN work_chunks c ON c.id=work_chunks_fts.rowid "+
                    "JOIN work_files f ON f.id=c.file_id "+
                    "WHERE work_chunks_fts MATCH ? AND f.active_version_id>0 AND c.version_id=f.active_version_id "+
                    "ORDER BY bm25(work_chunks_fts) LIMIT ?",
                    new String[]{match,String.valueOf(Math.max(1,max))});
            while(c.moveToNext()){
                WorkVaultSearch.Hit h=new WorkVaultSearch.Hit();h.kind="TEXT";h.fileId=c.getLong(0);h.fileName=s(c,1);h.documentUri=s(c,2);h.snippet=clip(s(c,3),900);h.sheet=s(c,4);h.page=c.getInt(5);h.slide=c.getInt(6);h.row=c.getInt(7);
                double rank=c.isNull(8)?0:c.getDouble(8);h.score=Math.min(1.45,1.05+Math.min(.40,Math.abs(rank)*.08));out.add(h);added++;
            }
            return added;
        }catch(Throwable ignored){return -1;}finally{if(c!=null)c.close();}
    }

    private static boolean ensure(SQLiteDatabase db){
        Boolean known=supported;if(Boolean.FALSE.equals(known))return false;
        try{
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS work_chunks_fts USING fts5(chunk_id UNINDEXED,file_id UNINDEXED,version_id UNINDEXED,file_name,body,tokenize='unicode61')");
            db.execSQL("CREATE TABLE IF NOT EXISTS work_fts_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS trg_work_chunks_fts_ai AFTER INSERT ON work_chunks BEGIN "+
                    "INSERT INTO work_chunks_fts(rowid,chunk_id,file_id,version_id,file_name,body) VALUES(new.id,new.id,new.file_id,new.version_id,COALESCE((SELECT display_name FROM work_files WHERE id=new.file_id),''),new.chunk_text); END");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS trg_work_chunks_fts_ad AFTER DELETE ON work_chunks BEGIN DELETE FROM work_chunks_fts WHERE rowid=old.id; END");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS trg_work_chunks_fts_au AFTER UPDATE OF chunk_text,version_id,file_id ON work_chunks BEGIN "+
                    "DELETE FROM work_chunks_fts WHERE rowid=old.id; "+
                    "INSERT INTO work_chunks_fts(rowid,chunk_id,file_id,version_id,file_name,body) VALUES(new.id,new.id,new.file_id,new.version_id,COALESCE((SELECT display_name FROM work_files WHERE id=new.file_id),''),new.chunk_text); END");
            Cursor c=db.rawQuery("SELECT value FROM work_fts_meta WHERE key='chunks_backfill_v1' LIMIT 1",null);boolean done=c.moveToFirst();c.close();
            if(!done){
                db.beginTransaction();try{
                    db.execSQL("INSERT INTO work_chunks_fts(rowid,chunk_id,file_id,version_id,file_name,body) "+
                            "SELECT c.id,c.id,c.file_id,c.version_id,COALESCE(f.display_name,''),c.chunk_text FROM work_chunks c JOIN work_files f ON f.id=c.file_id "+
                            "WHERE NOT EXISTS(SELECT 1 FROM work_chunks_fts x WHERE x.rowid=c.id)");
                    db.execSQL("INSERT OR REPLACE INTO work_fts_meta(key,value) VALUES('chunks_backfill_v1','done')");db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            }
            supported=Boolean.TRUE;return true;
        }catch(Throwable ignored){supported=Boolean.FALSE;return false;}
    }

    static String matchQuery(String query){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return "";
        String[] parts=q.split("[^\\p{L}\\p{N}_]+");StringBuilder b=new StringBuilder();int n=0;
        for(String p:parts){if(p.length()<2)continue;if(n++>0)b.append(" AND ");b.append('"').append(p.replace("\"","\"\"")).append("\"*");if(n>=12)break;}
        return b.toString();
    }

    private static String s(Cursor c,int i){return c.isNull(i)?"":c.getString(i);}
    private static String clip(String s,int n){String x=s==null?"":s.trim();return x.length()<=n?x:x.substring(0,n)+"…";}
}
