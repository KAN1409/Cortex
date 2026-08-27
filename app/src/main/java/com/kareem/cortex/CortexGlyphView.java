package com.kareem.cortex;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Approved reference icon language: thin monoline, bare navigation, compact card plates. */
public final class CortexGlyphView extends View {
    private final Paint plate=new Paint(Paint.ANTI_ALIAS_FLAG),edge=new Paint(Paint.ANTI_ALIAS_FLAG),glyph=new Paint(Paint.ANTI_ALIAS_FLAG),accentPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;private final float d;private boolean showDot;private int accent;

    public CortexGlyphView(Context c,String kind,int accent,boolean dot){super(c);this.kind=kind==null?"info":kind;this.accent=accent;this.showDot=dot;d=getResources().getDisplayMetrics().density;setLayerType(LAYER_TYPE_SOFTWARE,null);plate.setColor(CortexUi.BG);plate.setStyle(Paint.Style.FILL);edge.setColor(CortexUi.BORDER);edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(1*d);glyph.setColor(CortexUi.TEXT);glyph.setStyle(Paint.Style.STROKE);glyph.setStrokeWidth(1.65f*d);glyph.setStrokeCap(Paint.Cap.ROUND);glyph.setStrokeJoin(Paint.Join.ROUND);accentPaint.setColor(accent);accentPaint.setStyle(Paint.Style.FILL);setClickable(false);}
    public void setAccent(int c){accent=c;accentPaint.setColor(c);invalidate();}public void setShowDot(boolean s){showDot=s;invalidate();}

