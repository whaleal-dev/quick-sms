package com.whaleal.ark.cloud.third.sms.webhook;

import com.whaleal.ark.cloud.third.sms.error.SmsErrorCodes;
import com.whaleal.ark.cloud.third.sms.util.SignatureUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook 安全校验：HMAC 签名 + 时间窗 + nonce 防重放。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class WebhookSecurity {

    private final String secret;
    private final long maxSkewMs;
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    public WebhookSecurity(String secret) {
        this(secret, 5 * 60_000L);
    }

    public WebhookSecurity(String secret, long maxSkewMs) {
        this.secret = secret;
        this.maxSkewMs = maxSkewMs;
    }

    /**
     * @param timestampMs 请求时间戳（毫秒）
     * @param nonce       一次性随机串
     * @param payload     原始 body
     * @param signature   对方签名（hex HMAC-SHA256 of timestamp.nonce.payload）
     * @return null 表示通过；否则为 {@link SmsErrorCodes}
     */
    public String verify(long timestampMs, String nonce, String payload, String signature) {
        if (secret == null || secret.isBlank()) {
            return null; // 未配置密钥则跳过
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestampMs) > maxSkewMs) {
            return SmsErrorCodes.WEBHOOK_INVALID;
        }
        if (nonce == null || nonce.isBlank()) {
            return SmsErrorCodes.WEBHOOK_INVALID;
        }
        Long prev = seenNonces.putIfAbsent(nonce, now);
        purgeExpired(now);
        if (prev != null) {
            return SmsErrorCodes.WEBHOOK_REPLAY;
        }
        String raw = timestampMs + "." + nonce + "." + (payload == null ? "" : payload);
        String expect = SignatureUtils.hmacSha256(raw, secret);
        if (signature == null || !expect.equalsIgnoreCase(signature.trim())) {
            return SmsErrorCodes.WEBHOOK_INVALID;
        }
        return null;
    }

    public String sign(long timestampMs, String nonce, String payload) {
        String raw = timestampMs + "." + nonce + "." + (payload == null ? "" : payload);
        return SignatureUtils.hmacSha256(raw, secret);
    }

    private void purgeExpired(long now) {
        long expireBefore = now - maxSkewMs * 2;
        seenNonces.entrySet().removeIf(e -> e.getValue() < expireBefore);
    }
}
