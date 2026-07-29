package com.whaleal.ark.cloud.third.sms.enums;

/**
 * 供应商编码规范化工具。
 * <p>
 * 核心厂商见 {@link SmsProviderType}；扩展厂商无需改枚举，
 * 在 SPI 实现中通过 {@code getSupportedProvider()} 返回本类规范化后的 code 即可注册。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class SmsProviderKeys {

    private SmsProviderKeys() {
    }

    /**
     * 规范化供应商编码：trim + lower_case，空串视为无效。
     */
    public static String normalize(String providerCode) {
        if (providerCode == null) {
            return null;
        }
        String normalized = providerCode.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 从枚举解析稳定 code（与 SPI 注册键一致）。
     */
    public static String of(SmsProviderType type) {
        return type == null ? null : normalize(type.getCode());
    }

    /**
     * 解析有效编码：优先 {@code providerCode}，否则取枚举 code。
     */
    public static String resolve(SmsProviderType type, String providerCode) {
        String fromCode = normalize(providerCode);
        if (fromCode != null) {
            return fromCode;
        }
        return of(type);
    }
}
