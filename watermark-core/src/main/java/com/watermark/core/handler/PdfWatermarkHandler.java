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
            // 同一文档中相同页面尺寸复用同一张 XObject，避免多页 PDF 重复嵌入图片导致体积膨胀。
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
     * @param watermarkImageCache 当前 PDF 文档内按页面尺寸缓存的透明水印图层
     * @throws Exception PDFBox 内容流写入失败时抛出
     */
    private void addWatermarkToPage(PDDocument document, PDPage page, WatermarkOptions options,
                                    Map<String, PDImageXObject> watermarkImageCache) throws Exception {
        // 页面尺寸同时决定单点水印位置和平铺水印边界。
        PDRectangle mediaBox = page.getMediaBox();
        float width = mediaBox.getWidth();
        float height = mediaBox.getHeight();
        // 水印图层已包含透明度和旋转后的文字，PDF 侧只需要按页面尺寸铺满即可。
        PDImageXObject watermarkImage = getOrCreateWatermarkImage(document, mediaBox, options, watermarkImageCache);

        try (PDPageContentStream stream = new PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true)) {
            // 以页面点为单位绘制，PDFBox 会把高分辨率图片缩放回页面尺寸。
            stream.drawImage(watermarkImage, 0, 0, width, height);
        }
    }

    /**
     * 获取或创建当前页面尺寸对应的透明水印图片对象。
     *
     * @param document PDF 文档
     * @param mediaBox 当前页面尺寸
     * @param options 已归一化的水印参数
     * @param watermarkImageCache 当前文档内的水印图层缓存
     * @return 可绘制到 PDF 页面的图片对象
     * @throws IOException 创建 PDF 图片对象失败时抛出
     */
    private PDImageXObject getOrCreateWatermarkImage(PDDocument document, PDRectangle mediaBox, WatermarkOptions options,
                                                     Map<String, PDImageXObject> watermarkImageCache) throws IOException {
        int imageWidth = toRenderDimension(mediaBox.getWidth());
        int imageHeight = toRenderDimension(mediaBox.getHeight());
        String cacheKey = imageWidth + "x" + imageHeight;
        PDImageXObject cachedImage = watermarkImageCache.get(cacheKey);
        if (cachedImage != null) {
            return cachedImage;
        }

        // 高分辨率渲染能减少 PDF 查看器缩放和打印时的中文锯齿。
        BufferedImage watermarkImage = watermarkImageFactory.createWatermarkImage(scaleOptions(options), imageWidth, imageHeight);
        PDImageXObject imageObject = LosslessFactory.createFromImage(document, watermarkImage);
        watermarkImageCache.put(cacheKey, imageObject);
        return imageObject;
    }

    /**
     * 将 PDF 点单位转换为透明水印图片的像素尺寸。
     *
     * @param pageSize 页面尺寸，单位为 PDF 点
     * @return 对应的图片像素尺寸
     */
    private int toRenderDimension(float pageSize) {
        // 至少渲染 1 像素，避免异常 PDF 页面尺寸导致 BufferedImage 创建失败。
        return Math.max(1, (int) Math.ceil(pageSize * PDF_RENDER_SCALE));
    }

    /**
     * 按 PDF 高分辨率渲染比例同步放大字号。
     *
     * @param options 已归一化的水印参数
     * @return 适用于透明水印图层的参数副本
     */
    private WatermarkOptions scaleOptions(WatermarkOptions options) {
        return WatermarkOptions.builder()
                .text(options.getText())
                .opacity(options.getOpacity())
                .fontSize(Math.max(1, options.getFontSize() * PDF_RENDER_SCALE))
                .color(options.getColor())
                .rotation(options.getRotation())
                .position(options.getPosition())
                .fontPath(options.getFontPath())
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
