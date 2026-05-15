package com.watermark.sample;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 示例应用自身的文件输入输出配置。
 *
 * @author xuesong.lei
 * @since 2026/05/15
 */
@ConfigurationProperties(prefix = "sample")
public class SampleProperties {

    private String input;
    private String output;
    private String fileName;

    public boolean hasInput() {
        return input != null && !input.trim().isEmpty();
    }

    public Path inputPath() {
        return Paths.get(input.trim());
    }

    public Path outputPath(Path inputPath) {
        if (output != null && !output.trim().isEmpty()) {
            return Paths.get(output.trim());
        }
        String sourceName = inputPath.getFileName().toString();
        int dotIndex = sourceName.lastIndexOf('.');
        String outputName = dotIndex < 0
                ? sourceName + "-watermarked"
                : sourceName.substring(0, dotIndex) + "-watermarked" + sourceName.substring(dotIndex);
        Path parent = inputPath.getParent();
        return parent == null ? Paths.get(outputName) : parent.resolve(outputName);
    }

    public String resolveFileName(Path inputPath) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            return fileName.trim();
        }
        return inputPath.getFileName().toString();
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
