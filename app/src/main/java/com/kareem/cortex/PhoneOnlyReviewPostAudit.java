package com.kareem.cortex;

import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict product gates layered on top of the phone-only review so non-empty answers cannot false-green. */
final class PhoneOnlyReviewPostAudit {
    private PhoneOnlyReviewPostAudit(){}

    static JSONObject run(VaultDb vault, File root) throws Exception {
        JSONObject out=new JSONObject().put("schema","CORTEX_PHONE_ONLY_STRICT_AUDIT_V1");
        JSONArray checks=new JSONArray();int pass=0,fail=0;
        JSONObject v2=readJson(new File(root,"V2/report.json"));JSONArray cases=v2.optJSONArray("cases");
        String now=answer(cases,0),work=answer(cases,1),noise=answer(cases,2),life=answer(cases,3);

        boolean nowOk=!empty(now)&&!containsAny(norm(now),"past-dated; verify","tue, 25 aug","tomorrow — past","yesterday —","time not grounded. next: complete it");
        checks.put(check("NOW_CURRENT_VALIDITY",nowOk,nowOk?"No obvious stale/past-dated attention item surfaced":"NOW answer contains stale or ungrounded attention candidates"));if(nowOk)pass++;else fail++;

        String wz=norm(work);boolean workOk=!empty(work)&&!containsAny(wz,"authenticator","package installer","systemui","system ui","screenshot saved","response ready")&&!looksScreenshotList(work);
        checks.put(check("WORK_DOMAIN_PRECISION",workOk,workOk?"Work answer stayed inside work/project evidence":"Work answer leaked generic/system/screenshot evidence"));if(workOk)pass++;else fail++;

        boolean noiseOk=!empty(noise)&&containsAny(norm(noise),"context, not an action","noise","low-value evidence","should not become tasks");
        checks.put(check("NOISE_EXPLICIT_CLASSIFICATION",noiseOk,noiseOk?"Noise/context is explicitly suppressed":"Noise answer does not clearly suppress low-value evidence"));if(noiseOk)pass++;else fail++;

        boolean lifeOk=!empty(life)&&!lifecycleContradiction(life);
        checks.put(check("LIFECYCLE_NO_CONTRADICTION",lifeOk,lifeOk?"No canonical item appears both live and closed":"Same canonical situation appears in live and closed sections"));if(lifeOk)pass++;else fail++;

        JSONObject link=linkAudit(vault);boolean linkOk=link.optBoolean("pass");checks.put(check("LINK_STATE_EXPLICIT",linkOk,link.optString("message")));if(linkOk)pass++;else fail++;
        out.put("pass",pass).put("failure",fail).put("checks",checks).put("link_audit",link).put("strict_pass",fail==0);
        write(new File(root,"STRICT_AUDIT.json"),out.toString(2));return out;
    }

    private static JSONObject linkAudit(VaultDb vault){int recent=0,understood=0,pending=0,urlOnlyAnalyzed=0;Cursor c=vault.getReadableDatabase().rawQuery("SELECT raw_text,summary,status,metadata_json FROM knowledge_items WHERE source='android_share' AND (type='LINK' OR raw_text LIKE 'http%' OR summary LIKE 'http%') ORDER BY updated_at DESC LIMIT 20",null);try{while(c.moveToNext()){recent++;String raw=n(c.getString(0)),sum=n(c.getString(1)),status=n(c.getString(2)),meta=n(c.getString(3));String state="";try{state=new JSONObject(meta.isEmpty()?"{}":meta).optString("link_content_state","");}catch(Throwable ignored){}if("understood".equals(state))understood++;if("pending_content".equals(state)||"fetch_failed".equals(state)||"pending".equals(status))pending++;String url=SharedLinkIntelligence.firstUrl(!raw.isEmpty()?raw:sum);boolean urlOnly=!url.isEmpty()&&(raw.equals(url)||sum.equals(url)||raw.isEmpty());if(urlOnly&&"analyzed".equals(status)&&state.isEmpty())urlOnlyAnalyzed++;}}finally{c.close();}
        boolean ok=urlOnlyAnalyzed==0;String msg=recent==0?"No recent shared links to audit":ok?"Shared links have explicit understood/pending/failed state":"Found URL-only shared links incorrectly marked analyzed without link state";return new JSONObject().put("pass",ok).put("recent",recent).put("understood",understood).put("pending_or_failed",pending).put("url_only_analyzed_without_state",urlOnlyAnalyzed).put("message",msg);
    }

    private static boolean lifecycleContradiction(String text){String[] parts=text.split("(?i)Do not resurface as live:",2);if(parts.length<2)return false;Set<String> live=lines(parts[0]),closed=lines(parts[1]);for(String a:live)for(String b:closed)if(similar(a,b))return true;return false;}
    private static Set<String> lines(String s){LinkedHashSet<String> o=new LinkedHashSet<>();for(String x:s.split("\\n")){String z=canon(x.replaceFirst("^[•*\\-]\\s*",""));if(z.length()>=10)o.add(z);}return o;}
    private static boolean similar(String a,String b){Set<String>x=new HashSet<>(Arrays.asList(a.split(" "))),y=new HashSet<>(Arrays.asList(b.split(" ")));x.remove("");y.remove("");if(x.isEmpty()||y.isEmpty())return false;int n=0;for(String w:x)if(y.contains(w))n++;return n/(double)Math.min(x.size(),y.size())>=.72;}
    private static boolean looksScreenshotList(String s){String z=norm(s);int hits=0;for(String x:new String[]{"screenshot","image","home","medium","authenticator","installer"})if(z.contains(x))hits++;return hits>=2;}
    private static String answer(JSONArray a,int i){if(a==null||i<0||i>=a.length())return"";JSONObject o=a.optJSONObject(i);return o==null?"":o.optString("answer","");}
    private static JSONObject check(String id,boolean ok,String msg){try{return new JSONObject().put("id",id).put("status",ok?"PASS":"FAIL").put("message",msg);}catch(Exception e){return new JSONObject();}}
    private static JSONObject readJson(File f)throws Exception{return new JSONObject(read(f));}
    private static String read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);return new String(b.toByteArray(), StandardCharsets.UTF_8);}}
    private static void write(File f,String s)throws Exception{try(FileWriter w=new FileWriter(f)){w.write(s);}}
    private static boolean containsAny(String z,String...xs){for(String x:xs)if(z.contains(norm(x)))return true;return false;}
    private static String canon(String s){String z=norm(s).replaceAll("[^\\p{L}\\p{Nd}]+"," ").trim();StringBuilder b=new StringBuilder();for(String w:z.split(" ")){if(w.length()<2||STOP.contains(w))continue;if(b.length()>0)b.append(' ');b.append(w);}return b.toString();}
    private static final Set<String> STOP=new HashSet<>(Arrays.asList("the","a","an","to","of","and","or","for","in","on","at","is","are","still","live","resolved","done","closed","next","من","في","على","إلى","الى","و","أو","او"));
    private static String norm(String s){return LocalSemanticEmbedder.norm(n(s));}
    private static boolean empty(String s){return n(s).isEmpty();}
    private static String n(String s){return s==null?"":s.trim();}
}
