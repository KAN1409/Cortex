package com.kareem.cortex;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * Legacy compatibility stub. Full-screen ambient color washes are intentionally disabled.
 * Reference-led energy/light is component-local (for example CortexEnergyRibbonView) so the
 * AMOLED canvas and ordinary tiles remain neutral graphite.
 */
public final class CortexAmbientOverlayView extends View {
    public CortexAmbientOverlayView(Context c){super(c);setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void start(){}
    public void stop(){}
    @Override protected void onDraw(Canvas c){super.onDraw(c);}
    public static void attach(Activity a){/* deliberately no-op */}
}
