package com.whaleal.ark.cloud.third.sms.provider.plivo;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * Plivo回执解析器
 */
@Slf4j
public class PlivoReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析Plivo回执: {}", rawData);
        
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "MessageUUID"))
                .messageId(getString(rawData, "ParentMessageUUID"))
                .to(getString(rawData, "To"))
                .receiptStatus(parseStatus(getString(rawData, "Status")))
                .receiptCode(getString(rawData, "Status"))
                .receiptDescription(getString(rawData, "ErrorCode"))
                .deliveredTime(LocalDateTime.now()) // Plivo不提供准确送达时间
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
            case "failed":
            case "undelivered":
                return SmsReceipt.ReceiptStatus.FAILED;
            case "rejected":
                return SmsReceipt.ReceiptStatus.REJECTED;
            case "sent":
                return SmsReceipt.ReceiptStatus.SENT;
            default:
                return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
    }
    
    private SmsReceipt.CostInfo parseCostInfo(Map<String, Object> rawData) {
        return SmsReceipt.CostInfo.builder()
                .amount(getString(rawData, "TotalRate"))
                .currency("USD")
                .billingType("per_message")
                .messageCount(getInteger(rawData, "Units", 1))
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