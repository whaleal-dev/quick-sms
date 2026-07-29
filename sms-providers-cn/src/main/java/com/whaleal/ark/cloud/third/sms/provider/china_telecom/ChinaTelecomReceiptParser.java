package com.whaleal.ark.cloud.third.sms.provider.china_telecom;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * 中国电信回执解析器
 */
@Slf4j
public class ChinaTelecomReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析中国电信回执: {}", rawData);
        
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "reportId"))
                .messageId(getString(rawData, "msgId"))
                .to(getString(rawData, "destTerminalId"))
                .receiptStatus(parseStatus(getString(rawData, "status")))
                .receiptCode(getString(rawData, "status"))
                .receiptDescription(getString(rawData, "statusDesc"))
                .deliveredTime(parseDateTime(getString(rawData, "receiveTime")))
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) return SmsReceipt.ReceiptStatus.UNKNOWN;
        
        switch (status) {
            case "DELIVRD":
                return SmsReceipt.ReceiptStatus.DELIVERED;
            case "EXPIRED":
                return SmsReceipt.ReceiptStatus.EXPIRED;
            case "DELETED":
            case "UNDELIV":
                return SmsReceipt.ReceiptStatus.FAILED;
            case "ACCEPTD":
                return SmsReceipt.ReceiptStatus.UNKNOWN;
            case "REJECTD":
                return SmsReceipt.ReceiptStatus.REJECTED;
            default:
                return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
    }
    
    private LocalDateTime parseDateTime(String timestamp) {
        if (timestamp == null) return null;
        try {
            // 中国电信时间格式: yyyyMMddHHmmss
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (Exception e) {
            log.warn("解析中国电信时间失败: {}", timestamp);
            return null;
        }
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return "CHINA_TELECOM";
    }
} 