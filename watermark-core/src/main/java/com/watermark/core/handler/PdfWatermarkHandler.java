package com.watermark.core.handler;

import com.watermark.core.WatermarkHandler;
import com.watermark.core.WatermarkOptions;
import com.watermark.core.WatermarkPosition;
import com.watermark.core.font.WatermarkFontLoader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * PDF 水印处理器，负责使用 PDFBox 向 PDF 页面追加文字水印内容流。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public class PdfWatermarkHandler implements WatermarkHandler {

    private static final String PDF_EXTENSION = "pdf";
    private static final float PDF_FONT_UNITS_PER_EM = 1000f;
    private static final float TILE_EXTRA_GAP = 80f;
    private static final float MIN_TILE_STEP_X = 120f;
    private static final float MIN_TILE_STEP_Y = 100f;
    private static final float PAGE_SAFE_MARGIN = 80f;
    private static final float RIGHT_POSITION_TEXT_ALLOWANCE = 180f;
    private static final float CENTER_POSITION_TEXT_ALLOWANCE = 80f;

    private final WatermarkFontLoader fontLoader;

    /**
     * 使用默认字体加载器创建 PDF 处理器。
     */
    public PdfWatermarkHandler() {
        // 委托给共享构造器，确保默认字体和自定义字体使用相同初始化路径。
        this(new WatermarkFontLoader());
    }

    /**
     * 使用共享字体加载器创建 PDF 处理器。
     *
     * @param fontLoader 用于向 PDF 文档嵌入配置字体的字体加载器
     */
    public PdfWatermarkHandler(WatermarkFontLoader fontLoader) {
        // 手动装配时允许传入 null，并回退到默认字体加载策略。
        this.fontLoader = fontLoader == null ? new WatermarkFontLoader() : fontLoader;
    }

    @Override
    public boolean supports(String fileName) {
        // PDF 按扩展名路由，避免服务层选择处理器前提前消耗输入流。
        return PDF_EXTENSION.equals(extension(fileName));
    }

    @Override
    public void addWatermark(InputStream input, OutputStream output, String fileName, WatermarkOptions options) throws Exception {
        // 处理器可能绕过 DefaultWatermarkService 直接使用，因此这里仍需归一化参数。
        WatermarkOptions normalized = (options == null ? new WatermarkOptions() : options).normalize();
        // PDFBox 持有解析后的文档资源，保存后必须关闭。
        try (PDDocument document = PDDocument.load(input)) {
            // 跨模块字体加载会尽可能把配置字体嵌入目标 PDF 文档。
            PDFont font = fontLoader.loadPdfFont(document, normalized);
            for (PDPage page : document.getPages()) {
                // 每个页面尺寸可能不同，因此水印位置必须逐页计算。
                addWatermarkToPage(document, page, font, normalized);
            }
            // 所有页面追加完成后统一保存，确保 PDF 交叉引用表一致写出。
            document.save(output);
        }
    }

    /**
     * 向单个 PDF 页面追加水印绘制命令。
     *
     * @param document PDFBox 内容流需要的所属 PDF 文档
     * @param page 目标页面
     * @param font 当前文档选用的 PDF 字体
     * @param options 已归一化的水印参数
     * @throws Exception PDFBox 内容流写入失败时抛出
     */
    private void addWatermarkToPage(PDDocument document, PDPage page, PDFont font, WatermarkOptions options) throws Exception {
        // 页面尺寸同时决定单点水印位置和平铺水印边界。
        PDRectangle mediaBox = page.getMediaBox();
        float width = mediaBox.getWidth();
        float height = mediaBox.getHeight();
        PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
        // PDF 透明度通过图形状态设置，而不是像 Word 兜底方案那样调整文本颜色。
        graphicsState.setNonStrokingAlphaConstant(options.getOpacity());
        // alpha source 标志可让 PDF 查看器一致解释非描边透明度。
        graphicsState.setAlphaSourceFlag(true);

        try (PDPageContentStream stream = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true)) {
            // 绘制文字前先应用透明度，让所有水印实例共享同一图形状态。
            stream.setGraphicsStateParameters(graphicsState);
            // 参数归一化后再解析颜色，确保无效值使用文档约定的灰色兜底。
            Color color = options.getColorObject();
            stream.setNonStrokingColor(color);

            // DIAGONAL 是整页覆盖策略，其他位置只绘制一个可见水印。
            if (options.getPosition() == WatermarkPosition.DIAGONAL) {
                // 平铺绘制可降低裁剪或打印时唯一水印被移除的风险。
                drawDiagonalWatermark(stream, font, options, width, height);
            } else {
                // 根据页面尺寸和配置语义位置计算单个锚点。
                float[] point = calculatePosition(options.getPosition(), width, height);
                // 非 DIAGONAL 位置语义上只表示单个水印，因此只绘制一次。
                drawText(stream, font, options, point[0], point[1]);
            }
        }
    }

    /**
     * 在 PDF 页面上平铺水印文字。
     *
     * @param stream 已打开的 PDF 内容流
     * @param font 用于测量和绘制文字的 PDF 字体
     * @param options 已归一化的水印参数
     * @param width 页面宽度
     * @param height 页面高度
     * @throws Exception PDFBox 文字测量或绘制失败时抛出
     */
    private void drawDiagonalWatermark(PDPageContentStream stream, PDFont font, WatermarkOptions options, float width, float height) throws Exception {
        // PDFBox 以 1000 字形单位测量宽度，需要结合字号转换为页面单位。
        float textWidth = font.getStringWidth(options.getText()) / PDF_FONT_UNITS_PER_EM * options.getFontSize();
        float textHeight = options.getFontSize();
        // 水平步长包含文字宽度，避免长水印字符串互相重叠。
        float stepX = Math.max(textWidth + TILE_EXTRA_GAP, MIN_TILE_STEP_X);
        // PDF 文本此处没有 AWT 式度量，因此使用字号作为文字高度近似值。
        float stepY = Math.max(textHeight + TILE_EXTRA_GAP, MIN_TILE_STEP_Y);

        for (float y = textHeight; y < height + stepY; y += stepY) {
            for (float x = 0; x < width + stepX; x += stepX) {
                // 每个平铺点独立绘制，确保每个实例都保持配置的旋转原点。
                drawText(stream, font, options, x, y);
            }
        }
    }

    /**
     * 向当前 PDF 内容流写入一个旋转后的文字片段。
     *
     * @param stream 已打开的 PDF 内容流
     * @param font 用于绘制的 PDF 字体
     * @param options 已归一化的水印参数
     * @param x 基线 x 坐标
     * @param y 基线 y 坐标
     * @throws Exception PDFBox 文字写入失败时抛出
     */
    private void drawText(PDPageContentStream stream, PDFont font, WatermarkOptions options, float x, float y) throws Exception {
        // PDFBox 要求文字绘制命令必须包裹在明确的 begin/end text 块中。
        stream.beginText();
        // 每次绘制都设置字号，便于未来同一内容流混合不同文字操作。
        stream.setFont(font, options.getFontSize());
        // 旋转写入文字矩阵，使位置和角度作为一个整体原子应用。
        Matrix matrix = Matrix.getRotateInstance(Math.toRadians(options.getRotation()), x, y);
        stream.setTextMatrix(matrix);
        // showText 将实际水印字形写入 PDF 内容流。
        stream.showText(options.getText());
        stream.endText();
    }

    /**
     * 计算 PDF 页面单点水印的文字基线坐标。
     *
     * @param position 配置的位置
     * @param width 页面宽度
     * @param height 页面高度
     * @return 包含 x、y 基线坐标的二元素数组
     */
    private float[] calculatePosition(WatermarkPosition position, float width, float height) {
        switch (position) {
            case TOP_LEFT:
                // 安全边距可降低 PDF 查看器和打印场景中的裁剪风险。
                return new float[]{PAGE_SAFE_MARGIN, height - PAGE_SAFE_MARGIN};
            case TOP_RIGHT:
                // 右侧位置需要扣除文字预留宽度，因为 PDF 文本锚点位于基线起点。
                return new float[]{width - RIGHT_POSITION_TEXT_ALLOWANCE, height - PAGE_SAFE_MARGIN};
            case BOTTOM_LEFT:
                // 左下角以安全边距作为基线，避免文字贴近页面边缘。
                return new float[]{PAGE_SAFE_MARGIN, PAGE_SAFE_MARGIN};
            case BOTTOM_RIGHT:
                // 右下角同时处理右侧文字预留和底部安全边距。
                return new float[]{width - RIGHT_POSITION_TEXT_ALLOWANCE, PAGE_SAFE_MARGIN};
            case CENTER:
            default:
                // 居中时扣除少量文字预留，让文本视觉中心更接近页面中心。
                return new float[]{width / 2f - CENTER_POSITION_TEXT_ALLOWANCE, height / 2f};
        }
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
