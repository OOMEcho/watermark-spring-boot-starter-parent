package com.watermark.core.handler;

import com.watermark.core.WatermarkOptions;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelWatermarkHandlerTest {

    @Test
    void protectsSheetByDefault() throws Exception {
        byte[] output = addWatermark(createWorkbook(false), new WatermarkOptions());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            assertTrue(workbook.getSheetAt(0).getProtect());
            assertTrue(workbook.getSheetAt(0).validateSheetPassword(WatermarkOptions.DEFAULT_EXCEL_PROTECT_PASSWORD));
        }
    }

    @Test
    void doesNotProtectSheetWhenDisabled() throws Exception {
        WatermarkOptions options = WatermarkOptions.builder()
                .excelProtectSheet(false)
                .build();

        byte[] output = addWatermark(createWorkbook(false), options);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            assertFalse(workbook.getSheetAt(0).getProtect());
        }
    }

    @Test
    void keepsExistingSheetProtectionPassword() throws Exception {
        WatermarkOptions options = WatermarkOptions.builder()
                .excelProtectPassword("new-password")
                .build();

        byte[] output = addWatermark(createWorkbook(true), options);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertTrue(sheet.getProtect());
            assertTrue(sheet.validateSheetPassword("original-password"));
            assertFalse(sheet.validateSheetPassword("new-password"));
        }
    }

    private byte[] addWatermark(byte[] input, WatermarkOptions options) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ExcelWatermarkHandler().addWatermark(new ByteArrayInputStream(input), output, "demo.xlsx", options);
        return output.toByteArray();
    }

    private byte[] createWorkbook(boolean protectedSheet) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("demo");
            sheet.createRow(0).createCell(0).setCellValue("demo");
            if (protectedSheet) {
                sheet.protectSheet("original-password");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
