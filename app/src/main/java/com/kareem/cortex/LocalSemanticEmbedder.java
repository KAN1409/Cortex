package com.kareem.cortex;

import java.text.Normalizer;
import java.util.*;

public final class LocalSemanticEmbedder implements EmbeddingProvider {
    public static final int DIMS=256;
    private static final HashMap<String,String[]> ALIASES=new HashMap<>();
    static {
        alias("photo","صورة","صور","image","picture"); alias("lighting","اضاءة","إضاءة","light");
        alias("prompt","برومبت","prompting"); alias("result","نتيجة","output","answer");
        alias("doctor","دكتور","طبيب"); alias("medicine","دواء","ادوية","أدوية","medication","drug");
        alias("analysis","تحليل","تحاليل","analyze"); alias("test","فحص","اختبار","tests");
        alias("project","مشروع","projects"); alias("email","ايميل","إيميل","mail");
        alias("message","رسالة","رسائل","chat"); alias("call","مكالمة","مكالمات","phone");
        alias("reminder","تذكير","فكرني","remind"); alias("task","مهمة","مهام","todo");
        alias("appointment","ميعاد","موعد","حجز","booking"); alias("meeting","اجتماع","meeting");
        alias("invoice","فاتورة","receipt","ايصال","إيصال"); alias("price","سعر","تكلفة","cost");
        alias("screen","شاشة","screenshot","سكرين","لقطة"); alias("settings","اعدادات","إعدادات","setting");
        alias("search","بحث","دور","find"); alias("memory","ذاكرة","remember","recall");
        alias("voice","صوت","audio","recording","تسجيل"); alias("transcript","تفريغ","transcription");
        alias("summary","ملخص","تلخيص","summarize"); alias("data","بيانات","dataset");
        alias("table","جدول","csv","spreadsheet","excel"); alias("code","كود","script","سكربت");
        alias("android","اندرويد","أندرويد","phone","موبايل"); alias("github","جيتهاب","جيت هاب");
        alias("work","شغل","عمل"); alias("home","بيت","منزل"); alias("travel","سفر","trip");
        alias("restaurant","مطعم","اكل","أكل","food"); alias("product","منتج","منتجات");
    }
    private static void alias(String root,String... xs){String[] all=new String[xs.length+1];all[0]=root;System.arraycopy(xs,0,all,1,xs.length);for(String x:all)ALIASES.put(norm(x),all);}

    @Override public String name(){return "local_semantic";}
    @Override public String version(){return "1";}
    @Override public int dimensions(){return DIMS;}

    @Override public float[] embed(String text){
        float[] v=new float[DIMS];String n=norm(text);if(n.isEmpty())return v;
        LinkedHashSet<String> features=new LinkedHashSet<>();
        for(String token:n.split("[^\\p{L}\\p{Nd}]+")){
            if(token.length()<2)continue;features.add("w:"+token);
            String[] aliases=ALIASES.get(token);if(aliases!=null)for(String a:aliases)features.add("a:"+norm(a));
            if(token.length()>=4){String padded="^"+token+"$";for(int i=0;i+3<=padded.length();i++)features.add("g:"+padded.substring(i,i+3));}
        }
        for(String f:features){int h=f.hashCode();int idx=(h&0x7fffffff)%DIMS;float sign=((h>>>30)&1)==0?1f:-1f;float weight=f.startsWith("a:")?1.35f:(f.startsWith("w:")?1.0f:0.28f);v[idx]+=sign*weight;}
        float sum=0;for(float x:v)sum+=x*x;if(sum>0){float d=(float)Math.sqrt(sum);for(int i=0;i<v.length;i++)v[i]/=d;}return v;
    }

    public static String norm(String s){
        if(s==null)return "";String x=Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFKD).replaceAll("\\p{M}+","");
        x=x.replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي').replace('ؤ','و').replace('ئ','ي').replace('ة','ه');
        return x.replaceAll("\\s+"," ").trim();
    }
}
