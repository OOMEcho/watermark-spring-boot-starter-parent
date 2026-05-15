package com.watermark.sample;

import com.watermark.core.WatermarkService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 最小 Spring Boot 使用示例，演示通过自动装配注入 WatermarkService。
 *
 * @author xuesong.lei
 * @since 2026/05/15
 */
@SpringBootApplication
@EnableConfigurationProperties(SampleProperties.class)
public class SampleApplication implements CommandLineRunner {

    private final WatermarkService watermarkService;
    private final SampleProperties sampleProperties;

    public SampleApplication(WatermarkService watermarkService, SampleProperties sampleProperties) {
        this.watermarkService = watermarkService;
        this.sampleProperties = sampleProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (!sampleProperties.hasInput()) {
            System.out.println("未配置 sample.input，示例应用已启动但不会处理文件。");
            System.out.println("示例：mvn -pl watermark-spring-boot-sample spring-boot:run -Dspring-boot.run.arguments=--sample.input=D:/temp/demo.pdf,--sample.output=D:/temp/demo-watermarked.pdf,--sample.file-name=demo.pdf");
            return;
        }

        Path input = sampleProperties.inputPath();
        if (!Files.isRegularFile(input)) {
            System.out.println("输入文件不存在: " + input);
            return;
        }

        Path output = sampleProperties.outputPath(input);
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String fileName = sampleProperties.resolveFileName(input);
        try (InputStream inputStream = Files.newInputStream(input);
             OutputStream outputStream = Files.newOutputStream(output)) {
            watermarkService.addWatermark(inputStream, outputStream, fileName);
        }
        System.out.println("水印文件已生成: " + output);
    }
}
