package com.watermark.core;

/**
 * 水印位置枚举，定义单点水印和铺满水印的语义位置。
 *
 * @author xuesong.lei
 * @since 2026/05/14
 */
public enum WatermarkPosition {
    /** 在页面或图片中心附近放置单个水印。 */
    CENTER,
    /** 在左上角安全边距附近放置单个水印。 */
    TOP_LEFT,
    /** 在右上角安全边距附近放置单个水印。 */
    TOP_RIGHT,
    /** 在左下角安全边距附近放置单个水印。 */
    BOTTOM_LEFT,
    /** 在右下角安全边距附近放置单个水印。 */
    BOTTOM_RIGHT,
    /** 在整个页面、图片或表格区域平铺旋转水印。 */
    DIAGONAL
}
