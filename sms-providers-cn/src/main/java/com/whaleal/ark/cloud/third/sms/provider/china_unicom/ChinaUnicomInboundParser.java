package com.whaleal.ark.cloud.third.sms.provider.china_unicom;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

/**
 * 中国联通入站消息解析器
 */
@Slf4j
public class ChinaUnicomInboundParser implements InboundParser {
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析中国联通入站消息: {}", rawData);
        
        return SmsInboundMessage.builder()
                .messageId(getString(rawData, "seqId"))
                .providerMessageId(getString(rawData, "seqId"))
                .providerType(SmsProviderType.CHINA_UNICOM)
                .from(getString(rawData, "mobile"))
                .to(getString(rawData, "spNumber"))
                .content(getString(rawData, "content"))
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
            // 中国联通时间格式: yyyy-MM-dd HH:mm:ss
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("解析中国联通时间失败: {}", timestamp);
            return null;
        }
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return "CHINA_UNICOM";
    }
} 