package com.whaleal.ark.cloud.third.sms.provider.huawei_intl;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * 华为云国际短信回执解析器
 * 解析来自华为云国际短信平台的回执数据
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
public class HuaweiInternationalReceiptParser implements ReceiptParser {
    
    private static final Logger logger = LoggerFactory.getLogger(HuaweiInternationalReceiptParser.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            logger.warn("收到无效的华为云国际短信回执数据: {}", rawData);
            return null;
        }
        
        try {
            String status = getString(rawData, "status");
            String errorCode = getString(rawData, "code");
            
            return SmsReceipt.builder()
                    .receiptId(getString(rawData, "msgId"))
                    .messageId(getString(rawData, "msgId"))
                    .to(getString(rawData, "mobile"))
                    .receiptStatus(parseStatus(status))
                    .receiptCode(status)
                    .receiptDescription(getString(rawData, "description"))
                    .errorCode(errorCode)
                    .errorDescription(getString(rawData, "description"))
                    .deliveredTime(parseDateTime(getString(rawData, "updateTime")))
                    .receivedTime(LocalDateTime.now())
                    .providerType(SmsProviderType.HUAWEI_INTERNATIONAL)
                    .rawData(rawData)
                    .build();
                    
        } catch (Exception e) {
            logger.error("解析华为云国际短信回执数据失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && 
               rawData.containsKey("msgId") &&
               rawData.containsKey("mobile") &&
               rawData.containsKey("status");
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DATE_FORMATTER);
        } catch (Exception e) {
            logger.warn("解析时间失败: {}", dateTimeStr);
            return LocalDateTime.now();
        }
    }
    
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) return SmsReceipt.ReceiptStatus.UNKNOWN;
        
        switch (status.toLowerCase()) {
            case "delivrd":
            case "delivered":
                return SmsReceipt.ReceiptStatus.DELIVERED;
            case "undeliv":
            case "failed":
                return SmsReceipt.ReceiptStatus.FAILED;
            case "expired":
                return SmsReceipt.ReceiptStatus.EXPIRED;
            case "rejectd":
            case "rejected":
                return SmsReceipt.ReceiptStatus.REJECTED;
            case "undeliverable":
                return SmsReceipt.ReceiptStatus.UNDELIVERABLE;
            case "acceptd":
                return SmsReceipt.ReceiptStatus.SENT;
            default:
                return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.HUAWEI_INTERNATIONAL.name();
    }
} 