package com.kareem.cortex;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import java.util.regex.*;

/** Shared structured-memory layer. Raw screenshot OCR stays evidence until the user teaches Cortex what matters. */
public final class CoreBrainEngine {
    private CoreBrainEngine(){}

    public static void ensure(VaultDb db){
        SQLiteDatabase s=db.getWritableDatabase();
        s.execSQL("CREATE TABLE IF NOT EXISTS memory_facets(id INTEGER PRIMARY KEY AUTOINCREMENT,item_id INTEGER NOT NULL,facet_type TEXT NOT NULL,facet_value TEXT NOT NULL,normalized TEXT NOT NULL,confidence REAL DEFAULT 0,created_at INTEGER NOT NULL)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_facets_item ON memory_facets(item_id)");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_facets_norm ON memory_facets(facet_type,normalized)");
        s.execSQL("CREATE TABLE IF NOT EXISTS context_packs(id INTEGER PRIMARY KEY AUTOINCREMENT,pack_type TEXT NOT NULL,label TEXT NOT NULL,normalized TEXT NOT NULL UNIQUE,updated_at INTEGER NOT NULL)");
        s.execSQL("CREATE TABLE IF NOT EXISTS context_pack_items(pack_id INTEGER NOT NULL,item_id INTEGER NOT NULL,confidence REAL DEFAULT 0,created_at INTEGER NOT NULL,UNIQUE(pack_id,item_id))");
        s.execSQL("CREATE INDEX IF NOT EXISTS idx_pack_items_item ON context_pack_items(item_id)");
    }

    public static void backfill(VaultDb db,int limit){
        ensure(db);int n=0;
        for(KnowledgeItem k:db.lexicalSearch("",Math.max(1,limit))){
            if(!"analyzed".equals(k.status))continue;
            Cursor c=db.getReadableDatabase().rawQuery("SELECT 1 FROM memory_facets WHERE item_id=? LIMIT 1",new String[]{String.valueOf(k.id)});
            boolean exists=c.moveToFirst();c.close();
            if(!exists){afterAnalysis(db,k.id);n++;if(n>=limit)break;}
        }
    }

    public static void afterAnalysis(VaultDb db,long itemId){
        ensure(db);KnowledgeItem k=db.getById(itemId);if(k==null)return;
        SQLiteDatabase s=db.getWritableDatabase();long now=System.currentTimeMillis();boolean folderShot=isFolderScreenshot(k);boolean taught=folderShot&&hasUserPriority(s,itemId);
        // Preserve explicit user teaching. Everything else is derived and may be rebuilt.
        s.delete("memory_facets",folderShot?"item_id=? AND facet_type<>'USER_PRIORITY'":"item_id=?",new String[]{String.valueOf(itemId)});
        s.delete("context_pack_items","item_id=?",new String[]{String.valueOf(itemId)});
        s.delete("relations","from_item_id=? AND relation IN ('related','same_person','same_project','continuation')",new String[]{String.valueOf(itemId)});

        LinkedHashMap<String,Facet> facets=new LinkedHashMap<>();
        if(!folderShot||taught){
            Cursor e=s.query("entities",new String[]{"kind","value","confidence"},"item_id=?",new String[]{String.valueOf(itemId)},null,null,null);
            while(e.moveToNext()){
                String kind=e.getString(0),value=e.getString(1);double conf=e.getDouble(2);
                if(!folderShot||(conf>=.92&&safeEntity(value)))add(facets,kind,value,conf);
            }e.close();
        }

        String text=allText(k);
        if(!folderShot){
            scanPeople(facets,text);scanProjects(facets,text);scanTemporal(facets,text);scanState(facets,text);
            for(String tag:nz(k.tags).split(",")){String x=tag.trim();if(x.length()>2&&x.length()<60)add(facets,"TOPIC",x,.62);}
        }else if(taught){
            // A taught screenshot may contribute its stable content class, but never raw OCR regex guesses.
            String cat=nz(k.category).trim();if(cat.length()>2&&cat.length()<60)add(facets,"TOPIC",cat,.86);
        }

        for(Facet f:facets.values()){
            ContentValues v=new ContentValues();v.put("item_id",itemId);v.put("facet_type",f.type);v.put("facet_value",f.value);v.put("normalized",f.normalized);v.put("confidence",f.confidence);v.put("created_at",now);s.insert("memory_facets",null,v);
            if("PERSON".equals(f.type)||"PROJECT".equals(f.type)||"TOPIC".equals(f.type))attachPack(s,itemId,f,now);
        }

        // Screenshot evidence remains searchable/embeddable, but does not auto-create graph relations from OCR noise.
        if(folderShot)return;
        try{
            ArrayList<SemanticHit> hits=SemanticIndex.related(db,k,8);
            for(SemanticHit h:hits){if(h.item.id==itemId||h.score<0.16)continue;String relation="related";double conf=Math.min(.98,Math.max(.2,h.score));
                if(sharedFacet(s,itemId,h.item.id,"PROJECT")){relation="same_project";conf=Math.max(conf,.84);}else if(sharedFacet(s,itemId,h.item.id,"PERSON")){relation="same_person";conf=Math.max(conf,.82);}else if(isContinuation(text,allText(h.item))){relation="continuation";conf=Math.max(conf,.72);}insertRelationIfMissing(s,itemId,h.item.id,relation,conf,now);
            }
        }catch(Exception ignored){}
    }

