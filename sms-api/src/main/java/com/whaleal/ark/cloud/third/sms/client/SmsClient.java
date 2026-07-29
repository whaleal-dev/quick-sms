package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 短信 SDK 统一门面（对外主入口）
 * <p>
 * 不读取配置文件（yml 非必须）；Spring 可注入默认 Bean 或自行 {@code @Bean} 注册，
 * 纯 Java 通过 runtime 的 {@code SmsClients.builder()} 构建。
 * 凭证、通道均在调用时传入，适合 SaaS 多租户。
 *
 * @author whaleal-dev
 * @author 恒哥
 */
public interface SmsClient {

    SmsSendResult send(SmsSendRequest request);

    List<SmsSendResult> sendBatch(List<SmsSendRequest> requests);

    CompletableFuture<SmsSendResult> sendAsync(SmsSendRequest request);

    /** 使用指定配置发送（覆盖全局默认配置） */
    SmsSendResult send(SmsSendRequest request, SmsProviderConfig config);

    SmsProviderType getDefaultProvider();

    /**
     * 快捷发信：正文短信。
     */
    default SmsSendResult sendText(String to, String content) {
        return send(SmsSendRequest.builder().to(to).content(content).build());
    }

    /**
     * 快捷发信：正文 + 动态凭证。
     */
    default SmsSendResult sendText(String to, String content, SmsCredentials credentials) {
        return send(SmsSendRequest.builder().to(to).content(content).credentials(credentials).build());
    }

    /**
     * 快捷发信：指定通道编码（扩展厂商可不改枚举）。
     */
    default SmsSendResult sendText(String to, String content, String providerCode, SmsCredentials credentials) {
        return send(SmsSendRequest.builder()
                .to(to)
                .content(content)
                .providerCode(providerCode)
                .credentials(credentials)
                .build());
    }

    /**
     * 快捷发信：模板短信。
     */
    default SmsSendResult sendTemplate(String to, String templateId, Map<String, String> templateParams) {
        return send(SmsSendRequest.builder()
                .to(to)
                .templateId(templateId)
                .templateParams(templateParams)
                .build());
    }

    /**
     * 快捷发信：模板 + 动态凭证。
     */
    default SmsSendResult sendTemplate(String to, String templateId, Map<String, String> templateParams,
                                       SmsCredentials credentials) {
        return send(SmsSendRequest.builder()
                .to(to)
                .templateId(templateId)
                .templateParams(templateParams)
                .credentials(credentials)
                .build());
    }
}
