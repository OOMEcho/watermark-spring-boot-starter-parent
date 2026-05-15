package com.watermark.core.handler;

import com.watermark.core.WatermarkHandler;
import com.watermark.core.WatermarkOptions;
import com.watermark.core.font.WatermarkFontLoader;
import com.watermark.core.image.WatermarkImageFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Excel 水印处理器，负责为 XLSX 工作簿的每个工作表嵌入透明水印图片。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public class ExcelWatermarkHandler implements WatermarkHandler {

    private static final String XLSX_EXTENSION = "xlsx";
    private static final int DEFAULT_MIN_ROWS = 20;
    private static final int DEFAULT_MIN_COLUMNS = 10;
    private static final int DEFAULT_EMPTY_ROW_HEIGHT_PIXELS = 20;
    private static final int POINTS_PER_INCH = 72;
    private static final int SCREEN_DPI = 96;
    private static final int MIN_SHEET_WIDTH_PIXELS = 800;
    private static final int MIN_SHEET_HEIGHT_PIXELS = 600;
    private static final int SHEET_BOTTOM_PADDING_PIXELS = 50;
    private static final int MAX_WATERMARK_IMAGE_WIDTH_PIXELS = 4096;
    private static final int MAX_WATERMARK_IMAGE_HEIGHT_PIXELS = 4096;
    private static final int FIRST_ROW_INDEX = 0;
    private static final int FIRST_COLUMN_INDEX = 0;

    private final WatermarkImageFactory imageFactory;

    /**
     * 使用默认字体加载器创建 XLSX 处理器。
     */
    public ExcelWatermarkHandler() {
        // 委托给共享构造器，确保默认字体和自定义字体使用相同初始化路径。
        this(new WatermarkFontLoader());
    }

    /**
     * 使用共享字体加载器创建 XLSX 处理器。
     *
     * @param fontLoader 跨格式水印图片工厂使用的字体加载器
     */
    public ExcelWatermarkHandler(WatermarkFontLoader fontLoader) {
        // 图片工厂会在水印 PNG 嵌入工作簿前集中处理文字渲染。
        this.imageFactory = new WatermarkImageFactory(fontLoader);
    }

    @Override
    public boolean supports(String fileName) {
        // 当前实现依赖 XSSF 绘图 API，因此只支持 XLSX，不支持旧版 HSSF/XLS。
        return XLSX_EXTENSION.equals(extension(fileName));
    }

    @Override
    public void addWatermark(InputStream input, OutputStream output, String fileName, WatermarkOptions options) throws Exception {
        // XSSFWorkbook 持有解析后的 XML/OPC 包资源，处理结束必须关闭。
        try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                // 每个工作表尺寸可能不同，因此需要为每个 sheet 单独生成水印图层。
                addWatermarkToSheet(workbook, workbook.getSheetAt(i), options);
            }
            // 所有工作表处理完后再统一写出，确保工作簿包结构一次性保持一致。
            workbook.write(output);
        }
    }

    /**
     * 将生成的 PNG 水印图层嵌入工作表绘图容器。
     *
     * @param workbook 持有图片数据池的工作簿
     * @param sheet 需要接收水印绘图的目标工作表
     * @param options 已归一化的水印参数
     * @throws Exception 水印图片编码或 POI 绘图插入失败时抛出
     */
    private void addWatermarkToSheet(XSSFWorkbook workbook, XSSFSheet sheet, WatermarkOptions options) throws Exception {
        // 先计算工作表像素尺寸，确保生成的水印图层覆盖已使用区域。
        Dimension size = calculateSheetPixelSize(sheet);
        Dimension renderSize = limitRenderSize(size);
        // 跨模块调用图片水印渲染器，保证 Excel 与图片水印文字样式一致。
        BufferedImage watermarkImage = imageFactory.createWatermarkImage(scaleOptions(options, renderSize, size), renderSize.width, renderSize.height);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // PNG 能保留 Excel 水印叠加所需的透明背景。
        ImageIO.write(watermarkImage, "png", buffer);

        // 先把图片字节注册到工作簿包中，sheet 绘图才能引用该图片。
        int pictureIndex = workbook.addPicture(buffer.toByteArray(), Workbook.PICTURE_TYPE_PNG);
        // drawing patriarch 是 POI 管理工作表图片和形状的容器。
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        // 锚点决定生成的 PNG 水印图层覆盖哪些单元格区域。
        XSSFClientAnchor anchor = createAnchor(sheet);
        // 图片字节和锚点都准备好后再创建图片，因为 POI 会在构造时绑定二者。
        XSSFPicture picture = drawing.createPicture(anchor, pictureIndex);
        // 锁定图片对象，并按配置保护未受保护的工作表，降低水印被误删或拖动的概率。
        lockWatermarkPicture(sheet, picture, options);
    }

    /**
     * 锁定水印图片，并按配置保护未受保护的工作表，降低水印被鼠标误删或拖动的概率。
     *
     * @param sheet 目标工作表
     * @param picture 已插入的水印图片
     * @param options 已归一化的水印参数
     */
    private void lockWatermarkPicture(XSSFSheet sheet, XSSFPicture picture, WatermarkOptions options) {
        // 图片锁用于限制 Excel UI 对形状的直接编辑；真正生效还依赖工作表保护。
        picture.getCTPicture().getNvPicPr().getCNvPicPr().addNewPicLocks().setNoChangeAspect(true);
        // 禁止调整形状大小，避免用户取消保护前水印被误缩放导致覆盖范围变化。
        picture.getCTPicture().getNvPicPr().getCNvPicPr().getPicLocks().setNoChangeArrowheads(true);
        if (!Boolean.TRUE.equals(options.getExcelProtectSheet()) || sheet.getProtect()) {
            return;
        }
        // 保护工作表后，锁定对象在普通编辑模式下不能被选中拖动或删除。
        sheet.protectSheet(options.getExcelProtectPassword());
        // 明确锁定对象编辑权限，避免不同 Excel 版本对默认保护项理解不一致。
        sheet.lockObjects(true);
    }

    /**
     * 限制透明水印图层实际渲染尺寸，避免超大工作表生成过大的 PNG。
     *
     * @param size 工作表估算像素尺寸
     * @return 实际用于渲染 PNG 的尺寸
     */
    private Dimension limitRenderSize(Dimension size) {
        double scale = Math.min(
                (double) MAX_WATERMARK_IMAGE_WIDTH_PIXELS / Math.max(size.width, 1),
                (double) MAX_WATERMARK_IMAGE_HEIGHT_PIXELS / Math.max(size.height, 1)
        );
        if (scale >= 1d) {
            return size;
        }
        return new Dimension(
                Math.max(1, (int) Math.round(size.width * scale)),
                Math.max(1, (int) Math.round(size.height * scale))
        );
    }

    /**
     * 按 PNG 渲染尺寸相对锚点覆盖尺寸缩放字号。
     *
     * @param options 已归一化的水印参数
     * @param renderSize 实际渲染尺寸
     * @param targetSize 最终锚点覆盖尺寸
     * @return 适用于 PNG 图层的水印参数副本
     */
    private WatermarkOptions scaleOptions(WatermarkOptions options, Dimension renderSize, Dimension targetSize) {
        double scale = Math.min(
                (double) renderSize.width / Math.max(targetSize.width, 1),
                (double) renderSize.height / Math.max(targetSize.height, 1)
        );
        return WatermarkOptions.builder()
                .text(options.getText())
                .opacity(options.getOpacity())
                .fontSize(Math.max(1, (int) Math.round(options.getFontSize() * scale)))
                .color(options.getColor())
                .rotation(options.getRotation())
                .position(options.getPosition())
                .fontPath(options.getFontPath())
                .excelProtectSheet(options.getExcelProtectSheet())
                .excelProtectPassword(options.getExcelProtectPassword())
                .build();
    }

    /**
     * 估算工作表像素区域，使水印图片覆盖可见内容。
     *
     * @param sheet 目标工作表
     * @return 带有空表或小表默认值兜底的像素尺寸
     */
    private Dimension calculateSheetPixelSize(XSSFSheet sheet) {
        // 空表通常几乎没有尺寸信息，默认行数可保证后续用户添加内容时仍能看到水印。
        int lastRowNum = Math.max(sheet.getLastRowNum(), DEFAULT_MIN_ROWS);
        // 默认列数避免稀疏工作表或无单元格工作表生成零宽图片。
        int lastColumnNum = Math.max(getLastColumnNum(sheet), DEFAULT_MIN_COLUMNS);

        int totalWidth = 0;
        for (int col = 0; col < lastColumnNum; col++) {
            // POI 提供的列宽单位为像素，正好匹配水印图片坐标系。
            totalWidth += (int) sheet.getColumnWidthInPixels(col);
        }

        int totalHeight = 0;
        for (int rowIndex = 0; rowIndex <= lastRowNum; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            // 缺失行在 Excel 可视网格中仍占据默认行高，因此用近似值计入高度。
            if (row == null) {
                totalHeight += DEFAULT_EMPTY_ROW_HEIGHT_PIXELS;
            } else {
                // Excel 行高单位为 point；按标准 96 DPI 屏幕假设转换为像素。
                totalHeight += (int) (row.getHeightInPoints() * SCREEN_DPI / POINTS_PER_INCH);
            }
        }

        // 最小画布和底部 padding 可降低小表或稀疏表边缘漏水印的风险。
        return new Dimension(
                Math.max(totalWidth, MIN_SHEET_WIDTH_PIXELS),
                Math.max(totalHeight + SHEET_BOTTOM_PADDING_PIXELS, MIN_SHEET_HEIGHT_PIXELS)
        );
    }

    /**
     * 查找所有行中的最大已使用列索引。
     *
     * @param sheet 目标工作表
     * @return POI 报告的最大 last-cell number
     */
    private int getLastColumnNum(XSSFSheet sheet) {
        int maxColumn = 0;
        for (Row row : sheet) {
            // POI 返回最后一个单元格后一位索引，适合作为锚点的结束列。
            short lastCellNum = row.getLastCellNum();
            // 保留最大行宽，确保水印覆盖最宽的已填充区域。
            if (lastCellNum > maxColumn) {
                maxColumn = lastCellNum;
            }
        }
        return maxColumn;
    }

    /**
     * 创建覆盖估算使用区域的图片锚点。
     *
     * @param sheet 目标工作表
     * @return POI 插入水印图片时使用的客户端锚点
     */
    private XSSFClientAnchor createAnchor(XSSFSheet sheet) {
        // POI 行索引从 0 开始，而锚点结束行实践上近似排他，因此这里加 1。
        int lastRowNum = Math.max(sheet.getLastRowNum() + 1, DEFAULT_MIN_ROWS);
        // 这里重新计算列数，确保锚点尺寸和图片生成在稀疏表上保持一致。
        int lastColumnNum = Math.max(getLastColumnNum(sheet), DEFAULT_MIN_COLUMNS);
        XSSFClientAnchor anchor = new XSSFClientAnchor();
        // 从第一个单元格开始，让水印从工作表可视左上角覆盖。
        anchor.setCol1(FIRST_COLUMN_INDEX);
        anchor.setRow1(FIRST_ROW_INDEX);
        // 结束坐标决定 Excel 如何把图片铺展到工作表网格上。
        anchor.setCol2(lastColumnNum);
        anchor.setRow2(lastRowNum);
        return anchor;
    }

    /**
     * 提取小写文件扩展名，用于路由判断。
     *
     * @param fileName 调用方提供的文件名
     * @return 不带点的小写扩展名；不存在可用扩展名时返回空字符串
     */
    private String extension(String fileName) {
        // null 文件名无法被该处理器路由，应直接返回不匹配结果。
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        // 缺少点或以点结尾表示没有足够可靠的扩展名用于格式路由。
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        // 统一小写可让支持判断在大小写敏感和不敏感平台上保持一致。
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
