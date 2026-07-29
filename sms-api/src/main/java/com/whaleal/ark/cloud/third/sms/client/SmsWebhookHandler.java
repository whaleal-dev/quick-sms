package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.validation.entity.PhoneValidationResult;

import java.util.List;
import java.util.Map;

/**
 * Webhook 解析门面（回执 / 上行 / 状态查询 / 号码校验）
 * <p>需要调用厂商 API 的方法须动态传入 {@link SmsCredentials}。</p>
 *
 * @author whaleal-dev
 * @author 恒哥
 */
public interface SmsWebhookHandler {

    SmsReceipt parseReceipt(SmsProviderType provider, Map<String, Object> payload);

    SmsReceipt parseReceipt(Map<String, Object> payload);

    SmsInboundMessage parseInbound(SmsProviderType provider, Map<String, Object> payload);

    SmsInboundMessage parseInbound(Map<String, Object> payload);

    SmsReport fetchReport(SmsProviderType provider, String messageId, SmsCredentials credentials);

    List<SmsReport> fetchReports(SmsProviderType provider, List<String> messageIds, SmsCredentials credentials);

    PhoneValidationResult validatePhone(SmsProviderType provider, String phoneNumber, SmsCredentials credentials);

    List<PhoneValidationResult> validatePhones(SmsProviderType provider, List<String> phoneNumbers, SmsCredentials credentials);

    /**
     * 校验 Webhook 签名与防重放。
     *
     * @return null 表示通过；否则为统一错误码（如 E005/E006）
     */
    default String verifyWebhook(long timestampMs, String nonce, String rawBody, String signature) {
        return null;
    }

    /** 解析用基础配置（不含秘钥，由 SDK 内部持有） */
    SmsProviderConfig getBaseConfig();
}
