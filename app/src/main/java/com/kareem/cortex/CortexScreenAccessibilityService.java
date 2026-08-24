package com.kareem.cortex;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

/**
 * Accessibility is used only as an on-demand sensor for Understand Screen.
 * No event is persisted and no background monitoring occurs.
 */
public final class CortexScreenAccessibilityService extends AccessibilityService {
    private static volatile CortexScreenAccessibilityService live;
    private static final int MAX_NODES=320,MAX_CHARS=7000;

    public static final class Snapshot {
        public final String packageName,appLabel,text;
        public final long capturedAt;
        Snapshot(String p,String a,String t,long when){packageName=n(p);appLabel=n(a);text=n(t);capturedAt=when;}
        public boolean usable(){return !text.isEmpty();}
    }

    @Override protected void onServiceConnected(){super.onServiceConnected();live=this;}
    @Override public void onDestroy(){if(live==this)live=null;super.onDestroy();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){/* Explicit capture only. */}
    @Override public void onInterrupt(){}

    public static boolean connected(){return live!=null;}

    public static Snapshot snapshot(){
        CortexScreenAccessibilityService s=live;if(s==null)return null;AccessibilityNodeInfo root=null;
        try{
            root=s.getRootInActiveWindow();if(root==null)return null;String pkg=n(root.getPackageName()==null?null:root.getPackageName().toString());
            LinkedHashSet<String> lines=new LinkedHashSet<>();ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(root);int nodes=0,chars=0;
            while(!q.isEmpty()&&nodes<MAX_NODES&&chars<MAX_CHARS){
                AccessibilityNodeInfo x=q.removeFirst();nodes++;
                if(x==null)continue;
                if(!x.isPassword()){
                    chars+=add(lines,x.getText(),chars);chars+=add(lines,x.getContentDescription(),chars);if(android.os.Build.VERSION.SDK_INT>=26)chars+=add(lines,x.getHintText(),chars);
                }
                for(int i=0;i<x.getChildCount()&&q.size()<MAX_NODES;i++){AccessibilityNodeInfo child=x.getChild(i);if(child!=null)q.addLast(child);}
            }
            StringBuilder body=new StringBuilder();for(String line:lines){if(body.length()>0)body.append('\n');if(body.length()+line.length()>MAX_CHARS)break;body.append(line);}
            return new Snapshot(pkg,label(s,pkg),body.toString(),System.currentTimeMillis());
        }catch(Throwable ignored){return null;}
    }

    private static int add(LinkedHashSet<String> out,CharSequence raw,int current){String x=n(raw==null?null:raw.toString()).replaceAll("\\s+"," ");if(x.length()<2||current>=MAX_CHARS)return 0;if(x.length()>600)x=x.substring(0,600)+"…";return out.add(x)?x.length():0;}
    private static String label(CortexScreenAccessibilityService s,String pkg){if(pkg.isEmpty())return"Current app";try{ApplicationInfo ai=s.getPackageManager().getApplicationInfo(pkg,0);CharSequence l=s.getPackageManager().getApplicationLabel(ai);return l==null?pkg:l.toString();}catch(Throwable ignored){return pkg;}}
    private static String n(String s){return s==null?"":s.trim();}
}
