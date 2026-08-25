package com.kareem.cortex;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

/**
 * Cortex phone-context sensor.
 * Continuous accessibility events remain bounded local evidence; deeper screen snapshots and
 * robot controls are explicit and robot controls exist only during CortexExperimentalTestMode.
 */
public final class CortexScreenAccessibilityService extends AccessibilityService {
    private static volatile CortexScreenAccessibilityService live;
    private static final int MAX_NODES=320,MAX_CHARS=7000;

    public static final class Snapshot {
        public final String packageName,appLabel,text;public final long capturedAt;
        Snapshot(String p,String a,String t,long when){packageName=n(p);appLabel=n(a);text=n(t);capturedAt=when;}
        public boolean usable(){return !text.isEmpty();}
    }
    public static final class RobotNode {
        public final String path,label,className,packageName;public final boolean enabled,editable,scrollable;
        RobotNode(String p,String l,String c,String pkg,boolean e,boolean edit,boolean scroll){path=n(p);label=n(l);className=n(c);packageName=n(pkg);enabled=e;editable=edit;scrollable=scroll;}
    }

    @Override protected void onServiceConnected(){super.onServiceConnected();live=this;PhoneContextScheduler.schedule(this);}
    @Override public void onDestroy(){if(live==this)live=null;super.onDestroy();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){PhoneContextCollector.onAccessibilityEvent(this,event);}
    @Override public void onInterrupt(){}
    public static boolean connected(){return live!=null;}

    public static Snapshot snapshot(){CortexScreenAccessibilityService s=live;if(s==null)return null;try{AccessibilityNodeInfo root=s.getRootInActiveWindow();if(root==null)return null;String pkg=n(root.getPackageName()==null?null:root.getPackageName().toString());LinkedHashSet<String> lines=new LinkedHashSet<>();ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(root);int nodes=0,chars=0;while(!q.isEmpty()&&nodes<MAX_NODES&&chars<MAX_CHARS){AccessibilityNodeInfo x=q.removeFirst();nodes++;if(x==null)continue;if(!x.isPassword()){chars+=add(lines,x.getText(),chars);chars+=add(lines,x.getContentDescription(),chars);if(android.os.Build.VERSION.SDK_INT>=26)chars+=add(lines,x.getHintText(),chars);}for(int i=0;i<x.getChildCount()&&q.size()<MAX_NODES;i++){AccessibilityNodeInfo child=x.getChild(i);if(child!=null)q.addLast(child);}}StringBuilder body=new StringBuilder();for(String line:lines){if(body.length()>0)body.append('\n');if(body.length()+line.length()>MAX_CHARS)break;body.append(line);}return new Snapshot(pkg,label(s,pkg),body.toString(),System.currentTimeMillis());}catch(Throwable ignored){return null;}}

