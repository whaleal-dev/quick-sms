package com.whaleal.ark.cloud.third.sms.provider.twilio;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

/**
 * Twilio入站消息解析器
 */
@Slf4j
public class TwilioInboundParser implements InboundParser {
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析Twilio入站消息: {}", rawData);
        
        return SmsInboundMessage.builder()
                .messageId(getString(rawData, "MessageSid"))
                .providerMessageId(getString(rawData, "MessageSid"))
                .providerType(SmsProviderType.TWILIO)
                .from(getString(rawData, "From"))
                .to(getString(rawData, "To"))
                .content(getString(rawData, "Body"))
                .messageType(SmsInboundMessage.MessageType.TEXT)
                .encoding("UTF-8")
                .messageCount(getInteger(rawData, "NumSegments", 1))
                .sentTime(LocalDateTime.now()) // Twilio不提供发送时间
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
        return "TWILIO";
    }
} 