package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.error.SmsErrorCodes;
import com.whaleal.ark.cloud.third.sms.guard.PhoneBlacklist;
import com.whaleal.ark.cloud.third.sms.guard.RateLimiter;
import com.whaleal.ark.cloud.third.sms.metrics.SmsMetrics;
import com.whaleal.ark.cloud.third.sms.policy.ChannelStrategy;
import com.whaleal.ark.cloud.third.sms.policy.OrderChannelStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 多通道顺序 failover 客户端：按策略尝试通道，直到成功或全部失败。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@Slf4j
public class FailoverSmsClient implements SmsClient {

    private final DefaultSmsClient delegate;
    private final List<SmsChannel> channels;
    private final ChannelStrategy strategy;
    private final PhoneBlacklist blacklist;
    private final RateLimiter rateLimiter;
    private final SmsMetrics metrics;

    public FailoverSmsClient(DefaultSmsClient delegate,
                             List<SmsChannel> channels,
                             ChannelStrategy strategy,
                             PhoneBlacklist blacklist,
                             RateLimiter rateLimiter) {
        this(delegate, channels, strategy, blacklist, rateLimiter, null);
    }

    public FailoverSmsClient(DefaultSmsClient delegate,
                             List<SmsChannel> channels,
                             ChannelStrategy strategy,
                             PhoneBlacklist blacklist,
                             RateLimiter rateLimiter,
                             SmsMetrics metrics) {
        this.delegate = delegate;
        this.channels = channels == null ? List.of() : List.copyOf(channels);
        this.strategy = strategy == null ? OrderChannelStrategy.INSTANCE : strategy;
        this.blacklist = blacklist;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics == null ? SmsMetrics.NOOP : metrics;
    }

    @Override
    public SmsSendResult send(SmsSendRequest request) {
        long start = System.currentTimeMillis();
        if (request == null) {
            return finish("failover", SmsSendResult.failure(null, SmsErrorCodes.INVALID_REQUEST, "请求不能为空"), start);
        }
        if (blacklist != null && blacklist.isBlocked(request.getTo())) {
            return finish("failover", SmsSendResult.failure(request.getTo(), SmsErrorCodes.BLACKLISTED, "号码在黑名单中"), start);
        }
        if (rateLimiter != null && !rateLimiter.tryAcquire(request.getTo())) {
            return finish("failover", SmsSendResult.failure(request.getTo(), SmsErrorCodes.RATE_LIMITED, "发送过于频繁"), start);
        }

        if (request.getProvider() != null || (request.getProviderCode() != null && !request.getProviderCode().isBlank())) {
            return delegate.send(request);
        }

        List<SmsChannel> ordered = strategy.order(channels);
        if (ordered.isEmpty()) {
            return delegate.send(request);
        }

        List<String> errors = new ArrayList<>();
        for (SmsChannel channel : ordered) {
            SmsSendRequest attempt = SmsSendRequest.builder()
                    .to(request.getTo())
                    .content(request.getContent())
                    .from(request.getFrom())
                    .templateId(request.getTemplateId())
                    .templateParams(request.getTemplateParams())
                    .referenceId(request.getReferenceId())
                    .callbackUrl(request.getCallbackUrl())
                    .provider(channel.getProvider())
                    .providerCode(channel.getProviderCode())
                    .credentials(channel.getCredentials() != null ? channel.getCredentials() : request.getCredentials())
                    .build();
            try {
                SmsSendResult result = delegate.send(attempt);
                if (result.isSuccess()) {
                    return result;
                }
                errors.add((channel.getProviderCode() != null ? channel.getProviderCode() : String.valueOf(channel.getProvider()))
                        + ":" + result.getErrorCode() + "/" + result.getErrorMessage());
                log.warn("通道发送失败，尝试下一个: {}", errors.get(errors.size() - 1));
            } catch (Exception e) {
                errors.add(String.valueOf(channel.getProviderCode()) + ":" + e.getMessage());
                log.warn("通道异常，尝试下一个", e);
            }
        }
        return finish("failover", SmsSendResult.failure(request.getTo(), SmsErrorCodes.ALL_CHANNELS_FAILED,
                "全部通道失败: " + String.join(" | ", errors)), start);
    }

    private SmsSendResult finish(String provider, SmsSendResult result, long startMs) {
        try {
            metrics.recordSend(provider, result.isSuccess(),
                    System.currentTimeMillis() - startMs, result.getErrorCode());
        } catch (Exception ignored) {
            // ignore
        }
        return result;
    }

    @Override
    public SmsSendResult send(SmsSendRequest request, com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig config) {
        return delegate.send(request, config);
    }

    @Override
    public List<SmsSendResult> sendBatch(List<SmsSendRequest> requests) {
        List<SmsSendResult> results = new ArrayList<>();
        if (requests == null) {
            return results;
        }
        for (SmsSendRequest request : requests) {
            results.add(send(request));
        }
        return results;
    }

    @Override
    public CompletableFuture<SmsSendResult> sendAsync(SmsSendRequest request) {
        return CompletableFuture.supplyAsync(() -> send(request));
    }

    @Override
    public SmsProviderType getDefaultProvider() {
        if (!channels.isEmpty() && channels.get(0).getProvider() != null) {
            return channels.get(0).getProvider();
        }
        return delegate.getDefaultProvider();
    }
}
