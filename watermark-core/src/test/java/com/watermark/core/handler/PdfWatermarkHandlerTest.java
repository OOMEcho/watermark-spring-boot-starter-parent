package com.watermark.core.handler;

import com.watermark.core.WatermarkOptions;
import com.watermark.core.WatermarkPosition;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfWatermarkHandlerTest {

    @Test
    void appliesWatermarkInsideCropBoxWithNonZeroOriginAndRotation() throws Exception {
        PdfWatermarkHandler handler = new PdfWatermarkHandler();
        WatermarkOptions options = WatermarkOptions.builder()
                .text("PDF")
                .opacity(1f)
                .fontSize(48)
                .color("#000000")
                .rotation(0f)
                .position(WatermarkPosition.TOP_LEFT)
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        handler.addWatermark(new ByteArrayInputStream(createRotatedCropBoxPdf()), output, "demo.pdf", options);

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(output.toByteArray()))) {
            PDPage page = document.getPage(0);
            assertEquals(90, page.getRotation());
            assertEquals(200f, page.getCropBox().getLowerLeftX(), 0.01f);
            assertEquals(200f, page.getCropBox().getLowerLeftY(), 0.01f);
            assertTrue(renderedPageHasNonWhitePixels(document));
        }
    }

    @Test
    void handlesLargePageWithoutOversizedWatermarkImage() throws Exception {
        PdfWatermarkHandler handler = new PdfWatermarkHandler();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        handler.addWatermark(new ByteArrayInputStream(createLargePagePdf()), output, "large.pdf", WatermarkOptions.builder()
                .text("Large")
                .fontSize(80)
                .position(WatermarkPosition.CENTER)
                .build());

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(1, document.getNumberOfPages());
            assertTrue(renderedPageHasNonWhitePixels(document));
        }
    }

    private byte[] createRotatedCropBoxPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDRectangle visibleBox = new PDRectangle(200, 200, 200, 300);
            PDPage page = new PDPage(visibleBox);
            page.setCropBox(visibleBox);
            page.setRotation(90);
            document.addPage(page);
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createLargePagePdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(new PDRectangle(6000, 6000)));
            document.save(output);
            return output.toByteArray();
        }
    }

    private boolean renderedPageHasNonWhitePixels(PDDocument document) throws Exception {
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImageWithDPI(0, 72);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                if (rgb < 0xF0F0F0) {
                    return true;
                }
            }
        }
        return false;
    }
}
