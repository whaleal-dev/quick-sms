package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.core.SmsModuleManager;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.error.ProviderErrorMapper;
import com.whaleal.ark.cloud.third.sms.error.SmsErrorCodes;
import com.whaleal.ark.cloud.third.sms.guard.PhoneBlacklist;
import com.whaleal.ark.cloud.third.sms.guard.RateLimiter;
import com.whaleal.ark.cloud.third.sms.metrics.SmsMetrics;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import com.whaleal.ark.cloud.third.sms.util.TextUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link SmsClient} 默认实现，委托 {@link SmsModuleManager} 完成真实发送
 *
 * @author whaleal-dev
 * @author 恒哥
 */
@Slf4j
public class DefaultSmsClient implements SmsClient {

    private final SmsProviderConfig baseConfig;
    private final SmsModuleManager moduleManager;
    private final ExecutorService asyncExecutor;
    private final PhoneBlacklist blacklist;
    private final RateLimiter rateLimiter;
    private final SmsMetrics metrics;

    public DefaultSmsClient(SmsProviderConfig baseConfig, SmsModuleManager moduleManager) {
        this(baseConfig, moduleManager, null, null, null);
    }

    public DefaultSmsClient(SmsProviderConfig baseConfig,
                            SmsModuleManager moduleManager,
                            PhoneBlacklist blacklist,
                            RateLimiter rateLimiter) {
        this(baseConfig, moduleManager, blacklist, rateLimiter, null);
    }

