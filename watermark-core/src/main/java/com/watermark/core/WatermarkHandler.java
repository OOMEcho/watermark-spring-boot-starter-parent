package com.watermark.core;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件格式水印处理器接口，定义单一格式的识别与处理能力。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public interface WatermarkHandler {

    /**
     * 判断当前处理器是否负责给定文件格式。
     *
     * <p>starter 有意按文件扩展名路由，而不是读取内容探测格式，这样在大文件被格式库打开之前即可低成本、确定性地选择处理器。</p>
     *
     * @param fileName 调用方传入的原始文件名或逻辑文件名
     * @return 当前处理器可处理时返回 {@code true}；返回 {@code false} 时服务会继续尝试其他处理器
     */
    boolean supports(String fileName);

    /**
     * 为当前处理器支持的单一文件格式添加水印。
     *
     * <p>处理器负责具体格式的解析与写出。服务层会在委托前归一化通用配置，但处理器仍可根据格式限制追加处理，例如图片输出格式兜底。</p>
     *
     * @param input 源文件输入流；处理器会读取但不能关闭调用方拥有的流
     * @param output 目标输出流；处理器会写入但不能关闭调用方拥有的流
     * @param fileName 用于格式内决策的文件名，例如保留图片输出类型
     * @param options 服务层传入的已归一化水印配置
     * @throws Exception 解析、水印渲染或目标文件写出失败时抛出；异常前可能已经产生部分输出
     */
    void addWatermark(InputStream input, OutputStream output, String fileName, WatermarkOptions options) throws Exception;
}
