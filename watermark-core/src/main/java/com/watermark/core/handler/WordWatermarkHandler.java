package com.watermark.core.handler;

import com.watermark.core.WatermarkHandler;
import com.watermark.core.WatermarkOptions;
import org.apache.xmlbeans.XmlCursor;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import javax.xml.namespace.QName;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Word 水印处理器，负责为 DOCX 文档添加 Word 原生页眉水印或正文兜底水印。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public class WordWatermarkHandler implements WatermarkHandler {

    private static final String FONT_FAMILY = "Source Han Serif SC";
    private static final String DOCX_EXTENSION = "docx";
    private static final int RGB_COLOR_MASK = 0xFFFFFF;
    private static final int COLOR_CHANNEL_MAX = 255;
    private static final int RED_SHIFT_BITS = 16;
    private static final int GREEN_SHIFT_BITS = 8;
    private static final int COLOR_CHANNEL_MASK = 0xFF;
    private static final float MIN_OPACITY = 0f;
    private static final float MAX_OPACITY = 1f;
    private static final int NO_PARAGRAPH_SPACING = 0;
    private static final int FULL_CIRCLE_DEGREES = 360;
    private static final int WORD_WATERMARK_MIN_WIDTH_PT = 180;
    private static final int WORD_WATERMARK_MAX_WIDTH_PT = 360;
    private static final int WORD_WATERMARK_HEIGHT_PT = 90;
    private static final int WORD_WATERMARK_CHAR_WIDTH_FACTOR = 4;
    private static final String WATERMARK_OBJECT_ID_PREFIX = "PowerPlusWaterMarkObject";
    private static final String SHAPE_LOCAL_NAME = "shape";
    private static final String TEXT_PATH_LOCAL_NAME = "textpath";

    @Override
    public boolean supports(String fileName) {
        // 当前实现依赖 XWPF API，因此只支持 DOCX，不支持旧版 HWPF/DOC。
        return DOCX_EXTENSION.equals(extension(fileName));
    }

    @Override
    public void addWatermark(InputStream input, OutputStream output, String fileName, WatermarkOptions options) throws Exception {
        // 处理器可能绕过 DefaultWatermarkService 直接使用，因此这里仍需归一化参数。
        WatermarkOptions normalized = (options == null ? new WatermarkOptions() : options).normalize();
        // XWPFDocument 持有 OPC 包资源，写出后必须关闭。
        try (XWPFDocument document = new XWPFDocument(input)) {
            // 优先使用 Word 原生页眉水印，避免影响正文布局。
            addHeaderWatermark(document, normalized);
            // 水印插入后统一写出一次，保持 DOCX 包结构一致。
            document.write(output);
        }
    }

    /**
     * 尽可能添加 Word 原生页眉水印，不兼容文档则回退为正文浅色文本水印。
     *
     * @param document 目标 DOCX 文档
     * @param options 已归一化的水印参数
     */
    private void addHeaderWatermark(XWPFDocument document, WatermarkOptions options) {
        try {
            // Word 页眉水印依赖节属性；仅在文档缺失时创建，避免覆盖既有结构。
            CTSectPr sectPr = document.getDocument().getBody().isSetSectPr()
                    ? document.getDocument().getBody().getSectPr()
                    : document.getDocument().getBody().addNewSectPr();
            // POI 的页眉页脚策略封装了 Word 期望的 VML 水印结构。
            XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(document, sectPr);
            // 跨库调用只负责创建页眉关系和 VML 骨架，后续必须修正中文和样式。
            policy.createWatermark(options.getText());
            // POI 默认水印对中文和样式控制不足，因此创建骨架后立即修正 VML 属性。
            rewriteHeaderWatermark(policy, options);
        } catch (Exception ignored) {
            // 部分异常或极简 DOCX 会拒绝页眉修改；回退可保证 API 尽力完成水印处理。
            addTextWatermark(document, options);
        }
    }

    /**
     * 修正 POI 生成的页眉 VML 水印，解决中文乱码、黑色不透明和尺寸过大的问题。
     *
     * @param policy 已创建水印的页眉页脚策略
     * @param options 已归一化的水印参数
     */
    private void rewriteHeaderWatermark(XWPFHeaderFooterPolicy policy, WatermarkOptions options) {
        // createWatermark 会创建默认页、首页和偶数页页眉，必须逐个修正才能覆盖所有页面类型。
        rewriteHeaderWatermark(policy.getDefaultHeader(), options);
        rewriteHeaderWatermark(policy.getFirstPageHeader(), options);
        rewriteHeaderWatermark(policy.getEvenPageHeader(), options);
    }

    /**
     * 修正单个页眉中的 VML 水印节点。
     *
     * @param header 目标页眉
     * @param options 已归一化的水印参数
     */
    private void rewriteHeaderWatermark(XWPFHeader header, WatermarkOptions options) {
        if (header == null) {
            return;
        }
        XmlCursor cursor = header._getHdrFtr().newCursor();
        try {
            while (cursor.hasNextToken()) {
                // XmlCursor 直接修改底层 XMLBeans 对象，避免整段 XML 重解析导致回写失败。
                if (cursor.toNextToken().isStart()) {
                    QName name = cursor.getName();
                    if (isLocalName(name, SHAPE_LOCAL_NAME) && isWatermarkShape(cursor)) {
                        // shape 节点控制水印位置、大小、颜色和层级，是解决巨大黑字问题的关键。
                        rewriteShape(cursor, options);
                    }
                    if (isLocalName(name, TEXT_PATH_LOCAL_NAME) && cursor.getAttributeText(new QName("string")) != null) {
                        // textpath 节点承载实际水印文字，必须写入正确中文并指定中文字体。
                        rewriteTextPath(cursor, options);
                    }
                }
            }
        } finally {
            // XmlCursor 需要显式释放，避免批量处理 Word 文档时泄漏 XMLBeans 游标资源。
            cursor.dispose();
        }
    }

    /**
     * 判断当前 XML 节点本地名是否匹配，不依赖具体命名空间前缀。
     *
     * @param name 当前节点名称
     * @param localName 期望本地名
     * @return 本地名匹配时返回 true
     */
    private boolean isLocalName(QName name, String localName) {
        return name != null && localName.equals(name.getLocalPart());
    }

    /**
     * 判断当前 shape 是否为 POI 生成的水印对象。
     *
     * @param cursor 当前定位在 shape 开始节点的游标
     * @return 当前 shape 为水印对象时返回 true
     */
    private boolean isWatermarkShape(XmlCursor cursor) {
        String id = cursor.getAttributeText(new QName("id"));
        // 只处理 PowerPlusWaterMarkObject，避免误改页眉中的其他 VML 图形。
        return id != null && id.startsWith(WATERMARK_OBJECT_ID_PREFIX);
    }

    /**
     * 重写 VML shape 属性，控制水印颜色、大小、位置和旋转角度。
     *
     * @param cursor 当前定位在 v:shape 开始节点的游标
     * @param options 已归一化的水印参数
     */
    private void rewriteShape(XmlCursor cursor, WatermarkOptions options) {
        // Word VML 不可靠支持 alpha，这里使用淡化后的颜色近似表达透明度。
        cursor.setAttributeText(new QName("fillcolor"), "#" + fadeColor(options));
        // 保持无描边，避免水印文字外轮廓过重影响正文阅读。
        cursor.setAttributeText(new QName("stroked"), "false");
        // style 集中控制布局，避免 POI 默认 415pt 大水印遮挡正文。
        cursor.setAttributeText(new QName("style"), buildWatermarkShapeStyle(options));
    }

    /**
     * 重写 VML textpath 属性，设置正确水印文本和中文字体。
     *
     * @param cursor 当前定位在 v:textpath 开始节点的游标
     * @param options 已归一化的水印参数
     */
    private void rewriteTextPath(XmlCursor cursor, WatermarkOptions options) {
        // 直接写入 Java 字符串，XMLBeans 会负责属性转义并保留中文。
        cursor.setAttributeText(new QName("string"), options.getText());
        // 指定中文字体族并让 VML 按 shape 拉伸文字，这是 Word 水印常见写法。
        cursor.setAttributeText(new QName("style"), "font-family:'" + FONT_FAMILY + "';font-size:1pt");
        // textpath 必须开启，否则部分 Word 版本不会显示 VML 文字。
        cursor.setAttributeText(new QName("on"), "t");
        cursor.setAttributeText(new QName("fitshape"), "t");
    }

    /**
     * 生成 Word VML 水印 shape 样式。
     *
     * @param options 已归一化的水印参数
     * @return 可写入 v:shape style 属性的样式字符串
     */
    private String buildWatermarkShapeStyle(WatermarkOptions options) {
        int width = calculateWatermarkWidth(options);
        int rotation = normalizeWordRotation(options.getRotation());
        // 负 z-index 让水印处于正文后方，center 相对 margin 能适配大多数页面尺寸。
        return "position:absolute;" +
                "margin-left:0;" +
                "margin-top:0;" +
                "width:" + width + "pt;" +
                "height:" + WORD_WATERMARK_HEIGHT_PT + "pt;" +
                "rotation:" + rotation + ";" +
                "z-index:-251654144;" +
                "mso-wrap-edited:f;" +
                "mso-position-horizontal:center;" +
                "mso-position-horizontal-relative:margin;" +
                "mso-position-vertical:center;" +
                "mso-position-vertical-relative:margin";
    }

    /**
     * 按文字长度和字号估算 Word 水印宽度，并限制在可读范围内。
     *
     * @param options 已归一化的水印参数
     * @return VML shape 使用的宽度，单位 pt
     */
    private int calculateWatermarkWidth(WatermarkOptions options) {
        int textLength = options.getText() == null ? 0 : options.getText().length();
        int calculatedWidth = textLength * options.getFontSize() * WORD_WATERMARK_CHAR_WIDTH_FACTOR;
        // 宽度过小会挤压中文，过大又会遮挡正文，因此用上下限约束。
        return Math.max(WORD_WATERMARK_MIN_WIDTH_PT, Math.min(WORD_WATERMARK_MAX_WIDTH_PT, calculatedWidth));
    }

    /**
     * 将通用水印旋转角转换为 Word VML 可用角度。
     *
     * @param rotation 通用水印旋转角
     * @return 0..359 范围内的 Word VML 角度
     */
    private int normalizeWordRotation(Float rotation) {
        int value = rotation == null ? Math.round(WatermarkOptions.DEFAULT_ROTATION) : Math.round(rotation);
        // Word VML 的视觉方向与常规绘图旋转方向相反，使用 360 - angle 保持斜向水印观感一致。
        return (FULL_CIRCLE_DEGREES - (value % FULL_CIRCLE_DEGREES) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES;
    }

    /**
     * 在正文开头添加简单文本水印作为兜底。
     *
     * @param document 目标 DOCX 文档
     * @param options 已归一化的水印参数
     */
    private void addTextWatermark(XWPFDocument document, WatermarkOptions options) {
        // 插入兜底文本需要段落作为锚点；空文档必须先创建段落。
        if (document.getParagraphs().isEmpty()) {
            document.createParagraph();
        }

        // 插入到首段之前，让兜底水印尽量靠近文档开头可见区域。
        XWPFParagraph firstParagraph = document.getParagraphs().get(0);
        // 使用首段游标插入，可在 POI 能力范围内最大限度保持原有内容顺序。
        XWPFParagraph watermarkParagraph = document.insertNewParagraph(firstParagraph.getCTP().newCursor());
        // 居中对齐让兜底文本更像水印，而不是普通左对齐正文。
        watermarkParagraph.setAlignment(ParagraphAlignment.CENTER);
        // 去除段前段后间距，减少必须使用兜底方案时对正文布局的影响。
        watermarkParagraph.setSpacingBefore(NO_PARAGRAPH_SPACING);
        watermarkParagraph.setSpacingAfter(NO_PARAGRAPH_SPACING);

        // 使用单个 run 承载水印文本，确保所有兜底样式绑定到同一段文本。
        XWPFRun run = watermarkParagraph.createRun();
        run.setText(options.getText());
        // 使用内置字体的字体族名称；实际显示仍取决于 Word 查看环境是否有该字体。
        run.setFontFamily(FONT_FAMILY);
        run.setFontSize(options.getFontSize());
        // Word run 颜色不支持 alpha，因此通过向白色混合来近似配置透明度。
        run.setColor(fadeColor(options));
    }

    /**
     * 通过将配置颜色与白色混合，为 Word 文本 run 近似模拟透明度。
     *
     * @param options 已归一化的水印参数
     * @return XWPFRun#setColor 可接受的六位 RGB 十六进制字符串
     */
    private String fadeColor(WatermarkOptions options) {
        // Word run 颜色只接受 RGB，因此需要剥离 alpha 通道。
        int rgb = options.getColorObject().getRGB() & RGB_COLOR_MASK;
        // 防御性裁剪透明度，避免自定义调用方绕过服务层归一化。
        float opacity = Math.max(MIN_OPACITY, Math.min(MAX_OPACITY, options.getOpacity()));
        // 提取红色通道，用于向白色混合模拟透明度。
        int r = (rgb >> RED_SHIFT_BITS) & COLOR_CHANNEL_MASK;
        // 提取绿色通道，用于向白色混合模拟透明度。
        int g = (rgb >> GREEN_SHIFT_BITS) & COLOR_CHANNEL_MASK;
        // 提取蓝色通道，用于向白色混合模拟透明度。
        int b = rgb & COLOR_CHANNEL_MASK;
        // DOCX run 颜色不携带透明度信息，因此通过与白色混合表达淡化效果。
        r = (int) (r + (COLOR_CHANNEL_MAX - r) * (1 - opacity));
        // 对绿色通道应用同样的透明度近似，保持色相平衡。
        g = (int) (g + (COLOR_CHANNEL_MAX - g) * (1 - opacity));
        // 对蓝色通道应用同样的透明度近似，保持色相平衡。
        b = (int) (b + (COLOR_CHANNEL_MAX - b) * (1 - opacity));
        return String.format("%02X%02X%02X", r, g, b);
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
