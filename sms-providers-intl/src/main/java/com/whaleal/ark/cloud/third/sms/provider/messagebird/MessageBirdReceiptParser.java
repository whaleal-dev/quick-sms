package com.whaleal.ark.cloud.third.sms.provider.messagebird;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * MessageBird回执解析器
 */
@Slf4j
public class MessageBirdReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析MessageBird回执: {}", rawData);
        
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "id"))
                .messageId(getString(rawData, "reference"))
                .to(getString(rawData, "recipient"))
                .receiptStatus(parseStatus(getString(rawData, "status")))
                .receiptCode(getString(rawData, "status"))
                .receiptDescription(getString(rawData, "statusReason"))
                .deliveredTime(parseDateTime(getString(rawData, "statusDatetime")))
                .receivedTime(LocalDateTime.now())
                .costInfo(parseCostInfo(rawData))
                .rawData(rawData)
                .build();
    }
    
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) return SmsReceipt.ReceiptStatus.UNKNOWN;
        
        switch (status.toLowerCase()) {
            case "delivered":
                return SmsReceipt.ReceiptStatus.DELIVERED;
            case "delivery_failed":
            case "failed":
                return SmsReceipt.ReceiptStatus.FAILED;
            case "expired":
                return SmsReceipt.ReceiptStatus.EXPIRED;
            case "rejected":
                return SmsReceipt.ReceiptStatus.REJECTED;
            case "buffered":
                return SmsReceipt.ReceiptStatus.UNKNOWN;
            default:
                return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
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
    
    private SmsReceipt.CostInfo parseCostInfo(Map<String, Object> rawData) {
        return SmsReceipt.CostInfo.builder()
                .amount(getString(rawData, "price"))
                .currency("EUR")
                .billingType("per_message")
                .messageCount(getInteger(rawData, "mccmnc", 1))
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
        return "MESSAGEBIRD";
    }
} 