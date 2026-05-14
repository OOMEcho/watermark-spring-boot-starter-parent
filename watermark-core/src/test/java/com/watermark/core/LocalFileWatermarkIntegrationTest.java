package com.watermark.core;

import com.watermark.core.font.WatermarkFontLoader;
import com.watermark.core.handler.ExcelWatermarkHandler;
import com.watermark.core.handler.ImageWatermarkHandler;
import com.watermark.core.handler.PdfWatermarkHandler;
import com.watermark.core.handler.WordWatermarkHandler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 本地文件水印集成测试，用于验证真实图片、Excel、PDF、Word 文件的处理效果。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
class LocalFileWatermarkIntegrationTest {

    private static final String IMAGE_PATH_PROPERTY = "watermark.test.image";
    private static final String EXCEL_PATH_PROPERTY = "watermark.test.excel";
    private static final String PDF_PATH_PROPERTY = "watermark.test.pdf";
    private static final String WORD_PATH_PROPERTY = "watermark.test.word";
    private static final String OUTPUT_DIR_PROPERTY = "watermark.test.output";
    private static final String DEFAULT_OUTPUT_DIR = "target/watermark-samples";
    private static final String IMAGE_OUTPUT_NAME = "image-watermarked";
    private static final String EXCEL_OUTPUT_NAME = "excel-watermarked.xlsx";
    private static final String PDF_OUTPUT_NAME = "pdf-watermarked.pdf";
    private static final String WORD_OUTPUT_NAME = "word-watermarked.docx";

    private final WatermarkService watermarkService = createWatermarkService();
    private final WatermarkOptions options = createWatermarkOptions();

    @Test
    void addWatermarkToLocalImage() throws Exception {
        // 读取系统属性中的本地图片路径；未配置时跳过，避免日常构建依赖个人磁盘文件。
        Path input = requireLocalFile(IMAGE_PATH_PROPERTY);
        // 保留原始扩展名，便于人工打开输出文件时使用对应图片查看器。
        Path output = outputPath(IMAGE_OUTPUT_NAME + extensionWithDot(input));

        // 通过统一服务入口处理真实本地文件，验证 starter 对外 API 的完整调用链。
        addWatermark(input, output);

        // 使用 ImageIO 重新读取输出文件，确认结果至少是有效图片，而不是空文件或损坏文件。
        assertNotNull(ImageIO.read(output.toFile()));
    }

    @Test
    void addWatermarkToLocalExcel() throws Exception {
        // 读取系统属性中的本地 Excel 路径；未配置时跳过，避免日常构建依赖个人磁盘文件。
        Path input = requireLocalFile(EXCEL_PATH_PROPERTY);
        Path output = outputPath(EXCEL_OUTPUT_NAME);

        // 通过统一服务入口处理真实 XLSX 文件，验证 Excel handler 与图片水印工厂的协作。
        addWatermark(input, output);

        // 使用 POI 重新打开输出工作簿，确认水印处理后文件仍是合法 XLSX。
        try (InputStream in = Files.newInputStream(output); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertTrue(workbook.getNumberOfSheets() > 0);
        }
    }

    @Test
    void addWatermarkToLocalPdf() throws Exception {
        // 读取系统属性中的本地 PDF 路径；未配置时跳过，避免日常构建依赖个人磁盘文件。
        Path input = requireLocalFile(PDF_PATH_PROPERTY);
        Path output = outputPath(PDF_OUTPUT_NAME);

        // 通过统一服务入口处理真实 PDF 文件，验证 PDFBox 内容流追加逻辑。
        addWatermark(input, output);

        // 使用 PDFBox 重新打开输出文件，确认 PDF 结构没有在追加水印时被破坏。
        try (PDDocument document = PDDocument.load(output.toFile())) {
            assertTrue(document.getNumberOfPages() > 0);
        }
    }

