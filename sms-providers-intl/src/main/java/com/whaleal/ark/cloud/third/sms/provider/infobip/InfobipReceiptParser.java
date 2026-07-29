package com.whaleal.ark.cloud.third.sms.provider.infobip;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * Infobip回执解析器
 */
@Slf4j
public class InfobipReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析Infobip回执: {}", rawData);
        
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "messageId"))
                .messageId(getString(rawData, "messageId"))
                .to(getString(rawData, "to"))
                .receiptStatus(parseStatus(getString(rawData, "status")))
                .receiptCode(getString(rawData, "status"))
                .receiptDescription(getString(rawData, "error"))
                .deliveredTime(parseDateTime(getString(rawData, "doneAt")))
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
            case "not_delivered":
            case "failed":
                return SmsReceipt.ReceiptStatus.FAILED;
            case "expired":
                return SmsReceipt.ReceiptStatus.EXPIRED;
            case "rejected":
                return SmsReceipt.ReceiptStatus.REJECTED;
            case "pending":
                return SmsReceipt.ReceiptStatus.UNKNOWN;
            default:
                return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
    }
    
    private LocalDateTime parseDateTime(String timestamp) {
        if (timestamp == null) return null;
        try {
            // Infobip时间格式: 2020-01-01T12:00:00.000+0000
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
        } catch (Exception e) {
            log.warn("解析Infobip时间失败: {}", timestamp);
            return null;
        }
    }
    
    private SmsReceipt.CostInfo parseCostInfo(Map<String, Object> rawData) {
        return SmsReceipt.CostInfo.builder()
                .amount(getString(rawData, "price"))
                .currency(getString(rawData, "currency"))
                .billingType("per_message")
                .messageCount(getInteger(rawData, "smsCount", 1))
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
        return "INFOBIP";
    }
} 