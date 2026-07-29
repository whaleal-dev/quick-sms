package com.whaleal.ark.cloud.third.sms.provider.aliyun_intl;

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
 * 阿里云国际短信回执解析器
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
public class AliyunInternationalReceiptParser implements ReceiptParser {
    
    private static final Logger logger = LoggerFactory.getLogger(AliyunInternationalReceiptParser.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            logger.warn("收到无效的阿里云国际短信回执数据: {}", rawData);
            return null;
        }
        
        try {
            String status = getString(rawData, "status");
            String errorCode = getString(rawData, "err_code");
            
            return SmsReceipt.builder()
                    .receiptId(getString(rawData, "message_id"))
                    .messageId(getString(rawData, "message_id"))
                    .to(getString(rawData, "phone_number"))
                    .receiptStatus(parseStatus(status))
                    .receiptCode(status)
                    .receiptDescription(getString(rawData, "err_msg"))
                    .errorCode(errorCode)
                    .errorDescription(getString(rawData, "err_msg"))
                    .deliveredTime(parseDateTime(getString(rawData, "receive_time")))
                    .receivedTime(LocalDateTime.now())
                    .providerType(SmsProviderType.ALIYUN_INTERNATIONAL)
                    .rawData(rawData)
                    .build();
                    
        } catch (Exception e) {
            logger.error("解析阿里云国际短信回执数据失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) return SmsReceipt.ReceiptStatus.UNKNOWN;
        
        switch (status.toLowerCase()) {
            case "delivered":
                return SmsReceipt.ReceiptStatus.DELIVERED;
            case "failed":
                return SmsReceipt.ReceiptStatus.FAILED;
            case "expired":
                return SmsReceipt.ReceiptStatus.EXPIRED;
            case "rejected":
                return SmsReceipt.ReceiptStatus.REJECTED;
            case "undeliverable":
                return SmsReceipt.ReceiptStatus.UNDELIVERABLE;
            default:
                return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
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
    
    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && 
               rawData.containsKey("message_id") &&
               rawData.containsKey("phone_number") &&
               rawData.containsKey("status");
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.ALIYUN_INTERNATIONAL.name();
    }
} 