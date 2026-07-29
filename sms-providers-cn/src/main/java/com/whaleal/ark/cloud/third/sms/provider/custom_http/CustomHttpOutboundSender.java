package com.whaleal.ark.cloud.third.sms.provider.custom_http;

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
import java.util.*;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * 自定义HTTP短信发送器
 * 支持配置化的HTTP接口调用
 * 
 * @author whaleal-dev
 * @author 恒哥
 */
public class CustomHttpOutboundSender implements OutboundSender {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomHttpOutboundSender.class);
    
    private static final int TIMEOUT_SECONDS = 10;
    
    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        logger.info("Sending SMS via Custom HTTP to: {}", message.getTo());
        
        validateConfig(config);
        
        try {
            // 构建请求
            String requestBody = buildRequestBody(message, config);
            Map<String, String> headers = buildHeaders(config);
            
            // 发送HTTP请求
            String response = sendHttpRequest(requestBody, headers, config);
            
            // 解析响应
            return parseResponse(response, message, config);
            
        } catch (SmsException e) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to send SMS via Custom HTTP", e);
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsNetworkException("Failed to send SMS via Custom HTTP: " + e.getMessage(), 
                SmsProviderType.CUSTOM_HTTP, e);
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.CUSTOM_HTTP.name();
    }
    
    private void validateConfig(SmsProviderConfig config) {
        if (config == null) {
            throw new SmsParameterException("Custom HTTP SMS config cannot be null");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            throw new SmsParameterException("Custom HTTP baseUrl cannot be null or empty");
        }
    }
    
    private String buildRequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        String contentType = config.getStringConfig("contentType", "application/json");
        
        if (contentType.contains("json")) {
            return buildJsonRequest(message, config);
        } else if (contentType.contains("form")) {
            return buildFormRequest(message, config);
        } else {
            return buildJsonRequest(message, config); // 默认JSON
        }
    }
    
    private String buildJsonRequest(SmsOutboundMessage message, SmsProviderConfig config) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // 基本字段映射
        String toField = config.getStringConfig("toField", "to");
        String contentField = config.getStringConfig("contentField", "content");
        String fromField = config.getStringConfig("fromField", "from");
        
        json.append("\"").append(toField).append("\":\"").append(message.getTo()).append("\",");
        json.append("\"").append(contentField).append("\":\"").append(message.getContent()).append("\"");
        
        if (message.getFrom() != null) {
            json.append(",\"").append(fromField).append("\":\"").append(message.getFrom()).append("\"");
        }
        
        // 认证信息
        if (config.getApiKey() != null) {
            String apiKeyField = config.getStringConfig("apiKeyField", "apiKey");
            json.append(",\"").append(apiKeyField).append("\":\"").append(config.getApiKey()).append("\"");
        }
        
        if (config.getApiSecret() != null) {
            String apiSecretField = config.getStringConfig("apiSecretField", "apiSecret");
            json.append(",\"").append(apiSecretField).append("\":\"").append(config.getApiSecret()).append("\"");
        }
        
        // 模板信息
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateId() != null) {
            String templateField = config.getStringConfig("templateField", "templateId");
            json.append(",\"").append(templateField).append("\":\"").append(message.getBusinessInfo().getTemplateId()).append("\"");
        }
        
        // 额外参数
        if (message.getExtraInfo() != null) {
            for (Map.Entry<String, Object> entry : message.getExtraInfo().entrySet()) {
                json.append(",\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    private String buildFormRequest(SmsOutboundMessage message, SmsProviderConfig config) {
        StringBuilder form = new StringBuilder();
        
        try {
            // 基本字段映射
            String toField = config.getStringConfig("toField", "to");
            String contentField = config.getStringConfig("contentField", "content");
            String fromField = config.getStringConfig("fromField", "from");
            
            form.append(toField).append("=").append(URLEncoder.encode(message.getTo(), "UTF-8"));
            form.append("&").append(contentField).append("=").append(URLEncoder.encode(message.getContent(), "UTF-8"));
            
            if (message.getFrom() != null) {
                form.append("&").append(fromField).append("=").append(URLEncoder.encode(message.getFrom(), "UTF-8"));
            }
            
            // 认证信息
            if (config.getApiKey() != null) {
                String apiKeyField = config.getStringConfig("apiKeyField", "apiKey");
                form.append("&").append(apiKeyField).append("=").append(URLEncoder.encode(config.getApiKey(), "UTF-8"));
            }
            
            if (config.getApiSecret() != null) {
                String apiSecretField = config.getStringConfig("apiSecretField", "apiSecret");
                form.append("&").append(apiSecretField).append("=").append(URLEncoder.encode(config.getApiSecret(), "UTF-8"));
            }
            
            // 模板信息
            if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateId() != null) {
                String templateField = config.getStringConfig("templateField", "templateId");
                form.append("&").append(templateField).append("=").append(URLEncoder.encode(message.getBusinessInfo().getTemplateId(), "UTF-8"));
            }
            
            // 额外参数
            if (message.getExtraInfo() != null) {
                for (Map.Entry<String, Object> entry : message.getExtraInfo().entrySet()) {
                    form.append("&").append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to build form request", e);
        }
        
        return form.toString();
    }
    
    private Map<String, String> buildHeaders(SmsProviderConfig config) {
        Map<String, String> headers = new HashMap<>();
        
        String contentType = config.getStringConfig("contentType", "application/json");
        headers.put("Content-Type", contentType);
        
        // 自定义头部
        if (config.getApiKey() != null) {
            String authHeader = config.getStringConfig("authHeader", "Authorization");
            String authFormat = config.getStringConfig("authFormat", "Bearer %s");
            headers.put(authHeader, String.format(authFormat, config.getApiKey()));
        }
        
        // 额外头部
        String extraHeaders = config.getStringConfig("extraHeaders", "");
        if (!extraHeaders.isEmpty()) {
            String[] headerPairs = extraHeaders.split(",");
            for (String pair : headerPairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    headers.put(kv[0].trim(), kv[1].trim());
                }
            }
        }
        
        return headers;
    }
    
    private String sendHttpRequest(String requestBody, Map<String, String> headers, SmsProviderConfig config) throws Exception {
        String method = config.getStringConfig("method", "POST");
        URL url = new URL(com.whaleal.ark.cloud.third.sms.util.HttpUrlUtils.resolveOutboundUrl(config.getBaseUrl(), config));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_SECONDS * 1000);
            connection.setReadTimeout(TIMEOUT_SECONDS * 1000);
            
            // 设置请求头
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            
            // 发送请求体（如果是POST/PUT）
            if ("POST".equals(method) || "PUT".equals(method)) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
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
                        SmsProviderType.CUSTOM_HTTP);
                }
                
                return response.toString();
            }
            
        } catch (java.net.SocketTimeoutException e) {
            throw new SmsTimeoutException("Request timeout after " + TIMEOUT_SECONDS + " seconds", 
                TIMEOUT_SECONDS * 1000, SmsProviderType.CUSTOM_HTTP, e);
        } finally {
            connection.disconnect();
        }
    }
    
    private SmsOutboundMessage parseResponse(String responseBody, SmsOutboundMessage message, SmsProviderConfig config) {
        logger.debug("Custom HTTP SMS API response: {}", responseBody);
        
        try {
            // 可配置的成功判断条件
            String successPattern = config.getStringConfig("successPattern", "\"success\":true");
            String messageIdField = config.getStringConfig("messageIdField", "messageId");
            String errorCodeField = config.getStringConfig("errorCodeField", "code");
            String errorMessageField = config.getStringConfig("errorMessageField", "message");
            
            if (responseBody.contains(successPattern)) {
                // 成功响应
                String providerMessageId = extractValue(responseBody, messageIdField);
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.SENT);
                message.setProviderMessageId(providerMessageId);  // ✅ 设置提供商消息ID，不覆盖原始messageId
                message.setProviderType(SmsProviderType.CUSTOM_HTTP);
                
                return message;
            } else {
                String code = extractValue(responseBody, errorCodeField);
                String errorMessage = extractValue(responseBody, errorMessageField);
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                throw new SmsException(code != null ? code : "CUSTOM_HTTP_ERROR", 
                    "Custom HTTP SMS API error: " + code + " - " + errorMessage, 
                    SmsProviderType.CUSTOM_HTTP);
            }
            
        } catch (Exception e) {
            if (e instanceof SmsException) {
                throw e;
            }
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsException("PARSE_ERROR", "Failed to parse Custom HTTP SMS response: " + e.getMessage(), 
                SmsProviderType.CUSTOM_HTTP, e);
        }
    }
    
    private String extractValue(String content, String field) {
        // JSON格式提取
        String jsonSearchKey = "\"" + field + "\":\"";
        int jsonStartIndex = content.indexOf(jsonSearchKey);
        if (jsonStartIndex != -1) {
            jsonStartIndex += jsonSearchKey.length();
            int jsonEndIndex = content.indexOf("\"", jsonStartIndex);
            if (jsonEndIndex != -1) {
                return content.substring(jsonStartIndex, jsonEndIndex);
            }
        }
        
        // XML格式提取
        String xmlStartTag = "<" + field + ">";
        String xmlEndTag = "</" + field + ">";
        int xmlStartIndex = content.indexOf(xmlStartTag);
        if (xmlStartIndex != -1) {
            xmlStartIndex += xmlStartTag.length();
            int xmlEndIndex = content.indexOf(xmlEndTag, xmlStartIndex);
            if (xmlEndIndex != -1) {
                return content.substring(xmlStartIndex, xmlEndIndex);
            }
        }
        
        return null;
    }
} 