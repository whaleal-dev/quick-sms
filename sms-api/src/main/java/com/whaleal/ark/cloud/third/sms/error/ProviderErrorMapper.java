package com.whaleal.ark.cloud.third.sms.error;

import java.util.Locale;
import java.util.Map;

/**
 * 将厂商原始错误映射为 {@link SmsErrorCodes}。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class ProviderErrorMapper {

    private ProviderErrorMapper() {
    }

    public static String map(String providerCode, String rawCode, String rawMessage) {
        String blob = ((rawCode == null ? "" : rawCode) + " " + (rawMessage == null ? "" : rawMessage))
                .toLowerCase(Locale.ROOT);
        if (containsAny(blob, "auth", "credential", "unauthorized", "401", "签名", "密钥", "password")) {
            return SmsErrorCodes.AUTH_FAILED;
        }
        if (containsAny(blob, "quota", "balance", "insufficient", "余额", "欠费", "limit exceeded")) {
            return SmsErrorCodes.QUOTA_EXCEEDED;
        }
        if (containsAny(blob, "timeout", "timed out")) {
            return SmsErrorCodes.TIMEOUT;
        }
        if (containsAny(blob, "network", "connect", "connection", "dns")) {
            return SmsErrorCodes.NETWORK_ERROR;
        }
        if (containsAny(blob, "mobile", "phone", "number", "号码", "invalid to")) {
            return SmsErrorCodes.INVALID_NUMBER;
        }
        if (containsAny(blob, "content", "template", "sign", "签名", "敏感", "reject")) {
            return SmsErrorCodes.CONTENT_REJECTED;
        }
        if (containsAny(blob, "throttle", "rate", "频繁", "too many")) {
            return SmsErrorCodes.RATE_LIMITED;
        }
        return SmsErrorCodes.PROVIDER_ERROR;
    }

    public static void putMapped(Map<String, Object> extra, String providerCode, String rawCode, String rawMessage) {
        if (extra == null) {
            return;
        }
        extra.put("errorCode", rawCode);
        extra.put("error", rawMessage);
        extra.put("mappedErrorCode", map(providerCode, rawCode, rawMessage));
    }

    private static boolean containsAny(String blob, String... keys) {
        for (String key : keys) {
            if (blob.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
