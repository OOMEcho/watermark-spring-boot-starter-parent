package com.watermark.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 默认水印服务实现，负责按文件扩展名路由到具体格式处理器。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public class DefaultWatermarkService implements WatermarkService {

    private final List<WatermarkHandler> handlers;
    private final WatermarkOptions defaultOptions;

    /**
     * 在 Spring 自动配置或手动装配只提供处理器列表时，使用库默认配置创建服务。
     *
     * @param handlers 用于扩展名路由的具体格式处理器
     */
    public DefaultWatermarkService(List<WatermarkHandler> handlers) {
        // 委托给完整构造器，确保校验和默认值归一化只有一个维护点。
        this(handlers, new WatermarkOptions());
    }

    /**
     * 使用稳定的处理器列表和归一化后的默认参数创建服务。
     *
     * @param handlers 用于扩展名路由的具体格式处理器
     * @param defaultOptions 调用方未传入单次配置时使用的服务级默认参数
     */
    public DefaultWatermarkService(List<WatermarkHandler> handlers, WatermarkOptions defaultOptions) {
        // 没有处理器的服务后续所有请求都会失败，因此在装配阶段快速失败。
        if (handlers == null || handlers.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个水印处理器");
        }
        // 复制并冻结处理器列表，避免外部修改集合导致运行时路由行为变化。
        this.handlers = Collections.unmodifiableList(new ArrayList<WatermarkHandler>(handlers));
        // 默认参数只归一化一次，避免每次使用默认配置时重复执行兜底逻辑。
        this.defaultOptions = (defaultOptions == null ? new WatermarkOptions() : defaultOptions).normalize();
    }

    @Override
    public void addWatermark(InputStream input, OutputStream output, String fileName) throws Exception {
        // 统一走带参数的重载，保证默认配置和自定义配置的处理路径一致。
        addWatermark(input, output, fileName, defaultOptions);
    }

    @Override
    public void addWatermark(InputStream input, OutputStream output, String fileName, WatermarkOptions options) throws Exception {
        // 流的生命周期归调用方所有，但格式库无法从空输入流恢复，因此必须提前校验。
        Objects.requireNonNull(input, "输入流不能为空");
        // 输出流同样由调用方管理；提前校验可避免做完部分处理后没有写出目标。
        Objects.requireNonNull(output, "输出流不能为空");
        // starter 按扩展名路由，空文件名没有安全兜底策略。
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 在解析流之前先查找格式处理器，确保不支持的文件不会消耗输入流。
        WatermarkHandler handler = findHandler(fileName);
        // 在服务层归一化单次配置，让处理器只关注具体文件格式处理。
        WatermarkOptions effectiveOptions = (options == null ? defaultOptions : options).normalize();
        // 委托给选中的格式处理器，因为不同文档族的解析和写出规则完全不同。
        handler.addWatermark(input, output, fileName, effectiveOptions);
    }

    @Override
    public byte[] addWatermark(byte[] input, String fileName) throws Exception {
        // 复用带参数的重载，保证字节数组入口和流式入口的默认行为一致。
        return addWatermark(input, fileName, defaultOptions);
    }

    @Override
    public byte[] addWatermark(byte[] input, String fileName, WatermarkOptions options) throws Exception {
        // 空字节数组引用一定是调用方错误，格式处理器无法安全地区分它和空文件。
        Objects.requireNonNull(input, "输入字节不能为空");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // 转成流复用同一套格式路由和处理逻辑，避免两个入口行为漂移。
        addWatermark(new ByteArrayInputStream(input), output, fileName, options);
        // 返回新的字节数组，调用方可直接持久化或传输生成后的水印文件。
        return output.toByteArray();
    }

    /**
     * 查找第一个声明支持给定文件名的处理器。
     *
     * @param fileName 作为路由键的调用方文件名
     * @return 匹配的处理器
     */
    private WatermarkHandler findHandler(String fileName) {
        for (WatermarkHandler handler : handlers) {
            // 构造器已冻结处理器顺序；采用第一个匹配项，便于未来自定义处理器覆盖默认行为。
            if (handler.supports(fileName)) {
                return handler;
            }
        }
        // 不支持的格式必须显式失败，避免调用方误认为原文件已经成功加水印。
        throw new UnsupportedOperationException("不支持的文件类型: " + fileName);
    }
}