    public static ArrayList<String> facets(VaultDb db,long itemId,String type){
        ensure(db);ArrayList<String> out=new ArrayList<>();String where="item_id=?";ArrayList<String> args=new ArrayList<>();args.add(String.valueOf(itemId));if(type!=null&&!type.isEmpty()){where+=" AND facet_type=?";args.add(type);}Cursor c=db.getReadableDatabase().query("memory_facets",new String[]{"facet_type","facet_value"},where,args.toArray(new String[0]),null,null,"confidence DESC,id ASC");while(c.moveToNext())out.add(c.getString(0)+": "+c.getString(1));c.close();return out;
    }

    public static ArrayList<String> packLabels(VaultDb db,String type,int limit){
        ensure(db);ArrayList<String> out=new ArrayList<>();String sel=type==null?null:"pack_type=?";String[] a=type==null?null:new String[]{type};Cursor c=db.getReadableDatabase().query("context_packs",new String[]{"label"},sel,a,null,null,"updated_at DESC",String.valueOf(limit));while(c.moveToNext())out.add(c.getString(0));c.close();return out;
    }

    private static boolean isFolderScreenshot(KnowledgeItem k){return ("SCREENSHOT".equals(k.type)||"IMAGE".equals(k.type))&&"screenshot-folder".equals(k.source);}
    private static boolean hasUserPriority(SQLiteDatabase s,long itemId){Cursor c=s.rawQuery("SELECT 1 FROM memory_facets WHERE item_id=? AND facet_type='USER_PRIORITY' LIMIT 1",new String[]{String.valueOf(itemId)});boolean yes=c.moveToFirst();c.close();return yes;}
    private static boolean safeEntity(String v){if(v==null)return false;String x=v.trim();if(x.length()<2||x.length()>80)return false;int letters=0,bad=0;for(int i=0;i<x.length();i++){char ch=x.charAt(i);if(Character.isLetter(ch))letters++;else if(!Character.isDigit(ch)&&!Character.isWhitespace(ch)&&"._@+-/".indexOf(ch)<0)bad++;}return letters>=2&&bad<=2;}

    private static void attachPack(SQLiteDatabase s,long itemId,Facet f,long now){
        String packType="PERSON".equals(f.type)?"Person":"PROJECT".equals(f.type)?"Project":"Topic";ContentValues p=new ContentValues();p.put("pack_type",packType);p.put("label",f.value);p.put("normalized",packType+"|"+f.normalized);p.put("updated_at",now);s.insertWithOnConflict("context_packs",null,p,SQLiteDatabase.CONFLICT_IGNORE);s.update("context_packs",p,"normalized=?",new String[]{packType+"|"+f.normalized});Cursor c=s.query("context_packs",new String[]{"id"},"normalized=?",new String[]{packType+"|"+f.normalized},null,null,null,"1");long id=c.moveToFirst()?c.getLong(0):0;c.close();if(id>0){ContentValues x=new ContentValues();x.put("pack_id",id);x.put("item_id",itemId);x.put("confidence",f.confidence);x.put("created_at",now);s.insertWithOnConflict("context_pack_items",null,x,SQLiteDatabase.CONFLICT_REPLACE);}}

    private static boolean sharedFacet(SQLiteDatabase s,long a,long b,String type){String sql="SELECT 1 FROM memory_facets x JOIN memory_facets y ON x.facet_type=y.facet_type AND x.normalized=y.normalized WHERE x.item_id=? AND y.item_id=? AND x.facet_type=? LIMIT 1";Cursor c=s.rawQuery(sql,new String[]{String.valueOf(a),String.valueOf(b),type});boolean ok=c.moveToFirst();c.close();return ok;}
    private static void insertRelationIfMissing(SQLiteDatabase s,long from,long to,String rel,double conf,long now){Cursor c=s.rawQuery("SELECT 1 FROM relations WHERE from_item_id=? AND to_item_id=? AND relation=? LIMIT 1",new String[]{String.valueOf(from),String.valueOf(to),rel});boolean yes=c.moveToFirst();c.close();if(yes)return;ContentValues v=new ContentValues();v.put("from_item_id",from);v.put("to_item_id",to);v.put("relation",rel);v.put("confidence",conf);v.put("created_at",now);s.insert("relations",null,v);}

