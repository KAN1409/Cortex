package com.kareem.cortex;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * Cortex UI Design Lock V1 icon family.
 * Bare geometric monoline icons for the approved chat-first shell.
 * No icon plates, emoji, mixed platform assets, gradients, or per-screen style drift.
 */
public final class CortexLineIconView extends View {
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;
    private final float d;
    private int color;

    public CortexLineIconView(Context c,String kind,int color){
        super(c);this.kind=kind==null?"dot":kind;this.color=color;d=getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(1.8f*d);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeJoin(Paint.Join.ROUND);line.setColor(color);
        fill.setStyle(Paint.Style.FILL);fill.setColor(color);
    }
    public void setColor(int c){color=c;line.setColor(c);fill.setColor(c);invalidate();}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),r=Math.min(w,h),cx=w/2f,cy=h/2f,s=r*.30f;Path p=new Path();RectF q;
        switch(kind){
            case "logo":{
                for(int i=0;i<8;i++){double a=i*Math.PI/4;float x=cx+(float)Math.cos(a)*s*.72f,y=cy+(float)Math.sin(a)*s*.72f;c.drawCircle(x,y,Math.max(1.8f*d,s*.13f),fill);}c.drawCircle(cx,cy,Math.max(1.5f*d,s*.10f),fill);break;
            }
            case "menu":{for(int i=-1;i<=1;i++)c.drawLine(cx-s*.76f,cy+i*s*.58f,cx+s*.76f,cy+i*s*.58f,line);break;}
            case "search":{c.drawCircle(cx-s*.12f,cy-s*.12f,s*.62f,line);c.drawLine(cx+s*.34f,cy+s*.34f,cx+s*.88f,cy+s*.88f,line);break;}
            case "more":{c.drawCircle(cx,cy-s*.64f,s*.09f,fill);c.drawCircle(cx,cy,s*.09f,fill);c.drawCircle(cx,cy+s*.64f,s*.09f,fill);break;}
            case "plus":{c.drawLine(cx-s*.72f,cy,cx+s*.72f,cy,line);c.drawLine(cx,cy-s*.72f,cx,cy+s*.72f,line);break;}
            case "back":{p.moveTo(cx+s*.48f,cy-s*.75f);p.lineTo(cx-s*.38f,cy);p.lineTo(cx+s*.48f,cy+s*.75f);c.drawPath(p,line);break;}
            case "chevron":{p.moveTo(cx-s*.28f,cy-s*.52f);p.lineTo(cx+s*.30f,cy);p.lineTo(cx-s*.28f,cy+s*.52f);c.drawPath(p,line);break;}
            case "mic":{q=new RectF(cx-s*.38f,cy-s*.82f,cx+s*.38f,cy+s*.28f);c.drawRoundRect(q,s*.34f,s*.34f,line);c.drawArc(new RectF(cx-s*.72f,cy-s*.06f,cx+s*.72f,cy+s*.72f),0,180,false,line);c.drawLine(cx,cy+s*.72f,cx,cy+s*.98f,line);c.drawLine(cx-s*.34f,cy+s*.98f,cx+s*.34f,cy+s*.98f,line);break;}
            case "wave":{for(int i=-2;i<=2;i++){float x=cx+i*s*.34f,hh=s*(i==0?.86f:(Math.abs(i)==1?.62f:.36f));c.drawLine(x,cy-hh,x,cy+hh,line);}break;}
            case "send":{p.moveTo(cx-s*.82f,cy+s*.70f);p.lineTo(cx+s*.78f,cy);p.lineTo(cx-s*.82f,cy-s*.70f);p.close();c.drawPath(p,line);c.drawLine(cx-s*.42f,cy,cx+s*.45f,cy,line);break;}
            case "chat":{q=new RectF(cx-s*.86f,cy-s*.68f,cx+s*.86f,cy+s*.58f);c.drawRoundRect(q,s*.48f,s*.48f,line);p.moveTo(cx-s*.28f,cy+s*.58f);p.lineTo(cx-s*.60f,cy+s*.92f);p.lineTo(cx-s*.52f,cy+s*.48f);c.drawPath(p,line);break;}
            case "person":{c.drawCircle(cx,cy-s*.42f,s*.34f,line);c.drawArc(new RectF(cx-s*.74f,cy+s*.05f,cx+s*.74f,cy+s*.92f),190,160,false,line);break;}
            case "project":{q=new RectF(cx-s*.88f,cy-s*.48f,cx+s*.88f,cy+s*.66f);c.drawRoundRect(q,s*.16f,s*.16f,line);p.moveTo(cx-s*.78f,cy-s*.48f);p.lineTo(cx-s*.42f,cy-s*.82f);p.lineTo(cx+s*.02f,cy-s*.82f);p.lineTo(cx+s*.22f,cy-s*.48f);c.drawPath(p,line);break;}
            case "evidence":case "file":{q=new RectF(cx-s*.64f,cy-s*.88f,cx+s*.60f,cy+s*.88f);c.drawRoundRect(q,s*.10f,s*.10f,line);c.drawLine(cx-s*.36f,cy-s*.36f,cx+s*.30f,cy-s*.36f,line);c.drawLine(cx-s*.36f,cy,cx+s*.36f,cy,line);c.drawLine(cx-s*.36f,cy+s*.36f,cx+s*.14f,cy+s*.36f,line);break;}
            case "deep":case "brain":{c.drawCircle(cx,cy,s*.22f,line);for(int i=0;i<4;i++){double a=i*Math.PI/2;float x=cx+(float)Math.cos(a)*s*.78f,y=cy+(float)Math.sin(a)*s*.78f;c.drawArc(new RectF(x-s*.22f,y-s*.22f,x+s*.22f,y+s*.22f),0,300,false,line);c.drawLine(cx+(float)Math.cos(a)*s*.24f,cy+(float)Math.sin(a)*s*.24f,x-(float)Math.cos(a)*s*.18f,y-(float)Math.sin(a)*s*.18f,line);}break;}
            case "archive":{q=new RectF(cx-s*.80f,cy-s*.48f,cx+s*.80f,cy+s*.72f);c.drawRoundRect(q,s*.10f,s*.10f,line);c.drawRect(cx-s*.92f,cy-s*.76f,cx+s*.92f,cy-s*.42f,line);c.drawLine(cx-s*.28f,cy-s*.02f,cx+s*.28f,cy-s*.02f,line);break;}
            case "settings":{c.drawCircle(cx,cy,s*.32f,line);for(int i=0;i<8;i++){double a=i*Math.PI/4;float x1=cx+(float)Math.cos(a)*s*.60f,y1=cy+(float)Math.sin(a)*s*.60f,x2=cx+(float)Math.cos(a)*s*.90f,y2=cy+(float)Math.sin(a)*s*.90f;c.drawLine(x1,y1,x2,y2,line);}break;}
            case "history":{c.drawCircle(cx,cy,s*.72f,line);c.drawLine(cx,cy,cx,cy-s*.38f,line);c.drawLine(cx,cy,cx+s*.36f,cy+s*.16f,line);p.moveTo(cx-s*.95f,cy-s*.18f);p.lineTo(cx-s*.62f,cy-s*.50f);p.lineTo(cx-s*.55f,cy-s*.05f);c.drawPath(p,line);break;}
            case "filter":{for(int i=-1;i<=1;i++){float y=cy+i*s*.60f;c.drawLine(cx-s*.82f,y,cx+s*.82f,y,line);float k=(i==0?cx-s*.18f:(i<0?cx+s*.32f:cx+s*.02f));c.drawCircle(k,y,s*.12f,fill);}break;}
            case "copy":{q=new RectF(cx-s*.55f,cy-s*.64f,cx+s*.60f,cy+s*.72f);c.drawRoundRect(q,s*.08f,s*.08f,line);q=new RectF(cx-s*.78f,cy-s*.86f,cx+s*.36f,cy+s*.48f);c.drawRoundRect(q,s*.08f,s*.08f,line);break;}
            case "lock":{q=new RectF(cx-s*.60f,cy-s*.04f,cx+s*.60f,cy+s*.76f);c.drawRoundRect(q,s*.10f,s*.10f,line);c.drawArc(new RectF(cx-s*.42f,cy-s*.82f,cx+s*.42f,cy+s*.18f),180,180,false,line);break;}
            case "shield":{p.moveTo(cx,cy-s*.88f);p.lineTo(cx+s*.70f,cy-s*.52f);p.lineTo(cx+s*.58f,cy+s*.34f);p.quadTo(cx,cy+s*.92f,cx-s*.58f,cy+s*.34f);p.lineTo(cx-s*.70f,cy-s*.52f);p.close();c.drawPath(p,line);break;}
            case "check":{p.moveTo(cx-s*.70f,cy);p.lineTo(cx-s*.14f,cy+s*.56f);p.lineTo(cx+s*.76f,cy-s*.58f);c.drawPath(p,line);break;}
            case "photo":{q=new RectF(cx-s*.88f,cy-s*.68f,cx+s*.88f,cy+s*.68f);c.drawRoundRect(q,s*.12f,s*.12f,line);c.drawCircle(cx+s*.42f,cy-s*.28f,s*.14f,line);p.moveTo(cx-s*.62f,cy+s*.38f);p.lineTo(cx-s*.18f,cy-s*.04f);p.lineTo(cx+s*.10f,cy+s*.22f);p.lineTo(cx+s*.42f,cy-s*.08f);p.lineTo(cx+s*.68f,cy+s*.36f);c.drawPath(p,line);break;}
            case "code":{p.moveTo(cx-s*.24f,cy-s*.58f);p.lineTo(cx-s*.72f,cy);p.lineTo(cx-s*.24f,cy+s*.58f);c.drawPath(p,line);p.reset();p.moveTo(cx+s*.24f,cy-s*.58f);p.lineTo(cx+s*.72f,cy);p.lineTo(cx+s*.24f,cy+s*.58f);c.drawPath(p,line);break;}
            case "list":{for(int i=-1;i<=1;i++){float y=cy+i*s*.58f;c.drawCircle(cx-s*.72f,y,s*.07f,fill);c.drawLine(cx-s*.45f,y,cx+s*.76f,y,line);}break;}
            default:c.drawCircle(cx,cy,s*.12f,fill);
        }
    }
}
