package com.whaleal.ark.cloud.third.sms.provider.twilio;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;

import java.time.LocalDateTime;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * Twilio回执解析器
 */
public class TwilioReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "MessageSid"))
                .messageId(getString(rawData, "MessageSid"))
                .to(getString(rawData, "To"))
                .receiptStatus(parseStatus(getString(rawData, "MessageStatus")))
                .receiptCode(getString(rawData, "MessageStatus"))
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) return SmsReceipt.ReceiptStatus.UNKNOWN;
        switch (status.toLowerCase()) {
            case "delivered": return SmsReceipt.ReceiptStatus.DELIVERED;
            case "failed": return SmsReceipt.ReceiptStatus.FAILED;
            case "undelivered": return SmsReceipt.ReceiptStatus.UNDELIVERABLE;
            default: return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return "TWILIO";
    }
} 