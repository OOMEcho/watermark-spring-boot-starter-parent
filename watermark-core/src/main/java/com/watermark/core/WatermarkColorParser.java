package com.watermark.core;

import java.awt.Color;
import java.util.Locale;

/**
 * 水印颜色解析工具，统一处理配置颜色和默认颜色兜底。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
final class WatermarkColorParser {

    private static final String HEX_COLOR_PREFIX = "#";
    private static final String HEX_NUMBER_PREFIX = "0x";

    private WatermarkColorParser() {
    }

    /**
     * 将配置中的颜色字符串解析为 AWT 颜色，并保证无效配置不会中断水印处理。
     *
     * @param value API 参数或 Spring 配置中的颜色值
     * @return 解析后的颜色；为空或不支持时返回灰色
     */
    static Color parse(String value) {
        // 颜色缺失属于配置省略，不应被视为文档处理失败，因此使用灰色作为安全默认值。
        if (value == null || value.trim().isEmpty()) {
            return Color.GRAY;
        }

        String color = value.trim();
        try {
            // 十六进制颜色常见于应用配置，可避免依赖 Java 颜色常量字段名。
            if (color.startsWith(HEX_COLOR_PREFIX)) {
                return Color.decode(color);
            }
            // 支持 0x 形式，方便团队沿用 Java 风格的外部化颜色配置。
            if (color.toLowerCase(Locale.ROOT).startsWith(HEX_NUMBER_PREFIX)) {
                return Color.decode(color);
            }
            // 具名颜色复用 java.awt.Color 公共常量，减少 starter 自己维护颜色表的成本。
            return (Color) Color.class.getField(color.toUpperCase(Locale.ROOT)).get(null);
        } catch (Exception ignored) {
            // 无效颜色不应阻断水印处理；灰色兜底既可见又便于排查配置问题。
            return Color.GRAY;
        }
    }
}
