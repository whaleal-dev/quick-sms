package com.whaleal.ark.cloud.third.sms.provider.messagebird;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

/**
 * MessageBird入站消息解析器
 */
@Slf4j
public class MessageBirdInboundParser implements InboundParser {
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析MessageBird入站消息: {}", rawData);
        
        return SmsInboundMessage.builder()
                .messageId(getString(rawData, "id"))
                .providerMessageId(getString(rawData, "id"))
                .providerType(SmsProviderType.MESSAGEBIRD)
                .from(getString(rawData, "originator"))
                .to(getString(rawData, "recipient"))
                .content(getString(rawData, "body"))
                .messageType(SmsInboundMessage.MessageType.TEXT)
                .encoding("UTF-8")
                .messageCount(1)
                .sentTime(parseDateTime(getString(rawData, "createdDatetime")))
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private LocalDateTime parseDateTime(String timestamp) {
        if (timestamp == null) return null;
        try {
            // MessageBird时间格式: 2020-01-01T12:00:00+00:00
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
        } catch (Exception e) {
            log.warn("解析MessageBird时间失败: {}", timestamp);
            return null;
        }
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return "MESSAGEBIRD";
    }
} 