package com.watermark.core.image;

import com.watermark.core.WatermarkOptions;
import com.watermark.core.WatermarkPosition;
import com.watermark.core.font.WatermarkFontLoader;

import java.awt.AlphaComposite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * 透明水印图片工厂，负责生成可叠加到图片或 Office 文档中的文字水印图层。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public class WatermarkImageFactory {

    private static final int MIN_RENDER_SIZE = 1;
    private static final int DIAGONAL_MIN_MARGIN = 40;
    private static final int DIAGONAL_WIDTH_RATIO = 16;
    private static final int DIAGONAL_HEIGHT_RATIO = 12;
    private static final int POSITION_MIN_MARGIN = 20;
    private static final int POSITION_MARGIN_RATIO = 50;
    private static final int ROTATION_ZERO_DEGREES = 0;

    private final WatermarkFontLoader fontLoader;

    /**
     * 创建可复用的透明水印图片工厂。
     *
     * @param fontLoader 与处理器共享的字体加载器；传入 {@code null} 时使用库默认加载器
     */
    public WatermarkImageFactory(WatermarkFontLoader fontLoader) {
        // 工厂可能在 Spring 容器外创建，因此将 null 视为使用默认字体加载策略。
        this.fontLoader = fontLoader == null ? new WatermarkFontLoader() : fontLoader;
    }

    /**
     * 创建只包含水印文字的透明图片。
     *
     * @param options 水印参数；渲染前会归一化非法值和缺省值
     * @param width 请求的目标宽度，单位像素
     * @param height 请求的目标高度，单位像素
     * @return 透明水印图层，调用方可叠加到图片或嵌入文档
     */
    public BufferedImage createWatermarkImage(WatermarkOptions options, int width, int height) {
        // Excel 和图片处理器都会直接调用该工厂，因此这里仍需归一化参数。
        WatermarkOptions normalized = (options == null ? new WatermarkOptions() : options).normalize();
        // BufferedImage 不接受 0 或负尺寸，因此强制使用最小可渲染画布。
        int imageWidth = Math.max(width, MIN_RENDER_SIZE);
        int imageHeight = Math.max(height, MIN_RENDER_SIZE);
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = image.createGraphics();
        try {
            // 渲染提示可提升水印图层嵌入 Office、PDF 或图片后的可读性。
            setupRenderingHints(g2d);
            // 在图层绘制阶段统一应用透明度，确保所有文字使用同一透明度规则。
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, normalized.getOpacity()));
            // 归一化后再解析颜色，确保无效配置在各格式中统一兜底。
            g2d.setColor(normalized.getColorObject());
            // 跨模块字体加载集中处理 CJK 字形支持，避免在此重复字体逻辑。
            g2d.setFont(fontLoader.loadAwtFont(normalized));
            // 根据配置在透明图层上绘制平铺水印或单点水印。
            drawWatermark(g2d, normalized, imageWidth, imageHeight);
        } finally {
            // Graphics2D 持有本地资源，批量加水印时必须释放以避免泄漏。
            g2d.dispose();
        }
        return image;
    }

    /**
     * 开启文本渲染选项，提升文档压缩或缩放后的水印可读性。
     *
     * @param g2d 透明水印图层的图形上下文
     */
    private void setupRenderingHints(Graphics2D g2d) {
        // 水印文字通常会旋转且半透明，抗锯齿对可读性影响较大。
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 文本抗锯齿可减少图片和 Excel 输出中的中文锯齿边缘。
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // 水印渲染通常每个生成文件只执行一次，因此优先选择质量而非速度。
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /**
     * 按选定的位置策略绘制水印文字。
     *
     * @param g2d 透明水印图层的图形上下文
     * @param options 已归一化的水印参数
     * @param width 图层宽度，单位像素
     * @param height 图层高度，单位像素
     */
    private void drawWatermark(Graphics2D g2d, WatermarkOptions options, int width, int height) {
        // 需要字体度量信息，因为平铺间距必须适配实际文字和字体。
        FontMetrics fm = g2d.getFontMetrics();
        // 最小值为 1 可避免特殊字体返回 0 宽度导致循环无法推进。
        int textWidth = Math.max(fm.stringWidth(options.getText()), MIN_RENDER_SIZE);
        int textHeight = Math.max(fm.getHeight(), MIN_RENDER_SIZE);

        // DIAGONAL 表示覆盖整张画布，而不是单个斜向点位，因此使用平铺绘制。
        if (options.getPosition() == WatermarkPosition.DIAGONAL) {
            // 边距随画布缩放，同时保留最小间距，避免小图中文字过密不可读。
            int marginX = Math.max(DIAGONAL_MIN_MARGIN, width / DIAGONAL_WIDTH_RATIO);
            // 垂直间距略密于水平间距，用来抵消文字旋转后的高度占用。
            int marginY = Math.max(DIAGONAL_MIN_MARGIN, height / DIAGONAL_HEIGHT_RATIO);
            // 步长包含文字尺寸，避免相邻水印实例互相重叠。
            int xStep = Math.max(textWidth + marginX, MIN_RENDER_SIZE);
            // 垂直步长包含文字高度，确保旋转后各行仍有视觉间隔。
            int yStep = Math.max(textHeight + marginY, MIN_RENDER_SIZE);

            for (int y = textHeight; y < height + yStep; y += yStep) {
                for (int x = 0; x < width + xStep; x += xStep) {
                    // 每个平铺点独立旋转，保证重复水印都保持配置角度。
                    drawRotatedString(g2d, options, x, y, textWidth, textHeight);
                }
            }
            return;
        }

        // 非平铺位置需要根据画布和文字度量计算单个锚点。
        Point point = calculatePosition(options.getPosition(), width, height, textWidth, textHeight);
        // 非 DIAGONAL 模式的语义是单个可见水印，因此只在解析出的锚点绘制一次。
        drawRotatedString(g2d, options, point.x, point.y, textWidth, textHeight);
    }

    /**
     * 绘制单个水印字符串，并保留调用方原始坐标变换状态。
     *
     * @param g2d 透明水印图层的图形上下文
     * @param options 已归一化的水印参数
     * @param x 基线 x 坐标
     * @param y 基线 y 坐标
     * @param textWidth 用作旋转中心参考的文字宽度
     * @param textHeight 用作旋转中心参考的文字高度
     */
    private void drawRotatedString(Graphics2D g2d, WatermarkOptions options, int x, int y, int textWidth, int textHeight) {
        // 保存坐标变换，因为 Graphics2D 的旋转会累积，否则会影响后续平铺点。
        AffineTransform original = g2d.getTransform();
        // 角落水印常使用 0 度旋转，跳过旋转可避免不必要的浮点变换。
        if (options.getRotation() != ROTATION_ZERO_DEGREES) {
            g2d.rotate(Math.toRadians(options.getRotation()), x + textWidth / 2.0, y - textHeight / 2.0);
        }
        g2d.drawString(options.getText(), x, y);
        // 恢复原始坐标变换，确保后续每个平铺点都从干净坐标系开始。
        g2d.setTransform(original);
    }

    /**
     * 计算单点水印的文字基线坐标。
     *
     * @param position 配置的单点位置
     * @param width 画布宽度，单位像素
     * @param height 画布高度，单位像素
     * @param textWidth 文字宽度
     * @param textHeight 文字高度
     * @return 用于绘制水印文字的基线坐标
     */
    private Point calculatePosition(WatermarkPosition position, int width, int height, int textWidth, int textHeight) {
        // 边距随画布缩放，同时避免小图中的水印贴边被裁剪。
        int marginX = Math.max(POSITION_MIN_MARGIN, width / POSITION_MARGIN_RATIO);
        // 垂直方向使用相同比例，让角落水印的视觉边距保持平衡。
        int marginY = Math.max(POSITION_MIN_MARGIN, height / POSITION_MARGIN_RATIO);
        switch (position) {
            case TOP_LEFT:
                // 左上角锚点需要加上文字高度，避免字形上半部分被裁剪。
                return new Point(marginX, marginY + textHeight);
            case TOP_RIGHT:
                // 右上角需要扣除文字宽度，确保完整水印留在画布内。
                return new Point(width - textWidth - marginX, marginY + textHeight);
            case BOTTOM_LEFT:
                // 左下角以底部边距作为文字基线，避免下行字符被裁剪。
                return new Point(marginX, height - marginY);
            case BOTTOM_RIGHT:
                // 右下角同时处理右侧文字宽度补偿和底部基线边距。
                return new Point(width - textWidth - marginX, height - marginY);
            case CENTER:
            default:
                // 居中是最安全的兜底位置，即使未来扩展枚举也能保证水印可见。
                return new Point((width - textWidth) / 2, (height + textHeight) / 2);
        }
    }
}
