package com.whaleal.ark.cloud.third.sms.config;

import com.whaleal.ark.cloud.third.sms.metrics.CollectorSmsMetrics;
import com.whaleal.ark.cloud.third.sms.metrics.CompositeSmsMetrics;
import com.whaleal.ark.cloud.third.sms.metrics.MicrometerSmsMetrics;
import com.whaleal.ark.cloud.third.sms.metrics.SmsMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 存在 Micrometer {@link MeterRegistry} 时，用其覆盖默认 {@link SmsMetrics}。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@AutoConfiguration(before = SmsAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class SmsMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(SmsMetrics.class)
    public SmsMetrics micrometerSmsMetrics(MeterRegistry registry) {
        return CompositeSmsMetrics.of(CollectorSmsMetrics.INSTANCE, new MicrometerSmsMetrics(registry));
    }
}
