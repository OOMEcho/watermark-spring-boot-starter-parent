package com.watermark.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.Color;

/**
 * 水印参数对象，承载文字、透明度、字体、颜色、旋转角度、位置和 Excel 保护策略等通用配置。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatermarkOptions {

    public static final String DEFAULT_FONT_PATH = "classpath:/fonts/SourceHanSerifSC-Regular.otf";
    public static final String DEFAULT_TEXT = "watermark";
    public static final float DEFAULT_OPACITY = 0.3f;
    public static final int DEFAULT_FONT_SIZE = 40;
    public static final String DEFAULT_COLOR = "GRAY";
    public static final float DEFAULT_ROTATION = 45f;
    public static final WatermarkPosition DEFAULT_POSITION = WatermarkPosition.DIAGONAL;
    public static final boolean DEFAULT_EXCEL_PROTECT_SHEET = true;
    public static final String DEFAULT_EXCEL_PROTECT_PASSWORD = "watermark";
    private static final float MIN_OPACITY = 0f;
    private static final float MAX_OPACITY = 1f;

    @Builder.Default
    private String text = DEFAULT_TEXT;

    @Builder.Default
    private Float opacity = DEFAULT_OPACITY;

    @Builder.Default
    private Integer fontSize = DEFAULT_FONT_SIZE;

    @Builder.Default
    private String color = DEFAULT_COLOR;

    @Builder.Default
    private Float rotation = DEFAULT_ROTATION;

    @Builder.Default
    private WatermarkPosition position = DEFAULT_POSITION;

    @Builder.Default
    private String fontPath = DEFAULT_FONT_PATH;

    @Builder.Default
    private Boolean excelProtectSheet = DEFAULT_EXCEL_PROTECT_SHEET;

    @Builder.Default
    private String excelProtectPassword = DEFAULT_EXCEL_PROTECT_PASSWORD;

    /**
     * 将面向用户的颜色配置转换为图片和文档渲染器可使用的 AWT 颜色。
     *
     * @return 解析后的颜色；配置值无效时返回灰色
     */
    public Color getColorObject() {
        // 颜色解析集中在一个工具中，确保所有文件格式对无效用户输入使用同一套兜底规则。
        return WatermarkColorParser.parse(color);
    }

    /**
     * 返回一个新的参数对象，并将所有空值或非法值替换为稳定默认值。
     *
     * @return 归一化后的参数对象；当前实例不会被修改，避免影响复用 builder 或复用对象的调用方
     */
    public WatermarkOptions normalize() {
        WatermarkOptions options = new WatermarkOptions();
        // 空文字无法生成可见水印，因此统一使用库默认文字。
        options.setText(text == null || text.trim().isEmpty() ? DEFAULT_TEXT : text);
        // 透明度必须处于渲染器支持的 0..1 范围，提前裁剪可避免各格式库分别失败。
        options.setOpacity(clampOpacity(opacity));
        // 小于 1 的字号不可渲染，统一默认值可避免每个处理器重复判断。
        options.setFontSize(fontSize == null || fontSize < 1 ? DEFAULT_FONT_SIZE : fontSize);
        // 空颜色按缺省配置处理；格式错误的颜色再交由解析器兜底。
        options.setColor(color == null || color.trim().isEmpty() ? DEFAULT_COLOR : color);
        // 默认旋转角适合斜向水印，并被所有内置处理器共同使用。
        options.setRotation(normalizeRotation(rotation));
        // 位置在这里归一化，因为处理器内部假设一定存在具体位置策略。
        options.setPosition(position == null ? DEFAULT_POSITION : position);
        // 字体路径兜底保证用户未配置时仍能使用内置中文字体。
        options.setFontPath(fontPath == null || fontPath.trim().isEmpty() ? DEFAULT_FONT_PATH : fontPath);
        // Excel 保护策略默认保持历史行为，但允许调用方显式关闭。
        options.setExcelProtectSheet(excelProtectSheet == null ? DEFAULT_EXCEL_PROTECT_SHEET : excelProtectSheet);
        // 空密码没有明确业务语义，按默认密码兜底可避免生成不可预期保护配置。
        options.setExcelProtectPassword(excelProtectPassword == null || excelProtectPassword.trim().isEmpty()
                ? DEFAULT_EXCEL_PROTECT_PASSWORD
                : excelProtectPassword);
        return options;
    }

    /**
     * 将透明度裁剪到渲染器支持的范围内。
     *
     * @param value 用户传入的透明度
     * @return 0..1 闭区间内的透明度；用户未传入时返回默认值
     */
    private Float clampOpacity(Float value) {
        // 缺省透明度应使用文档默认值，而不是生成完全不透明的水印。
        if (value == null) {
            return DEFAULT_OPACITY;
        }
        // NaN 或无穷值无法被 AWT/PDF 渲染器可靠处理，应回落默认配置。
        if (value.isNaN() || value.isInfinite()) {
            return DEFAULT_OPACITY;
        }
        // 负数透明度对 AWT/PDF 渲染器非法；裁剪比直接失败更符合配置容错预期。
        if (value < MIN_OPACITY) {
            return MIN_OPACITY;
        }
        // 大于 1 的透明度对 AWT/PDF 渲染器非法；裁剪到 1 可保留可见水印。
        if (value > MAX_OPACITY) {
            return MAX_OPACITY;
        }
        return value;
    }

    /**
     * 将旋转角归一化为可渲染的有限浮点值。
     *
     * @param value 用户传入的旋转角
     * @return 可被各格式处理器安全使用的旋转角
     */
    private Float normalizeRotation(Float value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return DEFAULT_ROTATION;
        }
        return value;
    }
}
