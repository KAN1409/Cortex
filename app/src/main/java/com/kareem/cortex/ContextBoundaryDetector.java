package com.kareem.cortex;

/** Explicit semantic boundaries may override ordinary score hysteresis without weakening it globally. */
public final class ContextBoundaryDetector {
    public static final String INTERRUPT="BOUNDARY_INTERRUPT",RESUME="BOUNDARY_RESUME",USER_SWITCH="BOUNDARY_USER_SWITCH",USER_DONE="BOUNDARY_USER_DONE";
    private ContextBoundaryDetector(){}
    public static boolean strong(String reason){String x=reason==null?"":reason.trim();return x.startsWith(INTERRUPT)||x.startsWith(RESUME)||x.startsWith(USER_SWITCH)||x.startsWith(USER_DONE);}
    public static String interrupt(String detail){return INTERRUPT+" · "+safe(detail);}
    public static String resume(String detail){return RESUME+" · "+safe(detail);}
    private static String safe(String s){return s==null?"":s.trim();}
}