    public static List<RobotNode> robotClickableNodes(){CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return Collections.emptyList();try{AccessibilityNodeInfo root=s.getRootInActiveWindow();if(root==null)return Collections.emptyList();ArrayList<RobotNode> out=new ArrayList<>();collectRobot(root,"",out,0,true,false);return out;}catch(Throwable ignored){return Collections.emptyList();}}
    public static List<RobotNode> robotEditableNodes(){CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return Collections.emptyList();try{AccessibilityNodeInfo root=s.getRootInActiveWindow();if(root==null)return Collections.emptyList();ArrayList<RobotNode> out=new ArrayList<>();collectRobot(root,"",out,0,false,true);return out;}catch(Throwable ignored){return Collections.emptyList();}}
    public static boolean robotClick(String path){CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return false;AccessibilityNodeInfo x=findNode(s,path);if(x==null)return false;try{return x.isEnabled()&&x.isClickable()&&x.performAction(AccessibilityNodeInfo.ACTION_CLICK);}catch(Throwable ignored){return false;}}
    public static boolean robotSetText(String path,String value){CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return false;AccessibilityNodeInfo x=findNode(s,path);if(x==null)return false;try{if(!x.isEnabled()||!x.isEditable()||x.isPassword())return false;Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value==null?"":value);return x.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);}catch(Throwable ignored){return false;}}
    public static boolean robotScrollForward(){return robotScroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);}
    public static boolean robotScrollBackward(){return robotScroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);}
    private static boolean robotScroll(int action){CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return false;try{AccessibilityNodeInfo root=s.getRootInActiveWindow();if(root==null)return false;ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(root);int seen=0;while(!q.isEmpty()&&seen++<MAX_NODES){AccessibilityNodeInfo x=q.removeFirst();if(x==null)continue;if(x.isVisibleToUser()&&x.isEnabled()&&x.isScrollable()){try{if(x.performAction(action))return true;}catch(Throwable ignored){}}for(int i=0;i<x.getChildCount()&&q.size()<MAX_NODES;i++){AccessibilityNodeInfo child=x.getChild(i);if(child!=null)q.addLast(child);}}}catch(Throwable ignored){}return false;}
    public static boolean robotBack(){CortexScreenAccessibilityService s=live;if(s==null||!CortexExperimentalTestMode.active(s))return false;try{return s.performGlobalAction(GLOBAL_ACTION_BACK);}catch(Throwable ignored){return false;}}

    private static AccessibilityNodeInfo findNode(CortexScreenAccessibilityService s,String path){try{AccessibilityNodeInfo x=s.getRootInActiveWindow();if(x==null)return null;String p=n(path);if(!p.isEmpty())for(String part:p.split("/")){if(part.isEmpty())continue;int i=Integer.parseInt(part);if(i<0||i>=x.getChildCount())return null;AccessibilityNodeInfo child=x.getChild(i);if(child==null)return null;x=child;}return x;}catch(Throwable ignored){return null;}}
    private static void collectRobot(AccessibilityNodeInfo x,String path,List<RobotNode> out,int depth,boolean clickableOnly,boolean editableOnly){if(x==null||depth>28||out.size()>=180)return;boolean include=x.isVisibleToUser()&&!x.isPassword()&&((clickableOnly&&x.isClickable())||(editableOnly&&x.isEditable()));if(include){String lab=directLabel(x);if(lab.isEmpty())lab=descendantLabel(x,0,new int[]{0});if(lab.length()>180)lab=lab.substring(0,180)+"…";out.add(new RobotNode(path,lab,n(x.getClassName()==null?null:x.getClassName().toString()),n(x.getPackageName()==null?null:x.getPackageName().toString()),x.isEnabled(),x.isEditable(),x.isScrollable()));}for(int i=0;i<x.getChildCount()&&out.size()<180;i++){AccessibilityNodeInfo child=x.getChild(i);if(child!=null)collectRobot(child,path.isEmpty()?String.valueOf(i):path+"/"+i,out,depth+1,clickableOnly,editableOnly);}}
    private static String descendantLabel(AccessibilityNodeInfo root,int depth,int[] budget){if(root==null||depth>4||budget[0]>24)return"";StringBuilder b=new StringBuilder();for(int i=0;i<root.getChildCount()&&budget[0]<=24;i++){AccessibilityNodeInfo c=root.getChild(i);if(c==null)continue;budget[0]++;if(c.isPassword())continue;String d=directLabel(c);if(d.isEmpty())d=descendantLabel(c,depth+1,budget);if(!d.isEmpty()){if(b.length()>0)b.append(" · ");b.append(d);if(b.length()>160)break;}}return n(b.toString());}
    private static String directLabel(AccessibilityNodeInfo x){String text=n(x.getText()==null?null:x.getText().toString()),desc=n(x.getContentDescription()==null?null:x.getContentDescription().toString()),hint="";if(android.os.Build.VERSION.SDK_INT>=26)hint=n(x.getHintText()==null?null:x.getHintText().toString());return !text.isEmpty()?text:!desc.isEmpty()?desc:hint;}
    private static int add(LinkedHashSet<String> out,CharSequence raw,int current){String x=n(raw==null?null:raw.toString()).replaceAll("\\s+"," ");if(x.length()<2||current>=MAX_CHARS)return 0;if(x.length()>600)x=x.substring(0,600)+"…";return out.add(x)?x.length():0;}
    private static String label(CortexScreenAccessibilityService s,String pkg){if(pkg.isEmpty())return"Current app";try{ApplicationInfo ai=s.getPackageManager().getApplicationInfo(pkg,0);CharSequence l=s.getPackageManager().getApplicationLabel(ai);return l==null?pkg:l.toString();}catch(Throwable ignored){return pkg;}}
    private static String n(String s){return s==null?"":s.trim();}
}
