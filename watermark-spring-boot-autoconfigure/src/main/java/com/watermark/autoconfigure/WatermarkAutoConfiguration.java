package com.watermark.autoconfigure;

import com.watermark.core.DefaultWatermarkService;
import com.watermark.core.WatermarkHandler;
import com.watermark.core.WatermarkService;
import com.watermark.core.font.WatermarkFontLoader;
import com.watermark.core.handler.ExcelWatermarkHandler;
import com.watermark.core.handler.ImageWatermarkHandler;
import com.watermark.core.handler.PdfWatermarkHandler;
import com.watermark.core.handler.WordWatermarkHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 水印 Spring Boot 自动配置类，负责向业务项目注册默认水印服务和各格式处理器。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
@Configuration
@ConditionalOnClass(WatermarkService.class)
@ConditionalOnProperty(prefix = "watermark", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WatermarkProperties.class)
public class WatermarkAutoConfiguration {

    /**
     * 提供由文本渲染处理器共享的字体加载器。
     *
     * @return 支持 classpath、文件路径和系统字体兜底的默认字体加载器
     */
    @Bean
    @ConditionalOnMissingBean
    public WatermarkFontLoader watermarkFontLoader() {
        // 字体加载器作为独立 Bean 暴露，业务项目可只替换字体策略而不必替换所有处理器。
        return new WatermarkFontLoader();
    }

    /**
     * 在业务项目未提供自定义实现时注册图片处理器。
     *
     * @param fontLoader 共享字体加载器 Bean
     * @return 图片水印处理器
     */
    @Bean
    @ConditionalOnMissingBean(ImageWatermarkHandler.class)
    public ImageWatermarkHandler imageWatermarkHandler(WatermarkFontLoader fontLoader) {
        // 跨模块构造时注入 Spring 管理的字体加载器，确保 core 处理器复用业务侧配置。
        return new ImageWatermarkHandler(fontLoader);
    }

    /**
     * 在业务项目未提供自定义实现时注册 XLSX 处理器。
     *
     * @param fontLoader 共享字体加载器 Bean
     * @return Excel 水印处理器
     */
    @Bean
    @ConditionalOnMissingBean(ExcelWatermarkHandler.class)
    public ExcelWatermarkHandler excelWatermarkHandler(WatermarkFontLoader fontLoader) {
        // Excel 通过嵌入生成的 PNG 图层实现水印，因此需要复用图片渲染使用的字体加载器。
        return new ExcelWatermarkHandler(fontLoader);
    }

    /**
     * 在业务项目未提供自定义实现时注册 DOCX 处理器。
     *
     * @param fontLoader 共享字体加载器 Bean
     * @return Word 水印处理器
     */
    @Bean
    @ConditionalOnMissingBean(WordWatermarkHandler.class)
    public WordWatermarkHandler wordWatermarkHandler(WatermarkFontLoader fontLoader) {
        // Word 页眉水印需要从配置字体路径中解析字体族名称，因此复用同一套字体加载策略。
        return new WordWatermarkHandler(fontLoader);
    }

    /**
     * 在业务项目未提供自定义实现时注册 PDF 处理器。
     *
     * @param fontLoader 共享字体加载器 Bean
     * @return PDF 水印处理器
     */
    @Bean
    @ConditionalOnMissingBean(PdfWatermarkHandler.class)
    public PdfWatermarkHandler pdfWatermarkHandler(WatermarkFontLoader fontLoader) {
        // PDF 会把字体嵌入文档，因此必须使用同一套可配置字体加载策略。
        return new PdfWatermarkHandler(fontLoader);
    }

    /**
     * 注册业务项目使用的公共水印服务 Bean。
     *
     * @param handlers 自动配置或用户代码贡献的所有格式处理器
     * @param properties 外部化绑定得到的水印默认配置
     * @return 基于扩展名路由的水印服务
     */
    @Bean
    @ConditionalOnMissingBean
    public WatermarkService watermarkService(List<WatermarkHandler> handlers, WatermarkProperties properties) {
        // 进入 watermark-core 前先把 Spring 配置对象转换为 core 参数对象。
        // 注入处理器列表允许业务项目新增或替换格式支持，而无需改动服务 Bean。
        return new DefaultWatermarkService(handlers, properties.toOptions());
    }
}
