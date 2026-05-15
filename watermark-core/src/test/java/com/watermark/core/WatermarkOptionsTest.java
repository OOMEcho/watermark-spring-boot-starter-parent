package com.watermark.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatermarkOptionsTest {

    @Test
    void normalizeUsesDefaultOpacityForNonFiniteValues() {
        assertEquals(WatermarkOptions.DEFAULT_OPACITY, WatermarkOptions.builder()
                .opacity(Float.NaN)
                .build()
                .normalize()
                .getOpacity());
        assertEquals(WatermarkOptions.DEFAULT_OPACITY, WatermarkOptions.builder()
                .opacity(Float.POSITIVE_INFINITY)
                .build()
                .normalize()
                .getOpacity());
        assertEquals(WatermarkOptions.DEFAULT_OPACITY, WatermarkOptions.builder()
                .opacity(Float.NEGATIVE_INFINITY)
                .build()
                .normalize()
                .getOpacity());
    }

    @Test
    void normalizeUsesDefaultRotationForNonFiniteValues() {
        assertEquals(WatermarkOptions.DEFAULT_ROTATION, WatermarkOptions.builder()
                .rotation(Float.NaN)
                .build()
                .normalize()
                .getRotation());
        assertEquals(WatermarkOptions.DEFAULT_ROTATION, WatermarkOptions.builder()
                .rotation(Float.POSITIVE_INFINITY)
                .build()
                .normalize()
                .getRotation());
        assertEquals(WatermarkOptions.DEFAULT_ROTATION, WatermarkOptions.builder()
                .rotation(Float.NEGATIVE_INFINITY)
                .build()
                .normalize()
                .getRotation());
    }
}
