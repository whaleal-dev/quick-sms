package com.whaleal.ark.cloud.third.sms.provider.aliyun;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * 阿里云回执解析器
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class AliyunReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            throw new IllegalArgumentException("无效的阿里云回执数据");
        }
        
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "biz_id"))
                .messageId(getString(rawData, "biz_id"))
                .to(getString(rawData, "phone_number"))
                .receiptStatus(parseStatus(getString(rawData, "report_status")))
                .receiptCode(getString(rawData, "report_status"))
                .receiptDescription(getString(rawData, "status_desc"))
                .errorCode(getString(rawData, "err_code"))
                .errorDescription(getString(rawData, "err_msg"))
                .deliveredTime(parseDateTime(getString(rawData, "receive_date")))
                .receivedTime(LocalDateTime.now())
                .costInfo(parseCostInfo(rawData))
                .networkInfo(parseNetworkInfo(rawData))
                .rawData(rawData)
                .build();
    }
    
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) return SmsReceipt.ReceiptStatus.UNKNOWN;
        
        switch (status) {
            case "DELIVRD":
                return SmsReceipt.ReceiptStatus.DELIVERED;
            case "UNDELIV":
                return SmsReceipt.ReceiptStatus.FAILED;
            case "EXPIRED":
                return SmsReceipt.ReceiptStatus.EXPIRED;
            case "REJECTD":
                return SmsReceipt.ReceiptStatus.REJECTED;
            default:
                return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
    }
    
    private LocalDateTime parseDateTime(String timestamp) {
        if (timestamp == null) return null;
        
        try {
            // 阿里云时间格式: 2020-01-01 12:00:00
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("解析阿里云时间失败: {}", timestamp);
            return null;
        }
    }
    
    private SmsReceipt.CostInfo parseCostInfo(Map<String, Object> rawData) {
        return SmsReceipt.CostInfo.builder()
                .amount("0.045") // 阿里云默认价格
                .currency("CNY")
                .billingType("per_message")
                .messageCount(1)
                .build();
    }
    
    private SmsReceipt.NetworkInfo parseNetworkInfo(Map<String, Object> rawData) {
        return SmsReceipt.NetworkInfo.builder()
                .carrierName(getString(rawData, "carrier"))
                .countryCode("CN")
                .networkType("SMS")
                .build();
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return "ALIYUN";
    }
} 