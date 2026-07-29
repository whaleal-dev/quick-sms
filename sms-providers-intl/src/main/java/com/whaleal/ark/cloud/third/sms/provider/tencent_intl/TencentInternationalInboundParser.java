package com.whaleal.ark.cloud.third.sms.provider.tencent_intl;

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
 * 腾讯云国际短信入站解析器
 * 
 * 功能：
 * 1. 解析上行短信内容
 * 2. 提取发送方手机号
 * 3. 提取接收方号码
 * 4. 记录接收时间
 * 
 * 数据格式：
 * {
 *   "serial_no": "消息ID",
 *   "mobile": "发送方手机号",
 *   "dest_number": "接收方号码",
 *   "content": "短信内容",
 *   "receive_time": "接收时间"
 * }
 * 
 * 注意事项：
 * 1. 需要在腾讯云控制台开通上行短信功能
 * 2. 上行短信支持的国家和地区有限
 * 3. 手机号码格式为国际区号+手机号
 * 4. 支持的编码格式：UTF-8
 * 
 * @author whaleal-dev
 * @author 恒哥
 */
public class TencentInternationalInboundParser implements InboundParser {
    
    private static final Logger logger = LoggerFactory.getLogger(TencentInternationalInboundParser.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            logger.warn("收到无效的腾讯云国际短信上行数据: {}", rawData);
            return null;
        }
        
        try {
            return SmsInboundMessage.builder()
                    .messageId(getString(rawData, "serial_no"))
                    .from(getString(rawData, "mobile"))
                    .to(getString(rawData, "dest_number"))
                    .content(getString(rawData, "content"))
                    .messageType(SmsInboundMessage.MessageType.TEXT)
                    .receivedTime(parseDateTime(getString(rawData, "receive_time")))
                    .rawData(rawData)
                    .build();
                    
        } catch (Exception e) {
            logger.error("解析腾讯云国际短信上行数据失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && 
               rawData.containsKey("serial_no") &&
               rawData.containsKey("mobile") &&
               rawData.containsKey("content");
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
        return SmsProviderType.TENCENT_INTERNATIONAL.name();
    }
} 