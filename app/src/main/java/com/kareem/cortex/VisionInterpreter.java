package com.kareem.cortex;

import android.graphics.BitmapFactory;
import java.util.*;
import java.util.regex.*;

public final class VisionInterpreter {
    private static final Pattern ARABIC=Pattern.compile("[\\u0600-\\u06FF]");
    private static final Pattern MONEY=Pattern.compile("(?i)(?:EGP|USD|EUR|AED|SAR|LE|ج\\.?م|جنيه|ريال|دولار|€|\\$|£)\\s*[0-9][0-9,.]*|[0-9][0-9,.]*\\s*(?:EGP|USD|EUR|AED|SAR|LE|ج\\.?م|جنيه|ريال|دولار|€|\\$|£)");
    private static final Pattern RATING=Pattern.compile("(?i)(?:rating|rated|تقييم)\\s*[:\\-]?\\s*([0-5](?:\\.[0-9])?)|([0-5](?:\\.[0-9])?)\\s*/\\s*5");
    private VisionInterpreter(){}

    public static AnalysisResult interpret(KnowledgeItem item,String latin,String arabic,String arabicStatus){
        String merged=mergeText(latin,arabic);AnalysisResult r=LocalAnalyzer.analyze(merged,"text/plain");
        // OCR text is evidence, not user intent. Never turn imperative/UI words in a folder screenshot
        // into a personal task automatically. User teaching or a later high-confidence reasoning layer
        // may explicitly promote evidence into an action.
        r.actions.clear();
        String n=norm(merged);String type=classify(n,merged);double confidence=confidence(type,n,merged);
        r.extractedText=merged;r.visionType=type;r.visionConfidence=confidence;r.engine="cortex_vision_local+mlkit+tesseract_ar";r.version="3";

        int ar=countArabic(merged),lat=countLatin(merged);String lang=ar>0&&lat>0?"Arabic + Latin":(ar>0?"Arabic":(lat>0?"Latin":"No text"));
        r.visionFields.add(new AnalysisResult.VisionField("Content type",friendly(type),confidence));
        r.visionFields.add(new AnalysisResult.VisionField("Detected text",lang,0.95));
        r.visionFields.add(new AnalysisResult.VisionField("Arabic OCR",arabicStatus==null?"":arabicStatus,0.9));
        r.visionFields.add(new AnalysisResult.VisionField("Personal action extraction","Gated • OCR kept as reference until user/AI confirmation",1.0));
        addDimensions(item,r);

        String app=detectApp(n);if(!app.isEmpty())r.visionFields.add(new AnalysisResult.VisionField("App / source",app,0.9));
        String title=firstMeaningful(merged);if(!title.isEmpty())r.visionFields.add(new AnalysisResult.VisionField("Top text",clip(title,90),0.7));
        ArrayList<String> amounts=money(merged);if(!amounts.isEmpty())r.visionFields.add(new AnalysisResult.VisionField("Amounts",joinLimited(amounts,4),0.85));
        String total=findLine(merged,new String[]{"total","grand total","amount due","الإجمالي","اجمالي","المجموع","المطلوب"});if(!total.isEmpty())r.visionFields.add(new AnalysisResult.VisionField("Total line",clip(total,120),0.92));
        String rating=findRating(merged);if(!rating.isEmpty())r.visionFields.add(new AnalysisResult.VisionField("Rating",rating,0.82));
        String setting=findLine(merged,new String[]{"settings","battery","notifications","permissions","display","bluetooth","wi-fi","wifi","الإعدادات","البطارية","الإشعارات","الأذونات","الشاشة","بلوتوث","واي فاي"});if(!setting.isEmpty()&&"SETTINGS_SCREEN".equals(type))r.visionFields.add(new AnalysisResult.VisionField("Settings clue",clip(setting,120),0.84));

        applyPresentation(r,type,merged,app,total,amounts,title,lang);
        return r;
    }

