package com.kareem.cortex;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * Locked PRIME icon language from the approved preview: matte raised plate,
 * crisp monoline white glyph, and one warm semantic status dot.
 * No purple, glass reflections, emoji, or platform icon drift.
 */
public final class CortexGlyphView extends View {
    private final Paint plate=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;
    private final float d;
    private boolean showDot;
    private int accent;

    public CortexGlyphView(Context c,String kind,int accent,boolean dot){
        super(c);this.kind=kind==null?"info":kind;this.accent=accent;this.showDot=dot;
        d=getResources().getDisplayMetrics().density;setLayerType(LAYER_TYPE_SOFTWARE,null);
        plate.setColor(Color.rgb(17,17,18));plate.setStyle(Paint.Style.FILL);plate.setShadowLayer(7*d,0,3*d,Color.argb(150,0,0,0));
        edge.setColor(Color.argb(42,255,255,255));edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(1*d);
        glyph.setColor(Color.rgb(238,238,236));glyph.setStyle(Paint.Style.STROKE);glyph.setStrokeWidth(1.75f*d);glyph.setStrokeCap(Paint.Cap.ROUND);glyph.setStrokeJoin(Paint.Join.ROUND);
        accentPaint.setColor(accent);accentPaint.setStyle(Paint.Style.FILL);setClickable(false);
    }

