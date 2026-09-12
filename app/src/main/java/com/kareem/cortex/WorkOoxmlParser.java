package com.kareem.cortex;

import android.content.Context;
import android.net.Uri;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Streaming OOXML parser. Originals stay in place; the content URI is opened directly. */
public final class WorkOoxmlParser {
    public static final String VERSION="work_ooxml_parser_001";
    private WorkOoxmlParser(){}

    public static WorkParsedDocument parse(Context context,Uri uri,String ext)throws Exception{
        if("xlsx".equals(ext))return parseXlsx(context,uri);
        if("docx".equals(ext))return parseDocx(context,uri);
        if("pptx".equals(ext))return parsePptx(context,uri);
        throw new UnsupportedOperationException(ext);
    }

    private static WorkParsedDocument parseXlsx(Context c,Uri uri)throws Exception{
        WorkParsedDocument out=new WorkParsedDocument();out.parserVersion=VERSION;
        ArrayList<String> shared=new ArrayList<>();
        HashMap<String,String> relTarget=new HashMap<>(), ridName=new HashMap<>(), targetName=new HashMap<>();

        visit(c,uri,(name,in)->{
            if("xl/sharedStrings.xml".equals(name))parseSharedStrings(in,shared);
            else if("xl/workbook.xml".equals(name))parseWorkbook(in,ridName);
            else if("xl/_rels/workbook.xml.rels".equals(name))parseWorkbookRels(in,relTarget);
        });
        for(Map.Entry<String,String> e:ridName.entrySet()){
            String target=relTarget.get(e.getKey());if(target==null)continue;
            String normalized=target.startsWith("/")?target.substring(1):(target.startsWith("xl/")?target:"xl/"+target.replace("../",""));
            targetName.put(normalized,e.getValue());
        }

        visit(c,uri,(name,in)->{
            if(name.startsWith("xl/worksheets/")&&name.endsWith(".xml")){
                String sheet=targetName.get(name);if(sheet==null||sheet.isEmpty())sheet=sheetFallback(name);
                parseSheet(in,sheet,shared,out);
            }
        });
        return out;
    }

    private static WorkParsedDocument parseDocx(Context c,Uri uri)throws Exception{
        WorkParsedDocument out=new WorkParsedDocument();out.parserVersion=VERSION;
        visit(c,uri,(name,in)->{if("word/document.xml".equals(name))parseWordDocument(in,out);});
        return out;
    }

    private static WorkParsedDocument parsePptx(Context c,Uri uri)throws Exception{
        WorkParsedDocument out=new WorkParsedDocument();out.parserVersion=VERSION;
        visit(c,uri,(name,in)->{
            if(name.startsWith("ppt/slides/slide")&&name.endsWith(".xml"))parseSlide(in,slideNumber(name),out);
        });
        return out;
    }

