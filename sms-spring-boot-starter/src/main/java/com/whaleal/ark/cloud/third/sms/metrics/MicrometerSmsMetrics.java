package com.whaleal.ark.cloud.third.sms.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer 实现：sms.send / sms.send.duration，按 provider、result 打标。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class MicrometerSmsMetrics implements SmsMetrics {

    private final MeterRegistry registry;

    public MicrometerSmsMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordSend(String provider, boolean success, long durationMs, String errorCode) {
        String p = provider == null ? "unknown" : provider;
        Counter.builder("sms.send")
                .tag("provider", p)
                .tag("result", success ? "success" : "failure")
                .tag("error", success || errorCode == null ? "none" : errorCode)
                .register(registry)
                .increment();
        Timer.builder("sms.send.duration")
                .tag("provider", p)
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
    }
}
