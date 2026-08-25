package com.kareem.cortex;

import java.util.regex.Pattern;

/** Redacts likely credentials/tokens from robot test evidence before journal/report persistence. */
public final class CortexRobotReportSanitizer {
    private CortexRobotReportSanitizer(){}
    private static final Pattern OPENAI=Pattern.compile("(?i)\\b(sk|sk-proj|sk-or-v1)-[A-Za-z0-9_\\-]{12,}\\b");
    private static final Pattern GEMINI=Pattern.compile("\\bAIza[0-9A-Za-z_\\-]{20,}\\b");
    private static final Pattern BEARER=Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+\\-/=]{12,}");
    private static final Pattern KEY_VALUE=Pattern.compile("(?i)(api[_ -]?key|token|secret|authorization)(\\s*[:=]\\s*)([^\\s,;]{8,})");
    private static final Pattern LONG_HEX=Pattern.compile("\\b[0-9a-fA-F]{40,}\\b");

    public static void sanitize(CortexRobotUserTest.Step s){if(s==null)return;s.path=redact(s.path);s.screenBefore=redact(s.screenBefore);s.action=redact(s.action);s.actionClass=redact(s.actionClass);s.screenAfter=redact(s.screenAfter);s.beforeText=redact(s.beforeText);s.afterText=redact(s.afterText);s.detail=redact(s.detail);s.error=redact(s.error);}
    public static String redact(String raw){String x=raw==null?"":raw;x=OPENAI.matcher(x).replaceAll("[REDACTED_API_KEY]");x=GEMINI.matcher(x).replaceAll("[REDACTED_API_KEY]");x=BEARER.matcher(x).replaceAll("$1[REDACTED_TOKEN]");x=KEY_VALUE.matcher(x).replaceAll("$1$2[REDACTED]");x=LONG_HEX.matcher(x).replaceAll("[REDACTED_LONG_HEX]");return x;}
}