    private static void parseSharedStrings(InputStream in,ArrayList<String> out)throws Exception{
        XmlPullParser p=parser(in);StringBuilder cur=null;
        for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next()){
            String n=p.getName();
            if(ev==XmlPullParser.START_TAG&&"si".equals(n))cur=new StringBuilder();
            else if(ev==XmlPullParser.START_TAG&&"t".equals(n)&&cur!=null){String t=p.nextText();if(!t.isEmpty()){if(cur.length()>0)cur.append(' ');cur.append(t);}}
            else if(ev==XmlPullParser.END_TAG&&"si".equals(n)&&cur!=null){out.add(clean(cur.toString()));cur=null;}
        }
    }

    private static void parseWorkbook(InputStream in,Map<String,String> ridName)throws Exception{
        XmlPullParser p=parser(in);
        for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next())if(ev==XmlPullParser.START_TAG&&"sheet".equals(p.getName())){
            String name=attr(p,"name"),rid=attrAny(p,"id");if(!rid.isEmpty())ridName.put(rid,name);
        }
    }

    private static void parseWorkbookRels(InputStream in,Map<String,String> relTarget)throws Exception{
        XmlPullParser p=parser(in);
        for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next())if(ev==XmlPullParser.START_TAG&&"Relationship".equals(p.getName())){
            String id=attr(p,"Id"),target=attr(p,"Target");if(!id.isEmpty()&&!target.isEmpty())relTarget.put(id,target);
        }
    }

    private static void parseSheet(InputStream in,String sheet,List<String> shared,WorkParsedDocument out)throws Exception{
        XmlPullParser p=parser(in);WorkParsedDocument.Block row=null;String ref="",type="",formula="",value="";int rowNum=0;
        for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next()){
            String n=p.getName();
            if(ev==XmlPullParser.START_TAG&&"row".equals(n)){
                row=new WorkParsedDocument.Block();row.kind="TABLE_ROW";row.sheetName=sheet;rowNum=intVal(attr(p,"r"),rowNum+1);row.rowNumber=rowNum;
            }else if(ev==XmlPullParser.START_TAG&&"c".equals(n)){ref=attr(p,"r");type=attr(p,"t");formula="";value="";
            }else if(ev==XmlPullParser.START_TAG&&"f".equals(n)){formula=p.nextText();
            }else if(ev==XmlPullParser.START_TAG&&("v".equals(n)||("t".equals(n)&&"inlineStr".equals(type)))){value=p.nextText();
            }else if(ev==XmlPullParser.END_TAG&&"c".equals(n)&&row!=null){
                String col=column(ref);String resolved=value;
                if("s".equals(type)){int i=intVal(value,-1);resolved=i>=0&&i<shared.size()?shared.get(i):value;}
                if(!resolved.isEmpty())row.cells.put(col,resolved);if(!formula.isEmpty())row.formulas.put(col,formula);
            }else if(ev==XmlPullParser.END_TAG&&"row".equals(n)&&row!=null){
                StringBuilder text=new StringBuilder();for(Map.Entry<String,String> e:row.cells.entrySet()){if(text.length()>0)text.append(" | ");text.append(e.getKey()).append('=').append(e.getValue());}
                row.text=text.toString();if(!row.cells.isEmpty())out.blocks.add(row);row=null;
            }
        }
    }

    private static void parseWordDocument(InputStream in,WorkParsedDocument out)throws Exception{
        XmlPullParser p=parser(in);StringBuilder para=new StringBuilder(),cell=new StringBuilder();ArrayList<String> rowCells=new ArrayList<>();boolean inRow=false,inCell=false;
        for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next()){
            String n=p.getName();
            if(ev==XmlPullParser.START_TAG&&"tr".equals(n)){inRow=true;rowCells.clear();}
            else if(ev==XmlPullParser.START_TAG&&"tc".equals(n)){inCell=true;cell.setLength(0);}
            else if(ev==XmlPullParser.START_TAG&&"p".equals(n)&&!inRow)para.setLength(0);
            else if(ev==XmlPullParser.START_TAG&&"t".equals(n)){
                String t=p.nextText();if(inCell){if(cell.length()>0)cell.append(' ');cell.append(t);}else if(!inRow){if(para.length()>0)para.append(' ');para.append(t);}
            }else if(ev==XmlPullParser.END_TAG&&"tc".equals(n)){rowCells.add(clean(cell.toString()));inCell=false;}
            else if(ev==XmlPullParser.END_TAG&&"tr".equals(n)){
                WorkParsedDocument.Block b=new WorkParsedDocument.Block();b.kind="TABLE_ROW";b.rowNumber=out.blocks.size()+1;
                StringBuilder text=new StringBuilder();for(int i=0;i<rowCells.size();i++){String v=rowCells.get(i);if(v.isEmpty())continue;b.cells.put("C"+(i+1),v);if(text.length()>0)text.append(" | ");text.append(v);}b.text=text.toString();if(!b.cells.isEmpty())out.blocks.add(b);inRow=false;
            }else if(ev==XmlPullParser.END_TAG&&"p".equals(n)&&!inRow){String t=clean(para.toString());if(!t.isEmpty())out.blocks.add(new WorkParsedDocument.Block("PARAGRAPH",t));}
        }
    }

    private static void parseSlide(InputStream in,int slide,WorkParsedDocument out)throws Exception{
        XmlPullParser p=parser(in);StringBuilder s=new StringBuilder();
        for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next())if(ev==XmlPullParser.START_TAG&&"t".equals(p.getName())){String t=p.nextText();if(!t.isEmpty()){if(s.length()>0)s.append("\n");s.append(t);}}
        String text=cleanLines(s.toString());if(!text.isEmpty()){WorkParsedDocument.Block b=new WorkParsedDocument.Block("SLIDE",text);b.slideNumber=slide;out.blocks.add(b);}
    }

    private interface EntryVisitor{void visit(String name,InputStream in)throws Exception;}
    private static void visit(Context c,Uri uri,EntryVisitor visitor)throws Exception{
        try(InputStream raw=c.getContentResolver().openInputStream(uri);ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw))){
            ZipEntry e;while((e=zip.getNextEntry())!=null){if(!e.isDirectory())visitor.visit(e.getName(),zip);zip.closeEntry();}
        }
    }
    private static XmlPullParser parser(InputStream in)throws Exception{XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(in,"UTF-8");return p;}
    private static String attr(XmlPullParser p,String name){String v=p.getAttributeValue(null,name);return v==null?"":v;}
    private static String attrAny(XmlPullParser p,String local){for(int i=0;i<p.getAttributeCount();i++)if(local.equals(p.getAttributeName(i)))return p.getAttributeValue(i);return "";}
    private static int intVal(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    private static String column(String ref){Matcher m=Pattern.compile("([A-Z]+)").matcher(ref==null?"":ref.toUpperCase(Locale.ROOT));return m.find()?m.group(1):ref;}
    private static int slideNumber(String n){Matcher m=Pattern.compile("slide(\\d+)\\.xml$").matcher(n);return m.find()?intVal(m.group(1),0):0;}
    private static String sheetFallback(String n){Matcher m=Pattern.compile("sheet(\\d+)\\.xml$").matcher(n);return m.find()?"Sheet "+m.group(1):"Sheet";}
    private static String clean(String s){return s==null?"":s.replaceAll("\\s+"," ").trim();}
    private static String cleanLines(String s){return s==null?"":s.replaceAll("[ \\t]+"," ").replaceAll("\\n{3,}","\\n\\n").trim();}
}
