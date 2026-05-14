package com.watermark.core.font;

import com.watermark.core.WatermarkOptions;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * 水印字体加载器，负责从 classpath、文件路径和系统字体中获取可用字体。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public class WatermarkFontLoader {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILE_PREFIX = "file:";
    private static final String RESOURCE_PATH_SEPARATOR = "/";

    /**
     * 按中文桌面和服务器环境中的常见程度排序；越靠前越优先保留中文字形质量。
     */
    private static final List<String> CHINESE_FALLBACK_FONTS = Arrays.asList(
            "Source Han Serif SC", "Microsoft YaHei", "SimSun", "PingFang SC", "Heiti SC", "WenQuanYi Micro Hei"
    );

    /**
     * 为图片和 Excel 水印渲染加载 AWT 字体。
     *
     * @param options 已归一化或原始水印参数，包含字体路径和字号
     * @return 优先返回配置字体；不可用时返回支持中文的系统字体或逻辑 sans-serif 字体
     */
    public Font loadAwtFont(WatermarkOptions options) {
        // 该工具可能绕过 DefaultWatermarkService 直接使用，因此这里仍需归一化参数。
        WatermarkOptions normalized = (options == null ? new WatermarkOptions() : options).normalize();
        // 优先打开配置字体路径，确保内置思源宋体无需系统安装即可使用。
        try (InputStream input = openFontStream(normalized.getFontPath())) {
            // null 表示未找到字体资源；继续兜底可保证非中文水印仍可生成。
            if (input != null) {
                // 从字节创建基础字体，让 classpath 和文件系统字体共用同一加载路径。
                Font baseFont = Font.createFont(Font.TRUETYPE_FONT, input);
                // AWT Font 实例带有字号信息，因此每次按当前配置派生指定字号。
                return baseFont.deriveFont(Font.PLAIN, normalized.getFontSize().floatValue());
            }
        } catch (Exception ignored) {
            // 字体加载失败不应阻断整个水印流程，只要后续存在可读的兜底字体即可继续。
        }
        // 最后使用系统字体兜底，保证没有内置字体的环境仍能继续渲染。
        return loadFallbackAwtFont(normalized.getFontSize());
    }

    /**
     * 加载适合向 PDF 写入 Unicode 文本的 PDFBox 字体。
     *
     * @param document 目标 PDF 文档；PDFBox 会把加载到的 TrueType 字体嵌入该文档
     * @param options 已归一化或原始水印参数，包含字体路径
     * @return 优先返回已嵌入的配置字体；不可用时返回 PDFBox Helvetica 兜底字体
     * @throws IOException PDFBox 处理兜底字体失败时抛出
     */
    public PDFont loadPdfFont(PDDocument document, WatermarkOptions options) throws IOException {
        // PDF 处理器和自定义调用方都可能直接使用该加载器，因此这里仍需归一化参数。
        WatermarkOptions normalized = (options == null ? new WatermarkOptions() : options).normalize();
        // 优先尝试配置字体，确保中文文本能正确嵌入生成的 PDF 内容流。
        try (InputStream input = openFontStream(normalized.getFontPath())) {
            // PDFBox 输出中只有嵌入 Type0 字体才能可靠渲染 CJK 字符。
            if (input != null) {
                return PDType0Font.load(document, input, true);
            }
        } catch (Exception ignored) {
            // 即使配置的中文字体不可用，PDF 水印仍应尽量支持 ASCII 文本。
        }
        // Helvetica 是安全的内置兜底字体，但无法渲染中文；这是可用性优先的取舍。
        return PDType1Font.HELVETICA;
    }

    /**
     * 在配置字体不可读时选择系统字体。
     *
     * @param size 请求字号
     * @return 最合适的可用中文字体；仍不可用时返回逻辑 sans-serif 字体
     */
    private Font loadFallbackAwtFont(int size) {
        try {
            // 查询图形环境是判断当前 JVM 可渲染字体的唯一可移植方式。
            List<String> availableFonts = Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
            );
            for (String fallback : CHINESE_FALLBACK_FONTS) {
                // 兜底列表已按字形覆盖质量排序，因此第一个已安装字体优先。
                if (availableFonts.contains(fallback)) {
                    return new Font(fallback, Font.PLAIN, size);
                }
            }
        } catch (Exception ignored) {
            // 无头环境或受限环境可能无法枚举字体；逻辑字体可保证仍能渲染。
        }
        // sans-serif 属于 JDK 保证存在的逻辑字体，避免向调用方返回 null。
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    /**
     * 从 classpath、file URI、普通文件路径或近似 classpath 资源路径打开配置字体。
     *
     * @param fontPath 用户配置或默认字体路径
     * @return 字体输入流；资源不存在时返回 {@code null}
     * @throws IOException 声明的文件路径存在但无法打开时抛出
     */
    private InputStream openFontStream(String fontPath) throws IOException {
        // 空路径表示没有配置资源，由调用方决定如何兜底。
        if (fontPath == null || fontPath.trim().isEmpty()) {
            return null;
        }

        String path = fontPath.trim();
        // classpath: 是默认模式，因为 starter 内置资源不应依赖机器绝对路径。
        if (path.startsWith(CLASSPATH_PREFIX)) {
            String resourcePath = path.substring(CLASSPATH_PREFIX.length());
            // Class#getResourceAsStream 做绝对 classpath 查找时需要前导斜杠。
            if (!resourcePath.startsWith(RESOURCE_PATH_SEPARATOR)) {
                resourcePath = RESOURCE_PATH_SEPARATOR + resourcePath;
            }
            return WatermarkFontLoader.class.getResourceAsStream(resourcePath);
        }

        // file: 允许用户使用外部统一管理的企业字体覆盖内置字体。
        if (path.startsWith(FILE_PREFIX)) {
            try {
                // URI 解析支持配置系统产生的编码空格和非 ASCII 路径。
                return Files.newInputStream(Paths.get(URI.create(path)));
            } catch (Exception ignored) {
                // 部分 Windows 配置使用 file:C:/... 这种非严格 URI 形式，因此保留兼容分支。
                return Files.newInputStream(Paths.get(path.substring(FILE_PREFIX.length())));
            }
        }

        File file = new File(path);
        // 普通文件路径便于本地测试，也兼容历史配置值。
        if (file.exists() && file.isFile()) {
            return Files.newInputStream(file.toPath());
        }

        // 最后将该值视为资源路径，兼容省略 classpath: 前缀的调用方。
        String resourcePath = path.startsWith(RESOURCE_PATH_SEPARATOR) ? path : RESOURCE_PATH_SEPARATOR + path;
        return WatermarkFontLoader.class.getResourceAsStream(resourcePath);
    }
}
