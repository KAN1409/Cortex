package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkFollowUpExtractorTest {
    @Test public void extractsProcurementFollowUpRow(){
        WorkParsedDocument d=new WorkParsedDocument();
        WorkParsedDocument.Block h=new WorkParsedDocument.Block();h.kind="TABLE_ROW";h.sheetName="Follow Up";h.rowNumber=1;
        h.cells.put("A","PR No");h.cells.put("B","Item Description");h.cells.put("C","Status");h.cells.put("D","Responsible");h.cells.put("E","Due Date");h.cells.put("F","Vendor");h.cells.put("G","Remarks");d.blocks.add(h);
        WorkParsedDocument.Block r0=new WorkParsedDocument.Block();r0.kind="TABLE_ROW";r0.sheetName="Follow Up";r0.rowNumber=2;
        r0.cells.put("A","0262");r0.cells.put("B","Reception gypsum ceilings");r0.cells.put("C","Under Review");r0.cells.put("D","Eng. Karam");r0.cells.put("E","15/09/2026");r0.cells.put("F","Life Style");r0.cells.put("G","Awaiting approval");d.blocks.add(r0);

        WorkFollowUpExtractor.Result r=WorkFollowUpExtractor.extract(d);
        assertEquals(1,r.records.size());
        WorkFollowUpExtractor.Record x=r.records.get(0);
        assertEquals("PR",x.referenceType);
        assertEquals("0262",x.referenceValue);
        assertEquals("Reception gypsum ceilings",x.item);
        assertEquals("Under Review",x.status);
        assertEquals("open",x.normalizedStatus);
        assertEquals("Eng. Karam",x.owner);
        assertEquals("15/09/2026",x.dueText);
        assertEquals("Life Style",x.vendor);
        assertEquals("Follow Up",x.source.sheetName);
        assertEquals(2,x.source.rowNumber);
    }

    @Test public void extractsPrAndPoFromSameFollowUpRow(){
        WorkParsedDocument d=new WorkParsedDocument();
        WorkParsedDocument.Block h=new WorkParsedDocument.Block();h.kind="TABLE_ROW";h.sheetName="Tracker";h.rowNumber=1;
        h.cells.put("A","PR No");h.cells.put("B","PO No");h.cells.put("C","Item");h.cells.put("D","Status");d.blocks.add(h);
        WorkParsedDocument.Block r0=new WorkParsedDocument.Block();r0.kind="TABLE_ROW";r0.sheetName="Tracker";r0.rowNumber=2;
        r0.cells.put("A","0262");r0.cells.put("B","1047");r0.cells.put("C","Gypsum ceilings");r0.cells.put("D","Issued");d.blocks.add(r0);

        WorkFollowUpExtractor.Result r=WorkFollowUpExtractor.extract(d);
        assertEquals(2,r.records.size());
        assertEquals("PR",r.records.get(0).referenceType);assertEquals("0262",r.records.get(0).referenceValue);
        assertEquals("PO",r.records.get(1).referenceType);assertEquals("1047",r.records.get(1).referenceValue);
        assertEquals("issued",r.records.get(0).normalizedStatus);assertEquals("issued",r.records.get(1).normalizedStatus);
    }

    @Test public void switchesHeaderPatternWithinSameSheet(){
        WorkParsedDocument d=new WorkParsedDocument();
        WorkParsedDocument.Block h1=new WorkParsedDocument.Block();h1.kind="TABLE_ROW";h1.sheetName="Follow Up";h1.rowNumber=1;
        h1.cells.put("A","PR No");h1.cells.put("B","Status");d.blocks.add(h1);
        WorkParsedDocument.Block r1=new WorkParsedDocument.Block();r1.kind="TABLE_ROW";r1.sheetName="Follow Up";r1.rowNumber=2;
        r1.cells.put("A","100");r1.cells.put("B","Pending");d.blocks.add(r1);
        WorkParsedDocument.Block h2=new WorkParsedDocument.Block();h2.kind="TABLE_ROW";h2.sheetName="Follow Up";h2.rowNumber=5;
        h2.cells.put("C","PO Number");h2.cells.put("D","State");d.blocks.add(h2);
        WorkParsedDocument.Block r2=new WorkParsedDocument.Block();r2.kind="TABLE_ROW";r2.sheetName="Follow Up";r2.rowNumber=6;
        r2.cells.put("C","200");r2.cells.put("D","Completed");d.blocks.add(r2);

        WorkFollowUpExtractor.Result r=WorkFollowUpExtractor.extract(d);
        assertEquals(2,r.records.size());
        assertEquals("PR",r.records.get(0).referenceType);assertEquals("100",r.records.get(0).referenceValue);
        assertEquals("PO",r.records.get(1).referenceType);assertEquals("200",r.records.get(1).referenceValue);
        assertEquals("completed",r.records.get(1).normalizedStatus);
    }

    @Test public void infersTypedGenericReference(){
        WorkParsedDocument d=new WorkParsedDocument();
        WorkParsedDocument.Block h=new WorkParsedDocument.Block();h.kind="TABLE_ROW";h.sheetName="Follow Up";h.rowNumber=1;
        h.cells.put("A","Reference No");h.cells.put("B","Status");d.blocks.add(h);
        WorkParsedDocument.Block r0=new WorkParsedDocument.Block();r0.kind="TABLE_ROW";r0.sheetName="Follow Up";r0.rowNumber=2;
        r0.cells.put("A","PO–1047");r0.cells.put("B","Open");d.blocks.add(r0);
        WorkFollowUpExtractor.Result r=WorkFollowUpExtractor.extract(d);
        assertEquals(1,r.records.size());
        assertEquals("PO",r.records.get(0).referenceType);
        assertEquals("1047",r.records.get(0).referenceValue);
    }

    @Test public void normalizesArabicStatus(){
        assertEquals("approved",WorkFollowUpExtractor.normalizeStatus("تم الاعتماد"));
        assertEquals("rejected",WorkFollowUpExtractor.normalizeStatus("مرفوض"));
        assertEquals("open",WorkFollowUpExtractor.normalizeStatus("تحت المراجعة"));
        assertEquals("on_hold",WorkFollowUpExtractor.normalizeStatus("معلق"));
    }
}
