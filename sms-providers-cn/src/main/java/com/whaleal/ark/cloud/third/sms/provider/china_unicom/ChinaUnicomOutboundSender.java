package com.whaleal.ark.cloud.third.sms.provider.china_unicom;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * 中国联通短信发送器
 */
@Slf4j
public class ChinaUnicomOutboundSender implements OutboundSender {

    private static final int TIMEOUT_MS = 10000;

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        try {
            log.info("开始发送中国联通短信 - 接收方: {}", message.getTo());
            
            validateConfig(config);
            validateMessage(message);
            
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setSentTime(LocalDateTime.now());
            
            String requestBody = buildRequestBody(message, config);
            String response = sendHttpRequest(requestBody, config);
            
            return parseResponse(response, message);
            
        } catch (Exception e) {
            log.error("中国联通短信发送失败 - 接收方: {}, 错误: {}", 
                    message.getTo(), e.getMessage(), e);
            
            return createFailedMessage(message, e.getMessage());
        }
    }

    @Override
    public String getSupportedProvider() {
        return "CHINA_UNICOM";
    }

    private void validateConfig(SmsProviderConfig config) {
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("中国联通用户名不能为空");
        }
        if (config.getApiSecret() == null || config.getApiSecret().trim().isEmpty()) {
            throw new IllegalArgumentException("中国联通密码不能为空");
        }
    }

    private void validateMessage(SmsOutboundMessage message) {
        if (message.getTo() == null || message.getTo().trim().isEmpty()) {
            throw new IllegalArgumentException("接收号码不能为空");
        }
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("短信内容不能为空");
        }
    }

    private String buildRequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        Map<String, Object> params = new HashMap<>();
        
        // 基本认证参数
        params.put("username", config.getApiKey());
        params.put("password", generatePasswordHash(config.getApiSecret()));
        params.put("timestamp", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        
        // 短信参数
        params.put("mobile", message.getTo());
        params.put("content", message.getContent());
        params.put("sign", config.getSignName() != null ? config.getSignName() : "联通短信");
        
        // 模板短信参数
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateId() != null) {
            params.put("templateId", message.getBusinessInfo().getTemplateId());
            if (message.getBusinessInfo().getTemplateParams() != null) {
                params.put("templateParams", formatTemplateParams(message.getBusinessInfo().getTemplateParams()));
            }
        }
        
        return formatToJson(params);
    }

    private String generatePasswordHash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    private String formatTemplateParams(Map<String, String> templateParams) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : templateParams.entrySet()) {
            if (!first) sb.append(",");
            sb.append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    private String formatToJson(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String sendHttpRequest(String requestBody, SmsProviderConfig config) throws Exception {
        String apiUrl = config.getOutboundBaseUrl() != null ?
            config.getOutboundBaseUrl() : "https://sms.10010.com/api/v1/sendSms";
        
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "SMS-SDK/1.0");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }
            
            int responseCode = connection.getResponseCode();
            StringBuilder response = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode == 200 ? connection.getInputStream() : connection.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            if (responseCode != 200) {
                throw new RuntimeException("HTTP请求失败，状态码: " + responseCode + ", 响应: " + response.toString());
            }
            
            return response.toString();
            
        } finally {
            connection.disconnect();
        }
    }

    private SmsOutboundMessage parseResponse(String response, SmsOutboundMessage message) {
        try {
            log.debug("中国联通API响应: {}", response);
            
            if (response.contains("\"status\":\"0\"") || 
                response.contains("\"code\":\"200\"") || 
                response.contains("\"success\":true")) {
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.SENT);
                
                String messageId = extractJsonValue(response, "messageId");
                if (messageId != null) {
                    message.setProviderMessageId(messageId);
                }
                
                message.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(2));
                
            } else {
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                String errorMessage = extractJsonValue(response, "message");
                
                if (message.getExtraInfo() == null) {
                    message.setExtraInfo(new HashMap<>());
                }
                message.getExtraInfo().put("error", errorMessage != null ? errorMessage : "未知错误");
            }
            
            // 保存原始响应
            if (message.getRawData() == null) {
                message.setRawData(new HashMap<>());
            }
            message.getRawData().put("china_unicom_response", response);
            
            return message;
            
        } catch (Exception e) {
            log.error("中国联通响应解析失败: {}", e.getMessage(), e);
            return createFailedMessage(message, "响应解析失败: " + e.getMessage());
        }
    }

    /**
     * 创建失败消息
     */
    public SmsOutboundMessage createFailedMessage(SmsOutboundMessage originalMessage, String errorMessage) {
        originalMessage.setMessageId(UUID.randomUUID().toString());
        originalMessage.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        originalMessage.setSentTime(LocalDateTime.now());
        
        if (originalMessage.getExtraInfo() == null) {
            originalMessage.setExtraInfo(new HashMap<>());
        }
        originalMessage.getExtraInfo().put("error", "中国联通发送失败: " + errorMessage);
        
        return originalMessage;
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return null;
        
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex > startIndex) {
            return json.substring(startIndex, endIndex);
        }
        return null;
    }
} 