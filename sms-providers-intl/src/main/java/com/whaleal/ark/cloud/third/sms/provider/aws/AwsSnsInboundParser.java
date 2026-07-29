package com.whaleal.ark.cloud.third.sms.provider.aws;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

/**
 * AWS SNS入站消息解析器
 */
@Slf4j
public class AwsSnsInboundParser implements InboundParser {

    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析AWS SNS入站消息: {}", rawData);

        return SmsInboundMessage.builder()
                .messageId(getString(rawData, "messageId"))
                .providerMessageId(getString(rawData, "messageId"))
                .providerType(SmsProviderType.AWS)
                .from(getString(rawData, "originationNumber"))
                .to(getString(rawData, "destinationNumber"))
                .content(getString(rawData, "messageBody"))
                .messageType(SmsInboundMessage.MessageType.TEXT)
                .encoding("UTF-8")
                .messageCount(1)
                .sentTime(LocalDateTime.now()) // AWS SNS不提供准确发送时间
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.AWS.name();
    }
}
