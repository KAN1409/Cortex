package com.kareem.cortex;

import java.io.File;
import java.io.FileInputStream;

/** Minimal, architecture-aware validation for Cortex-supported whisper.cpp GGML models. */
final class WhisperGgmlModel {
    static final int HEADER_BYTES=48;
    static final int GGML_MAGIC=0x67676d6c;
    static final int Q8_0_FILE_TYPE=2007;
    static final long MIN_SUPPORTED_BYTES=250_000_000L;

    static final class Profile {
        final String id;
        final String label;
        final long minimumBytes;

        Profile(String id,String label,long minimumBytes){
            this.id=id;this.label=label;this.minimumBytes=minimumBytes;
        }
    }

    private WhisperGgmlModel(){}

    static Profile inspect(File file){
        if(file==null||!file.exists()||!file.isFile()||file.length()<HEADER_BYTES)return null;
        byte[] header=new byte[HEADER_BYTES];
        try(FileInputStream in=new FileInputStream(file)){
            int offset=0;
            while(offset<header.length){int n=in.read(header,offset,header.length-offset);if(n<0)return null;offset+=n;}
            return inspect(header,file.length());
        }catch(Exception ignored){return null;}
    }

    static Profile inspect(byte[] header,long fileBytes){
        if(header==null||header.length<HEADER_BYTES)return null;
        int magic=le32(header,0),vocab=le32(header,4),audioCtx=le32(header,8);
        int audioState=le32(header,12),audioHeads=le32(header,16),audioLayers=le32(header,20);
        int textCtx=le32(header,24),textState=le32(header,28),textHeads=le32(header,32);
        int textLayers=le32(header,36),mels=le32(header,40),fileType=le32(header,44);
        if(magic!=GGML_MAGIC||vocab!=51865||audioCtx!=1500||textCtx!=448||mels!=80||fileType!=Q8_0_FILE_TYPE)return null;

        if(audioState==768&&audioHeads==12&&audioLayers==12&&textState==768&&textHeads==12&&textLayers==12){
            Profile p=new Profile("small_q8_0","Whisper Small Q8_0",250_000_000L);
            return fileBytes>=p.minimumBytes?p:null;
        }
        if(audioState==1024&&audioHeads==16&&audioLayers==24&&textState==1024&&textHeads==16&&textLayers==24){
            Profile p=new Profile("medium_q8_0","Whisper Medium Q8_0",600_000_000L);
            return fileBytes>=p.minimumBytes?p:null;
        }
        return null;
    }

    static boolean hasGgmlMagic(byte[] header){return header!=null&&header.length>=4&&le32(header,0)==GGML_MAGIC;}

    private static int le32(byte[] bytes,int offset){
        return (bytes[offset]&0xff)|((bytes[offset+1]&0xff)<<8)|((bytes[offset+2]&0xff)<<16)|(bytes[offset+3]<<24);
    }
}
