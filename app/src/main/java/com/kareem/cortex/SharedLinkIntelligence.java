package com.kareem.cortex;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * Turns a shared URL into useful Cortex knowledge instead of storing the URL as the content.
 * Best-effort and transport-safe: the original URL is always preserved as provenance.
 */
public final class SharedLinkIntelligence {
    private SharedLinkIntelligence(){}
    private static final Pattern URL=Pattern.compile("https?://[^\\s]+",Pattern.CASE_INSENSITIVE);

    public static String firstUrl(String text){if(text==null)return"";Matcher m=URL.matcher(text);return m.find()?trimUrl(m.group()):"";}
    public static boolean containsUrl(String text){return !firstUrl(text).isEmpty();}

    public static void enrichAsync(Context ctx,VaultDb db,long itemId,String sharedText){
        final String url=firstUrl(sharedText);if(itemId<=0||url.isEmpty())return;
        Context app=ctx.getApplicationContext();new Thread(()->{try{enrich(app,db,itemId,url,sharedText);}catch(Throwable ignored){}},"cortex-link-intel").start();
    }

    static void enrich(Context ctx,VaultDb vault,long itemId,String url,String sharedText)throws Exception{
        Fetch f=fetch(url);SQLiteDatabase db=vault.getWritableDatabase();long now=System.currentTimeMillis();
        JSONObject meta=new JSONObject();meta.put("shared_url",url).put("link_intelligence",true).put("fetched_at",now).put("http_status",f.status).put("final_url",f.finalUrl).put("content_type",f.contentType);
        String source=host(f.finalUrl.isEmpty()?url:f.finalUrl);
        String title=clean(f.title);if(title.isEmpty())title=source.isEmpty()?"Shared link":source;
        String body=clean(f.text);String summary=summarize(body,title,source);String useful=usefulTakeaway(body,title,source);
        if(body.isEmpty()){
            summary="Shared link from "+(source.isEmpty()?"the web":source)+". Cortex could not extract readable page content yet; the URL is preserved for retry.";
            useful="Retry content extraction later or open the source if this link matters.";
        }
        meta.put("useful_takeaway",useful).put("extraction_chars",body.length());
        ContentValues v=new ContentValues();v.put("type","LINK");v.put("title",title);v.put("extracted_text",clip(body,7000));v.put("summary",summary);v.put("category","Links & Research");v.put("tags","link,shared,web,understood");v.put("metadata_json",meta.toString());v.put("status","analyzed");v.put("analysis_error","");v.put("updated_at",now);db.update("knowledge_items",v,"id=?",new String[]{String.valueOf(itemId)});
        try{SemanticIndex.indexItem(vault,itemId);}catch(Throwable ignored){}
    }

    private static Fetch fetch(String input)throws Exception{
        URL u=new URL(input);HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(true);c.setConnectTimeout(9000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","Mozilla/5.0 CortexLink/1.0");c.setRequestProperty("Accept","text/html,application/xhtml+xml,text/plain;q=0.8,*/*;q=0.5");int status=c.getResponseCode();String ct=n(c.getContentType());InputStream raw=status>=200&&status<400?c.getInputStream():c.getErrorStream();String html=raw==null?"":read(raw,512000);String finalUrl=c.getURL()==null?input:c.getURL().toString();c.disconnect();
        String title=match(html,"(?is)<title[^>]*>(.*?)</title>");if(title.isEmpty())title=meta(html,"og:title");
        String text=htmlToText(html);return new Fetch(status,ct,finalUrl,decode(title),text);
    }
    private static String meta(String h,String prop){Matcher m=Pattern.compile("(?is)<meta[^>]+(?:property|name)=[\"']"+Pattern.quote(prop)+"[\"'][^>]+content=[\"'](.*?)[\"'][^>]*>").matcher(h);return m.find()?m.group(1):"";}
    private static String htmlToText(String h){if(h==null)return"";String x=h.replaceAll("(?is)<script.*?</script>"," ").replaceAll("(?is)<style.*?</style>"," ").replaceAll("(?is)<noscript.*?</noscript>"," ").replaceAll("(?is)<svg.*?</svg>"," ").replaceAll("(?is)<br\\s*/?>","\n").replaceAll("(?is)</(?:p|div|li|h[1-6]|article|section)>","\n").replaceAll("(?is)<[^>]+>"," ");x=decode(x).replace('\u00a0',' ').replaceAll("[ \\t\\x0B\\f\\r]+"," ").replaceAll("\\n\\s*\\n+","\n").trim();return x;}
    private static String summarize(String body,String title,String source){List<String> s=sentences(body);StringBuilder b=new StringBuilder();for(String x:s){if(x.length()<35)continue;if(b.length()>0)b.append(' ');b.append(x);if(b.length()>520)break;}String core=clip(b.toString(),560);if(core.isEmpty())core=clip(body,560);return (source.isEmpty()?"":source+" — ")+(!title.isEmpty()?title+". ":"")+core;}
    private static String usefulTakeaway(String body,String title,String source){List<String> s=sentences(body);for(String x:s){String z=x.toLowerCase(Locale.ROOT);if(x.length()>=45&&(z.contains("how ")||z.contains("why ")||z.contains("should ")||z.contains("can ")||z.contains("important")||z.contains("best ")||z.contains("guide")||z.contains("طريقة")||z.contains("مهم")||z.contains("نصيحة")||z.contains("يمكن")))return clip(x,360);}return s.isEmpty()?"Review the source if it is relevant to an active situation, person, or project.":clip(s.get(0),360);}
    private static List<String> sentences(String x){ArrayList<String> o=new ArrayList<>();if(x==null)return o;for(String s:x.split("(?<=[.!?؟])\\s+|\\n+")){String z=clean(s);if(z.length()>20)o.add(z);}return o;}
    private static String read(InputStream in,int max)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n,total=0;while((n=in.read(x))>0&&total<max){int w=Math.min(n,max-total);b.write(x,0,w);total+=w;}in.close();return new String(b.toByteArray(),StandardCharsets.UTF_8);}
    private static String decode(String x){return n(x).replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace("&lt;","<").replace("&gt;",">").replaceAll("&#(\\d+);","");}
    private static String match(String s,String re){Matcher m=Pattern.compile(re).matcher(n(s));return m.find()?m.group(1):"";}
    private static String host(String u){try{return new URL(u).getHost().replaceFirst("^www\\.","");}catch(Exception e){return"";}}
    private static String trimUrl(String u){return n(u).replaceAll("[),.;!?]+$","");}
    private static String clean(String s){return n(s).replaceAll("\\s+"," ").trim();}
    private static String clip(String s,int n){String x=n(s);return x.length()<=n?x:x.substring(0,n)+"…";}
    private static String n(String s){return s==null?"":s;}
    static final class Fetch{final int status;final String contentType,finalUrl,title,text;Fetch(int s,String c,String f,String t,String x){status=s;contentType=c;finalUrl=f;title=t;text=x;}}
}
