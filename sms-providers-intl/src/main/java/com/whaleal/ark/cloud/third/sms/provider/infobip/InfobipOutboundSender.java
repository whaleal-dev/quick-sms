package com.whaleal.ark.cloud.third.sms.provider.infobip;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import lombok.extern.slf4j.Slf4j;

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

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * Infobip短信发送器
 * 
 */
@Slf4j
public class InfobipOutboundSender implements OutboundSender {

    private static final int TIMEOUT_MS = 10000; // 10秒超时

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        try {
            log.info("开始发送Infobip短信 - 接收方: {}", message.getTo());
            
            // 参数验证
            validateConfig(config);
            validateMessage(message);
            
            // 更新消息状态为发送中
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setSentTime(LocalDateTime.now());
            
            // 构建请求
            String requestBody = buildRequestBody(message, config);
            
            // 发送HTTP请求
            String response = sendHttpRequest(requestBody, config);
            
            // 解析响应
            return parseResponse(response, message);
            
        } catch (Exception e) {
            log.error("Infobip短信发送失败 - 接收方: {}, 错误: {}", 
                    message.getTo(), e.getMessage(), e);
            
            return createFailedMessage(message, e.getMessage());
        }
    }

    @Override
    public String getSupportedProvider() {
        return "INFOBIP";
    }

    /**
     * 验证配置参数
     */
    private void validateConfig(SmsProviderConfig config) {
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Infobip API Key不能为空");
        }
    }

    /**
     * 验证消息参数
     */
    private void validateMessage(SmsOutboundMessage message) {
        if (message.getTo() == null || message.getTo().trim().isEmpty()) {
            throw new IllegalArgumentException("接收号码不能为空");
        }
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("短信内容不能为空");
        }
    }

    /**
     * 构建请求体
     */
    private String buildRequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        Map<String, Object> requestData = new HashMap<>();
        
        // 构建消息数组
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("from", getFromNumber(message, config));
        messageData.put("to", message.getTo());
        messageData.put("text", message.getContent());
        
        // 添加编码/音译参数
        // Infobip自动检测编码，但可通过transliteration控制字符转换
        String encoding = message.getEncoding();
        if (encoding != null) {
            // 如果明确指定为text/gsm7，使用音译将Unicode字符转为GSM-7
            if ("text".equalsIgnoreCase(encoding) || "gsm7".equalsIgnoreCase(encoding)) {
                messageData.put("transliteration", "NON_UNICODE");
            }
            // unicode编码不需要特殊处理，Infobip会自动处理
        }
        
        // 可选参数
        if (message.getSendConfig() != null) {
            // 状态报告URL
            if (message.getSendConfig().getCallbackUrl() != null) {
                messageData.put("notifyUrl", message.getSendConfig().getCallbackUrl());
            }
            
            // 有效期设置 (Infobip使用分钟)
            if (message.getSendConfig().getValidityPeriod() != null) {
                messageData.put("validityPeriod", message.getSendConfig().getValidityPeriod());
            }
            
            // 消息ID
            if (message.getMessageId() != null) {
                messageData.put("messageId", message.getMessageId());
            }
        }
        
        requestData.put("messages", new Object[]{messageData});
        
        return formatToJson(requestData);
    }

    /**
     * 获取发送方号码
     * 对于国际短信提供商，优先级：signName(品牌名) > message.getFrom() > config.getDefaultFrom() > 默认值
     */
    private String getFromNumber(SmsOutboundMessage message, SmsProviderConfig config) {
        // 1. 优先使用配置的签名作为品牌发送方（国际短信常用）
        if (config.getSignName() != null && !config.getSignName().trim().isEmpty()) {
            return config.getSignName();
        }
        // 2. 使用消息指定的发送方号码
        else if (message.getFrom() != null && !message.getFrom().trim().isEmpty()) {
            return message.getFrom();
        }
        // 3. 使用配置的默认发送方号码
        else if (config.getDefaultFrom() != null && !config.getDefaultFrom().trim().isEmpty()) {
            return config.getDefaultFrom();
        }
        // 4. 最后使用提供商名称作为默认值
        else {
            return "Infobip";
        }
    }

    /**
     * 格式化为JSON字符串
     */
    private String formatToJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) sb.append(",");
            
            sb.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Number) {
                sb.append(value);
            } else if (value instanceof Object[]) {
                Object[] array = (Object[]) value;
                sb.append("[");
                for (int i = 0; i < array.length; i++) {
                    if (i > 0) sb.append(",");
                    if (array[i] instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapItem = (Map<String, Object>) array[i];
                        sb.append(formatToJson(mapItem));
                    } else {
                        sb.append("\"").append(array[i]).append("\"");
                    }
                }
                sb.append("]");
            } else {
                sb.append("\"").append(value.toString()).append("\"");
            }
            
            first = false;
        }
        
        sb.append("}");
        return sb.toString();
    }

    /**
     * 发送HTTP请求
     */
    private String sendHttpRequest(String requestBody, SmsProviderConfig config) throws Exception {
        String apiUrl = config.getOutboundBaseUrl() != null ?
            config.getOutboundBaseUrl() + "/sms/2/text/advanced" : "https://api.infobip.com/sms/2/text/advanced";
        
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            // 设置请求属性
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            
            // Infobip使用基本认证
            String auth = config.getApiKey() + ":" + (config.getApiSecret() != null ? config.getApiSecret() : "");
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            
            connection.setRequestProperty("User-Agent", "SMS-SDK/1.0");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            
            // 发送请求体
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            // 读取响应
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

    /**
     * 解析响应结果
     */
    private SmsOutboundMessage parseResponse(String response, SmsOutboundMessage message) {
        try {
            log.debug("Infobip API响应: {}", response);
            
            // 检查是否包含错误
            if (response.contains("\"requestError\"")) {
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                String errorMessage = extractJsonValue(response, "text");
                if (errorMessage == null) {
                    errorMessage = "Infobip API返回错误";
                }
                
                if (message.getExtraInfo() == null) {
                    message.setExtraInfo(new HashMap<>());
                }
                message.getExtraInfo().put("error", errorMessage);
                
                return message;
            }
            
            // 成功情况 - 检查messages数组
            if (response.contains("\"messages\":[")) {
                // 提取第一个消息的状态
                String messageId = extractJsonValue(response, "messageId");
                String status = extractJsonValue(response, "groupName");
                
                if (messageId != null) {
                    message.setProviderMessageId(messageId);
                }
                
                // 根据状态设置发送结果
                if ("PENDING".equals(status) || "ACCEPTED".equals(status)) {
                    message.setSendStatus(SmsOutboundMessage.SendStatus.SENT);
                    message.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(2));
                } else if ("REJECTED".equals(status)) {
                    message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                    String errorDescription = extractJsonValue(response, "description");
                    if (message.getExtraInfo() == null) {
                        message.setExtraInfo(new HashMap<>());
                    }
                    message.getExtraInfo().put("error", errorDescription != null ? errorDescription : "消息被拒绝");
                } else {
                    message.setSendStatus(SmsOutboundMessage.SendStatus.SENT);
                    message.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(2));
                }
                
                // 提取费用信息
                String messagePrice = extractJsonValue(response, "pricePerMessage");
                String currency = extractJsonValue(response, "currency");
                if (messagePrice != null && currency != null) {
                    message.setCostInfo(SmsOutboundMessage.CostInfo.builder()
                            .amount(messagePrice)
                            .currency(currency)
                            .billingType("per_message")
                            .messageCount(1)
                            .unitPrice(messagePrice)
                            .billingTime(LocalDateTime.now())
                            .build());
                }
                
            } else {
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                if (message.getExtraInfo() == null) {
                    message.setExtraInfo(new HashMap<>());
                }
                message.getExtraInfo().put("error", "未知的响应格式");
            }
            
            // 保存原始响应
            if (message.getRawData() == null) {
                message.setRawData(new HashMap<>());
            }
            message.getRawData().put("infobip_response", response);
            
            return message;
            
        } catch (Exception e) {
            log.error("Infobip响应解析失败: {}", e.getMessage(), e);
            return createFailedMessage(message, "响应解析失败: " + e.getMessage());
        }
    }



    /**
     * 从JSON字符串中提取值（简单实现）
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            // 尝试数字值
            searchKey = "\"" + key + "\":";
            startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return null;
            
            startIndex += searchKey.length();
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) {
                endIndex = json.indexOf("}", startIndex);
            }
            
            if (endIndex > startIndex) {
                return json.substring(startIndex, endIndex).replace("\"", "").trim();
            }
        } else {
            startIndex += searchKey.length();
            int endIndex = json.indexOf("\"", startIndex);
            
            if (endIndex > startIndex) {
                return json.substring(startIndex, endIndex);
            }
        }
        
        return null;
    }
} 