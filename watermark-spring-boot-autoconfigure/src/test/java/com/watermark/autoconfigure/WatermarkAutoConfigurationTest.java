package com.watermark.autoconfigure;

import com.watermark.core.WatermarkService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WatermarkAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WatermarkAutoConfiguration.class));

    @Test
    void createsWatermarkServiceByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(WatermarkService.class));
    }

    @Test
    void backsOffWhenDisabled() {
        contextRunner
                .withPropertyValues("watermark.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(WatermarkService.class));
    }
}
