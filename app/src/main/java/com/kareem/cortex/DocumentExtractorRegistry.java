package com.kareem.cortex;

import android.content.Context;
import android.graphics.Bitmap;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Bounded local-first extraction for professional documents imported into Cortex.
 * Originals are never mutated. Extracted text is derived evidence only.
 */
public final class DocumentExtractorRegistry {
    private static final long MAX_FILE_BYTES=100L*1024L*1024L;
    private static final long MAX_ZIP_TOTAL=320L*1024L*1024L;
    private static final long MAX_ENTRY_BYTES=24L*1024L*1024L;
    private static final int MAX_ZIP_ENTRIES=20000;
    private static final int MAX_TEXT_CHARS=500000;
    private static final int MAX_PDF_PAGES=300;
    private static final int MAX_OCR_PAGES=12;

    private DocumentExtractorRegistry(){}

    public static boolean supports(String name){
        String x=lower(name);
        return x.endsWith(".pdf")||x.endsWith(".xlsx")||x.endsWith(".docx")||x.endsWith(".pptx");
    }

    public static AnalysisResult extract(Context ctx,File file,String displayName)throws Exception{
        if(file==null||!file.isFile())throw new FileNotFoundException("Attachment missing");
        if(file.length()>MAX_FILE_BYTES)throw new IOException("Document exceeds safe local extraction size");
        String n=displayName==null||displayName.trim().isEmpty()?file.getName():displayName.trim();
        String low=lower(n);
        String text;
        String engine;
        if(low.endsWith(".pdf")){text=extractPdf(ctx,file);engine="document_pdfbox_ocr";}
        else if(low.endsWith(".xlsx")){text=extractXlsx(file);engine="document_ooxml_xlsx";}
        else if(low.endsWith(".docx")){text=extractDocx(file);engine="document_ooxml_docx";}
        else if(low.endsWith(".pptx")){text=extractPptx(file);engine="document_ooxml_pptx";}
        else throw new UnsupportedOperationException("Unsupported document type");
        text=normalize(text);
        if(text.isEmpty())throw new IOException("Document contained no extractable text");
        AnalysisResult r=TabularAnalyzer.looksTabular(text)?TabularAnalyzer.analyze(text):LocalAnalyzer.analyze(text,"text/plain");
        if(r==null)r=LocalAnalyzer.analyze(text,"text/plain");
        if(r==null)r=new AnalysisResult();
        r.title=n;r.extractedText=clip(text,MAX_TEXT_CHARS);r.engine=engine;r.version="document_intelligence_001";
        if(r.summary==null||r.summary.trim().isEmpty())r.summary=summaryFor(low,text);
        if(r.category==null||r.category.trim().isEmpty()||"Notes".equals(r.category))r.category=categoryFor(low);
        r.tags=appendTag(r.tags,"document");
        if(low.endsWith(".xlsx"))r.tags=appendTag(r.tags,"spreadsheet");
        else if(low.endsWith(".pdf"))r.tags=appendTag(r.tags,"pdf");
        else if(low.endsWith(".docx"))r.tags=appendTag(r.tags,"word");
        else if(low.endsWith(".pptx"))r.tags=appendTag(r.tags,"presentation");
        return r;
    }

    private static String extractPdf(Context ctx,File file)throws Exception{
        if(ctx!=null)PDFBoxResourceLoader.init(ctx.getApplicationContext());
        try(PDDocument doc=PDDocument.load(file)){
            if(doc.isEncrypted())throw new IOException("Password-protected PDF requires user unlock before extraction");
            int pages=doc.getNumberOfPages();if(pages>MAX_PDF_PAGES)throw new IOException("PDF exceeds safe page limit");
            PDFTextStripper s=new PDFTextStripper();s.setSortByPosition(true);String text=clip(s.getText(doc),MAX_TEXT_CHARS);
            if(useful(text))return text;
            if(ctx==null)return text;
            return ocrPdf(ctx,doc,pages);
        }
    }

