package com.whaleal.ark.cloud.third.sms.provider.aws;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;

import java.time.LocalDateTime;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * AWS SNS回执解析器
 */
public class AwsSnsReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "MessageId"))
                .messageId(getString(rawData, "MessageId"))
                .receiptStatus(SmsReceipt.ReceiptStatus.DELIVERED)
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.AWS.name();
    }
} 