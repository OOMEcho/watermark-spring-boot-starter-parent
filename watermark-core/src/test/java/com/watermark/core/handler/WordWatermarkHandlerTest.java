package com.watermark.core.handler;

import com.watermark.core.WatermarkOptions;
import com.watermark.core.WatermarkPosition;
import com.watermark.core.font.WatermarkFontLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WordWatermarkHandlerTest {

    @Test
    void appliesConfiguredFontFamilyAndPositionToHeaderWatermark() throws Exception {
        WordWatermarkHandler handler = new WordWatermarkHandler(new FixedFontLoader());
        WatermarkOptions options = WatermarkOptions.builder()
                .text("WordMark")
                .position(WatermarkPosition.TOP_RIGHT)
                .rotation(15f)
                .build();

        String headerXml = addWatermarkAndReadHeaderXml(handler, options);

        assertTrue(headerXml.contains("font-family:'Dialog'"));
        assertTrue(headerXml.contains("mso-position-horizontal:right"));
        assertTrue(headerXml.contains("mso-position-vertical:top"));
    }

    @Test
    void diagonalUsesCenteredRotatedHeaderWatermark() throws Exception {
        WordWatermarkHandler handler = new WordWatermarkHandler(new FixedFontLoader());
        WatermarkOptions options = WatermarkOptions.builder()
                .text("Diagonal")
                .position(WatermarkPosition.DIAGONAL)
                .rotation(30f)
                .build();

        String headerXml = addWatermarkAndReadHeaderXml(handler, options);

        assertTrue(headerXml.contains("mso-position-horizontal:center"));
        assertTrue(headerXml.contains("mso-position-vertical:center"));
        assertTrue(headerXml.contains("rotation:330"));
    }

    @Test
    void addsHeaderWatermarkForMultipleSections() throws Exception {
        WordWatermarkHandler handler = new WordWatermarkHandler(new FixedFontLoader());
        WatermarkOptions options = WatermarkOptions.builder()
                .text("MultiSection")
                .position(WatermarkPosition.CENTER)
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        handler.addWatermark(new ByteArrayInputStream(createMultiSectionDocument()), output, "multi.docx", options);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            int watermarkHeaderCount = 0;
            for (XWPFHeader header : document.getHeaderList()) {
                if (header._getHdrFtr().xmlText().contains("MultiSection")) {
                    watermarkHeaderCount++;
                }
            }
            assertTrue(watermarkHeaderCount >= 2);
        }
    }

    private String addWatermarkAndReadHeaderXml(WordWatermarkHandler handler, WatermarkOptions options) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        handler.addWatermark(new ByteArrayInputStream(createDocument()), output, "demo.docx", options);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            StringBuilder xml = new StringBuilder();
            List<XWPFHeader> headers = document.getHeaderList();
            for (XWPFHeader header : headers) {
                xml.append(header._getHdrFtr().xmlText());
            }
            return xml.toString();
        }
    }

    private byte[] createDocument() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("body");
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] createMultiSectionDocument() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("section one");
            CTSectPr firstSection = document.createParagraph().getCTP().addNewPPr().addNewSectPr();
            firstSection.addNewPgSz().setW(java.math.BigInteger.valueOf(11906));
            document.createParagraph().createRun().setText("section two");
            CTSectPr bodySection = document.getDocument().getBody().addNewSectPr();
            bodySection.addNewPgSz().setW(java.math.BigInteger.valueOf(11906));
            document.write(output);
            return output.toByteArray();
        }
    }

    private static class FixedFontLoader extends WatermarkFontLoader {
        @Override
        public Font loadAwtFont(WatermarkOptions options) {
            return new Font(Font.DIALOG, Font.PLAIN, options.normalize().getFontSize());
        }
    }
}
