package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.core.SmsModuleManager;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.guard.PhoneBlacklist;
import com.whaleal.ark.cloud.third.sms.guard.RateLimiter;
import com.whaleal.ark.cloud.third.sms.metrics.CollectorSmsMetrics;
import com.whaleal.ark.cloud.third.sms.metrics.CompositeSmsMetrics;
import com.whaleal.ark.cloud.third.sms.metrics.SmsMetrics;
import com.whaleal.ark.cloud.third.sms.policy.ChannelStrategy;
import com.whaleal.ark.cloud.third.sms.policy.OrderChannelStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯 Java 场景下的 {@link SmsClient} 构建器（无需 Spring / 无需 yml）
 * <p>仅设置默认供应商与非敏感项；秘钥与供应商可在每次 {@link SmsSendRequest} 中动态传入。</p>
 *
 * @author whaleal-dev
 * @author 恒哥
 */
public final class SmsClientBuilder {

    private SmsProviderConfig baseConfig = SmsProviderConfig.builder()
            .providerType(SmsProviderType.MOCK)
            .name(SmsProviderType.MOCK.getDisplayName())
            .build();

    private final List<SmsChannel> failoverChannels = new ArrayList<>();
    private ChannelStrategy channelStrategy = OrderChannelStrategy.INSTANCE;
    private PhoneBlacklist blacklist;
    private RateLimiter rateLimiter;
    private SmsMetrics metrics = CollectorSmsMetrics.INSTANCE;

    public SmsClientBuilder provider(SmsProviderType provider) {
        this.baseConfig = baseConfig.toBuilder()
                .providerType(provider)
                .name(provider.getDisplayName())
                .build();
        return this;
    }

    public SmsClientBuilder region(String region) {
        this.baseConfig = baseConfig.toBuilder().region(region).build();
        return this;
    }

    public SmsClientBuilder signName(String signName) {
        this.baseConfig = baseConfig.toBuilder().signName(signName).build();
        return this;
    }

    public SmsClientBuilder defaultFrom(String defaultFrom) {
        this.baseConfig = baseConfig.toBuilder().defaultFrom(defaultFrom).build();
        return this;
    }

    public SmsClientBuilder baseUrl(String baseUrl) {
        this.baseConfig = baseConfig.toBuilder().baseUrl(baseUrl).build();
        return this;
    }

    public SmsClientBuilder deliveryReceiptUrl(String deliveryReceiptUrl) {
        this.baseConfig = baseConfig.toBuilder().deliveryReceiptUrl(deliveryReceiptUrl).build();
        return this;
    }

    public SmsClientBuilder callbackUrl(String callbackUrl) {
        this.baseConfig = baseConfig.toBuilder().callbackUrl(callbackUrl).build();
        return this;
    }

    /** 追加 failover 通道（按添加顺序尝试） */
    public SmsClientBuilder addChannel(SmsChannel channel) {
        if (channel != null) {
            this.failoverChannels.add(channel);
        }
        return this;
    }

    public SmsClientBuilder channels(List<SmsChannel> channels) {
        this.failoverChannels.clear();
        if (channels != null) {
            this.failoverChannels.addAll(channels);
        }
        return this;
    }

    public SmsClientBuilder channelStrategy(ChannelStrategy strategy) {
        if (strategy != null) {
            this.channelStrategy = strategy;
        }
        return this;
    }

    public SmsClientBuilder blacklist(PhoneBlacklist blacklist) {
        this.blacklist = blacklist;
        return this;
    }

    public SmsClientBuilder rateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        return this;
    }

    /** 追加自定义指标（默认已挂载内置 MetricsCollector） */
    public SmsClientBuilder metrics(SmsMetrics metrics) {
        this.metrics = CompositeSmsMetrics.of(CollectorSmsMetrics.INSTANCE, metrics);
        return this;
    }

    public SmsClientBuilder metricsOnly(SmsMetrics metrics) {
        this.metrics = metrics == null ? SmsMetrics.NOOP : metrics;
        return this;
    }

    public SmsClient build() {
        SmsMetrics effective = metrics == null ? SmsMetrics.NOOP : metrics;
        DefaultSmsClient primary = failoverChannels.isEmpty()
                ? new DefaultSmsClient(baseConfig, new SmsModuleManager(), blacklist, rateLimiter, effective)
                : new DefaultSmsClient(baseConfig, new SmsModuleManager(), null, null, effective);
        if (failoverChannels.isEmpty()) {
            return primary;
        }
        return new FailoverSmsClient(primary, failoverChannels, channelStrategy, blacklist, rateLimiter, effective);
    }
}
