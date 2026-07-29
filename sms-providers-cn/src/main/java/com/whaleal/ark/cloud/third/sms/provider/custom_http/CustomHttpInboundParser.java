package com.whaleal.ark.cloud.third.sms.provider.custom_http;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

/**
 * 自定义HTTP入站消息解析器
 * 支持灵活的字段映射配置
 */
@Slf4j
public class CustomHttpInboundParser implements InboundParser {
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析自定义HTTP入站消息: {}", rawData);
        
        // 从配置中获取字段映射，如果没有配置则使用默认字段名
        String messageIdField = config.getStringConfig("inbound.messageId.field", "messageId");
        String fromField = config.getStringConfig("inbound.from.field", "from");
        String toField = config.getStringConfig("inbound.to.field", "to");
        String contentField = config.getStringConfig("inbound.content.field", "content");
        String timeField = config.getStringConfig("inbound.time.field", "time");
        String timeFormat = config.getStringConfig("inbound.time.format", "yyyy-MM-dd HH:mm:ss");
        
        return SmsInboundMessage.builder()
                .messageId(getString(rawData, messageIdField))
                .providerMessageId(getString(rawData, messageIdField))
                .providerType(SmsProviderType.CUSTOM_HTTP)
                .from(getString(rawData, fromField))
                .to(getString(rawData, toField))
                .content(getString(rawData, contentField))
                .messageType(determineMessageType(getString(rawData, contentField)))
                .encoding("UTF-8")
                .messageCount(1)
                .sentTime(parseDateTime(getString(rawData, timeField), timeFormat))
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private SmsInboundMessage.MessageType determineMessageType(String content) {
        if (content == null || content.trim().isEmpty()) {
            return SmsInboundMessage.MessageType.UNKNOWN;
        }
        
        // 简单的关键词检测
        String lowerContent = content.toLowerCase().trim();
        if (lowerContent.startsWith("td") || lowerContent.startsWith("退订")) {
            return SmsInboundMessage.MessageType.UNSUBSCRIBE;
        } else if (lowerContent.matches("^\\d{4,8}$")) {
            return SmsInboundMessage.MessageType.VERIFICATION;
        } else {
            return SmsInboundMessage.MessageType.TEXT;
        }
    }
    
    private LocalDateTime parseDateTime(String timestamp, String format) {
        if (timestamp == null) return null;
        try {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern(format));
        } catch (Exception e) {
            log.warn("解析自定义HTTP时间失败: {} (格式: {})", timestamp, format);
            return null;
        }
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return "CUSTOM_HTTP";
    }
} 