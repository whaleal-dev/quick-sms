package com.whaleal.ark.cloud.third.sms.provider.china_mobile;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import com.whaleal.ark.cloud.third.sms.exception.SmsException;
import com.whaleal.ark.cloud.third.sms.exception.SmsTimeoutException;
import com.whaleal.ark.cloud.third.sms.exception.SmsCredentialsException;
import com.whaleal.ark.cloud.third.sms.exception.SmsParameterException;
import com.whaleal.ark.cloud.third.sms.exception.SmsNetworkException;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * 中国移动短信发送器
 * 支持国内和国际短信
 * 
 * @author whaleal-dev
 * @author 恒哥
 */
public class ChinaMobileOutboundSender implements OutboundSender {
    
    private static final Logger logger = LoggerFactory.getLogger(ChinaMobileOutboundSender.class);
    
    private static final String ENDPOINT = "https://mas.10086.cn/sms/send";
    private static final int TIMEOUT_SECONDS = 10;
    
    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        logger.info("Sending SMS via China Mobile to: {}", message.getTo());
        
        validateConfig(config);
        
        try {
            // 构建请求参数
            Map<String, String> params = buildRequestParams(message, config);
            
            // 发送HTTP请求
            String response = sendHttpRequest(params);
            
            // 解析响应
            return parseResponse(response, message);
            
        } catch (SmsException e) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to send SMS via China Mobile", e);
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsNetworkException("Failed to send SMS via China Mobile: " + e.getMessage(), 
                SmsProviderType.CHINA_MOBILE, e);
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.CHINA_MOBILE.name();
    }
    
    private void validateConfig(SmsProviderConfig config) {
        if (config == null) {
            throw new SmsParameterException("China Mobile SMS config cannot be null");
        }
        if (config.getStringConfig("userId", null) == null) {
            throw new SmsCredentialsException("China Mobile userId cannot be null or empty");
        }
        if (config.getStringConfig("password", null) == null) {
            throw new SmsCredentialsException("China Mobile password cannot be null or empty");
        }
        if (config.getStringConfig("spCode", null) == null) {
            throw new SmsParameterException("China Mobile spCode cannot be null or empty");
        }
    }
    
    private Map<String, String> buildRequestParams(SmsOutboundMessage message, SmsProviderConfig config) {
        Map<String, String> params = new HashMap<>();
        
        // 基本参数
        params.put("userId", config.getStringConfig("userId", ""));
        params.put("password", generatePassword(config));
        params.put("spCode", config.getStringConfig("spCode", ""));
        params.put("msisdn", message.getTo());
        params.put("content", message.getContent());
        params.put("timestamp", getCurrentTimestamp());
        
        // 扩展参数
        if (message.getExtraInfo() != null && message.getExtraInfo().get("linkId") != null) {
            params.put("linkId", message.getExtraInfo().get("linkId").toString());
        }
        
        if (message.getExtraInfo() != null && message.getExtraInfo().get("extendCode") != null) {
            params.put("extendCode", message.getExtraInfo().get("extendCode").toString());
        }
        
        return params;
    }
    
    private String generatePassword(SmsProviderConfig config) {
        String userId = config.getStringConfig("userId", "");
        String password = config.getStringConfig("password", "");
        String timestamp = getCurrentTimestamp();
        
        try {
            // MD5(userId+password+timestamp)
            String source = userId + password + timestamp;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(source.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to generate password", e);
            return password;
        }
    }
    
    private String sendHttpRequest(Map<String, String> params) throws Exception {
        // 构建请求体
        StringBuilder requestBody = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                requestBody.append("&");
            }
            requestBody.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                .append("=").append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            first = false;
        }
        
        URL url = new URL(ENDPOINT);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT_SECONDS * 1000);
            connection.setReadTimeout(TIMEOUT_SECONDS * 1000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            
            // 发送请求体
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            int responseCode = connection.getResponseCode();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode == 200 ? connection.getInputStream() : connection.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                if (responseCode != 200) {
                    throw new SmsNetworkException("HTTP request failed with code: " + responseCode + ", response: " + response.toString(),
                        SmsProviderType.CHINA_MOBILE);
                }
                
                return response.toString();
            }
            
        } catch (java.net.SocketTimeoutException e) {
            throw new SmsTimeoutException("Request timeout after " + TIMEOUT_SECONDS + " seconds", 
                TIMEOUT_SECONDS * 1000, SmsProviderType.CHINA_MOBILE);
        } finally {
            connection.disconnect();
        }
    }
    
    private SmsOutboundMessage parseResponse(String responseBody, SmsOutboundMessage message) {
        logger.debug("China Mobile SMS API response: {}", responseBody);
        
        try {
            // 中国移动通常返回XML格式
            if (responseBody.contains("<code>0</code>") || responseBody.contains("\"code\":\"0\"")) {
                // 成功响应
                String messageId = extractValue(responseBody, "msgId", "messageId");
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.SENT);
                message.setMessageId(messageId);
                message.setProviderType(SmsProviderType.CHINA_MOBILE);
                
                return message;
            } else {
                String code = extractValue(responseBody, "code", "code");
                String errorMessage = extractValue(responseBody, "message", "message");
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                throw new SmsException("CHINA_MOBILE_API_ERROR", "China Mobile SMS API error: " + code + " - " + errorMessage, 
                    SmsProviderType.CHINA_MOBILE);
            }
            
        } catch (Exception e) {
            if (e instanceof SmsException) {
                throw e;
            }
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsException("RESPONSE_PARSE_ERROR", "Failed to parse China Mobile SMS response: " + e.getMessage(), 
                SmsProviderType.CHINA_MOBILE, e);
        }
    }
    
    private String extractValue(String content, String xmlTag, String jsonKey) {
        // 尝试XML格式
        String xmlStartTag = "<" + xmlTag + ">";
        String xmlEndTag = "</" + xmlTag + ">";
        int xmlStartIndex = content.indexOf(xmlStartTag);
        if (xmlStartIndex != -1) {
            xmlStartIndex += xmlStartTag.length();
            int xmlEndIndex = content.indexOf(xmlEndTag, xmlStartIndex);
            if (xmlEndIndex != -1) {
                return content.substring(xmlStartIndex, xmlEndIndex);
            }
        }
        
        // 尝试JSON格式
        String jsonSearchKey = "\"" + jsonKey + "\":\"";
        int jsonStartIndex = content.indexOf(jsonSearchKey);
        if (jsonStartIndex != -1) {
            jsonStartIndex += jsonSearchKey.length();
            int jsonEndIndex = content.indexOf("\"", jsonStartIndex);
            if (jsonEndIndex != -1) {
                return content.substring(jsonStartIndex, jsonEndIndex);
            }
        }
        
        return null;
    }
    
    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }
} 