package com.whaleal.ark.cloud.third.sms.util;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * SMS SDK 工具类
 * 提供短信相关的常用工具方法
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class SmsUtils {
    
    // 国际手机号正则表达式
    private static final Pattern INTERNATIONAL_PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");
    
    // 中国大陆手机号正则表达式
    private static final Pattern CHINA_MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    
    // 常见时间格式
    private static final DateTimeFormatter[] TIME_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME
    };
    
    /**
     * 生成消息ID
     * 
     * @return 唯一的消息ID
     */
    public static String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成带前缀的消息ID
     * 
     * @param prefix 前缀
     * @return 带前缀的消息ID
     */
    public static String generateMessageId(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return generateMessageId();
        }
        return prefix + "_" + generateMessageId();
    }
    
    /**
     * 校验国际手机号格式
     * 
     * @param phoneNumber 手机号
     * @return 是否为有效的国际手机号格式
     */
    public static boolean isValidInternationalPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        String cleanNumber = cleanPhoneNumber(phoneNumber);
        return INTERNATIONAL_PHONE_PATTERN.matcher(cleanNumber).matches();
    }
    
    /**
     * 校验中国大陆手机号格式
     * 
     * @param phoneNumber 手机号
     * @return 是否为有效的中国大陆手机号格式
     */
    public static boolean isValidChinaMobile(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        String cleanNumber = cleanPhoneNumber(phoneNumber);
        return CHINA_MOBILE_PATTERN.matcher(cleanNumber).matches();
    }
    
    /**
     * 清理手机号（移除非数字字符，保留加号）
     * 
     * @param phoneNumber 原始手机号
     * @return 清理后的手机号
     */
    public static String cleanPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        
        return phoneNumber.replaceAll("[^\\+\\d]", "");
    }
    
    /**
     * 格式化手机号（添加国际区号）
     * 
     * @param phoneNumber 手机号
     * @param countryCode 国家代码（如86）
     * @return 格式化后的手机号
     */
    public static String formatPhoneNumber(String phoneNumber, String countryCode) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return phoneNumber;
        }
        
        String cleanNumber = cleanPhoneNumber(phoneNumber);
        
        // 如果已经有国际区号，直接返回
        if (cleanNumber.startsWith("+")) {
            return cleanNumber;
        }
        
        // 如果是中国大陆手机号且没有区号，添加+86
        if (countryCode != null && isValidChinaMobile(cleanNumber)) {
            return "+" + countryCode + cleanNumber;
        }
        
        return cleanNumber;
    }
    
    /**
     * 计算短信长度（考虑中英文差异）
     * 
     * @param content 短信内容
     * @return 短信长度信息
     */
    public static SmsLengthInfo calculateSmsLength(String content) {
        if (content == null || content.isEmpty()) {
            return new SmsLengthInfo(0, 0, 0, false);
        }
        
        // 检查是否包含Unicode字符
        boolean hasUnicode = !content.matches("^[\\x00-\\x7F]*$");
        
        // 计算字符数
        int characterCount = content.length();
        
        // 计算短信条数
        int smsCount;
        int maxLength;
        
        if (hasUnicode) {
            // Unicode SMS (UTF-16): 单条70字符，多条67字符
            maxLength = characterCount <= 70 ? 70 : 67;
            smsCount = (int) Math.ceil((double) characterCount / maxLength);
        } else {
            // GSM 7-bit: 单条160字符，多条153字符
            maxLength = characterCount <= 160 ? 160 : 153;
            smsCount = (int) Math.ceil((double) characterCount / maxLength);
        }
        
        return new SmsLengthInfo(characterCount, smsCount, maxLength, hasUnicode);
    }
    
    /**
     * 解析时间字符串
     * 
     * @param timeString 时间字符串
     * @return LocalDateTime对象
     */
    public static LocalDateTime parseDateTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }
        
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(timeString, formatter);
            } catch (Exception e) {
                // 继续尝试下一个格式
            }
        }
        
        log.warn("无法解析时间字符串: {}", timeString);
        return null;
    }
    
    /**
     * 生成签名
     * 
     * @param data 待签名数据
     * @param secret 密钥
     * @return MD5签名
     */
    public static String generateMd5Signature(String data, String secret) {
        try {
            String signData = data + secret;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(signData.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("生成MD5签名失败", e);
            return null;
        }
    }
    
    /**
     * Base64编码
     * 
     * @param data 原始数据
     * @return Base64编码后的字符串
     */
    public static String base64Encode(String data) {
        if (data == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Base64解码
     * 
     * @param encodedData Base64编码的数据
     * @return 解码后的字符串
     */
    public static String base64Decode(String encodedData) {
        if (encodedData == null) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedData);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Base64解码失败", e);
            return null;
        }
    }
    
    /**
     * 提取JSON值（简单实现）
     * 
     * @param json JSON字符串
     * @param key 键名
     * @return 值
     */
    public static String extractJsonValue(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        
        try {
            // 匹配字符串值
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            
            // 匹配数字或布尔值
            pattern = "\"" + key + "\"\\s*:\\s*([^,}\\]]+)";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(json);
            if (m.find()) {
                return m.group(1).trim();
            }
            
            return null;
        } catch (Exception e) {
            log.warn("提取JSON值失败 - key: {}, json: {}", key, json);
            return null;
        }
    }
    
    /**
     * 掩码显示敏感信息
     * 
     * @param sensitive 敏感信息
     * @param visibleStart 开始显示的字符数
     * @param visibleEnd 结尾显示的字符数
     * @return 掩码后的字符串
     */
    public static String maskSensitive(String sensitive, int visibleStart, int visibleEnd) {
        if (sensitive == null || sensitive.length() <= visibleStart + visibleEnd) {
            return sensitive;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(sensitive.substring(0, visibleStart));
        
        int maskLength = sensitive.length() - visibleStart - visibleEnd;
        for (int i = 0; i < maskLength; i++) {
            sb.append("*");
        }
        
        sb.append(sensitive.substring(sensitive.length() - visibleEnd));
        return sb.toString();
    }
    
    /**
     * 短信长度信息
     */
    public static class SmsLengthInfo {
        private final int characterCount;
        private final int smsCount;
        private final int maxLength;
        private final boolean hasUnicode;
        
        public SmsLengthInfo(int characterCount, int smsCount, int maxLength, boolean hasUnicode) {
            this.characterCount = characterCount;
            this.smsCount = smsCount;
            this.maxLength = maxLength;
            this.hasUnicode = hasUnicode;
        }
        
        public int getCharacterCount() { return characterCount; }
        public int getSmsCount() { return smsCount; }
        public int getMaxLength() { return maxLength; }
        public boolean hasUnicode() { return hasUnicode; }
        
        @Override
        public String toString() {
            return String.format("SmsLengthInfo{characters=%d, smsCount=%d, maxLength=%d, unicode=%s}", 
                    characterCount, smsCount, maxLength, hasUnicode);
        }
    }
} 