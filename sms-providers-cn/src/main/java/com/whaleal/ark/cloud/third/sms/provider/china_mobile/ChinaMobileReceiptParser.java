package com.whaleal.ark.cloud.third.sms.provider.china_mobile;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;

import java.time.LocalDateTime;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * 中国移动回执解析器
 */
public class ChinaMobileReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "msgId"))
                .messageId(getString(rawData, "msgId"))
                .to(getString(rawData, "destTermId"))
                .receiptStatus(parseStatus(getString(rawData, "stat")))
                .receiptCode(getString(rawData, "stat"))
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) return SmsReceipt.ReceiptStatus.UNKNOWN;
        return "DELIVRD".equals(status) ? SmsReceipt.ReceiptStatus.DELIVERED : SmsReceipt.ReceiptStatus.FAILED;
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return "CHINA_MOBILE";
    }
} 