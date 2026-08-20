package com.kareem.cortex;

import android.database.Cursor;
import java.net.URI;
import java.util.*;

public final class ContextEngine {
    private ContextEngine() {}

    private static final Set<String> GENERIC_TAGS = new HashSet<>(Arrays.asList(
            "notes","screenshots","images","actions","examples","example","input","result",
            "ai prompts","links","research","code","logs","text","manual","data"));

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "this","that","with","from","have","will","your","about","into","when","where","what","which","then","than","them","they","there","here","just","like","more","some","very","only","also","using","used","make","made","save","saved","image","photo","prompt","result","example","android","cortex",
            "على","الى","إلى","فيه","فيها","اللي","ده","دي","دول","كان","كانت","يكون","تكون","عشان","عاوز","عايز","ممكن","كمان","بعد","قبل","منها","منه","عليه","عليها","معاه","معاها","مش","بس","كل","أنا","انا"));

    public static ArrayList<ContextPack> build(VaultDb db) {
        ArrayList<KnowledgeItem> items = db.search("");
        if (items.size() < 2) return new ArrayList<>();
        if (items.size() > 350) items = new ArrayList<>(items.subList(0, 350));

        int n = items.size();
        SignalSet[] sig = new SignalSet[n];
        HashMap<Long,Integer> index = new HashMap<>();
        for (int i=0;i<n;i++) {
            index.put(items.get(i).id,i);
            sig[i] = signals(db, items.get(i));
        }
        HashSet<String> explicit = explicitRelations(db);
        UnionFind uf = new UnionFind(n);
        for (int i=0;i<n;i++) {
            for (int j=i+1;j<n;j++) {
                boolean linked = explicit.contains(pair(items.get(i).id, items.get(j).id));
                int score = linked ? 7 : 0;
                score += Math.min(8, intersectionCount(sig[i].entities,sig[j].entities) * 4);
                score += Math.min(6, intersectionCount(sig[i].tags,sig[j].tags) * 3);
                if (!sig[i].category.isEmpty() && sig[i].category.equals(sig[j].category)) score += 1;
                score += Math.min(3, intersectionCount(sig[i].keywords,sig[j].keywords));
                if (score >= 5) uf.union(i,j);
            }
        }

        LinkedHashMap<Integer,ArrayList<Integer>> comps = new LinkedHashMap<>();
        for (int i=0;i<n;i++) comps.computeIfAbsent(uf.find(i),k->new ArrayList<>()).add(i);

        ArrayList<ContextPack> out = new ArrayList<>();
        for (ArrayList<Integer> group : comps.values()) {
            if (group.size() < 2) continue;
            String best = bestSignal(group,sig);
            ArrayList<KnowledgeItem> members = new ArrayList<>();
            for (int i:group) members.add(items.get(i));
            members.sort((a,b)->Long.compare(b.createdAt,a.createdAt));
            String title = best.isEmpty() ? fallbackTitle(members) : friendly(best);
            String reason = best.isEmpty() ? "Connected by related content" : "Connected by “"+friendly(best)+"”";
            out.add(new ContextPack(title,reason,members));
        }
        out.sort((a,b)->{
            int x=Integer.compare(b.items.size(),a.items.size());
            return x!=0?x:a.title.compareToIgnoreCase(b.title);
        });
        return out;
    }

    private static SignalSet signals(VaultDb db, KnowledgeItem k) {
        SignalSet s = new SignalSet();
        String cat = clean(k.category);
        if (!cat.isEmpty() && !GENERIC_TAGS.contains(cat)) s.category=cat;
        if (k.tags!=null) for (String x:k.tags.split(",")) {
            String t=clean(x); if(t.length()>=3 && !GENERIC_TAGS.contains(t)) s.tags.add(t);
        }
        for (String x:db.entities(k.id)) {
            int p=x.indexOf(':'); if(p<=0) continue;
            String kind=x.substring(0,p).trim().toLowerCase(Locale.ROOT);
            String value=x.substring(p+1).trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty() || kind.contains("date") || kind.contains("phone") || kind.contains("money")) continue;
            if (kind.contains("url")) value=domain(value);
            if (!value.isEmpty()) s.entities.add(value);
        }
        String body=(nz(k.title)+" "+nz(k.summary)+" "+nz(k.rawText));
        if(body.length()>1800)body=body.substring(0,1800);
        for(String w:body.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+")){
            if(w.length()<4 || STOP.contains(w) || isNumeric(w))continue;
            s.keywords.add(w); if(s.keywords.size()>=28)break;
        }
        return s;
    }

    private static HashSet<String> explicitRelations(VaultDb db) {
        HashSet<String> out=new HashSet<>();
        try {
            Cursor c=db.getReadableDatabase().rawQuery("SELECT from_item_id,to_item_id FROM relations",null);
            while(c.moveToNext())out.add(pair(c.getLong(0),c.getLong(1)));
            c.close();
        } catch(Exception ignored) {}
        return out;
    }

    private static String bestSignal(ArrayList<Integer> group, SignalSet[] all) {
        HashMap<String,Integer> score=new HashMap<>();
        for(int i:group){
            SignalSet s=all[i];
            for(String x:s.entities)score.put("entity:"+x,score.getOrDefault("entity:"+x,0)+6);
            for(String x:s.tags)score.put("tag:"+x,score.getOrDefault("tag:"+x,0)+4);
            for(String x:s.keywords)score.put("word:"+x,score.getOrDefault("word:"+x,0)+1);
            if(!s.category.isEmpty())score.put("cat:"+s.category,score.getOrDefault("cat:"+s.category,0)+2);
        }
        String best="";int bestScore=0;
        for(Map.Entry<String,Integer> e:score.entrySet()){
            String raw=e.getKey().substring(e.getKey().indexOf(':')+1);
            int present=0;
            for(int i:group) if(all[i].contains(raw)) present++;
            if(present<2)continue;
            int v=e.getValue()+present*3;
            if(v>bestScore){bestScore=v;best=raw;}
        }
        return best;
    }

    private static String fallbackTitle(ArrayList<KnowledgeItem> items){
        HashMap<String,Integer> cats=new HashMap<>();
        for(KnowledgeItem k:items){String c=clean(k.category);if(!c.isEmpty())cats.put(c,cats.getOrDefault(c,0)+1);}
        String best="Related memories";int n=0;for(Map.Entry<String,Integer>e:cats.entrySet())if(e.getValue()>n){n=e.getValue();best=e.getKey();}
        return friendly(best);
    }

    private static int intersectionCount(Set<String>a,Set<String>b){int n=0;Set<String> small=a.size()<b.size()?a:b,big=a.size()<b.size()?b:a;for(String x:small)if(big.contains(x))n++;return n;}
    private static String pair(long a,long b){return a<b?a+":"+b:b+":"+a;}
    private static String clean(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
    private static String nz(String s){return s==null?"":s;}
    private static boolean isNumeric(String s){for(int i=0;i<s.length();i++)if(!Character.isDigit(s.charAt(i)))return false;return true;}
    private static String domain(String u){try{String x=u.contains("://")?u:"https://"+u;String h=new URI(x).getHost();return h==null?u:h.replaceFirst("^www\\.","");}catch(Exception e){return u;}}
    private static String friendly(String s){
        String x=s.replace('_',' ').replace('-',' ').trim();if(x.length()>42)x=x.substring(0,42)+"…";
        if(x.isEmpty())return "Related memories";
        return Character.toUpperCase(x.charAt(0))+x.substring(1);
    }

    private static final class SignalSet {
        String category="";LinkedHashSet<String> tags=new LinkedHashSet<>(),entities=new LinkedHashSet<>(),keywords=new LinkedHashSet<>();
        boolean contains(String x){return x.equals(category)||tags.contains(x)||entities.contains(x)||keywords.contains(x);}
    }
    private static final class UnionFind {
        int[] p,r;UnionFind(int n){p=new int[n];r=new int[n];for(int i=0;i<n;i++)p[i]=i;}
        int find(int x){return p[x]==x?x:(p[x]=find(p[x]));}
        void union(int a,int b){a=find(a);b=find(b);if(a==b)return;if(r[a]<r[b])p[a]=b;else{p[b]=a;if(r[a]==r[b])r[a]++;}}
    }
}
