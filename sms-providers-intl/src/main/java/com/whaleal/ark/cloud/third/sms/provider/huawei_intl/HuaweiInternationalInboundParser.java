package com.whaleal.ark.cloud.third.sms.provider.huawei_intl;

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
 * 华为云国际短信入站解析器
 * 
 * 功能：
 * 1. 解析上行短信内容
 * 2. 提取发送方手机号
 * 3. 提取接收方号码
 * 4. 记录接收时间
 * 
 * 数据格式：
 * {
 *   "smsMsgId": "消息ID",
 *   "sender": "发送方手机号",
 *   "destinationAddress": "接收方号码",
 *   "content": "短信内容",
 *   "receiveTime": "接收时间"
 * }
 * 
 * 注意事项：
 * 1. 需要在华为云控制台开通上行短信功能
 * 2. 上行短信仅支持部分国家和地区
 * 3. 手机号码格式为：+国际区号手机号码
 * 4. 支持的编码格式：UTF-8
 * 5. 上行短信内容长度限制：70个字符（英文）或30个字符（中文）
 * 
 * @author whaleal-dev
 * @author 恒哥
 */
public class HuaweiInternationalInboundParser implements InboundParser {
    
    private static final Logger logger = LoggerFactory.getLogger(HuaweiInternationalInboundParser.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            logger.warn("收到无效的华为云国际短信上行数据: {}", rawData);
            return null;
        }
        
        try {
            return SmsInboundMessage.builder()
                    .messageId(getString(rawData, "smsMsgId"))
                    .from(getString(rawData, "sender"))
                    .to(getString(rawData, "destinationAddress"))
                    .content(getString(rawData, "content"))
                    .messageType(SmsInboundMessage.MessageType.TEXT)
                    .receivedTime(parseDateTime(getString(rawData, "receiveTime")))
                    .rawData(rawData)
                    .build();
                    
        } catch (Exception e) {
            logger.error("解析华为云国际短信上行数据失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && 
               rawData.containsKey("smsMsgId") &&
               rawData.containsKey("sender") &&
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
        return SmsProviderType.HUAWEI_INTERNATIONAL.name();
    }
} 