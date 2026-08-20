package com.kareem.cortex;

import java.util.*;

public final class TabularAnalyzer {
    private TabularAnalyzer(){}
    public static boolean looksTabular(String text){ return analyzeInternal(text,false)!=null; }

    public static AnalysisResult analyze(String text){
        Table t=analyzeInternal(text,true); if(t==null)return null;
        AnalysisResult r=new AnalysisResult();r.engine="local_table_rules";r.version="1";
        r.title="Dataset: "+t.rows+" rows × "+t.cols+" columns";
        r.category="Data & Analysis";r.tags="data,table,"+(t.delim=='\t'?"tsv":"csv");
        StringBuilder sum=new StringBuilder("Table with ").append(t.rows).append(" data rows and ").append(t.cols).append(" columns.");
        if(!t.headers.isEmpty())sum.append(" Columns: ").append(String.join(", ",t.headers)).append('.');
        int shown=0;
        for(int c=0;c<t.cols&&shown<4;c++){
            Stats s=stats(t,c);if(s.count>=Math.max(2,(int)Math.ceil(t.rows*0.6))){
                String name=c<t.headers.size()?t.headers.get(c):"Column "+(c+1);
                sum.append(" ").append(name).append(": avg ").append(fmt(s.sum/s.count)).append(", min ").append(fmt(s.min)).append(", max ").append(fmt(s.max)).append('.');shown++;
            }
        }
        r.summary=sum.toString();return r;
    }

    private static Table analyzeInternal(String text,boolean keep){
        if(text==null)return null;String[] raw=text.trim().split("\\r?\\n");if(raw.length<3)return null;
        char[] ds={'\t',',',';'};char best=0;int cols=0;
        for(char d:ds){int n=split(raw[0],d).size();if(n>cols){int consistent=0;for(int i=1;i<Math.min(raw.length,8);i++)if(split(raw[i],d).size()==n)consistent++;if(n>=2&&consistent>=Math.min(raw.length-1,4)) {best=d;cols=n;}}}
        if(best==0)return null;Table t=new Table();t.delim=best;t.cols=cols;t.headers=split(raw[0],best);t.data=new ArrayList<>();
        for(int i=1;i<raw.length;i++){List<String> row=split(raw[i],best);if(row.size()==cols)t.data.add(row);}t.rows=t.data.size();if(t.rows<2)return null;return t;
    }
    private static List<String> split(String line,char d){
        ArrayList<String> out=new ArrayList<>();StringBuilder s=new StringBuilder();boolean q=false;
        for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){q=!q;continue;}if(c==d&&!q){out.add(s.toString().trim());s.setLength(0);}else s.append(c);}out.add(s.toString().trim());return out;
    }
    private static Stats stats(Table t,int col){Stats s=new Stats();for(List<String> row:t.data){try{String v=row.get(col).replace(",","").replaceAll("[^0-9+\\-.]","");if(v.isEmpty())continue;double x=Double.parseDouble(v);s.count++;s.sum+=x;s.min=Math.min(s.min,x);s.max=Math.max(s.max,x);}catch(Exception ignored){}}return s;}
    private static String fmt(double x){if(Math.abs(x-Math.rint(x))<0.000001)return String.valueOf((long)Math.rint(x));return String.format(Locale.US,"%.2f",x);}
    private static class Table{char delim;int rows,cols;List<String> headers;List<List<String>> data;}
    private static class Stats{int count=0;double sum=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;}
}
