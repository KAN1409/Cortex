package com.kareem.cortex;

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal dependency-free XLSX writer for deterministic table documents. */
public final class WorkLocalXlsxGenerator {
    public static final String VERSION="work_local_xlsx_generator_001";
    private WorkLocalXlsxGenerator(){}

    public static final class Generated {
        public final File file;
        public final Uri contentUri;
        public final int rows;
        Generated(File file,Uri uri,int rows){this.file=file;this.contentUri=uri;this.rows=rows;}
    }

    public static Generated generate(Context context,WorkDocumentRecipe.Kind kind,JSONObject payload) throws Exception {
        if(context==null||payload==null)throw new IllegalArgumentException("Missing generation input");
        List<List<Cell>> table=table(kind,payload);
        if(table.size()<2)throw new IllegalStateException("No grounded rows available for local XLSX generation");
        File dir=new File(context.getFilesDir(),"generated_documents");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create generated document directory");
        String name="cortex_"+kind.name().toLowerCase(Locale.ROOT)+"_"+System.currentTimeMillis()+".xlsx";
        File out=new File(dir,name);
        writeWorkbook(out,WorkDocumentRecipe.displayName(kind),table);
        Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".feedback.files",out);
        return new Generated(out,uri,table.size()-1);
    }

    private static List<List<Cell>> table(WorkDocumentRecipe.Kind kind,JSONObject payload){
        ArrayList<List<Cell>> rows=new ArrayList<>();
        rows.add(header("Project","Item","Vendor","Quantity","Unit","Unit Price","Total Price","Currency","Reference Type","Reference","Source File","Sheet","Row"));
        JSONArray prices=payload.optJSONArray("priceRecords");
        if(prices==null)return rows;
        for(int i=0;i<prices.length();i++){
            JSONObject p=prices.optJSONObject(i);if(p==null)continue;
            ArrayList<Cell> r=new ArrayList<>();
            r.add(s(p.optString("project","")));r.add(s(p.optString("item","")));r.add(s(p.optString("vendor","")));
            r.add(n(p,"quantity"));r.add(s(p.optString("unit","")));r.add(n(p,"unitPrice"));r.add(n(p,"totalPrice"));
            r.add(s(p.optString("currency","")));r.add(s(p.optString("referenceType","")));r.add(s(p.optString("referenceValue","")));
            r.add(s(p.optString("file","")));r.add(s(p.optString("sheet","")));r.add(n(p,"row"));rows.add(r);
        }
        return rows;
    }

    private static ArrayList<Cell> header(String...xs){ArrayList<Cell> r=new ArrayList<>();for(String x:xs)r.add(s(x));return r;}
    private static Cell s(String x){return new Cell(false,x==null?"":x);}
    private static Cell n(JSONObject o,String key){return o.has(key)&&!o.isNull(key)?new Cell(true,String.valueOf(o.optDouble(key,0))):s("");}
    private static final class Cell{final boolean number;final String value;Cell(boolean n,String v){number=n;value=v;}}

    private static void writeWorkbook(File file,String sheetName,List<List<Cell>> rows) throws Exception {
        try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))){
            put(z,"[Content_Types].xml",contentTypes());
            put(z,"_rels/.rels",rootRels());
            put(z,"docProps/app.xml",appProps());
            put(z,"docProps/core.xml",coreProps());
            put(z,"xl/workbook.xml",workbook(sheetName));
            put(z,"xl/_rels/workbook.xml.rels",workbookRels());
            put(z,"xl/styles.xml",styles());
            put(z,"xl/worksheets/sheet1.xml",sheet(rows));
        }
    }

    private static void put(ZipOutputStream z,String path,String text) throws Exception {z.putNextEntry(new ZipEntry(path));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static String contentTypes(){return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/><Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/><Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/></Types>";}
    private static String rootRels(){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/></Relationships>";}
    private static String workbookRels(){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>";}
    private static String workbook(String name){return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\""+xml(trimSheet(name))+"\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";}
    private static String styles(){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font/><font><b/></font></fonts><fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs></styleSheet>";}
    private static String sheet(List<List<Cell>> rows){StringBuilder b=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");for(int r=0;r<rows.size();r++){int rn=r+1;b.append("<row r=\"").append(rn).append("\">");List<Cell> row=rows.get(r);for(int c=0;c<row.size();c++){Cell cell=row.get(c);String ref=col(c+1)+rn;if(cell.number&&cell.value!=null&&!cell.value.isEmpty()){b.append("<c r=\"").append(ref).append("\"><v>").append(xml(cell.value)).append("</v></c>");}else{b.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(r==0?" s=\"1\"":"").append("><is><t xml:space=\"preserve\">").append(xml(cell.value)).append("</t></is></c>");}}b.append("</row>");}return b.append("</sheetData></worksheet>").toString();}
    private static String appProps(){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\"><Application>Cortex Work Vault</Application></Properties>";}
    private static String coreProps(){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:creator>Cortex Work Vault</dc:creator><dc:title>Generated work document</dc:title></cp:coreProperties>";}
    private static String col(int n){StringBuilder s=new StringBuilder();while(n>0){n--;s.insert(0,(char)('A'+n%26));n/=26;}return s.toString();}
    private static String trimSheet(String x){String s=x==null?"Sheet1":x.replaceAll("[\\\\/:*?\\[\\]]"," ").trim();return s.isEmpty()?"Sheet1":s.substring(0,Math.min(31,s.length()));}
    private static String xml(String x){if(x==null)return "";return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
}
