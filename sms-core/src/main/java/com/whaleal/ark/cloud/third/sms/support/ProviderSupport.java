package com.whaleal.ark.cloud.third.sms.support;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderKeys;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;

/**
 * 厂商实现侧公共辅助：解析 SPI 查找键、校验基础配置等。
 * <p>
 * 具体 HTTP 协议仍在 {@code sms-providers-cn} / {@code sms-providers-intl}；
 * 通道策略 / failover 等后续也可落在本包。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class ProviderSupport {

    private ProviderSupport() {
    }

    /**
     * 解析厂商查找键：优先 config.providerCode，否则枚举 code。
     */
    public static String resolveKey(SmsProviderType type, SmsProviderConfig config) {
        if (config != null) {
            String fromConfig = config.resolveProviderKey();
            if (fromConfig != null) {
                return fromConfig;
            }
        }
        return SmsProviderKeys.of(type);
    }

    /**
     * 是否具备至少一种常用认证字段。
     */
    public static boolean hasAnyCredential(SmsProviderConfig config) {
        if (config == null) {
            return false;
        }
        return notBlank(config.getApiKey())
                || notBlank(config.getApiSecret())
                || notBlank(config.getAccessKeyId())
                || notBlank(config.getAccessKeySecret());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
