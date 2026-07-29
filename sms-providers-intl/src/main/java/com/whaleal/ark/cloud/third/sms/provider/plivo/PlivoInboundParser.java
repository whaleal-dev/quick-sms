package com.whaleal.ark.cloud.third.sms.provider.plivo;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

/**
 * Plivo入站消息解析器
 */
@Slf4j
public class PlivoInboundParser implements InboundParser {
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析Plivo入站消息: {}", rawData);
        
        return SmsInboundMessage.builder()
                .messageId(getString(rawData, "MessageUUID"))
                .providerMessageId(getString(rawData, "MessageUUID"))
                .providerType(SmsProviderType.PLIVO)
                .from(getString(rawData, "From"))
                .to(getString(rawData, "To"))
                .content(getString(rawData, "Text"))
                .messageType(SmsInboundMessage.MessageType.TEXT)
                .encoding("UTF-8")
                .messageCount(getInteger(rawData, "Units", 1))
                .sentTime(LocalDateTime.now()) // Plivo不提供准确发送时间
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    private Integer getInteger(Map<String, Object> data, String key, Integer defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return "PLIVO";
    }
} 