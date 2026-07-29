package com.whaleal.ark.cloud.third.sms.metrics;

import java.util.ArrayList;
import java.util.List;

/**
 * 组合多个 {@link SmsMetrics}。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class CompositeSmsMetrics implements SmsMetrics {

    private final List<SmsMetrics> delegates;

    public CompositeSmsMetrics(List<SmsMetrics> delegates) {
        this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
    }

    public static SmsMetrics of(SmsMetrics... items) {
        List<SmsMetrics> list = new ArrayList<>();
        if (items != null) {
            for (SmsMetrics m : items) {
                if (m != null && m != NOOP) {
                    list.add(m);
                }
            }
        }
        if (list.isEmpty()) {
            return NOOP;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        return new CompositeSmsMetrics(list);
    }

    @Override
    public void recordSend(String provider, boolean success, long durationMs, String errorCode) {
        for (SmsMetrics d : delegates) {
            try {
                d.recordSend(provider, success, durationMs, errorCode);
            } catch (Exception ignored) {
                // 指标失败不影响主流程
            }
        }
    }
}
