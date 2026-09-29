package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短信发送结果（对外统一出参）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsSendResult {

    private boolean success;
    private String messageId;
    private String providerMessageId;
    private SmsOutboundMessage.SendStatus status;
    private SmsProviderType provider;
    private String from;
    private String to;
    private String errorCode;
    private String errorMessage;

    public static SmsSendResult success(SmsOutboundMessage message) {
        return SmsSendResult.builder()
                .success(message.getSendStatus() == SmsOutboundMessage.SendStatus.SENT
                        || message.getSendStatus() == SmsOutboundMessage.SendStatus.SUBMITTED
                        || message.getSendStatus() == SmsOutboundMessage.SendStatus.DELIVERED)
                .messageId(message.getMessageId())
                .providerMessageId(message.getProviderMessageId())
                .status(message.getSendStatus())
                .provider(message.getProviderType())
                // 实际使用的发送方：调用方未指定时由 FailoverSmsClient 注入渠道绑定的
                // sender——调用方无从知晓，必须随结果带回，否则平台日志记录不了真实发信号码
                .from(message.getFrom())
                .to(message.getTo())
                .build();
    }

    public static SmsSendResult failure(String to, String errorCode, String errorMessage) {
        return SmsSendResult.builder()
                .success(false)
                .to(to)
                .status(SmsOutboundMessage.SendStatus.FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