    private boolean bare(){return kind.startsWith("nav_")||"brand".equals(kind)||"search".equals(kind)||"filter".equals(kind)||"arrow".equals(kind)||"menu".equals(kind)||"calendar".equals(kind)||"relevance".equals(kind)||"attachment".equals(kind)||"link".equals(kind);}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),r=Math.min(w,h);if(!bare()){RectF b=new RectF(1.5f*d,1.5f*d,w-1.5f*d,h-1.5f*d);float cr=r*.22f;c.drawRoundRect(b,cr,cr,plate);c.drawRoundRect(b,cr,cr,edge);}float cx=w*.5f,cy=h*.5f,s=r*(bare()?.27f:.23f);drawGlyph(c,cx,cy,s);if(showDot)c.drawCircle(w*.78f,h*.22f,Math.max(2.3f*d,r*.055f),accentPaint);}

    private void drawGlyph(Canvas c,float cx,float cy,float s){Path p=new Path();RectF r;switch(kind){
        case "brand":{float rr=s*1.12f;for(int i=0;i<6;i++){double a=Math.PI/3*i-Math.PI/6;float x=cx+(float)Math.cos(a)*rr,y=cy+(float)Math.sin(a)*rr;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();Paint gp=copy(accent,Paint.Style.STROKE,2.1f*d);c.drawPath(p,gp);Path cc=new Path();r=new RectF(cx-s*.48f,cy-s*.47f,cx+s*.48f,cy+s*.47f);c.drawArc(r,55,250,false,gp);c.drawLine(cx+s*.18f,cy-s*.40f,cx+s*.48f,cy-s*.24f,gp);break;}
        case "menu":{Paint g=copy(CortexUi.TEXT,Paint.Style.STROKE,1.5f*d);RectF b=new RectF(cx-s*1.25f,cy-s*1.25f,cx+s*1.25f,cy+s*1.25f);c.drawRoundRect(b,s*.34f,s*.34f,edge);for(int i=-1;i<=1;i++){float y=cy+i*s*.55f;c.drawCircle(cx-s*.62f,y,s*.075f,g);c.drawLine(cx-s*.30f,y,cx+s*.68f,y,g);}break;}
        case "filter":{p.moveTo(cx-s,cy-s*.78f);p.lineTo(cx+s,cy-s*.78f);p.lineTo(cx+s*.34f,cy);p.lineTo(cx+s*.34f,cy+s*.72f);p.lineTo(cx-s*.18f,cy+s);p.lineTo(cx-s*.18f,cy);p.close();c.drawPath(p,glyph);break;}
        case "arrow":{c.drawLine(cx-s*.75f,cy,cx+s*.75f,cy,glyph);c.drawLine(cx+s*.28f,cy-s*.48f,cx+s*.75f,cy,glyph);c.drawLine(cx+s*.28f,cy+s*.48f,cx+s*.75f,cy,glyph);break;}
        case "nav_clock":case "clock":case "waiting":{c.drawCircle(cx,cy,s*.90f,glyph);c.drawLine(cx,cy,cx,cy-s*.52f,glyph);c.drawLine(cx,cy,cx+s*.45f,cy+s*.12f,glyph);break;}
        case "nav_inbox":{r=new RectF(cx-s,cy-s*.68f,cx+s,cy+s*.68f);c.drawRoundRect(r,s*.16f,s*.16f,glyph);p.moveTo(cx-s,cy-s*.45f);p.lineTo(cx,cy+s*.16f);p.lineTo(cx+s,cy-s*.45f);c.drawPath(p,glyph);break;}
        case "nav_atlas":{float q=s*.55f,g=s*.18f;for(int yy=-1;yy<=1;yy+=2)for(int xx=-1;xx<=1;xx+=2){r=new RectF(cx+xx*(q+g)-q/2,cy+yy*(q+g)-q/2,cx+xx*(q+g)+q/2,cy+yy*(q+g)+q/2);c.drawRoundRect(r,s*.08f,s*.08f,glyph);}break;}
        case "nav_brain":{drawBrain(c,cx,cy,s);break;}
        case "wave":case "voice":{for(int i=-2;i<=2;i++){float x=cx+i*s*.42f;float hh=s*(i==0?1f:(Math.abs(i)==1?.70f:.42f));c.drawLine(x,cy-hh,x,cy+hh,glyph);}break;}
        case "phone":{r=new RectF(cx-s*.90f,cy-s*.95f,cx-s*.35f,cy-s*.20f);c.drawArc(r,120,85,false,glyph);r=new RectF(cx+s*.28f,cy+s*.18f,cx+s*.88f,cy+s*.92f);c.drawArc(r,-55,88,false,glyph);p.moveTo(cx-s*.55f,cy-s*.45f);p.cubicTo(cx-s*.05f,cy+s*.15f,cx+s*.05f,cy+s*.30f,cx+s*.55f,cy+s*.50f);c.drawPath(p,glyph);break;}
        case "nodes":case "decision":case "project":{c.drawCircle(cx-s*.78f,cy-s*.55f,s*.20f,glyph);c.drawCircle(cx+s*.78f,cy-s*.55f,s*.20f,glyph);c.drawCircle(cx,cy+s*.78f,s*.20f,glyph);c.drawLine(cx-s*.62f,cy-s*.42f,cx-s*.13f,cy+s*.55f,glyph);c.drawLine(cx+s*.62f,cy-s*.42f,cx+s*.13f,cy+s*.55f,glyph);c.drawLine(cx-s*.55f,cy-s*.55f,cx+s*.55f,cy-s*.55f,glyph);break;}
        case "bolt":case "action":{p.moveTo(cx+s*.12f,cy-s);p.lineTo(cx-s*.55f,cy+s*.05f);p.lineTo(cx-s*.05f,cy+s*.05f);p.lineTo(cx-s*.20f,cy+s);p.lineTo(cx+s*.62f,cy-s*.16f);p.lineTo(cx+s*.12f,cy-s*.16f);p.close();c.drawPath(p,glyph);break;}
        case "brief":case "note":case "info":case "file":{r=new RectF(cx-s*.78f,cy-s,cx+s*.62f,cy+s);c.drawRoundRect(r,s*.12f,s*.12f,glyph);c.drawLine(cx-s*.47f,cy-s*.45f,cx+s*.25f,cy-s*.45f,glyph);c.drawLine(cx-s*.47f,cy,cx+s*.38f,cy,glyph);c.drawLine(cx-s*.47f,cy+s*.45f,cx+s*.10f,cy+s*.45f,glyph);break;}
        case "open":{r=new RectF(cx-s*.85f,cy-s*.72f,cx+s*.38f,cy+s*.72f);c.drawRect(r,glyph);c.drawLine(cx,cy,cx+s*.92f,cy-s*.92f,glyph);c.drawLine(cx+s*.43f,cy-s*.92f,cx+s*.92f,cy-s*.92f,glyph);c.drawLine(cx+s*.92f,cy-s*.92f,cx+s*.92f,cy-s*.43f,glyph);break;}
        case "search":{c.drawCircle(cx-s*.18f,cy-s*.18f,s*.62f,glyph);c.drawLine(cx+s*.28f,cy+s*.28f,cx+s*.86f,cy+s*.86f,glyph);break;}
        case "person":case "people":{c.drawCircle(cx,cy-s*.48f,s*.38f,glyph);r=new RectF(cx-s*.76f,cy+s*.05f,cx+s*.76f,cy+s*.86f);c.drawArc(r,190,160,false,glyph);break;}
        case "photo":{r=new RectF(cx-s,cy-s*.78f,cx+s,cy+s*.78f);c.drawRoundRect(r,s*.12f,s*.12f,glyph);c.drawCircle(cx+s*.45f,cy-s*.35f,s*.16f,glyph);p.moveTo(cx-s*.72f,cy+s*.40f);p.lineTo(cx-s*.22f,cy-s*.08f);p.lineTo(cx+s*.12f,cy+s*.25f);p.lineTo(cx+s*.42f,cy-s*.02f);p.lineTo(cx+s*.75f,cy+s*.42f);c.drawPath(p,glyph);break;}
        case "text":{c.drawLine(cx-s*.82f,cy-s*.62f,cx+s*.82f,cy-s*.62f,glyph);c.drawLine(cx,cy-s*.62f,cx,cy+s*.72f,glyph);c.drawLine(cx-s*.30f,cy+s*.72f,cx+s*.30f,cy+s*.72f,glyph);break;}
        case "attachment":{r=new RectF(cx-s*.42f,cy-s*.98f,cx+s*.52f,cy+s*.98f);c.drawArc(r,210,260,false,glyph);r=new RectF(cx-s*.14f,cy-s*.70f,cx+s*.27f,cy+s*.60f);c.drawArc(r,208,270,false,glyph);break;}
        case "link":{r=new RectF(cx-s*.95f,cy-s*.55f,cx+s*.05f,cy+s*.42f);c.drawArc(r,135,180,false,glyph);r=new RectF(cx-s*.05f,cy-s*.42f,cx+s*.95f,cy+s*.55f);c.drawArc(r,-45,180,false,glyph);c.drawLine(cx-s*.34f,cy+s*.24f,cx+s*.34f,cy-s*.24f,glyph);break;}
        case "calendar":{r=new RectF(cx-s*.90f,cy-s*.72f,cx+s*.90f,cy+s*.82f);c.drawRoundRect(r,s*.14f,s*.14f,glyph);c.drawLine(cx-s*.90f,cy-s*.25f,cx+s*.90f,cy-s*.25f,glyph);c.drawLine(cx-s*.45f,cy-s*.94f,cx-s*.45f,cy-s*.48f,glyph);c.drawLine(cx+s*.45f,cy-s*.94f,cx+s*.45f,cy-s*.48f,glyph);break;}
        case "relevance":{for(int i=0;i<4;i++){float x=cx-s*.78f+i*s*.50f;c.drawLine(x,cy+s*.65f,x,cy+s*(.15f-i*.25f),glyph);}break;}
        case "plus":case "input":{c.drawCircle(cx,cy,s*.86f,glyph);c.drawLine(cx-s*.42f,cy,cx+s*.42f,cy,glyph);c.drawLine(cx,cy-s*.42f,cx,cy+s*.42f,glyph);break;}
        case "brain":case "spark":{drawBrain(c,cx,cy,s);break;}
        case "settings":{c.drawCircle(cx,cy,s*.82f,glyph);c.drawCircle(cx,cy,s*.30f,glyph);break;}
        case "check":{p.moveTo(cx-s*.72f,cy);p.lineTo(cx-s*.15f,cy+s*.58f);p.lineTo(cx+s*.82f,cy-s*.60f);c.drawPath(p,glyph);break;}
        default:{c.drawCircle(cx,cy,s*.80f,glyph);c.drawCircle(cx,cy,s*.10f,glyph);break;}
    }}
    private void drawBrain(Canvas c,float cx,float cy,float s){RectF l=new RectF(cx-s*.95f,cy-s*.82f,cx+s*.05f,cy+s*.82f),r=new RectF(cx-s*.05f,cy-s*.82f,cx+s*.95f,cy+s*.82f);c.drawArc(l,75,210,false,glyph);c.drawArc(r,-105,210,false,glyph);c.drawLine(cx,cy-s*.62f,cx,cy+s*.62f,glyph);c.drawCircle(cx-s*.34f,cy-s*.22f,s*.12f,glyph);c.drawCircle(cx+s*.34f,cy+s*.22f,s*.12f,glyph);}
    private Paint copy(int color,Paint.Style style,float width){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(style);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);return p;}
}
