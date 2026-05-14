package com.watermark.core;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 水印服务接口，面向业务项目提供统一的文件水印入口。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public interface WatermarkService {

    /**
     * 使用服务级默认配置为输入文件添加水印。
     *
     * <p>实现类会根据 {@code fileName} 选择对应的 {@link WatermarkHandler}；调用方必须传入原始文件名或带有可靠扩展名的逻辑文件名，
     * 因为本 starter 以扩展名作为格式路由规则。</p>
     *
     * @param input 源文件输入流；方法会读取但不会关闭，流生命周期仍由调用方负责
     * @param output 水印文件输出流；方法会写入但不会关闭，流生命周期仍由调用方负责
     * @param fileName 用于识别文件格式的文件名，例如 {@code demo.pdf}
     * @throws Exception 文件格式不支持、源内容无法解析或目标格式写入失败时抛出；异常发生前 {@code output} 可能已经写入部分字节
     * @implSpec 实现类可能会在内部缓冲数据，并可能根据底层文件格式库的要求修改文档元数据或正文内容。
     */
    void addWatermark(InputStream input, OutputStream output, String fileName) throws Exception;

    /**
     * 使用本次调用指定的配置为输入文件添加水印。
     *
     * <p>{@code options} 仅覆盖本次调用的服务默认配置。允许传入 {@code null}，此时应按服务级默认配置处理，避免业务代码额外分支。</p>
     *
     * @param input 源文件输入流；方法会读取但不会关闭，流生命周期仍由调用方负责
     * @param output 水印文件输出流；方法会写入但不会关闭，流生命周期仍由调用方负责
     * @param fileName 用于识别文件格式的文件名，例如 {@code report.xlsx}
     * @param options 水印文字、透明度、字体、颜色、旋转角度和位置配置；{@code null} 表示使用默认配置
     * @throws Exception 文件格式不支持、源内容无法解析、字体加载失败且无可用兜底字体或目标格式写入失败时抛出；异常发生前 {@code output} 可能已经写入部分字节
     * @implSpec 实现类应先归一化默认配置，再委托给具体格式处理器。
     */
    void addWatermark(InputStream input, OutputStream output, String fileName, WatermarkOptions options) throws Exception;

    /**
     * 使用服务级默认配置为内存中的文件字节添加水印。
     *
     * <p>该便捷方法适用于业务服务层已经拿到字节数组的场景。方法会为结果分配新的字节数组，因此超大文件优先使用流式重载方法。</p>
     *
     * @param input 源文件字节；方法只读取，不修改原数组
     * @param fileName 用于识别文件格式的文件名，例如 {@code image.png}
     * @return 对应处理器生成的水印文件字节
     * @throws Exception 文件格式不支持、输入字节与声明格式不匹配或写出结果失败时抛出
     * @implSpec 实现类可能创建临时内存缓冲区；调用方应在上游控制文件大小。
     */
    byte[] addWatermark(byte[] input, String fileName) throws Exception;

    /**
     * 使用本次调用指定的配置为内存中的文件字节添加水印。
     *
     * <p>该方法与流式重载方法使用相同的路由规则和副作用边界，但内部会自行创建临时输入输出缓冲区。传入 {@code null} 配置时必须使用服务默认值。</p>
     *
     * @param input 源文件字节；方法只读取，不修改原数组
     * @param fileName 用于识别文件格式的文件名，例如 {@code contract.docx}
     * @param options 水印文字、透明度、字体、颜色、旋转角度和位置配置；{@code null} 表示使用默认配置
     * @return 对应处理器生成的水印文件字节
     * @throws Exception 文件格式不支持、输入字节与声明格式不匹配、配置无法应用或写出结果失败时抛出
     * @implSpec 实现类应复用流式处理流程，确保不同入口的格式行为一致。
     */
    byte[] addWatermark(byte[] input, String fileName, WatermarkOptions options) throws Exception;
}
