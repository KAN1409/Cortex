package com.kareem.cortex;

import android.util.Patterns;
import java.util.LinkedHashSet;

public class AutoClassifier {
    public static String category(String text,String mime){
        String t=text==null?"":text.toLowerCase();
        if(mime!=null&&mime.startsWith("image/")) return "Screenshots & Images";
        if(t.contains("prompt")||t.contains("negative prompt")||t.contains("system prompt"))return "AI Prompts";
        if(t.contains("select ")||t.contains("function ")||t.contains(" class ")||t.contains("#!/")||t.contains("exception")||t.contains("stacktrace"))return "Code & Logs";
        if(t.contains("todo")||t.contains("remind")||t.contains("follow up")||t.contains("deadline")||t.contains("لازم")||t.contains("فكرني"))return "Actions";
        if(Patterns.WEB_URL.matcher(t).find())return "Links & Research";
        return "Notes";
    }
    public static String title(String text,String mime){
        if(mime!=null&&mime.startsWith("image/"))return "Screenshot / Image";
        if(text==null||text.trim().isEmpty())return "Untitled item";
        String one=text.trim().replace('\n',' ').replaceAll("\\s+"," ");return one.length()>72?one.substring(0,72)+"…":one;
    }
    public static String tags(String text,String category){
        String t=text==null?"":text.toLowerCase();LinkedHashSet<String> set=new LinkedHashSet<>();
        for(String x:category.toLowerCase().replace(" & ",",").split(","))if(!x.trim().isEmpty())set.add(x.trim());
        if(t.contains("chatgpt")||t.contains("gpt"))set.add("chatgpt");
        if(t.contains("prompt"))set.add("prompt");
        if(t.contains("photo")||t.contains("image")||t.contains("lighting"))set.add("photo");
        if(t.contains("android")||t.contains("termux"))set.add("android");
        if(t.contains("excel")||t.contains("csv")||t.contains("dataset"))set.add("data");
        return String.join(",",set);
    }
}
