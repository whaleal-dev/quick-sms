package com.whaleal.ark.cloud.third.sms.metrics;

/**
 * 发送侧可观测性钩子（Micrometer / 日志 / 自定义均可实现）。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public interface SmsMetrics {

    SmsMetrics NOOP = (provider, success, durationMs, errorCode) -> {
    };

    /**
     * @param provider   通道编码或枚举名
     * @param success    是否成功
     * @param durationMs 耗时毫秒
     * @param errorCode  失败时的统一错误码，成功可为 null
     */
    void recordSend(String provider, boolean success, long durationMs, String errorCode);
}
