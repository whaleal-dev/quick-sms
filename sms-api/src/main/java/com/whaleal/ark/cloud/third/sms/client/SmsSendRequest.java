package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 短信发送请求（对外统一入参）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsSendRequest {

    /** 接收方手机号（E.164 或本地格式） */
    private String to;

    /** 短信正文（与 templateId 二选一） */
    private String content;

    /** 发送方号码/签名（可选，未设置时使用全局 defaultFrom） */
    private String from;

    /** 模板 ID（与 content 二选一） */
    private String templateId;

    /** 模板参数 */
    private Map<String, String> templateParams;

    /** 业务引用 ID（可选） */
    private String referenceId;

    /**
     * 送达状态回调 URL（可选）
     * <p>发信时传给支持 per-message callback 的厂商（如 Twilio、Vonage），
     * 厂商在送达/失败后会 POST 到该地址。未设置时使用 {@link SmsClient} 全局配置中的
     * {@code deliveryReceiptUrl} 或 {@code callbackUrl}。</p>
     */
    private String callbackUrl;

    /**
     * 发送凭证（除 MOCK 外必填）
     * <p>运行时动态传入，勿写入配置文件。</p>
     */
    private SmsCredentials credentials;

    /** 短信供应商（核心通道枚举；与 {@link #providerCode} 二选一或同时指定） */
    private SmsProviderType provider;

    /**
     * 扩展供应商编码（无需改枚举）。
     * <p>优先于 {@link #provider} 用于 SPI 查找；未设置时回退到 {@code provider.getCode()}。</p>
     */
    private String providerCode;
}
