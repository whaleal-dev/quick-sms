package com.whaleal.ark.cloud.third.sms.provider.china_telecom;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * 中国电信短信发送器
 * 
 */
@Slf4j
public class ChinaTelecomOutboundSender implements OutboundSender {

    private static final int TIMEOUT_MS = 10000;

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        try {
            log.info("开始发送中国电信短信 - 接收方: {}", message.getTo());
            
            validateConfig(config);
            validateMessage(message);
            
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setSentTime(LocalDateTime.now());
            
            String requestBody = buildRequestBody(message, config);
            String signature = generateSignature(requestBody, config.getAccessKeySecret());
            String response = sendHttpRequest(requestBody, signature, config);
            
            return parseResponse(response, message);
            
        } catch (Exception e) {
            log.error("中国电信短信发送失败 - 接收方: {}, 错误: {}", 
                    message.getTo(), e.getMessage(), e);
            
            return createFailedMessage(message, e.getMessage());
        }
    }

    @Override
    public String getSupportedProvider() {
        return "CHINA_TELECOM";
    }

    private void validateConfig(SmsProviderConfig config) {
        if (config.getAccessKeyId() == null || config.getAccessKeyId().trim().isEmpty()) {
            throw new IllegalArgumentException("中国电信AccessKeyId不能为空");
        }
        if (config.getAccessKeySecret() == null || config.getAccessKeySecret().trim().isEmpty()) {
            throw new IllegalArgumentException("中国电信AccessKeySecret不能为空");
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
        params.put("accessKeyId", config.getAccessKeyId());
        params.put("timestamp", System.currentTimeMillis() / 1000);
        params.put("phoneNumbers", message.getTo());
        params.put("signName", config.getSignName() != null ? config.getSignName() : "电信短信");
        
        // 如果有模板ID，使用模板短信
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateId() != null) {
            params.put("templateCode", message.getBusinessInfo().getTemplateId());
            params.put("templateParam", formatTemplateContent(message));
        } else {
            params.put("templateCode", "SMS_DEFAULT");
            params.put("templateParam", "{\"content\":\"" + message.getContent() + "\"}");
        }
        
        return formatParams(params);
    }

    private String formatTemplateContent(SmsOutboundMessage message) {
        if (message.getBusinessInfo().getTemplateParams() != null) {
            return formatTemplateParams(message.getBusinessInfo().getTemplateParams());
        }
        return "{\"content\":\"" + message.getContent() + "\"}";
    }

    private String formatTemplateParams(Map<String, String> templateParams) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : templateParams.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String formatParams(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    private String generateSignature(String requestBody, String secretKey) throws Exception {
        String stringToSign = "POST\napplication/x-www-form-urlencoded\n\n" + requestBody;
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            (secretKey + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(secretKeySpec);
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    private String sendHttpRequest(String requestBody, String signature, SmsProviderConfig config) throws Exception {
        String apiUrl = config.getOutboundBaseUrl() != null ?
            config.getOutboundBaseUrl() : "https://sms.189.cn/v2/sendSms";
        
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Authorization", "Bearer " + signature);
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
            log.debug("中国电信API响应: {}", response);
            
            if (response.contains("\"code\":\"OK\"") || response.contains("\"success\":true")) {
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
            message.getRawData().put("china_telecom_response", response);
            
            return message;
            
        } catch (Exception e) {
            log.error("中国电信响应解析失败: {}", e.getMessage(), e);
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
        originalMessage.getExtraInfo().put("error", "中国电信发送失败: " + errorMessage);
        
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