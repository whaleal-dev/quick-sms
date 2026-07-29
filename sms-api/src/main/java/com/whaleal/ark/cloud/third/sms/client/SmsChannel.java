package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条可发送通道：厂商 + 凭证（用于 failover 路由）。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsChannel {

    /** 核心枚举（可空，与 providerCode 二选一） */
    private SmsProviderType provider;

    /** 扩展厂商编码 */
    private String providerCode;

    /** 该通道凭证 */
    private SmsCredentials credentials;

    /** 可选展示名 */
    private String name;

    public static SmsChannel of(SmsProviderType provider, SmsCredentials credentials) {
        return SmsChannel.builder()
                .provider(provider)
                .providerCode(provider != null ? provider.getCode() : null)
                .credentials(credentials)
                .name(provider != null ? provider.getDisplayName() : null)
                .build();
    }

    public static SmsChannel of(String providerCode, SmsCredentials credentials) {
        return SmsChannel.builder()
                .providerCode(providerCode)
                .credentials(credentials)
                .build();
    }
}
