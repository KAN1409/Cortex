package com.kareem.cortex;

import java.util.Locale;

/** Conservative policy for user-selected direct ChatGPT reference documents. */
public final class WorkReferenceFilePolicy {
    public static final String VERSION="work_reference_file_policy_001";
    public static final long LARGE_FILE_WARNING_BYTES=50L*1024L*1024L;
    private WorkReferenceFilePolicy(){}

    public static boolean isSupportedMime(String mime){
        if(mime==null)return false;
        String m=mime.trim().toLowerCase(Locale.ROOT);
        return m.equals("application/pdf") ||
                m.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                m.equals("application/msword") ||
                m.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
                m.equals("application/vnd.ms-excel") ||
                m.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation") ||
                m.equals("application/vnd.ms-powerpoint");
    }

    public static boolean shouldWarnForSize(long bytes){return bytes>LARGE_FILE_WARNING_BYTES;}

    public static String shortType(String mime){
        if(mime==null)return "UNKNOWN";
        String m=mime.toLowerCase(Locale.ROOT);
        if(m.equals("application/pdf"))return "PDF";
        if(m.contains("wordprocessingml")||m.equals("application/msword"))return "WORD";
        if(m.contains("spreadsheetml")||m.equals("application/vnd.ms-excel"))return "EXCEL";
        if(m.contains("presentationml")||m.equals("application/vnd.ms-powerpoint"))return "POWERPOINT";
        return "UNKNOWN";
    }

    public static String formatBytes(long bytes){
        if(bytes<0)return "size unknown";
        if(bytes<1024)return bytes+" B";
        double kb=bytes/1024.0;
        if(kb<1024)return String.format(Locale.US,"%.1f KB",kb);
        double mb=kb/1024.0;
        if(mb<1024)return String.format(Locale.US,"%.1f MB",mb);
        return String.format(Locale.US,"%.2f GB",mb/1024.0);
    }
}
