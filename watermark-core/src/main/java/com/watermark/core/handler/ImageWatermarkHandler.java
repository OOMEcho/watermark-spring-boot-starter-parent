package com.watermark.core.handler;

import com.watermark.core.WatermarkHandler;
import com.watermark.core.WatermarkOptions;
import com.watermark.core.font.WatermarkFontLoader;
import com.watermark.core.image.WatermarkImageFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 图片水印处理器，负责为 PNG、JPG、JPEG、BMP 图片叠加文字水印。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public class ImageWatermarkHandler implements WatermarkHandler {

    private static final String FORMAT_JPG = "jpg";
    private static final String FORMAT_JPEG = "jpeg";
    private static final String FORMAT_PNG = "png";
    private static final String FORMAT_BMP = "bmp";
    private static final int CANVAS_ORIGIN = 0;
    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>(Arrays.asList("jpg", "jpeg", "png", "bmp"));

    private final WatermarkImageFactory imageFactory;

    /**
     * 使用默认字体加载器创建图片处理器。
     */
    public ImageWatermarkHandler() {
        // 委托给共享构造器，确保默认字体和自定义字体使用相同初始化路径。
        this(new WatermarkFontLoader());
    }

    /**
     * 使用共享字体加载器创建图片处理器。
     *
     * @param fontLoader 跨格式水印图片工厂使用的字体加载器
     */
    public ImageWatermarkHandler(WatermarkFontLoader fontLoader) {
        // 图片工厂集中处理文字渲染，保证图片和 Excel 水印图层视觉一致。
        this.imageFactory = new WatermarkImageFactory(fontLoader);
    }

    @Override
    public boolean supports(String fileName) {
        // 扩展名解析单独封装，确保路由行为和输出格式选择保持一致。
        return SUPPORTED_EXTENSIONS.contains(extension(fileName));
    }

    @Override
    public void addWatermark(InputStream input, OutputStream output, String fileName, WatermarkOptions options) throws Exception {
        // ImageIO 在这里解析图片内容；返回 null 表示输入流不是可解码图片。
        BufferedImage sourceImage = ImageIO.read(input);
        // 解码失败必须显式报错，否则写回原图会让调用方误以为水印成功。
        if (sourceImage == null) {
            throw new IllegalArgumentException("无法读取输入图片: " + fileName);
        }

        // 创建与源图透明模型兼容的目标画布，避免透明通道丢失。
        BufferedImage targetImage = createCompatibleImage(sourceImage);
        Graphics2D g2d = targetImage.createGraphics();
        try {
            // 先绘制原图，再把透明水印图层叠加在上方。
            g2d.drawImage(sourceImage, CANVAS_ORIGIN, CANVAS_ORIGIN, null);
            // 跨模块调用创建可复用文字图层，并复用 Excel 水印的同一套渲染器。
            BufferedImage watermarkImage = imageFactory.createWatermarkImage(options, sourceImage.getWidth(), sourceImage.getHeight());
            // 生成的图层已经与源图同尺寸，因此从画布原点直接叠加。
            g2d.drawImage(watermarkImage, CANVAS_ORIGIN, CANVAS_ORIGIN, null);
        } finally {
            // Graphics2D 持有本地资源，批量图片处理时必须及时释放。
            g2d.dispose();
        }

        // 在兼容范围内保留输出格式；遇到透明度限制时进行格式兜底。
        String format = outputFormat(fileName, targetImage);
        // ImageIO 找不到写出器时会返回 false，显式报错可避免静默生成空输出。
        if (!ImageIO.write(targetImage, format, output)) {
            throw new IllegalArgumentException("不支持的图片输出格式: " + format);
        }
    }

    /**
     * 创建与源图片透明模型兼容的目标画布。
     *
     * @param source 已解码的源图片
     * @return 用于最终合成的图片画布
     */
    private BufferedImage createCompatibleImage(BufferedImage source) {
        // 不透明图片可使用 RGB 控制 JPEG/BMP 输出体积；透明图片必须使用 ARGB 避免数据丢失。
        int type = source.getTransparency() == Transparency.OPAQUE ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        return new BufferedImage(source.getWidth(), source.getHeight(), type);
    }

    /**
     * 为合成后的图片选择输出格式。
     *
     * @param fileName 调用方提供的文件名，用作首选输出格式依据
     * @param image 合成后的图片，用于判断透明度限制
     * @return ImageIO 格式名
     */
    private String outputFormat(String fileName, BufferedImage image) {
        // 复用扩展名解析，确保支持判断和写出器选择保持一致。
        String ext = extension(fileName);
        // JPEG 无法表示 alpha；透明输入升级为 PNG，避免意外扁平化。
        if (FORMAT_JPG.equals(ext) || FORMAT_JPEG.equals(ext)) {
            return image.getTransparency() == Transparency.OPAQUE ? FORMAT_JPG : FORMAT_PNG;
        }
        // BMP 保留给依赖简单位图输出且不需要透明度的调用方。
        if (FORMAT_BMP.equals(ext)) {
            return FORMAT_BMP;
        }
        // PNG 是最安全默认格式，可无损保留合成图层。
        return FORMAT_PNG;
    }

    /**
     * 提取小写文件扩展名，用于路由和输出格式决策。
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
        // 统一小写可规避不同平台大小写敏感差异，并保持测试确定性。
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
