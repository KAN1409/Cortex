package com.kareem.cortex;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

/**
 * Cortex phone-context sensor.
 *
 * Continuous accessibility events are stored only in the bounded local PhoneContext timeline.
 * Explicit Understand Screen still performs the deeper on-demand tree snapshot below.
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

    /** Lightweight test-only description of an active-window node. */
    public static final class RobotNode {
        public final String path,label,className,packageName;
        public final boolean enabled,editable;
        RobotNode(String p,String l,String c,String pkg,boolean e,boolean edit){path=n(p);label=n(l);className=n(c);packageName=n(pkg);enabled=e;editable=edit;}
    }

    @Override protected void onServiceConnected(){super.onServiceConnected();live=this;PhoneContextScheduler.schedule(this);}
    @Override public void onDestroy(){if(live==this)live=null;super.onDestroy();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){PhoneContextCollector.onAccessibilityEvent(this,event);}
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

    /** Enumerate active-window click targets only while the explicit experimental test mode is enabled. */
    public static List<RobotNode> robotClickableNodes(){
        CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return Collections.emptyList();AccessibilityNodeInfo root=null;
        try{root=s.getRootInActiveWindow();if(root==null)return Collections.emptyList();ArrayList<RobotNode> out=new ArrayList<>();collectRobot(root,"",out,0,true,false);return out;}catch(Throwable ignored){return Collections.emptyList();}
    }

    /** Enumerate editable active-window nodes so the robot can fill synthetic fixtures before pressing submit/save. */
    public static List<RobotNode> robotEditableNodes(){
        CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return Collections.emptyList();AccessibilityNodeInfo root=null;
        try{root=s.getRootInActiveWindow();if(root==null)return Collections.emptyList();ArrayList<RobotNode> out=new ArrayList<>();collectRobot(root,"",out,0,false,true);return out;}catch(Throwable ignored){return Collections.emptyList();}
    }

    /** Perform one accessibility click by tree path. Never available outside explicit test mode. */
    public static boolean robotClick(String path){
        CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return false;AccessibilityNodeInfo x=findNode(s,path);if(x==null)return false;try{if(!x.isEnabled()||!x.isClickable())return false;return x.performAction(AccessibilityNodeInfo.ACTION_CLICK);}catch(Throwable ignored){return false;}
    }

    /** Put synthetic text into an editable field. Password nodes are never exposed. */
    public static boolean robotSetText(String path,String value){
        CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return false;AccessibilityNodeInfo x=findNode(s,path);if(x==null)return false;try{if(!x.isEnabled()||!x.isEditable()||x.isPassword())return false;Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value==null?"":value);return x.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);}catch(Throwable ignored){return false;}
    }

    /** Global Back for dialog/system-window backtracking. Test mode is mandatory. */
    public static boolean robotBack(){CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return false;try{return s.performGlobalAction(GLOBAL_ACTION_BACK);}catch(Throwable ignored){return false;}}

    private static AccessibilityNodeInfo findNode(CortexScreenAccessibilityService s,String path){
        try{AccessibilityNodeInfo x=s.getRootInActiveWindow();if(x==null)return null;String p=n(path);if(!p.isEmpty())for(String part:p.split("/")){if(part.isEmpty())continue;int i=Integer.parseInt(part);if(i<0||i>=x.getChildCount())return null;AccessibilityNodeInfo child=x.getChild(i);if(child==null)return null;x=child;}return x;}catch(Throwable ignored){return null;}
    }

    private static void collectRobot(AccessibilityNodeInfo x,String path,List<RobotNode> out,int depth,boolean clickableOnly,boolean editableOnly){
        if(x==null||depth>28||out.size()>=180)return;
        boolean include=x.isVisibleToUser()&&!x.isPassword()&&((clickableOnly&&x.isClickable())||(editableOnly&&x.isEditable()));
        if(include){
            String text=n(x.getText()==null?null:x.getText().toString()),desc=n(x.getContentDescription()==null?null:x.getContentDescription().toString()),hint=android.os.Build.VERSION.SDK_INT>=26?n(x.getHintText()==null?null:x.getHintText().toString()):"";String label=!text.isEmpty()?text:!desc.isEmpty()?desc:hint;if(label.length()>180)label=label.substring(0,180)+"…";
            out.add(new RobotNode(path,label,n(x.getClassName()==null?null:x.getClassName().toString()),n(x.getPackageName()==null?null:x.getPackageName().toString()),x.isEnabled(),x.isEditable()));
        }
        for(int i=0;i<x.getChildCount()&&out.size()<180;i++){AccessibilityNodeInfo child=x.getChild(i);if(child!=null)collectRobot(child,path.isEmpty()?String.valueOf(i):path+"/"+i,out,depth+1,clickableOnly,editableOnly);}
    }

    private static int add(LinkedHashSet<String> out,CharSequence raw,int current){String x=n(raw==null?null:raw.toString()).replaceAll("\\s+"," ");if(x.length()<2||current>=MAX_CHARS)return 0;if(x.length()>600)x=x.substring(0,600)+"…";return out.add(x)?x.length():0;}
    private static String label(CortexScreenAccessibilityService s,String pkg){if(pkg.isEmpty())return"Current app";try{ApplicationInfo ai=s.getPackageManager().getApplicationInfo(pkg,0);CharSequence l=s.getPackageManager().getApplicationLabel(ai);return l==null?pkg:l.toString();}catch(Throwable ignored){return pkg;}}
    private static String n(String s){return s==null?"":s.trim();}
}
