package com.whaleal.ark.cloud.third.sms.provider.huawei;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.TimeZone;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * 华为云短信发送器
 * 支持国内和国际短信统一接口
 * 
 * @author whaleal-dev
 * @author 恒哥
 */
public class HuaweiOutboundSender implements OutboundSender {
    
    private static final Logger logger = LoggerFactory.getLogger(HuaweiOutboundSender.class);
    
    private static final String ENDPOINT = "https://smsapi.cn-north-4.myhuaweicloud.com:443/sms/batchSendSms/v1";
    private static final int TIMEOUT_SECONDS = 10;
    
    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        logger.info("Sending SMS via Huawei Cloud to: {}", message.getTo());
        
        validateConfig(config);
        
        try {
            // 构建请求体
            String requestBody = buildRequestBody(message, config);
            
            // 发送HTTP请求
            String response = sendHttpRequest(requestBody, config);
            
            // 解析响应
            return parseResponse(response, message);
            
        } catch (SmsException e) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to send SMS via Huawei Cloud", e);
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsNetworkException("Failed to send SMS via Huawei Cloud: " + e.getMessage(), 
                SmsProviderType.HUAWEI, e);
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.HUAWEI.name();
    }
    
    private void validateConfig(SmsProviderConfig config) {
        if (config == null) {
            throw new SmsParameterException("Huawei SMS config cannot be null");
        }
        if (isBlank(resolveAppKey(config))) {
            throw new SmsCredentialsException("Huawei appKey cannot be null or empty");
        }
        if (isBlank(resolveAppSecret(config))) {
            throw new SmsCredentialsException("Huawei appSecret cannot be null or empty");
        }
        if (isBlank(resolveSender(config))) {
            throw new SmsParameterException("Huawei sender cannot be null or empty");
        }
    }
    
    private String buildRequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // 发送方
        json.append("\"from\":\"").append(resolveSender(config)).append("\",");
        
        // 接收方
        json.append("\"to\":[\"").append(message.getTo()).append("\"],");
        
        // 模板ID
        String templateId = "SMS_DEFAULT";
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateId() != null) {
            templateId = message.getBusinessInfo().getTemplateId();
        }
        json.append("\"templateId\":\"").append(templateId).append("\"");
        
        // 模板参数
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null && !message.getBusinessInfo().getTemplateParams().isEmpty()) {
            json.append(",\"templateParas\":[");
            boolean first = true;
            for (String value : message.getBusinessInfo().getTemplateParams().values()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(value).append("\"");
                first = false;
            }
            json.append("]");
        }
        
        // 签名
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getSignature() != null) {
            json.append(",\"signature\":\"").append(message.getBusinessInfo().getSignature()).append("\"");
        }
        
        json.append("}");
        return json.toString();
    }
    
    private String sendHttpRequest(String requestBody, SmsProviderConfig config) throws Exception {
        URL url = new URL(ENDPOINT);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT_SECONDS * 1000);
            connection.setReadTimeout(TIMEOUT_SECONDS * 1000);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "WSSE realm=\"SDP\",profile=\"UsernameToken\",type=\"Appkey\"");
            connection.setRequestProperty("X-WSSE", buildWSSEHeader(config));
            
            // 发送请求体
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
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
                        SmsProviderType.HUAWEI);
                }
                
                return response.toString();
            }
            
        } catch (java.net.SocketTimeoutException e) {
            throw new SmsTimeoutException("Request timeout after " + TIMEOUT_SECONDS + " seconds", 
                TIMEOUT_SECONDS * 1000, SmsProviderType.HUAWEI);
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * PasswordDigest = Base64(SHA-256(nonce + created + appSecret))，Created 为 UTC ISO8601。
     * 与华为云官方及国际通道实现一致。
     */
    private String buildWSSEHeader(SmsProviderConfig config) {
        try {
            String appKey = resolveAppKey(config);
            String appSecret = resolveAppSecret(config);
            String nonce = UUID.randomUUID().toString().replace("-", "");
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            String created = df.format(new Date());
            String passwordDigest = passwordDigest(nonce, created, appSecret);
            return String.format(
                    "UsernameToken Username=\"%s\",PasswordDigest=\"%s\",Nonce=\"%s\",Created=\"%s\"",
                    appKey, passwordDigest, nonce, created);
        } catch (Exception e) {
            throw new SmsCredentialsException("Huawei WSSE signature failed: " + e.getMessage(),
                    SmsProviderType.HUAWEI, e);
        }
    }

    /** 供单测校验摘要算法 */
    static String passwordDigest(String nonce, String created, String appSecret) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest((nonce + created + appSecret).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static String resolveAppKey(SmsProviderConfig config) {
        return firstNonBlank(config.getAppKey(), config.getApiKey(), config.getAccessKeyId(),
                config.getStringConfig("appKey", null));
    }

    private static String resolveAppSecret(SmsProviderConfig config) {
        return firstNonBlank(config.getAppSecret(), config.getApiSecret(), config.getAccessKeySecret(),
                config.getStringConfig("appSecret", null));
    }

    private static String resolveSender(SmsProviderConfig config) {
        return firstNonBlank(config.getDefaultFrom(), config.getStringConfig("sender", null));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (!isBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }

    private SmsOutboundMessage parseResponse(String responseBody, SmsOutboundMessage message) {
        logger.debug("Huawei SMS API response: {}", responseBody);
        
        try {
            // 检查响应状态
            if (responseBody.contains("\"code\":\"000000\"")) {
                // 成功响应
                String result = extractJsonValue(responseBody, "result");
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.SENT);
                message.setProviderMessageId(result);
                message.setProviderType(SmsProviderType.HUAWEI);
                
                return message;
            } else {
                String code = extractJsonValue(responseBody, "code");
                String description = extractJsonValue(responseBody, "description");
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                throw new SmsException("HUAWEI_API_ERROR", "Huawei SMS API error: " + code + " - " + description, 
                    SmsProviderType.HUAWEI);
            }
            
        } catch (Exception e) {
            if (e instanceof SmsException) {
                throw e;
            }
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsException("RESPONSE_PARSE_ERROR", "Failed to parse Huawei SMS response: " + e.getMessage(), 
                SmsProviderType.HUAWEI, e);
        }
    }
    
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return null;
        }
        return json.substring(startIndex, endIndex);
    }
} 