package com.whaleal.ark.cloud.third.sms.provider.aliyun;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

/**
 * 阿里云国内短信入站解析器
 * 
 * 功能：
 * 1. 解析上行短信内容
 * 2. 提取发送方手机号
 * 3. 提取接收方号码
 * 4. 记录接收时间
 * 
 * 数据格式：
 * {
 *   "phone_number": "发送方手机号",
 *   "sms_content": "短信内容",
 *   "dest_code": "接收方号码",
 *   "send_time": "发送时间",
 *   "sign_name": "签名"
 * }
 * 
 * 注意事项：
 * 1. 需要在阿里云控制台开通上行短信功能
 * 2. 上行短信仅支持部分运营商
 * 3. 签名需要提前在阿里云控制台审核通过
 * 4. 支持的编码格式：UTF-8
 * 
 * @author whaleal-dev
 * @author 恒哥
 */
public class AliyunInboundParser implements InboundParser {
    
    private static final Logger logger = LoggerFactory.getLogger(AliyunInboundParser.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            logger.warn("收到无效的阿里云短信上行数据: {}", rawData);
            return null;
        }
        
        try {
            return SmsInboundMessage.builder()
                    .messageId(getString(rawData, "message_id"))
                    .from(getString(rawData, "phone_number"))
                    .to(getString(rawData, "dest_code"))
                    .content(getString(rawData, "sms_content"))
                    .messageType(SmsInboundMessage.MessageType.TEXT)
                    .receivedTime(parseDateTime(getString(rawData, "send_time")))
                    .rawData(rawData)
                    .build();
                    
        } catch (Exception e) {
            logger.error("解析阿里云短信上行数据失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && 
               rawData.containsKey("phone_number") &&
               rawData.containsKey("sms_content");
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
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.ALIYUN.name();
    }
} 