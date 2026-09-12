package com.kareem.cortex;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkStructuredExtractorTest {
    @Test public void extractsPriceRowAndProcurementRefs(){
        WorkParsedDocument d=new WorkParsedDocument();

        WorkParsedDocument.Block intro=new WorkParsedDocument.Block("PARAGRAPH","Project: Negma   PR-0262   PO-1047");
        d.blocks.add(intro);

        WorkParsedDocument.Block header=new WorkParsedDocument.Block();
        header.kind="TABLE_ROW";header.sheetName="Comparison";header.rowNumber=1;
        header.cells.put("A","Item Description");
        header.cells.put("B","Vendor");
        header.cells.put("C","Qty");
        header.cells.put("D","Unit");
        header.cells.put("E","Unit Price");
        header.cells.put("F","Total");
        d.blocks.add(header);

        WorkParsedDocument.Block row=new WorkParsedDocument.Block();
        row.kind="TABLE_ROW";row.sheetName="Comparison";row.rowNumber=2;
        row.cells.put("A","Galala marble supply");
        row.cells.put("B","Life Style");
        row.cells.put("C","430");
        row.cells.put("D","m2");
        row.cells.put("E","2,650 EGP");
        row.cells.put("F","1,139,500 EGP");
        d.blocks.add(row);

        WorkStructuredExtractor.Result r=WorkStructuredExtractor.extract(d);
        assertEquals(1,r.projects.size());
        assertEquals("Negma",r.projects.get(0).name);
        assertEquals(2,r.refs.size());
        assertEquals(1,r.prices.size());
        WorkStructuredExtractor.Price p=r.prices.get(0);
        assertEquals("Galala marble supply",p.item);
        assertEquals("Life Style",p.vendor);
        assertEquals(430.0,p.quantity,0.001);
        assertEquals("m2",p.unit);
        assertEquals(2650.0,p.unitPrice,0.001);
        assertEquals(1139500.0,p.totalPrice,0.001);
        assertEquals("EGP",p.currency);
        assertEquals("Comparison",p.source.sheetName);
        assertEquals(2,p.source.rowNumber);
    }

    @Test public void recognizesArabicHeaders(){
        WorkParsedDocument d=new WorkParsedDocument();
        WorkParsedDocument.Block h=new WorkParsedDocument.Block();h.kind="TABLE_ROW";h.sheetName="أسعار";h.rowNumber=4;
        h.cells.put("A","البند");h.cells.put("B","المورد");h.cells.put("C","الكمية");h.cells.put("D","سعر الوحدة");h.cells.put("E","الإجمالي");d.blocks.add(h);
        WorkParsedDocument.Block r0=new WorkParsedDocument.Block();r0.kind="TABLE_ROW";r0.sheetName="أسعار";r0.rowNumber=5;
        r0.cells.put("A","رخام جلالة");r0.cells.put("B","مورد أ");r0.cells.put("C","100");r0.cells.put("D","2500");r0.cells.put("E","250000");d.blocks.add(r0);
        WorkStructuredExtractor.Result r=WorkStructuredExtractor.extract(d);
        assertEquals(1,r.prices.size());
        assertEquals("رخام جلالة",r.prices.get(0).item);
        assertEquals(2500.0,r.prices.get(0).unitPrice,0.001);
    }
}
