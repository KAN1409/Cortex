package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Projects are never created from inference alone. This store requires an explicit user action. */
public final class ProjectCandidateStore {
    private ProjectCandidateStore(){}

    public static boolean confirm(VaultDb db,long candidateId){
        if(candidateId<=0)return false;CognitiveStore.ensure(db);SQLiteDatabase sql=db.getWritableDatabase();
        Cursor c=sql.query("derived_items",new String[]{"title","body","state"},"id=? AND kind='PROJECT_CANDIDATE'",new String[]{String.valueOf(candidateId)},null,null,null,"1");
        if(!c.moveToFirst()){c.close();return false;}String title=n(c.getString(0)),body=n(c.getString(1)),state=n(c.getString(2));c.close();if("confirmed".equals(state))return true;
        String name=EntityQualityPolicy.cleanProjectName(projectName(title,body));if(!EntityQualityPolicy.plausibleProject(name))return false;long now=System.currentTimeMillis();String key="project|"+LocalSemanticEmbedder.norm(name);
        ContentValues v=new ContentValues();v.put("kind","PROJECT");v.put("canonical_name",name);v.put("normalized_key",key);v.put("status","active");v.put("metadata_json","{\"created_from\":\"project_candidate\",\"candidate_id\":"+candidateId+",\"explicit_user_action\":true}");v.put("created_at",now);v.put("updated_at",now);
        long entity=sql.insertWithOnConflict("entity_nodes",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(entity<=0){Cursor e=sql.query("entity_nodes",new String[]{"id"},"normalized_key=?",new String[]{key},null,null,null,"1");entity=e.moveToFirst()?e.getLong(0):0;e.close();}if(entity<=0)return false;
        ContentValues done=new ContentValues();done.put("state","confirmed");done.put("title",name);done.put("resolved_at",now);done.put("updated_at",now);sql.update("derived_items",done,"id=? AND kind='PROJECT_CANDIDATE'",new String[]{String.valueOf(candidateId)});
        CognitiveStore.link(db,"derived",candidateId,"entity",entity,"confirmed_as",1.0,"{\"explicit_user_action\":true}");return true;
    }

    private static String projectName(String title,String body){String x=n(title);x=x.replace(" · Project candidate","").replace("Project candidate · ","").replace(" · Review project","").trim();if(x.isEmpty()){x=n(body);int nl=x.indexOf('\n');if(nl>0)x=x.substring(0,nl);if(x.length()>80)x=x.substring(0,80);}return x;}
    private static String n(String s){return s==null?"":s.trim();}
}
