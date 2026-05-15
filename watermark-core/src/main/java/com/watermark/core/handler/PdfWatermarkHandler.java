package com.watermark.core.handler;

import com.watermark.core.WatermarkHandler;
import com.watermark.core.WatermarkOptions;
import com.watermark.core.font.WatermarkFontLoader;
import com.watermark.core.image.WatermarkImageFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * PDF 水印处理器，负责使用 PDFBox 向 PDF 页面追加透明图片水印图层。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public class PdfWatermarkHandler implements WatermarkHandler {

    private static final String PDF_EXTENSION = "pdf";
    private static final int PDF_RENDER_SCALE = 2;
    private static final int RIGHT_ANGLE_DEGREES = 90;
    private static final int HALF_TURN_DEGREES = 180;
    private static final int THREE_QUARTER_TURN_DEGREES = 270;
    private static final int FULL_TURN_DEGREES = 360;
    private static final int MAX_WATERMARK_IMAGE_WIDTH_PIXELS = 4096;
    private static final int MAX_WATERMARK_IMAGE_HEIGHT_PIXELS = 4096;

    private final WatermarkImageFactory watermarkImageFactory;

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
        // PDF 中文文本字体嵌入依赖字体格式；改为透明图片水印后可复用 AWT 字体加载路径。
        this.watermarkImageFactory = new WatermarkImageFactory(fontLoader);
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
            // 同一文档中相同可见区域和旋转角复用同一张 XObject，避免多页 PDF 重复嵌入图片导致体积膨胀。
            Map<String, PDImageXObject> watermarkImageCache = new HashMap<>();
            for (PDPage page : document.getPages()) {
                // 每个页面尺寸可能不同，因此水印位置必须逐页计算。
                addWatermarkToPage(document, page, normalized, watermarkImageCache);
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
     * @param options 已归一化的水印参数
     * @param watermarkImageCache 当前 PDF 文档内按可见区域尺寸和旋转角缓存的透明水印图层
     * @throws Exception PDFBox 内容流写入失败时抛出
     */
    private void addWatermarkToPage(PDDocument document, PDPage page, WatermarkOptions options,
                                    Map<String, PDImageXObject> watermarkImageCache) throws Exception {
        // CropBox 表示 PDF 查看器实际展示区域；缺失时 PDFBox 会回退到 MediaBox。
        PDRectangle visibleBox = visibleBox(page);
        float width = visibleBox.getWidth();
        float height = visibleBox.getHeight();
        int rotation = normalizePageRotation(page.getRotation());
        // 水印图层已包含透明度和旋转后的文字，PDF 侧只需要按页面尺寸铺满即可。
        PDImageXObject watermarkImage = getOrCreateWatermarkImage(document, visibleBox, rotation, options, watermarkImageCache);

        try (PDPageContentStream stream = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true)) {
            // 以页面点为单位绘制，PDFBox 会把高分辨率图片缩放回可见页面尺寸。
            drawWatermarkImage(stream, watermarkImage, visibleBox, rotation, width, height);
        }
    }

    /**
     * 获取或创建当前页面可见区域对应的透明水印图片对象。
     *
     * @param document PDF 文档
     * @param visibleBox 页面可见区域
     * @param rotation 页面旋转角
     * @param options 已归一化的水印参数
     * @param watermarkImageCache 当前文档内的水印图层缓存
     * @return 可绘制到当前页面可见区域的图片对象
     * @throws IOException 创建 PDF 图片对象失败时抛出
     */
    private PDImageXObject getOrCreateWatermarkImage(PDDocument document, PDRectangle visibleBox, int rotation, WatermarkOptions options,
                                                      Map<String, PDImageXObject> watermarkImageCache) throws IOException {
        float displayWidth = displayWidth(visibleBox, rotation);
        float displayHeight = displayHeight(visibleBox, rotation);
        float renderScale = renderScale(displayWidth, displayHeight);
        int imageWidth = toRenderDimension(displayWidth, renderScale);
        int imageHeight = toRenderDimension(displayHeight, renderScale);
        String cacheKey = imageWidth + "x" + imageHeight + "@" + rotation;
        PDImageXObject cachedImage = watermarkImageCache.get(cacheKey);
        if (cachedImage != null) {
            return cachedImage;
        }

        // 高分辨率渲染能减少 PDF 查看器缩放和打印时的中文锯齿。
        BufferedImage watermarkImage = watermarkImageFactory.createWatermarkImage(scaleOptions(options, renderScale), imageWidth, imageHeight);
        PDImageXObject imageObject = LosslessFactory.createFromImage(document, watermarkImage);
        watermarkImageCache.put(cacheKey, imageObject);
        return imageObject;
    }

    /**
     * 将水印图层绘制到 PDF 可见区域，并抵消页面旋转带来的坐标系变化。
     *
     * @param stream 页面内容流
     * @param watermarkImage 水印图片对象
     * @param visibleBox 页面可见区域
     * @param rotation 页面旋转角
     * @param width 可见区域宽度
     * @param height 可见区域高度
     * @throws IOException 内容流写入失败时抛出
     */
    private void drawWatermarkImage(PDPageContentStream stream, PDImageXObject watermarkImage, PDRectangle visibleBox,
                                    int rotation, float width, float height) throws IOException {
        float x = visibleBox.getLowerLeftX();
        float y = visibleBox.getLowerLeftY();
        stream.saveGraphicsState();
        try {
            switch (rotation) {
                case RIGHT_ANGLE_DEGREES:
                    stream.transform(new Matrix(0, -1, 1, 0, x, y + height));
                    stream.drawImage(watermarkImage, 0, 0, height, width);
                    break;
                case HALF_TURN_DEGREES:
                    stream.transform(new Matrix(-1, 0, 0, -1, x + width, y + height));
                    stream.drawImage(watermarkImage, 0, 0, width, height);
                    break;
                case THREE_QUARTER_TURN_DEGREES:
                    stream.transform(new Matrix(0, 1, -1, 0, x + width, y));
                    stream.drawImage(watermarkImage, 0, 0, height, width);
                    break;
                default:
                    stream.drawImage(watermarkImage, x, y, width, height);
                    break;
            }
        } finally {
            stream.restoreGraphicsState();
        }
    }

    /**
     * 获取 PDF 查看器实际展示的页面区域。
     *
     * @param page 目标页面
     * @return CropBox 或 MediaBox 兜底
     */
    private PDRectangle visibleBox(PDPage page) {
        PDRectangle cropBox = page.getCropBox();
        return cropBox == null ? page.getMediaBox() : cropBox;
    }

    /**
     * 将页面旋转角归一化为 PDF 标准四象限角度。
     *
     * @param rotation 页面旋转角
     * @return 0、90、180 或 270
     */
    private int normalizePageRotation(int rotation) {
        int value = ((rotation % FULL_TURN_DEGREES) + FULL_TURN_DEGREES) % FULL_TURN_DEGREES;
        if (value == RIGHT_ANGLE_DEGREES || value == HALF_TURN_DEGREES || value == THREE_QUARTER_TURN_DEGREES) {
            return value;
        }
        return 0;
    }

    /**
     * 页面旋转 90 或 270 度时，查看器中的宽度来自未旋转坐标系的高度。
     *
     * @param visibleBox 页面可见区域
     * @param rotation 页面旋转角
     * @return 查看器方向下的页面宽度
     */
    private float displayWidth(PDRectangle visibleBox, int rotation) {
        return rotation == RIGHT_ANGLE_DEGREES || rotation == THREE_QUARTER_TURN_DEGREES
                ? visibleBox.getHeight()
                : visibleBox.getWidth();
    }

    /**
     * 页面旋转 90 或 270 度时，查看器中的高度来自未旋转坐标系的宽度。
     *
     * @param visibleBox 页面可见区域
     * @param rotation 页面旋转角
     * @return 查看器方向下的页面高度
     */
    private float displayHeight(PDRectangle visibleBox, int rotation) {
        return rotation == RIGHT_ANGLE_DEGREES || rotation == THREE_QUARTER_TURN_DEGREES
                ? visibleBox.getWidth()
                : visibleBox.getHeight();
    }

    /**
     * 计算 PDF 透明水印图层实际渲染比例，常规页面保持高清，超大页面自动降采样。
     *
     * @param width 查看器方向下的页面宽度
     * @param height 查看器方向下的页面高度
     * @return 实际用于渲染图片的缩放比例
     */
    private float renderScale(float width, float height) {
        float scale = PDF_RENDER_SCALE;
        scale = Math.min(scale, MAX_WATERMARK_IMAGE_WIDTH_PIXELS / Math.max(width, 1f));
        scale = Math.min(scale, MAX_WATERMARK_IMAGE_HEIGHT_PIXELS / Math.max(height, 1f));
        return Math.max(scale, 1f / PDF_RENDER_SCALE);
    }

    /**
     * 将 PDF 点单位按实际渲染比例转换为透明水印图片的像素尺寸。
     *
     * @param pageSize 页面尺寸，单位为 PDF 点
     * @param renderScale 实际渲染缩放比例
     * @return 应用渲染比例后的图片像素尺寸
     */
    private int toRenderDimension(float pageSize, float renderScale) {
        // 至少渲染 1 像素，避免异常 PDF 页面尺寸导致 BufferedImage 创建失败。
        return Math.max(1, (int) Math.ceil(pageSize * renderScale));
    }

    /**
     * 按 PDF 实际渲染比例同步调整字号。
     *
     * @param options 已归一化的水印参数
     * @param renderScale 实际渲染缩放比例
     * @return 适用于透明水印图层的参数副本
     */
    private WatermarkOptions scaleOptions(WatermarkOptions options, float renderScale) {
        return WatermarkOptions.builder()
                .text(options.getText())
                .opacity(options.getOpacity())
                .fontSize(Math.max(1, Math.round(options.getFontSize() * renderScale)))
                .color(options.getColor())
                .rotation(options.getRotation())
                .position(options.getPosition())
                .fontPath(options.getFontPath())
                .excelProtectSheet(options.getExcelProtectSheet())
                .excelProtectPassword(options.getExcelProtectPassword())
                .build();
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