    private static String ocrPdf(Context ctx,PDDocument doc,int pages)throws Exception{
        StringBuilder out=new StringBuilder();PDFRenderer renderer=new PDFRenderer(doc);int limit=Math.min(pages,MAX_OCR_PAGES);
        for(int i=0;i<limit&&out.length()<MAX_TEXT_CHARS;i++){
            Bitmap bm=null;File tmp=null;TextRecognizer latinRec=null;
            try{
                bm=renderer.renderImageWithDPI(i,135,ImageType.RGB);
                String latin="";
                try{
                    latinRec=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                    latin=Tasks.await(latinRec.process(InputImage.fromBitmap(bm,0)),25,TimeUnit.SECONDS).getText();
                }catch(Throwable ignored){}finally{try{if(latinRec!=null)latinRec.close();}catch(Throwable ignored){}}
                tmp=new File(ctx.getCacheDir(),"pdf_ocr_"+System.nanoTime()+".png");
                try(FileOutputStream fos=new FileOutputStream(tmp)){bm.compress(Bitmap.CompressFormat.PNG,92,fos);}
                String arabic=arabicBlocking(ctx,tmp,latin);
                String merged=mergeOcr(latin,arabic);
                if(!merged.trim().isEmpty())out.append("\n--- Page ").append(i+1).append(" ---\n").append(merged.trim()).append('\n');
            }finally{
                try{if(bm!=null&&!bm.isRecycled())bm.recycle();}catch(Throwable ignored){}
                try{if(tmp!=null)tmp.delete();}catch(Throwable ignored){}
            }
        }
        return clip(out.toString(),MAX_TEXT_CHARS);
    }

    private static String arabicBlocking(Context ctx,File image,String latin){
        if(ctx==null||image==null)return"";
        final String[] result={""};CountDownLatch latch=new CountDownLatch(1);
        try{
            ArabicOcr.recognizeDetailed(ctx,image,latin,r->{if(r!=null&&r.accepted)result[0]=r.text;latch.countDown();});
            latch.await(35,TimeUnit.SECONDS);
        }catch(Throwable ignored){}
        return result[0]==null?"":result[0];
    }

    private static String mergeOcr(String a,String b){String x=normalize(a),y=normalize(b);if(x.isEmpty())return y;if(y.isEmpty())return x;if(x.contains(y))return x;if(y.contains(x))return y;return x+"\n"+y;}

    private static String extractDocx(File f)throws Exception{
        try(ZipFile z=openSafeZip(f)){return textFromXmlEntry(z,"word/document.xml",new HashSet<>(Arrays.asList("t","tab","br","p","tr")),true);}
    }

    private static String extractPptx(File f)throws Exception{
        try(ZipFile z=openSafeZip(f)){
            ArrayList<String> slides=new ArrayList<>();Enumeration<? extends ZipEntry> en=z.entries();
            while(en.hasMoreElements()){String n=en.nextElement().getName();if(n.matches("ppt/slides/slide[0-9]+\\.xml"))slides.add(n);}
            slides.sort(Comparator.comparingInt(DocumentExtractorRegistry::numericSuffix));StringBuilder b=new StringBuilder();
            for(String n:slides){b.append("\n--- Slide ").append(numericSuffix(n)).append(" ---\n");b.append(textFromXmlEntry(z,n,new HashSet<>(Arrays.asList("t","br")),false));if(b.length()>MAX_TEXT_CHARS)break;}
            return clip(b.toString(),MAX_TEXT_CHARS);
        }
    }

