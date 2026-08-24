package com.kareem.cortex;

import android.content.Context;
import java.util.*;

/** Filters Cortex evidence before an explicit Combined cloud route. Local-only evidence never leaves device. */
public final class CloudEvidencePolicy {
    private CloudEvidencePolicy(){}

    public static GroundedAnswer filter(Context context,GroundedAnswer g){
        if(g==null)return empty("");ArrayList<SemanticHit> allowed=new ArrayList<>();for(SemanticHit h:g.sources){if(h==null||h.item==null)continue;if(canSend(context,h.item))allowed.add(h);}
        return new GroundedAnswer(g.question,g.answer,g.confidence,allowed,new ArrayList<String>(),new ArrayList<String>());
    }

    public static boolean canSend(Context context,KnowledgeItem k){if(k==null)return false;String group=privacyGroup(k);if("contacts".equals(group)||"calendar".equals(group))return PrivacyPolicy.canUseCloud(context,group);return group.isEmpty()||PrivacyPolicy.canUseCloud(context,group);}

    public static String privacyGroup(KnowledgeItem k){String type=n(k.type).toLowerCase(Locale.ROOT),source=n(k.source).toLowerCase(Locale.ROOT);
        if(type.contains("contact")||source.contains("contact"))return"contacts";
        if(type.contains("calendar")||source.contains("calendar"))return"calendar";
        if(type.contains("notification")||source.contains("notification"))return"notifications";
        if(type.contains("voice")||type.contains("audio")||source.contains("audio")||source.contains("voice"))return"audio";
        if(type.contains("image")||type.contains("screenshot")||source.contains("screenshot")||source.contains("image"))return"images";
        if(type.contains("file")||type.contains("document")||type.contains("pdf")||type.contains("sheet")||source.contains("file")||source.contains("share"))return"files";
        return"";
    }
    private static GroundedAnswer empty(String q){return new GroundedAnswer(q,"",0,new ArrayList<SemanticHit>(),new ArrayList<String>(),new ArrayList<String>());}private static String n(String s){return s==null?"":s;}
}
