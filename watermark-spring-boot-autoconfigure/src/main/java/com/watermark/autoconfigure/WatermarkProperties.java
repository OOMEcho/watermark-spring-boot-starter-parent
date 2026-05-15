package com.watermark.autoconfigure;

import com.watermark.core.WatermarkOptions;
import com.watermark.core.WatermarkPosition;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 水印自动配置属性，承接业务项目中 watermark 前缀下的外部化配置。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
@Data
@ConfigurationProperties(prefix = "watermark")
public class WatermarkProperties {

    private boolean enabled = true;

    private String text = WatermarkOptions.DEFAULT_TEXT;

    private Float opacity = WatermarkOptions.DEFAULT_OPACITY;

    private Integer fontSize = WatermarkOptions.DEFAULT_FONT_SIZE;

    private String color = WatermarkOptions.DEFAULT_COLOR;

    private Float rotation = WatermarkOptions.DEFAULT_ROTATION;

    private WatermarkPosition position = WatermarkOptions.DEFAULT_POSITION;

    private String fontPath = WatermarkOptions.DEFAULT_FONT_PATH;

    private Boolean excelProtectSheet = WatermarkOptions.DEFAULT_EXCEL_PROTECT_SHEET;

    private String excelProtectPassword = WatermarkOptions.DEFAULT_EXCEL_PROTECT_PASSWORD;

    /**
     * 将 Spring Boot 外部化配置转换为 core 模块使用的水印参数。
     *
     * @return {@code watermark-core} 使用的已归一化参数
     */
    public WatermarkOptions toOptions() {
        // 跨模块 DTO 转换可避免 watermark-core 依赖 Spring Boot 绑定类型。
        return WatermarkOptions.builder()
                .text(text)
                .opacity(opacity)
                .fontSize(fontSize)
                .color(color)
                .rotation(rotation)
                .position(position)
                .fontPath(fontPath)
                .excelProtectSheet(excelProtectSheet)
                .excelProtectPassword(excelProtectPassword)
                .build()
                // 外部配置可能省略或传入非法字段，绑定后必须再次归一化。
                .normalize();
    }
}