    private static String extractXlsx(File f)throws Exception{
        try(ZipFile z=openSafeZip(f)){
            ArrayList<String> shared=sharedStrings(z);Map<String,String> sheetNames=sheetNames(z);ArrayList<String> sheets=new ArrayList<>();Enumeration<? extends ZipEntry> en=z.entries();
            while(en.hasMoreElements()){String n=en.nextElement().getName();if(n.matches("xl/worksheets/sheet[0-9]+\\.xml"))sheets.add(n);}
            sheets.sort(Comparator.comparingInt(DocumentExtractorRegistry::numericSuffix));StringBuilder out=new StringBuilder();
            for(String path:sheets){int num=numericSuffix(path);String label=sheetNames.getOrDefault(String.valueOf(num),"Sheet "+num);out.append("\n### ").append(label).append("\n");appendWorksheet(z,path,shared,out);if(out.length()>MAX_TEXT_CHARS)break;}
            return clip(out.toString(),MAX_TEXT_CHARS);
        }
    }

    private static void appendWorksheet(ZipFile z,String path,List<String> shared,StringBuilder out)throws Exception{
        Document d=xml(readEntry(z,path));NodeList rows=d.getElementsByTagName("row");
        for(int i=0;i<rows.getLength()&&out.length()<MAX_TEXT_CHARS;i++){
            Element row=(Element)rows.item(i);NodeList cells=row.getElementsByTagName("c");boolean first=true;
            for(int j=0;j<cells.getLength();j++){
                Element c=(Element)cells.item(j);String type=c.getAttribute("t");String v=firstText(c,"v");String value="";
                if("s".equals(type)){try{int idx=Integer.parseInt(v);if(idx>=0&&idx<shared.size())value=shared.get(idx);}catch(Exception ignored){}}
                else if("inlineStr".equals(type))value=allText(c,"t");else value=v;
                if(!first)out.append('\t');out.append(safeCell(value));first=false;
            }
            out.append('\n');
        }
    }

    private static ArrayList<String> sharedStrings(ZipFile z)throws Exception{
        ArrayList<String> out=new ArrayList<>();ZipEntry e=z.getEntry("xl/sharedStrings.xml");if(e==null)return out;Document d=xml(readEntry(z,e.getName()));NodeList si=d.getElementsByTagName("si");for(int i=0;i<si.getLength();i++)out.add(allText((Element)si.item(i),"t"));return out;
    }

    private static Map<String,String> sheetNames(ZipFile z){
        LinkedHashMap<String,String> out=new LinkedHashMap<>();try{ZipEntry e=z.getEntry("xl/workbook.xml");if(e==null)return out;Document d=xml(readEntry(z,e.getName()));NodeList ns=d.getElementsByTagName("sheet");for(int i=0;i<ns.getLength();i++){Element s=(Element)ns.item(i);String id=s.getAttribute("sheetId"),name=s.getAttribute("name");if(!id.isEmpty()&&!name.isEmpty())out.put(id,name);}}catch(Throwable ignored){}return out;
    }

    private static ZipFile openSafeZip(File f)throws Exception{
        ZipFile z=new ZipFile(f);long total=0;int count=0;Enumeration<? extends ZipEntry> en=z.entries();while(en.hasMoreElements()){ZipEntry e=en.nextElement();count++;if(count>MAX_ZIP_ENTRIES){z.close();throw new IOException("Document has too many package entries");}long n=e.getSize();if(n>MAX_ENTRY_BYTES){z.close();throw new IOException("Document package entry exceeds safe size");}if(n>0){total+=n;if(total>MAX_ZIP_TOTAL){z.close();throw new IOException("Document expanded size exceeds safe limit");}}}return z;
    }

    private static byte[] readEntry(ZipFile z,String name)throws Exception{
        ZipEntry e=z.getEntry(name);if(e==null)return new byte[0];long declared=e.getSize();if(declared>MAX_ENTRY_BYTES)throw new IOException("Document XML entry too large");try(InputStream in=z.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[32768];int n;long total=0;while((n=in.read(buf))!=-1){total+=n;if(total>MAX_ENTRY_BYTES)throw new IOException("Document XML entry too large");out.write(buf,0,n);}return out.toByteArray();}
    }

