package com.kareem.cortex;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Velvet circular control. Warm orange is interactive by default; red is reserved for record/urgent signal states. */
public final class CortexRingButton extends View {
    public enum Glyph { PLAY, PAUSE, RECORD, STOP, PREVIOUS, NEXT }

    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring=new RectF();
    private Glyph glyph=Glyph.PLAY;
    private float progress=0f;
    private boolean pressedDown=false;
    private final float density;
    private int accent=CortexUi.ORANGE;

    public CortexRingButton(Context c){this(c,null);}
    public CortexRingButton(Context c,AttributeSet a){super(c,a);density=getResources().getDisplayMetrics().density;setLayerType(LAYER_TYPE_SOFTWARE,null);setClickable(true);setFocusable(true);
        fill.setColor(CortexUi.SURFACE_2);fill.setStyle(Paint.Style.FILL);fill.setShadowLayer(16*density,0,9*density,Color.argb(225,0,0,0));
        inner.setColor(Color.argb(18,255,226,190));inner.setStyle(Paint.Style.STROKE);inner.setStrokeWidth(1*density);
        border.setColor(Color.argb(45,255,228,194));border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(1*density);
        track.setColor(CortexUi.BORDER);track.setStyle(Paint.Style.STROKE);track.setStrokeCap(Paint.Cap.ROUND);track.setStrokeWidth(3*density);
        glow.setColor(accent);glow.setStyle(Paint.Style.STROKE);glow.setStrokeCap(Paint.Cap.ROUND);glow.setStrokeWidth(7*density);applyGlowShadow(accent);
        arc.setColor(accent);arc.setStyle(Paint.Style.STROKE);arc.setStrokeCap(Paint.Cap.ROUND);arc.setStrokeWidth(3.2f*density);
        icon.setColor(CortexUi.TEXT);icon.setStyle(Paint.Style.STROKE);icon.setStrokeWidth(2.4f*density);icon.setStrokeCap(Paint.Cap.ROUND);icon.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setGlyph(Glyph g){glyph=g==null?Glyph.PLAY:g;invalidate();}
    public Glyph getGlyph(){return glyph;}
    public void setProgress(float p){progress=Math.max(0f,Math.min(1f,p));invalidate();}
    public float getProgress(){return progress;}
    public void setAccent(int color){accent=color;arc.setColor(color);glow.setColor(color);applyGlowShadow(color);invalidate();}
    private void applyGlowShadow(int color){glow.setShadowLayer(13*density,0,1*density,Color.argb(175,Color.red(color),Color.green(color),Color.blue(color)));}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;float radius=Math.min(w,h)*.39f;if(pressedDown)radius*=.96f;
        c.drawCircle(cx,cy,radius,fill);c.drawCircle(cx,cy,radius-1.5f*density,inner);c.drawCircle(cx,cy,radius,border);
        float rr=radius+7*density;ring.set(cx-rr,cy-rr,cx+rr,cy+rr);c.drawArc(ring,-90,359.9f,false,track);
        if(progress>.002f){float sweep=360f*progress;c.drawArc(ring,-90,sweep,false,glow);c.drawArc(ring,-90,sweep,false,arc);}
        drawGlyph(c,cx,cy,radius*.78f);
    }

    private void drawGlyph(Canvas c,float cx,float cy,float s){Path p=new Path();switch(glyph){
        case PLAY:{icon.setStyle(Paint.Style.FILL);p.moveTo(cx-s*.18f,cy-s*.28f);p.lineTo(cx+s*.27f,cy);p.lineTo(cx-s*.18f,cy+s*.28f);p.close();c.drawPath(p,icon);icon.setStyle(Paint.Style.STROKE);break;}
        case PAUSE:{icon.setStrokeWidth(4*density);c.drawLine(cx-s*.13f,cy-s*.27f,cx-s*.13f,cy+s*.27f,icon);c.drawLine(cx+s*.13f,cy-s*.27f,cx+s*.13f,cy+s*.27f,icon);icon.setStrokeWidth(2.4f*density);break;}
        case STOP:{icon.setStyle(Paint.Style.FILL);float q=s*.24f;RectF stop=new RectF(cx-q,cy-q,cx+q,cy+q);c.drawRoundRect(stop,s*.07f,s*.07f,icon);icon.setStyle(Paint.Style.STROKE);break;}
        case PREVIOUS:{c.drawLine(cx-s*.23f,cy-s*.25f,cx-s*.23f,cy+s*.25f,icon);p.moveTo(cx+s*.20f,cy-s*.28f);p.lineTo(cx-s*.12f,cy);p.lineTo(cx+s*.20f,cy+s*.28f);c.drawPath(p,icon);break;}
        case NEXT:{c.drawLine(cx+s*.23f,cy-s*.25f,cx+s*.23f,cy+s*.25f,icon);p.moveTo(cx-s*.20f,cy-s*.28f);p.lineTo(cx+s*.12f,cy);p.lineTo(cx-s*.20f,cy+s*.28f);c.drawPath(p,icon);break;}
        case RECORD:{float bodyW=s*.22f,bodyH=s*.38f;RectF mic=new RectF(cx-bodyW,cy-bodyH,cx+bodyW,cy+s*.08f);c.drawRoundRect(mic,bodyW,bodyW,icon);p.moveTo(cx-s*.36f,cy);p.cubicTo(cx-s*.34f,cy+s*.32f,cx+s*.34f,cy+s*.32f,cx+s*.36f,cy);c.drawPath(p,icon);c.drawLine(cx,cy+s*.31f,cx,cy+s*.48f,icon);c.drawLine(cx-s*.18f,cy+s*.48f,cx+s*.18f,cy+s*.48f,icon);break;}
    }}

    @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled())return false;switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:pressedDown=true;invalidate();return true;
        case MotionEvent.ACTION_CANCEL:pressedDown=false;invalidate();return true;
        case MotionEvent.ACTION_UP:pressedDown=false;invalidate();if(e.getX()>=0&&e.getX()<=getWidth()&&e.getY()>=0&&e.getY()<=getHeight())performClick();return true;
    }return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
