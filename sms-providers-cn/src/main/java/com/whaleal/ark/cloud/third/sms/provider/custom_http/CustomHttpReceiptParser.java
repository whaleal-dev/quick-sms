package com.whaleal.ark.cloud.third.sms.provider.custom_http;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;

import java.time.LocalDateTime;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * 自定义HTTP回执解析器
 */
public class CustomHttpReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "id"))
                .messageId(getString(rawData, "messageId"))
                .to(getString(rawData, "to"))
                .receiptStatus(parseStatus(getString(rawData, "status")))
                .receiptCode(getString(rawData, "status"))
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) return SmsReceipt.ReceiptStatus.UNKNOWN;
        switch (status.toLowerCase()) {
            case "delivered": case "success": return SmsReceipt.ReceiptStatus.DELIVERED;
            case "failed": case "error": return SmsReceipt.ReceiptStatus.FAILED;
            default: return SmsReceipt.ReceiptStatus.UNKNOWN;
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