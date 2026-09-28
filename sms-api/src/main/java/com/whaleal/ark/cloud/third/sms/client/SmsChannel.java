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

    /**
     * 该通道绑定的发送方号码/签名（可选）。
     * sender 与厂商账号天然绑定：多 sender 展开为多条同厂商通道时各自携带，
     * 解析顺序 request.from（调用方显式）→ channel.from（通道绑定）→ fromByProvider。
     *
     * @since 1.2.0
     */
    private String from;

    /**
     * 负载均衡权重（≥1，默认 1）。
     * 平滑加权轮询按此比例分发；加权随机按此概率抽取；顺序策略忽略此字段。
     *
     * @since 1.1.0
     */
    @Builder.Default
    private int weight = 1;

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