    public void setAccent(int color){accent=color;accentPaint.setColor(color);invalidate();}
    public void setShowDot(boolean show){showDot=show;invalidate();}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight(),r=Math.min(w,h);RectF box=new RectF(1.5f*d,1.5f*d,w-1.5f*d,h-1.5f*d);float cr=r*.22f;
        c.drawRoundRect(box,cr,cr,plate);c.drawRoundRect(box,cr,cr,edge);
        float cx=w*.50f,cy=h*.51f,s=r*.23f;drawGlyph(c,cx,cy,s);
        if(showDot)c.drawCircle(w*.77f,h*.24f,Math.max(2.5f*d,r*.055f),accentPaint);
    }

    private void drawGlyph(Canvas c,float cx,float cy,float s){
        Path p=new Path();RectF r;
        switch(kind){
            case "wave":case "voice":{
                for(int i=-2;i<=2;i++){float x=cx+i*s*.42f;float hh=s*(i==0?1f:(Math.abs(i)==1?.70f:.42f));c.drawLine(x,cy-hh,x,cy+hh,glyph);}break;
            }
            case "phone":{
                r=new RectF(cx-s*.90f,cy-s*.95f,cx-s*.35f,cy-s*.20f);c.drawArc(r,120,85,false,glyph);r=new RectF(cx+s*.28f,cy+s*.18f,cx+s*.88f,cy+s*.92f);c.drawArc(r,-55,88,false,glyph);p.moveTo(cx-s*.55f,cy-s*.45f);p.cubicTo(cx-s*.05f,cy+s*.15f,cx+s*.05f,cy+s*.30f,cx+s*.55f,cy+s*.50f);c.drawPath(p,glyph);break;
            }
            case "clock":case "waiting":{
                c.drawCircle(cx,cy,s*.95f,glyph);c.drawLine(cx,cy,cx,cy-s*.55f,glyph);c.drawLine(cx,cy,cx+s*.48f,cy+s*.12f,glyph);break;
            }
            case "nodes":case "decision":case "project":{
                c.drawCircle(cx-s*.78f,cy-s*.55f,s*.20f,glyph);c.drawCircle(cx+s*.78f,cy-s*.55f,s*.20f,glyph);c.drawCircle(cx,cy+s*.78f,s*.20f,glyph);c.drawLine(cx-s*.62f,cy-s*.42f,cx-s*.13f,cy+s*.55f,glyph);c.drawLine(cx+s*.62f,cy-s*.42f,cx+s*.13f,cy+s*.55f,glyph);c.drawLine(cx-s*.55f,cy-s*.55f,cx+s*.55f,cy-s*.55f,glyph);break;
            }
            case "bolt":case "action":{
                p.moveTo(cx+s*.12f,cy-s);p.lineTo(cx-s*.55f,cy+s*.05f);p.lineTo(cx-s*.05f,cy+s*.05f);p.lineTo(cx-s*.20f,cy+s);p.lineTo(cx+s*.62f,cy-s*.16f);p.lineTo(cx+s*.12f,cy-s*.16f);p.close();c.drawPath(p,glyph);break;
            }
            case "brief":case "note":case "info":case "file":{
                r=new RectF(cx-s*.78f,cy-s,cx+s*.62f,cy+s);c.drawRoundRect(r,s*.12f,s*.12f,glyph);c.drawLine(cx-s*.47f,cy-s*.45f,cx+s*.25f,cy-s*.45f,glyph);c.drawLine(cx-s*.47f,cy,cx+s*.38f,cy,glyph);c.drawLine(cx-s*.47f,cy+s*.45f,cx+s*.10f,cy+s*.45f,glyph);break;
            }
            case "open":{
                r=new RectF(cx-s*.85f,cy-s*.72f,cx+s*.38f,cy+s*.72f);c.drawRect(r,glyph);c.drawLine(cx,cy,cx+s*.92f,cy-s*.92f,glyph);c.drawLine(cx+s*.43f,cy-s*.92f,cx+s*.92f,cy-s*.92f,glyph);c.drawLine(cx+s*.92f,cy-s*.92f,cx+s*.92f,cy-s*.43f,glyph);break;
            }
            case "search":{
                c.drawCircle(cx-s*.18f,cy-s*.18f,s*.62f,glyph);c.drawLine(cx+s*.28f,cy+s*.28f,cx+s*.85f,cy+s*.85f,glyph);break;
            }
            case "person":case "people":{
                c.drawCircle(cx,cy-s*.48f,s*.38f,glyph);r=new RectF(cx-s*.76f,cy+s*.05f,cx+s*.76f,cy+s*.86f);c.drawArc(r,190,160,false,glyph);break;
            }
            case "photo":{
                r=new RectF(cx-s,cy-s*.78f,cx+s,cy+s*.78f);c.drawRoundRect(r,s*.12f,s*.12f,glyph);c.drawCircle(cx+s*.45f,cy-s*.35f,s*.16f,glyph);p.moveTo(cx-s*.72f,cy+s*.40f);p.lineTo(cx-s*.22f,cy-s*.08f);p.lineTo(cx+s*.12f,cy+s*.25f);p.lineTo(cx+s*.42f,cy-s*.02f);p.lineTo(cx+s*.75f,cy+s*.42f);c.drawPath(p,glyph);break;
            }
            case "text":{
                c.drawLine(cx-s*.82f,cy-s*.62f,cx+s*.82f,cy-s*.62f,glyph);c.drawLine(cx,cy-s*.62f,cx,cy+s*.72f,glyph);c.drawLine(cx-s*.30f,cy+s*.72f,cx+s*.30f,cy+s*.72f,glyph);break;
            }
            case "plus":case "input":{
                c.drawCircle(cx,cy,s*.86f,glyph);c.drawLine(cx-s*.42f,cy,cx+s*.42f,cy,glyph);c.drawLine(cx,cy-s*.42f,cx,cy+s*.42f,glyph);break;
            }
            case "brain":case "spark":{
                for(int i=0;i<8;i++){double a=i*Math.PI/4;float x=cx+(float)Math.cos(a)*s*.72f,y=cy+(float)Math.sin(a)*s*.72f;c.drawCircle(x,y,s*.10f,glyph);c.drawLine(cx,cy,x,y,glyph);}c.drawCircle(cx,cy,s*.18f,glyph);break;
            }
            case "settings":{
                c.drawCircle(cx,cy,s*.82f,glyph);c.drawCircle(cx,cy,s*.30f,glyph);break;
            }
            case "check":{
                p.moveTo(cx-s*.72f,cy);p.lineTo(cx-s*.15f,cy+s*.58f);p.lineTo(cx+s*.82f,cy-s*.60f);c.drawPath(p,glyph);break;
            }
            default:{c.drawCircle(cx,cy,s*.80f,glyph);c.drawCircle(cx,cy,s*.10f,glyph);break;}
        }
    }
}
