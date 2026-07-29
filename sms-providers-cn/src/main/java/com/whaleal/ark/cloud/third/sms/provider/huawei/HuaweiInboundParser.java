package com.whaleal.ark.cloud.third.sms.provider.huawei;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

/**
 * 华为云入站消息解析器
 */
@Slf4j
public class HuaweiInboundParser implements InboundParser {
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析华为云入站消息: {}", rawData);
        
        return SmsInboundMessage.builder()
                .messageId(getString(rawData, "messageId"))
                .providerMessageId(getString(rawData, "messageId"))
                .providerType(SmsProviderType.HUAWEI)
                .from(getString(rawData, "from"))
                .to(getString(rawData, "to"))
                .content(getString(rawData, "body"))
                .messageType(SmsInboundMessage.MessageType.TEXT)
                .encoding("UTF-8")
                .messageCount(1)
                .sentTime(parseDateTime(getString(rawData, "receiveTime")))
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private LocalDateTime parseDateTime(String timestamp) {
        if (timestamp == null) return null;
        try {
            // 华为云时间格式: 2020-01-01T12:00:00Z
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        } catch (Exception e) {
            log.warn("解析华为云时间失败: {}", timestamp);
            return null;
        }
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return "HUAWEI";
    }
} 