    private static void applyPresentation(AnalysisResult r,String type,String text,String app,String total,ArrayList<String> amounts,String title,String lang){
        switch(type){
            case "CHAT_SCREEN":r.category="Chats";r.tags=tags(r.tags,"vision,chat,screenshot");r.title=(app.isEmpty()?"Chat":app+" chat")+": "+safeTitle(title);r.summary="Chat screenshot"+(app.isEmpty()?"":" from "+app)+" with "+lineCount(text)+" text lines; "+lang+" text captured.";break;
            case "SETTINGS_SCREEN":r.category="Settings";r.tags=tags(r.tags,"vision,settings,screenshot");r.title="Settings: "+safeTitle(title);r.summary="Settings screen captured and indexed; "+lang+" text is searchable and related settings terms were extracted.";break;
            case "RECEIPT":r.category="Receipts";r.tags=tags(r.tags,"vision,receipt,invoice,screenshot");r.title="Receipt: "+safeTitle(title);r.summary="Receipt / invoice screenshot"+(!total.isEmpty()?" — "+clip(total,90):(!amounts.isEmpty()?" — amount "+amounts.get(0):""))+". "+lang+" text captured.";break;
            case "PRODUCT_PAGE":r.category="Products";r.tags=tags(r.tags,"vision,product,shopping,screenshot");r.title="Product: "+safeTitle(title);r.summary="Product page screenshot"+(!amounts.isEmpty()?" with price clue "+amounts.get(0):"")+"; searchable product details were extracted.";break;
            case "AI_CONVERSATION":r.category="AI Captures";r.tags=tags(r.tags,"vision,ai,prompt,result,screenshot");r.title=(app.isEmpty()?"AI capture":app+" capture")+": "+safeTitle(title);r.summary="AI conversation/result screenshot"+(app.isEmpty()?"":" from "+app)+"; prompt/result text is now searchable and semantically indexed.";break;
            case "DOCUMENT":r.category="Documents";r.tags=tags(r.tags,"vision,document,screenshot");r.title="Document: "+safeTitle(title);r.summary="Document screenshot with "+lineCount(text)+" text lines; text, entities and document clues were extracted.";break;
            case "WEBPAGE":r.category="Web & Research";r.tags=tags(r.tags,"vision,web,research,screenshot");r.title="Web: "+safeTitle(title);r.summary="Web/research screenshot captured; visible text is searchable and semantically indexed.";break;
            default:r.category="Screenshots & Images";r.tags=tags(r.tags,"vision,screenshot,image");r.title="Screenshot: "+safeTitle(title);r.summary=text.trim().isEmpty()?"Image stored. No readable text was detected.":"Screenshot understood locally; "+lang+" text and visual context clues were indexed.";
        }
    }

