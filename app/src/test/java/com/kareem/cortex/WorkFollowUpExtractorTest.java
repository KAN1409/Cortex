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

    @Test public void normalizesArabicStatus(){
        assertEquals("approved",WorkFollowUpExtractor.normalizeStatus("تم الاعتماد"));
        assertEquals("rejected",WorkFollowUpExtractor.normalizeStatus("مرفوض"));
        assertEquals("open",WorkFollowUpExtractor.normalizeStatus("تحت المراجعة"));
        assertEquals("on_hold",WorkFollowUpExtractor.normalizeStatus("معلق"));
    }
}