    @Test
    void addWatermarkToLocalWord() throws Exception {
        // 读取系统属性中的本地 Word 路径；未配置时跳过，避免日常构建依赖个人磁盘文件。
        Path input = requireLocalFile(WORD_PATH_PROPERTY);
        Path output = outputPath(WORD_OUTPUT_NAME);

        // 通过统一服务入口处理真实 DOCX 文件，验证 Word 页眉水印和正文兜底逻辑。
        addWatermark(input, output);

        // 使用 POI 重新打开输出文档，确认 DOCX 包结构仍然有效。
        try (InputStream in = Files.newInputStream(output); XWPFDocument document = new XWPFDocument(in)) {
            assertNotNull(document.getDocument());
        }
    }

    /**
     * 创建测试使用的水印服务，直接装配 core 模块处理器以覆盖真实格式处理链路。
     *
     * @return 水印服务实例
     */
    private WatermarkService createWatermarkService() {
        WatermarkFontLoader fontLoader = new WatermarkFontLoader();
        // 直接装配 core 处理器，避免本地文件测试依赖 Spring 上下文启动。
        return new DefaultWatermarkService(Arrays.asList(
                new ImageWatermarkHandler(fontLoader),
                new ExcelWatermarkHandler(fontLoader),
                new WordWatermarkHandler(),
                new PdfWatermarkHandler(fontLoader)
        ));
    }

    /**
     * 创建测试水印参数，使用中文文本验证内置中文字体是否能覆盖真实文件场景。
     *
     * @return 水印参数
     */
    private WatermarkOptions createWatermarkOptions() {
        return WatermarkOptions.builder()
                .text("测试水印")
                .opacity(0.3f)
                .fontSize(32)
                .color("#999999")
                .rotation(45f)
                .position(WatermarkPosition.DIAGONAL)
                .build();
    }

    /**
     * 要求指定系统属性指向一个存在的本地文件，否则跳过当前测试。
     *
     * @param propertyName 系统属性名
     * @return 已存在的本地文件路径
     */
    private Path requireLocalFile(String propertyName) {
        String filePath = System.getProperty(propertyName);
        // 未配置路径时跳过测试，使该类可以长期保留在普通构建流程中。
        assumeTrue(filePath != null && !filePath.trim().isEmpty(), "未配置本地测试文件: -D" + propertyName);
        Path path = Paths.get(filePath.trim());
        // 路径不存在时跳过而不是失败，便于不同机器只运行自己准备好的文件类型。
        assumeTrue(Files.isRegularFile(path), "本地测试文件不存在: " + path);
        return path;
    }

    /**
     * 生成输出文件路径，并确保输出目录存在。
     *
     * @param fileName 输出文件名
     * @return 输出文件路径
     * @throws IOException 创建输出目录失败时抛出
     */
    private Path outputPath(String fileName) throws IOException {
        Path outputDir = Paths.get(System.getProperty(OUTPUT_DIR_PROPERTY, DEFAULT_OUTPUT_DIR));
        // 输出目录可能被 clean 删除，因此每次测试执行前都显式创建。
        Files.createDirectories(outputDir);
        return outputDir.resolve(fileName);
    }

    /**
     * 通过统一服务入口为本地文件添加水印并写出到目标路径。
     *
     * @param input 输入文件路径
     * @param output 输出文件路径
     * @throws Exception 文件读取、水印处理或输出写入失败时抛出
     */
    private void addWatermark(Path input, Path output) throws Exception {
        byte[] inputBytes = Files.readAllBytes(input);
        // 文件名用于扩展名路由，因此这里必须传入原始文件名而不是固定测试名。
        byte[] outputBytes = watermarkService.addWatermark(inputBytes, input.getFileName().toString(), options);
        // 写出文件用于人工打开检查水印视觉效果。
        Files.write(output, outputBytes);
    }

    /**
     * 获取输入文件扩展名并保留点号，用于生成图片输出文件名。
     *
     * @param path 输入文件路径
     * @return 带点号的扩展名；无扩展名时返回空字符串
     */
    private String extensionWithDot(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        // 无扩展名时不拼接后缀，让后续 ImageIO 校验暴露格式路由问题。
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex);
    }
}
