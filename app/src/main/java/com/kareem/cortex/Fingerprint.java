package com.kareem.cortex;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Fingerprint {
    private Fingerprint(){}

    public static String text(String value){
        String normalized=value==null?"":value.trim().replaceAll("\\s+"," ").toLowerCase();
        return sha(normalized.getBytes(StandardCharsets.UTF_8));
    }

    public static String file(String path){
        if(path==null || path.isEmpty()) return "";
        try(InputStream in=new FileInputStream(path)){
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] b=new byte[16384]; int n;
            while((n=in.read(b))!=-1) md.update(b,0,n);
            return hex(md.digest());
        }catch(Exception e){ return ""; }
    }

    private static String sha(byte[] bytes){
        try{ MessageDigest md=MessageDigest.getInstance("SHA-256"); return hex(md.digest(bytes)); }
        catch(Exception e){ return ""; }
    }
    private static String hex(byte[] b){
        StringBuilder s=new StringBuilder();
        for(byte x:b) s.append(String.format("%02x",x));
        return s.toString();
    }
}