    private static void scanPeople(Map<String,Facet> out,String t){
        scan(out,"PERSON",t,Pattern.compile("(?i)\\b(?:dr|eng|mr|mrs|ms|prof)\\.?\\s+([A-Z][A-Za-z'-]+(?:\\s+[A-Z][A-Za-z'-]+){0,3})"),.88);
        scan(out,"PERSON",t,Pattern.compile("(?:دكتور|د\\.|م\\.|مهندس|أستاذ|استاذ)\\s+([\\p{IsArabic}]{2,}(?:\\s+[\\p{IsArabic}]{2,}){0,3})"),.88);
        scan(out,"PERSON",t,Pattern.compile("(?:أكلم|اكلم|كلم|ابعت(?:له|لها)?|أبعت(?:له|لها)?|اتابع مع|أتابع مع)\\s+([\\p{IsArabic}]{2,18})"),.72);
    }
    private static void scanProjects(Map<String,Facet> out,String t){
        scan(out,"PROJECT",t,Pattern.compile("(?i)\\b(?:project|job|site|proposal)\\s*[:#-]?\\s*([A-Za-z0-9][A-Za-z0-9 _-]{2,48})"),.76);
        scan(out,"PROJECT",t,Pattern.compile("(?:مشروع|موقع|بروجكت)\\s*[:#-]?\\s*([\\p{IsArabic}A-Za-z0-9][\\p{IsArabic}A-Za-z0-9 _-]{2,48})"),.78);
    }
    private static void scanTemporal(Map<String,Facet> out,String t){
        Matcher m=Pattern.compile("(?i)\\b(today|tomorrow|tonight|next week|next month|sunday|monday|tuesday|wednesday|thursday|friday|saturday)\\b|(?:النهاردة|بكرة|بكره|الليلة|الأسبوع الجاي|الاسبوع الجاي|الشهر الجاي|الأحد|الاتنين|الثلاثاء|الأربعاء|الخميس|الجمعة|السبت)").matcher(t);while(m.find())add(out,"DATE",m.group(),.82);
        Matcher tm=Pattern.compile("(?i)(?:الساعة\\s*)?(?:[01]?\\d|2[0-3])(?::[0-5]\\d)?\\s*(?:am|pm|ص|م)?").matcher(t);while(tm.find()){String x=tm.group().trim();if(x.length()>1)add(out,"TIME",x,.68);}
    }
    private static void scanState(Map<String,Facet> out,String t){String n=norm(t);if(has(n,"waiting for","waiting on","awaiting","مستني","مستنى","منتظر","في انتظار","لما يرد"))add(out,"STATE","Waiting",.9);if(has(n,"decided","agreed","approved","قررنا","اتفقنا","اعتمدنا"))add(out,"STATE","Decision",.84);if(has(n,"follow up","follow-up","هتابع","متابعة","أتابع","اتابع"))add(out,"STATE","Follow-up",.9);}

    private static void scan(Map<String,Facet> out,String type,String text,Pattern p,double conf){Matcher m=p.matcher(text);while(m.find()){String x=m.group(1);if(x!=null)add(out,type,x,conf);}}
    private static void add(Map<String,Facet> out,String type,String value,double conf){if(value==null)return;String v=value.replaceAll("[.,;:!?؟]+$","").replaceAll("\\s+"," ").trim();if(v.length()<2||v.length()>100)return;String n=norm(v);String key=type+"|"+n;Facet old=out.get(key);if(old==null||conf>old.confidence)out.put(key,new Facet(type,v,n,conf));}
    private static boolean isContinuation(String a,String b){String x=norm(a),y=norm(b);return has(x,"follow up","متابعة","نرجع","رجعنا","update","تحديث")&&wordOverlap(x,y)>=2;}
    private static int wordOverlap(String a,String b){Set<String>x=new HashSet<>(Arrays.asList(a.split("[^\\p{L}\\p{Nd}]+"))),y=new HashSet<>(Arrays.asList(b.split("[^\\p{L}\\p{Nd}]+")));int n=0;for(String w:x)if(w.length()>3&&y.contains(w))n++;return n;}
    private static boolean has(String t,String... xs){for(String x:xs)if(t.contains(norm(x)))return true;return false;}
    private static String allText(KnowledgeItem k){return nz(k.title)+". "+nz(k.summary)+". "+nz(k.extractedText)+". "+nz(k.rawText)+". "+nz(k.tags);}
    private static String norm(String s){return LocalSemanticEmbedder.norm(nz(s));}
    private static String nz(String s){return s==null?"":s;}
    private static class Facet{final String type,value,normalized;final double confidence;Facet(String t,String v,String n,double c){type=t;value=v;normalized=n;confidence=c;}}
}
