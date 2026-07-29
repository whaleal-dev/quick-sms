package com.whaleal.ark.cloud.third.sms.provider.twilio;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import com.whaleal.ark.cloud.third.sms.exception.SmsException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * Twilio短信发送器
 * 实现真实的Twilio API调用
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class TwilioOutboundSender implements OutboundSender {
    
    private final HttpClient httpClient;
    
    public TwilioOutboundSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }
    
    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) throws SmsException {
        try {
            log.info("Twilio发送短信 - 接收方: {}, 内容长度: {}", message.getTo(), 
                    message.getContent() != null ? message.getContent().length() : 0);
            
            // 构建请求参数
            String requestBody = buildRequestBody(message, config);
            
            // 发送HTTP请求
            HttpResponse<String> response = sendHttpRequest(requestBody, config);
            
            // 解析响应
            return parseResponse(message, response);
            
        } catch (Exception e) {
            log.error("Twilio发送短信失败，接收方: {}, 错误: {}", message.getTo(), e.getMessage(), e);
            return createFailedMessage(message, e.getMessage());
        }
    }
    
    @Override
    public List<SmsOutboundMessage> sendMessages(List<SmsOutboundMessage> messages, SmsProviderConfig config) throws SmsException {
        log.info("Twilio批量发送短信 - 数量: {}", messages.size());
        
        // Twilio不支持批量发送，逐个发送
        return messages.stream()
                .map(msg -> {
                    try {
                        return sendMessage(msg, config);
                    } catch (SmsException e) {
                        return createFailedMessage(msg, e.getMessage());
                    }
                })
                .toList();
    }
    
    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) throws SmsException {
        log.info("Twilio发送模板短信 - 模板ID: {}", 
                message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : "未知");
        
        // Twilio使用普通短信方式发送模板内容
        // 模板参数应该已经在业务层替换完成
        return sendMessage(message, config);
    }
    
    /**
     * 构建请求体
     */
    private String buildRequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        Map<String, String> params = new HashMap<>();
        params.put("From", message.getFrom() != null ? message.getFrom() : config.getDefaultFrom());
        params.put("To", message.getTo());
        params.put("Body", message.getContent());
        
        // 添加可选参数
        if (message.getSendConfig() != null) {
            // 设置回调URL
            if (message.getSendConfig().getCallbackUrl() != null) {
                params.put("StatusCallback", message.getSendConfig().getCallbackUrl());
            }
            
            // 设置有效期
            if (message.getSendConfig().getValidityPeriod() != null) {
                params.put("ValidityPeriod", message.getSendConfig().getValidityPeriod().toString());
            }
        }
        
        // 构建form-urlencoded格式
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
        }
        
        return sb.toString();
    }
    
    /**
     * 发送HTTP请求
     */
    private HttpResponse<String> sendHttpRequest(String requestBody, SmsProviderConfig config) throws IOException, InterruptedException {
        // 构建API URL
        String accountSid = config.getApiKey(); // Twilio使用ApiKey作为AccountSid
        String url = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", accountSid);
        
        // 构建认证头
        String auth = accountSid + ":" + config.getApiSecret();
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        
        // 构建请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", "SMS-SDK/1.0")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        log.debug("Twilio API请求 - URL: {}, Body: {}", url, requestBody);
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    /**
     * 解析响应
     */
    private SmsOutboundMessage parseResponse(SmsOutboundMessage originalMessage, HttpResponse<String> response) {
        String responseBody = response.body();
        int statusCode = response.statusCode();
        
        log.debug("Twilio API响应 - 状态码: {}, 响应体: {}", statusCode, responseBody);
        
        if (statusCode >= 200 && statusCode < 300) {
            // 解析成功响应
            return parseSuccessResponse(originalMessage, responseBody);
        } else {
            // 解析错误响应
            return parseErrorResponse(originalMessage, responseBody, statusCode);
        }
    }
    
    /**
     * 解析成功响应
     */
    private SmsOutboundMessage parseSuccessResponse(SmsOutboundMessage originalMessage, String responseBody) {
        try {
            // 简单的JSON解析（实际项目中应该使用JSON库）
            String messageSid = extractJsonValue(responseBody, "sid");
            String status = extractJsonValue(responseBody, "status");
            String price = extractJsonValue(responseBody, "price");
            String priceUnit = extractJsonValue(responseBody, "price_unit");
            
            // 设置消息ID
            originalMessage.setMessageId(UUID.randomUUID().toString());
            originalMessage.setProviderMessageId(messageSid);
            
            // 设置发送状态
            originalMessage.setSendStatus(mapTwilioStatus(status));
            originalMessage.setSentTime(LocalDateTime.now());
            
            // 设置费用信息
            if (price != null && !price.equals("null")) {
                originalMessage.setCostInfo(SmsOutboundMessage.CostInfo.builder()
                        .amount(price)
                        .currency(priceUnit != null ? priceUnit : "USD")
                        .billingType("per_message")
                        .messageCount(1)
                        .unitPrice(price)
                        .billingTime(LocalDateTime.now())
                        .build());
            }
            
            // 设置扩展信息
            if (originalMessage.getExtraInfo() == null) {
                originalMessage.setExtraInfo(new HashMap<>());
            }
            originalMessage.getExtraInfo().put("twilio_sid", messageSid);
            originalMessage.getExtraInfo().put("twilio_status", status);
            originalMessage.getExtraInfo().put("api_response", responseBody);
            
            log.info("Twilio发送成功 - MessageSid: {}, Status: {}", messageSid, status);
            
            return originalMessage;
            
        } catch (Exception e) {
            log.error("解析Twilio成功响应失败: {}", e.getMessage(), e);
            return createFailedMessage(originalMessage, "响应解析失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析错误响应
     */
    private SmsOutboundMessage parseErrorResponse(SmsOutboundMessage originalMessage, String responseBody, int statusCode) {
        try {
            String errorCode = extractJsonValue(responseBody, "code");
            String errorMessage = extractJsonValue(responseBody, "message");
            
            log.error("Twilio发送失败 - 状态码: {}, 错误代码: {}, 错误信息: {}", statusCode, errorCode, errorMessage);
            
            originalMessage.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            
            // 设置扩展信息
            if (originalMessage.getExtraInfo() == null) {
                originalMessage.setExtraInfo(new HashMap<>());
            }
            originalMessage.getExtraInfo().put("error_code", errorCode);
            originalMessage.getExtraInfo().put("error_message", errorMessage);
            originalMessage.getExtraInfo().put("http_status", statusCode);
            originalMessage.getExtraInfo().put("api_response", responseBody);
            
            return originalMessage;
            
        } catch (Exception e) {
            log.error("解析Twilio错误响应失败: {}", e.getMessage(), e);
            return createFailedMessage(originalMessage, "错误响应解析失败: " + e.getMessage());
        }
    }
    
    /**
     * 映射Twilio状态到内部状态
     */
    private SmsOutboundMessage.SendStatus mapTwilioStatus(String twilioStatus) {
        if (twilioStatus == null) {
            return SmsOutboundMessage.SendStatus.FAILED;
        }
        
        switch (twilioStatus.toLowerCase()) {
            case "queued":
            case "accepted":
            case "sending":
                return SmsOutboundMessage.SendStatus.SUBMITTED;
            case "sent":
            case "delivered":
                return SmsOutboundMessage.SendStatus.SUBMITTED;
            case "failed":
            case "undelivered":
                return SmsOutboundMessage.SendStatus.FAILED;
            default:
                return SmsOutboundMessage.SendStatus.FAILED;
        }
    }
    
    /**
     * 简单的JSON值提取（实际项目中应该使用JSON库）
     */
    private String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            
            // 尝试匹配数字或null值
            pattern = "\"" + key + "\"\\s*:\\s*([^,}]+)";
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
     * URL编码
     */
    private String urlEncode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return "TWILIO";
    }
} 