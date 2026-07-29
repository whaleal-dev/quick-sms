package com.whaleal.ark.cloud.third.sms.provider.tencent;

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
 * 腾讯云国内短信入站解析器
 * 
 * 功能：
 * 1. 解析上行短信内容
 * 2. 提取发送方手机号
 * 3. 提取接收方号码
 * 4. 记录接收时间
 * 
 * 数据格式：
 * {
 *   "mobile": "发送方手机号",
 *   "content": "短信内容",
 *   "sign": "签名",
 *   "ext": "扩展码",
 *   "time": "接收时间"
 * }
 * 
 * 注意事项：
 * 1. 需要在腾讯云控制台开通上行短信功能
 * 2. 上行短信仅支持部分运营商
 * 3. 签名需要提前在腾讯云控制台审核通过
 * 4. 支持的编码格式：UTF-8
 * 5. 时间格式为Unix时间戳（秒）
 * 
 * @author whaleal-dev
 * @author 恒哥
 */
public class TencentInboundParser implements InboundParser {
    
    private static final Logger logger = LoggerFactory.getLogger(TencentInboundParser.class);
    
    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            logger.warn("收到无效的腾讯云短信上行数据: {}", rawData);
            return null;
        }
        
        try {
        return SmsInboundMessage.builder()
                .messageId(getString(rawData, "sid"))
                .from(getString(rawData, "mobile"))
                    .to(getString(rawData, "ext"))
                .content(getString(rawData, "content"))
                .messageType(SmsInboundMessage.MessageType.TEXT)
                    .receivedTime(parseDateTime(getString(rawData, "time")))
                .rawData(rawData)
                .build();
    
        } catch (Exception e) {
            logger.error("解析腾讯云短信上行数据失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && 
               rawData.containsKey("mobile") &&
               rawData.containsKey("content");
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    private LocalDateTime parseDateTime(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            long timestamp = Long.parseLong(timestampStr);
            return LocalDateTime.ofEpochSecond(timestamp, 0, java.time.ZoneOffset.ofHours(8));
        } catch (Exception e) {
            logger.warn("解析时间失败: {}", timestampStr);
            return LocalDateTime.now();
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.TENCENT.name();
    }
} 