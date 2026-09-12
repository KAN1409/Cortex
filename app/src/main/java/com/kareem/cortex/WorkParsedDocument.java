package com.kareem.cortex;

import java.util.*;

/** Format-neutral parsed work document with source location preserved for every block. */
public final class WorkParsedDocument {
    public final ArrayList<Block> blocks=new ArrayList<>();
    public boolean needsOcr=false;
    public String parserVersion="";

    public static final class Block {
        public String kind="TEXT";
        public String text="";
        public String sheetName="";
        public int pageNumber=0;
        public int slideNumber=0;
        public int rowNumber=0;
        public final LinkedHashMap<String,String> cells=new LinkedHashMap<>();
        public final LinkedHashMap<String,String> formulas=new LinkedHashMap<>();

        public Block(){}
        public Block(String kind,String text){this.kind=kind==null?"TEXT":kind;this.text=text==null?"":text;}
    }
}
