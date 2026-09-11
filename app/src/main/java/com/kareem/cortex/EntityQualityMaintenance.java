package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Reversible graph hygiene: inferred labels are cleaned/merged; source evidence is never deleted. */
public final class EntityQualityMaintenance {
    private EntityQualityMaintenance(){}

    private static final class Node{
        final long id;final String kind,name;
        Node(long id,String kind,String name){this.id=id;this.kind=kind;this.name=name;}
    }

    public static int run(VaultDb db){
        if(db==null)return 0;
        SQLiteDatabase s=db.getWritableDatabase();
        CognitiveSchema.ensure(s);KnowledgeV2Schema.ensure(s);
        ArrayList<Node> nodes=new ArrayList<>();
        Cursor c=s.rawQuery("SELECT id,kind,canonical_name FROM entity_nodes WHERE status='active'",null);
        while(c.moveToNext())nodes.add(new Node(c.getLong(0),n(c.getString(1)).toUpperCase(Locale.ROOT),n(c.getString(2))));
        c.close();

        int changed=0;long now=System.currentTimeMillis();
        for(Node node:nodes){
            boolean identity=isIdentity(node.kind);if(!identity)continue;
            String clean=EntityQualityPolicy.cleanEntityValue(node.kind,node.name);
            if(!EntityQualityPolicy.plausibleEntity(node.kind,clean)){
                changed+=setStatus(s,node.id,"filtered",now,"quality_policy_rejected");
                continue;
            }
            if(clean.equals(node.name))continue;

            long target=findCanonical(s,node.kind,clean,node.id);
            if(target>0){
                mergeInto(s,node.id,target,now);
                changed++;
            }else{
                ContentValues v=new ContentValues();v.put("canonical_name",clean);v.put("updated_at",now);
                changed+=s.update("entity_nodes",v,"id=?",new String[]{String.valueOf(node.id)});
                addAlias(s,node.id,node.name,"quality_original",.75,now);
                addAlias(s,node.id,clean,"quality_cleanup",.96,now);
            }
        }
        return changed;
    }

    private static void mergeInto(SQLiteDatabase s,long oldId,long targetId,long now){
        if(oldId<=0||targetId<=0||oldId==targetId)return;
        s.beginTransaction();
        try{
            s.execSQL("INSERT OR IGNORE INTO entity_aliases(entity_id,source,alias,normalized_alias,confidence,metadata_json,created_at) "+
                            "SELECT ?,source,alias,normalized_alias,confidence,metadata_json,created_at FROM entity_aliases WHERE entity_id=?",
                    new Object[]{targetId,oldId});
            s.delete("entity_aliases","entity_id=?",new String[]{String.valueOf(oldId)});
            s.execSQL("UPDATE kv2_entity_mentions SET resolved_entity_id=? WHERE resolved_entity_id=?",new Object[]{targetId,oldId});

            copyEdges(s,"from",oldId,targetId);
            copyEdges(s,"to",oldId,targetId);
            s.delete("kv2_edges","(from_type='entity' AND from_id=?) OR (to_type='entity' AND to_id=?)",new String[]{String.valueOf(oldId),String.valueOf(oldId)});

            copySourceLinks(s,"from",oldId,targetId);
            copySourceLinks(s,"to",oldId,targetId);
            s.delete("source_links","(from_type='entity' AND from_id=?) OR (to_type='entity' AND to_id=?)",new String[]{String.valueOf(oldId),String.valueOf(oldId)});

            ContentValues v=new ContentValues();v.put("status","merged");v.put("updated_at",now);
            try{JSONObject meta=new JSONObject();meta.put("merged_into",targetId);meta.put("reason","entity_quality_cleanup");v.put("metadata_json",meta.toString());}catch(Exception ignored){}
            s.update("entity_nodes",v,"id=?",new String[]{String.valueOf(oldId)});
            s.setTransactionSuccessful();
        }finally{s.endTransaction();}
    }

    private static void copyEdges(SQLiteDatabase s,String side,long oldId,long targetId){
        if("from".equals(side)){
            s.execSQL("INSERT OR IGNORE INTO kv2_edges(from_type,from_id,to_type,to_id,relation,confidence,valid_from,valid_to,metadata_json,created_at) "+
                            "SELECT from_type,?,to_type,to_id,relation,confidence,valid_from,valid_to,metadata_json,created_at FROM kv2_edges WHERE from_type='entity' AND from_id=?",
                    new Object[]{targetId,oldId});
        }else{
            s.execSQL("INSERT OR IGNORE INTO kv2_edges(from_type,from_id,to_type,to_id,relation,confidence,valid_from,valid_to,metadata_json,created_at) "+
                            "SELECT from_type,from_id,to_type,?,relation,confidence,valid_from,valid_to,metadata_json,created_at FROM kv2_edges WHERE to_type='entity' AND to_id=?",
                    new Object[]{targetId,oldId});
        }
    }

    private static void copySourceLinks(SQLiteDatabase s,String side,long oldId,long targetId){
        if("from".equals(side)){
            s.execSQL("INSERT OR IGNORE INTO source_links(from_type,from_id,to_type,to_id,relation,confidence,metadata_json,created_at) "+
                            "SELECT from_type,?,to_type,to_id,relation,confidence,metadata_json,created_at FROM source_links WHERE from_type='entity' AND from_id=?",
                    new Object[]{targetId,oldId});
        }else{
            s.execSQL("INSERT OR IGNORE INTO source_links(from_type,from_id,to_type,to_id,relation,confidence,metadata_json,created_at) "+
                            "SELECT from_type,from_id,to_type,?,relation,confidence,metadata_json,created_at FROM source_links WHERE to_type='entity' AND to_id=?",
                    new Object[]{targetId,oldId});
        }
    }

    private static long findCanonical(SQLiteDatabase s,String kind,String clean,long exclude){
        Cursor c=s.rawQuery("SELECT id FROM entity_nodes WHERE id<>? AND status='active' AND upper(kind)=? AND lower(trim(canonical_name))=lower(trim(?)) ORDER BY id LIMIT 1",
                new String[]{String.valueOf(exclude),kind,clean});
        long id=c.moveToFirst()?c.getLong(0):0;c.close();return id;
    }

    private static void addAlias(SQLiteDatabase s,long id,String alias,String source,double confidence,long now){
        String clean=n(alias);if(clean.isEmpty())return;
        ContentValues v=new ContentValues();v.put("entity_id",id);v.put("source",source);v.put("alias",clean);v.put("normalized_alias",LocalSemanticEmbedder.norm(clean));v.put("confidence",confidence);v.put("metadata_json","{}");v.put("created_at",now);
        s.insertWithOnConflict("entity_aliases",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static int setStatus(SQLiteDatabase s,long id,String status,long now,String reason){
        ContentValues v=new ContentValues();v.put("status",status);v.put("updated_at",now);
        try{JSONObject m=new JSONObject();m.put("quality_reason",reason);v.put("metadata_json",m.toString());}catch(Exception ignored){}
        return s.update("entity_nodes",v,"id=?",new String[]{String.valueOf(id)});
    }
    private static boolean isIdentity(String kind){return "PERSON".equals(kind)||"PROJECT".equals(kind)||"ORGANIZATION".equals(kind)||"ORG".equals(kind)||"PRODUCT".equals(kind)||"PLACE".equals(kind);}
    private static String n(String s){return s==null?"":s.trim();}
}
