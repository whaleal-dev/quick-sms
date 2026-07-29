package com.whaleal.ark.cloud.third.sms.metrics;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.util.MetricsCollector;

import java.time.Duration;

/**
 * 将 {@link SmsMetrics} 桥接到内置 {@link MetricsCollector}。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class CollectorSmsMetrics implements SmsMetrics {

    public static final CollectorSmsMetrics INSTANCE = new CollectorSmsMetrics();

    @Override
    public void recordSend(String provider, boolean success, long durationMs, String errorCode) {
        SmsProviderType type = SmsProviderType.tryFromCode(provider).orElse(null);
        if (type != null) {
            MetricsCollector.getInstance().recordSendOperation(
                    type, success, Duration.ofMillis(Math.max(0, durationMs)), null);
            if (!success && errorCode != null) {
                MetricsCollector.getInstance().recordException(type, errorCode, errorCode);
            }
        }
    }
}
