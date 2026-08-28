package com.kareem.cortex;

public final class BrainException extends Exception {
    public final String code;
    public BrainException(String code,String message){super(message==null?"":message);this.code=code==null?"BRAIN_ERROR":code;}
    public BrainException(String code,String message,Throwable cause){super(message==null?"":message,cause);this.code=code==null?"BRAIN_ERROR":code;}
}
