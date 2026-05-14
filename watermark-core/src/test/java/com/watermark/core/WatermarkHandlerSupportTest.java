package com.watermark.core;

import com.watermark.core.handler.ExcelWatermarkHandler;
import com.watermark.core.handler.ImageWatermarkHandler;
import com.watermark.core.handler.PdfWatermarkHandler;
import com.watermark.core.handler.WordWatermarkHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatermarkHandlerSupportTest {

    @Test
    void imageHandlerSupportsCommonImageTypes() {
        ImageWatermarkHandler handler = new ImageWatermarkHandler();

        assertTrue(handler.supports("a.png"));
        assertTrue(handler.supports("a.JPG"));
        assertTrue(handler.supports("a.jpeg"));
        assertTrue(handler.supports("a.bmp"));
        assertFalse(handler.supports("a.gif"));
    }

    @Test
    void officeAndPdfHandlersSupportExpectedTypes() {
        assertTrue(new ExcelWatermarkHandler().supports("a.xlsx"));
        assertFalse(new ExcelWatermarkHandler().supports("a.xls"));
        assertTrue(new WordWatermarkHandler().supports("a.docx"));
        assertFalse(new WordWatermarkHandler().supports("a.doc"));
        assertTrue(new PdfWatermarkHandler().supports("a.pdf"));
    }
}