    public DefaultSmsClient(SmsProviderConfig baseConfig,
                            SmsModuleManager moduleManager,
                            PhoneBlacklist blacklist,
                            RateLimiter rateLimiter,
                            SmsMetrics metrics) {
        this.baseConfig = baseConfig;
        this.moduleManager = moduleManager;
        this.blacklist = blacklist;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics == null ? SmsMetrics.NOOP : metrics;
        this.asyncExecutor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "sms-sdk-client");
                    t.setDaemon(true);
                    return t;
                });
    }

    @Override
    public SmsSendResult send(SmsSendRequest request) {
        return send(request, baseConfig);
    }

    @Override
    public SmsSendResult send(SmsSendRequest request, SmsProviderConfig config) {
        long start = System.currentTimeMillis();
        String providerKey = "unknown";
        try {
            if (request != null) {
                if (blacklist != null && blacklist.isBlocked(request.getTo())) {
                    return finish(providerKey, SmsSendResult.failure(
                            request.getTo(), SmsErrorCodes.BLACKLISTED, "号码在黑名单中"), start);
                }
                if (rateLimiter != null && !rateLimiter.tryAcquire(request.getTo())) {
                    return finish(providerKey, SmsSendResult.failure(
                            request.getTo(), SmsErrorCodes.RATE_LIMITED, "发送过于频繁"), start);
                }
            }

            ValidationError validationError = validate(request, config);
            if (validationError != null) {
                return finish(providerKey, SmsSendResult.failure(
                        request != null ? request.getTo() : null,
                        validationError.code,
                        validationError.message), start);
            }

            SmsProviderConfig effectiveConfig = resolveConfig(request, config);
            providerKey = effectiveConfig.resolveProviderKey();
            SmsProviderType provider = effectiveConfig.getProviderType();

            SmsOutboundMessage message = toOutboundMessage(request, effectiveConfig);
            SmsOutboundMessage result;
            if (TextUtils.hasText(request.getTemplateId())) {
                if (provider != null) {
                    result = moduleManager.sendTemplateMessage(provider, message, effectiveConfig);
                } else {
                    result = moduleManager.sendTemplateMessage(providerKey, message, effectiveConfig);
                }
            } else {
                if (provider != null) {
                    result = moduleManager.sendMessage(provider, message, effectiveConfig);
                } else {
                    result = moduleManager.sendMessage(providerKey, message, effectiveConfig);
                }
            }
            SmsSendResult sendResult = SmsSendResult.success(result);
            if (result.getExtraInfo() != null) {
                Object error = result.getExtraInfo().get("error");
                if (error != null) {
                    sendResult.setSuccess(false);
                    sendResult.setErrorMessage(String.valueOf(error));
                    Object mapped = result.getExtraInfo().get("mappedErrorCode");
                    Object raw = result.getExtraInfo().get("errorCode");
                    if (mapped != null) {
                        sendResult.setErrorCode(String.valueOf(mapped));
                    } else if (raw != null) {
                        sendResult.setErrorCode(ProviderErrorMapper.map(
                                providerKey, String.valueOf(raw), String.valueOf(error)));
                    } else {
                        sendResult.setErrorCode(SmsErrorCodes.PROVIDER_ERROR);
                    }
                }
            }
            return finish(providerKey, sendResult, start);
        } catch (Exception e) {
            log.error("SMS send failed, to={}", request != null ? request.getTo() : null, e);
            return finish(providerKey, SmsSendResult.failure(
                    request != null ? request.getTo() : null,
                    SmsErrorCodes.SEND_ERROR, e.getMessage()), start);
        }
    }

    private SmsSendResult finish(String provider, SmsSendResult result, long startMs) {
        try {
            metrics.recordSend(provider, result.isSuccess(),
                    System.currentTimeMillis() - startMs, result.getErrorCode());
        } catch (Exception e) {
            log.debug("metrics record failed: {}", e.getMessage());
        }
        return result;
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
        return CompletableFuture.supplyAsync(() -> send(request), asyncExecutor);
    }

    @Override
    public SmsProviderType getDefaultProvider() {
        return baseConfig.getProviderType();
    }

    private SmsProviderConfig resolveConfig(SmsSendRequest request, SmsProviderConfig config) {
        SmsProviderConfig merged = config != null ? config : baseConfig;
        if (request.getCredentials() != null) {
            merged = request.getCredentials().mergeWith(merged);
        }
        SmsProviderType provider = request.getProvider() != null
                ? request.getProvider()
                : (merged.getProviderType() != null ? merged.getProviderType() : null);
        String providerCode = request.getProviderCode() != null
                ? request.getProviderCode()
                : merged.getProviderCode();
        if (provider == null && providerCode == null) {
            provider = SmsProviderType.MOCK;
        }
        if (provider == null && providerCode != null) {
            provider = SmsProviderType.tryFromCode(providerCode).orElse(null);
        }
        return merged.toBuilder()
                .providerType(provider)
                .providerCode(providerCode)
                .build();
    }

    private ValidationError validate(SmsSendRequest request, SmsProviderConfig config) {
        if (request == null) {
            return ValidationError.of("E001", "请求不能为空");
        }
        if (!TextUtils.hasText(request.getTo())) {
            return ValidationError.of("E001", "接收方手机号不能为空");
        }
        if (!TextUtils.hasText(request.getContent()) && !TextUtils.hasText(request.getTemplateId())) {
            return ValidationError.of("E001", "短信内容与模板ID不能同时为空");
        }

        SmsProviderConfig effective = resolveConfig(request, config);
        SmsProviderType provider = effective.getProviderType();
        boolean isMock = provider == SmsProviderType.MOCK
                || "mock".equalsIgnoreCase(effective.resolveProviderKey());
        if (!isMock && !hasAuth(effective)) {
            return ValidationError.of("E002", "发送凭证不能为空，请通过 request.credentials 动态传入");
        }
        return null;
    }

    private boolean hasAuth(SmsProviderConfig config) {
        return TextUtils.hasText(config.getApiKey())
                || TextUtils.hasText(config.getApiSecret())
                || TextUtils.hasText(config.getAccessKeyId())
                || TextUtils.hasText(config.getAccessKeySecret());
    }

    private SmsOutboundMessage toOutboundMessage(SmsSendRequest request, SmsProviderConfig config) {
        SmsOutboundMessage.SmsOutboundMessageBuilder builder = SmsOutboundMessage.builder()
                .to(request.getTo())
                .content(request.getContent())
                .from(TextUtils.hasText(request.getFrom()) ? request.getFrom() : config.getDefaultFrom());

        if (TextUtils.hasText(request.getTemplateId()) || request.getTemplateParams() != null) {
            builder.businessInfo(SmsOutboundMessage.BusinessInfo.builder()
                    .templateId(request.getTemplateId())
                    .templateParams(request.getTemplateParams())
                    .signature(config.getSignName())
                    .relatedBusinessId(request.getReferenceId())
                    .build());
        }

        String callbackUrl = resolveCallbackUrl(request, config);
        if (TextUtils.hasText(callbackUrl)) {
            builder.sendConfig(SmsOutboundMessage.SendConfig.builder()
                    .callbackUrl(callbackUrl)
                    .build());
        }

        return builder.build();
    }

    /**
     * 解析回调 URL：请求级 &gt; deliveryReceiptUrl &gt; callbackUrl
     */
    static String resolveCallbackUrl(SmsSendRequest request, SmsProviderConfig config) {
        if (request != null && TextUtils.hasText(request.getCallbackUrl())) {
            return request.getCallbackUrl();
        }
        if (config != null && TextUtils.hasText(config.getDeliveryReceiptUrl())) {
            return config.getDeliveryReceiptUrl();
        }
        if (config != null && TextUtils.hasText(config.getCallbackUrl())) {
            return config.getCallbackUrl();
        }
        return null;
    }

    private static final class ValidationError {
        private final String code;
        private final String message;

        private ValidationError(String code, String message) {
            this.code = code;
            this.message = message;
        }

        static ValidationError of(String code, String message) {
            return new ValidationError(code, message);
        }
    }
}