    private static Document xml(byte[] bytes)throws Exception{
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(false);f.setExpandEntityReferences(false);try{f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(Throwable ignored){}try{f.setFeature("http://xml.org/sax/features/external-general-entities",false);}catch(Throwable ignored){}try{f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}catch(Throwable ignored){}return f.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(bytes)));
    }

    private static String textFromXmlEntry(ZipFile z,String path,Set<String> tokens,boolean paragraphBreaks)throws Exception{
        ZipEntry e=z.getEntry(path);if(e==null)return"";Document d=xml(readEntry(z,path));StringBuilder b=new StringBuilder();walk(d.getDocumentElement(),tokens,paragraphBreaks,b);return clip(b.toString(),MAX_TEXT_CHARS);
    }
    private static void walk(Node n,Set<String> tokens,boolean paragraphBreaks,StringBuilder b){if(n==null||b.length()>=MAX_TEXT_CHARS)return;String name=stripPrefix(n.getNodeName());if(n.getNodeType()==Node.ELEMENT_NODE){if("t".equals(name)){b.append(n.getTextContent());b.append(' ');return;}if("tab".equals(name))b.append('\t');if("br".equals(name))b.append('\n');}Node c=n.getFirstChild();while(c!=null){walk(c,tokens,paragraphBreaks,b);c=c.getNextSibling();}if(paragraphBreaks&&("p".equals(name)||"tr".equals(name)))b.append('\n');else if("t".equals(name))b.append(' ');}

    private static String allText(Element e,String tag){NodeList n=e.getElementsByTagName(tag);StringBuilder b=new StringBuilder();for(int i=0;i<n.getLength();i++){if(b.length()>0)b.append(' ');b.append(n.item(i).getTextContent());}return normalize(b.toString());}
    private static String firstText(Element e,String tag){NodeList n=e.getElementsByTagName(tag);return n.getLength()==0?"":normalize(n.item(0).getTextContent());}
    private static String stripPrefix(String n){int i=n==null?-1:n.indexOf(':');return i>=0?n.substring(i+1):n;}
    private static int numericSuffix(String path){String s=path==null?"":path.replaceAll(".*?(\\d+)\\.xml$","$1");try{return Integer.parseInt(s);}catch(Exception e){return Integer.MAX_VALUE;}}
    private static boolean useful(String s){String x=normalize(s);int letters=0;for(int i=0;i<x.length();i++)if(Character.isLetterOrDigit(x.charAt(i)))letters++;return letters>=40;}
    private static String safeCell(String s){return normalize(s).replace('\t',' ').replace('\n',' ');}
    private static String normalize(String s){if(s==null)return"";return s.replace('\u0000',' ').replace("\r\n","\n").replace('\r','\n').replaceAll("[ \\t]+"," ").replaceAll("\\n{3,}","\n\n").trim();}
    private static String summaryFor(String low,String text){if(low.endsWith(".xlsx"))return"Spreadsheet content extracted locally and indexed as structured evidence.";if(low.endsWith(".pdf"))return"PDF content extracted locally and indexed as inspectable evidence.";if(low.endsWith(".docx"))return"Word document content extracted locally and indexed as evidence.";return"Presentation content extracted locally and indexed as evidence.";}
    private static String categoryFor(String low){return low.endsWith(".xlsx")?"Spreadsheets":low.endsWith(".pdf")?"PDF documents":low.endsWith(".docx")?"Documents":"Presentations";}
    private static String appendTag(String tags,String tag){String t=tags==null?"":tags.trim();if(t.isEmpty())return tag;if(Arrays.asList(t.split("\\s*,\\s*")).contains(tag))return t;return t+","+tag;}
    private static String clip(String s,int n){String x=s==null?"":s;return x.length()<=n?x:x.substring(0,n);}
    private static String lower(String s){return s==null?"":s.toLowerCase(Locale.ROOT);}
}