    private static String classify(String n,String raw){LinkedHashMap<String,Integer>s=new LinkedHashMap<>();s.put("CHAT_SCREEN",score(n,new String[]{"whatsapp","telegram","messenger","message","typing","online","last seen","رسالة","رسائل","متصل","يكتب","واتساب","تليجرام"}));s.put("SETTINGS_SCREEN",score(n,new String[]{"settings","permission","permissions","notification","notifications","battery","display","bluetooth","wifi","wi-fi","enabled","disabled","الإعدادات","إعدادات","الأذونات","الإشعارات","البطارية","الشاشة","بلوتوث","تفعيل","تعطيل"}));s.put("RECEIPT",score(n,new String[]{"receipt","invoice","subtotal","grand total","amount due","tax","vat","cash","card","فاتورة","إيصال","الإجمالي","اجمالي","ضريبة","نقدي","بطاقة"})+(money(raw).size()>=2?4:0));s.put("PRODUCT_PAGE",score(n,new String[]{"add to cart","buy now","price","size","colour","color","reviews","rating","in stock","أضف للسلة","اشتر الآن","شراء الآن","السعر","مقاس","اللون","تقييم","متوفر"})+(money(raw).size()>0?2:0));s.put("AI_CONVERSATION",score(n,new String[]{"chatgpt","gemini","claude","copilot","regenerate","prompt","assistant","model","ask chatgpt","جيميني","كلود","برومبت","إعادة إنشاء"}));s.put("WEBPAGE",score(n,new String[]{"http://","https://","www.","chrome","browser","search results","بحث google","نتائج البحث"}));s.put("DOCUMENT",score(n,new String[]{"document","report","subject","page ","reference","dear ","sincerely","تقرير","الموضوع","صفحة","مرجع","السيد","التاريخ"})+(raw.length()>900?3:0));String best="GENERAL_SCREENSHOT";int max=2;for(Map.Entry<String,Integer>e:s.entrySet())if(e.getValue()>max){max=e.getValue();best=e.getKey();}return best;}
    private static double confidence(String type,String n,String raw){if("GENERAL_SCREENSHOT".equals(type))return raw.trim().isEmpty()?0.45:0.62;int x=score(n,new String[]{friendly(type).toLowerCase(Locale.ROOT)});double base=0.78+Math.min(0.17,(raw.length()/6000.0));return Math.min(0.96,base+x*0.01);}
    private static int score(String n,String[] keys){int x=0;for(String k:keys)if(n.contains(norm(k)))x+=2;return x;}
    private static String detectApp(String n){if(n.contains("whatsapp")||n.contains("واتساب"))return"WhatsApp";if(n.contains("telegram")||n.contains("تليجرام"))return"Telegram";if(n.contains("chatgpt"))return"ChatGPT";if(n.contains("gemini")||n.contains("جيميني"))return"Gemini";if(n.contains("claude")||n.contains("كلود"))return"Claude";if(n.contains("copilot"))return"Copilot";if(n.contains("instagram"))return"Instagram";if(n.contains("facebook"))return"Facebook";return"";}
    private static void addDimensions(KnowledgeItem item,AnalysisResult r){try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(item.attachmentPath,o);if(o.outWidth>0&&o.outHeight>0){String shape=o.outHeight>o.outWidth?"portrait":(o.outWidth>o.outHeight?"landscape":"square");r.visionFields.add(new AnalysisResult.VisionField("Image",o.outWidth+"×"+o.outHeight+" • "+shape,0.99));}}catch(Exception ignored){}}
    private static ArrayList<String> money(String text){ArrayList<String>out=new ArrayList<>();Matcher m=MONEY.matcher(text==null?"":text);while(m.find()){String x=m.group().trim();if(!out.contains(x))out.add(x);if(out.size()>=8)break;}return out;}
    private static String findRating(String text){Matcher m=RATING.matcher(text==null?"":text);if(m.find())return m.group().trim();return"";}
    private static String findLine(String text,String[] keys){for(String line:(text==null?"":text).split("\\r?\\n")){String n=norm(line);for(String k:keys)if(n.contains(norm(k)))return line.trim();}return"";}
    private static String firstMeaningful(String text){for(String line:(text==null?"":text).split("\\r?\\n")){String x=line.trim();if(x.length()>=3&&!x.matches("^[0-9:% .|]+$"))return x;}return"";}
    private static String mergeText(String a,String b){LinkedHashSet<String>lines=new LinkedHashSet<>();addLines(lines,a);addLines(lines,b);StringBuilder s=new StringBuilder();for(String x:lines){if(s.length()>0)s.append('\n');s.append(x);}return s.toString().trim();}
    private static void addLines(LinkedHashSet<String>set,String t){if(t==null)return;for(String l:t.split("\\r?\\n")){String x=l.trim();if(x.isEmpty())continue;String key=norm(x);boolean seen=false;for(String old:set)if(norm(old).equals(key)){seen=true;break;}if(!seen)set.add(x);}}
    private static int countArabic(String s){int n=0;Matcher m=ARABIC.matcher(s==null?"":s);while(m.find())n++;return n;}
    private static int countLatin(String s){int n=0;for(char c:(s==null?"":s).toCharArray())if((c>='A'&&c<='Z')||(c>='a'&&c<='z'))n++;return n;}
    private static int lineCount(String s){String x=s==null?"":s.trim();return x.isEmpty()?0:x.split("\\r?\\n").length;}
    private static String norm(String s){return LocalSemanticEmbedder.norm(s==null?"":s);}
    private static String tags(String old,String add){LinkedHashSet<String>set=new LinkedHashSet<>();for(String s:(old==null?"":old).split(","))if(!s.trim().isEmpty())set.add(s.trim());for(String s:add.split(","))if(!s.trim().isEmpty())set.add(s.trim());return String.join(",",set);}
    private static String safeTitle(String s){String x=s==null?"":s.trim();return x.isEmpty()?"Captured image":clip(x,62);}
    private static String friendly(String s){return s.toLowerCase(Locale.ROOT).replace('_',' ');}
    private static String clip(String s,int n){return s==null?"":(s.length()<=n?s:s.substring(0,n)+"…");}
    private static String joinLimited(ArrayList<String>a,int n){StringBuilder s=new StringBuilder();for(int i=0;i<a.size()&&i<n;i++){if(i>0)s.append(" • ");s.append(a.get(i));}return s.toString();}
}
