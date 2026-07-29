package com.whaleal.ark.cloud.third.sms.provider.aliyun;

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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * 阿里云短信发送器
 * 支持国内和国际短信统一接口
 * 
 * @author whaleal-dev
 * @author 恒哥
 */
public class AliyunOutboundSender implements OutboundSender {
    
    private static final Logger logger = LoggerFactory.getLogger(AliyunOutboundSender.class);
    
    private static final String ENDPOINT = "https://dysmsapi.aliyuncs.com";
    private static final String VERSION = "2017-05-25";
    private static final String ACTION = "SendSms";
    private static final String SIGNATURE_METHOD = "HMAC-SHA1";
    private static final String SIGNATURE_VERSION = "1.0";
    private static final String FORMAT = "JSON";
    private static final int TIMEOUT_SECONDS = 10;
    
    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        logger.info("Sending SMS via Aliyun to: {}", message.getTo());
        
        validateConfig(config);
        
        try {
            // 构建请求参数
            Map<String, String> params = buildRequestParams(message, config);
            
            // 生成签名
            String signature = generateSignature(params, config);
            params.put("Signature", signature);
            
            // 发送HTTP请求
            String response = sendHttpRequest(params);
            
            // 解析响应
            return parseResponse(response, message);
            
        } catch (SmsException e) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to send SMS via Aliyun", e);
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsNetworkException("Failed to send SMS via Aliyun: " + e.getMessage(), 
                SmsProviderType.ALIYUN, e);
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.ALIYUN.name();
    }
    
    private void validateConfig(SmsProviderConfig config) {
        if (config == null) {
            throw new SmsParameterException("Aliyun SMS config cannot be null");
        }
        if (config.getAccessKeyId() == null || config.getAccessKeyId().trim().isEmpty()) {
            throw new SmsCredentialsException("Aliyun AccessKey cannot be null or empty");
        }
        if (config.getAccessKeySecret() == null || config.getAccessKeySecret().trim().isEmpty()) {
            throw new SmsCredentialsException("Aliyun AccessKeySecret cannot be null or empty");
        }
    }
    
    private Map<String, String> buildRequestParams(SmsOutboundMessage message, SmsProviderConfig config) {
        Map<String, String> params = new TreeMap<>();
        
        // 公共参数
        params.put("AccessKeyId", config.getAccessKeyId());
        params.put("Action", ACTION);
        params.put("Format", FORMAT);
        params.put("RegionId", "cn-hangzhou");
        params.put("SignatureMethod", SIGNATURE_METHOD);
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", SIGNATURE_VERSION);
        params.put("Timestamp", getCurrentTimestamp());
        params.put("Version", VERSION);
        
        // 业务参数
        params.put("PhoneNumbers", message.getTo());
        params.put("SignName", config.getSignName() != null ? config.getSignName() : "阿里云短信");
        
        // 模板代码
        String templateCode = "SMS_DEFAULT";
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateId() != null) {
            templateCode = message.getBusinessInfo().getTemplateId();
        }
        params.put("TemplateCode", templateCode);
        
        // 模板参数
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null && !message.getBusinessInfo().getTemplateParams().isEmpty()) {
            params.put("TemplateParam", buildTemplateParams(message.getBusinessInfo().getTemplateParams()));
        }
        
        // 扩展参数
        if (message.getExtraInfo() != null && message.getExtraInfo().get("smsUpExtendCode") != null) {
            params.put("SmsUpExtendCode", message.getExtraInfo().get("smsUpExtendCode").toString());
        }
        
        if (message.getExtraInfo() != null && message.getExtraInfo().get("outId") != null) {
            params.put("OutId", message.getExtraInfo().get("outId").toString());
        }
        
        return params;
    }
    
    private String buildTemplateParams(Map<String, String> templateParams) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : templateParams.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":\"")
                .append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("}");
        return json.toString();
    }
    
    private String generateSignature(Map<String, String> params, SmsProviderConfig config) throws Exception {
        // 构建待签名字符串
        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append("GET&%2F&");
        
        StringBuilder canonicalizedQueryString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                canonicalizedQueryString.append("&");
            }
            canonicalizedQueryString.append(percentEncode(entry.getKey()))
                .append("=").append(percentEncode(entry.getValue()));
            first = false;
        }
        
        stringToSign.append(percentEncode(canonicalizedQueryString.toString()));
        
        // HMAC-SHA1签名
        String accessKeySecret = config.getAccessKeySecret() + "&";
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKey = new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(secretKey);
        byte[] signData = mac.doFinal(stringToSign.toString().getBytes(StandardCharsets.UTF_8));
        
        return Base64.getEncoder().encodeToString(signData);
    }
    
    private String percentEncode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
    
    private String sendHttpRequest(Map<String, String> params) throws Exception {
        StringBuilder queryString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                queryString.append("&");
            }
            queryString.append(entry.getKey()).append("=")
                .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            first = false;
        }
        
        URL url = new URL(ENDPOINT + "/?" + queryString.toString());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_SECONDS * 1000);
            connection.setReadTimeout(TIMEOUT_SECONDS * 1000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            
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
                        SmsProviderType.ALIYUN);
                }
                
                return response.toString();
            }
            
        } catch (java.net.SocketTimeoutException e) {
            throw new SmsTimeoutException("Request timeout after " + TIMEOUT_SECONDS + " seconds", 
                TIMEOUT_SECONDS * 1000, SmsProviderType.ALIYUN);
        } finally {
            connection.disconnect();
        }
    }
    
    private SmsOutboundMessage parseResponse(String responseBody, SmsOutboundMessage message) {
        logger.debug("Aliyun SMS API response: {}", responseBody);
        
        try {
            // 简单JSON解析（生产环境建议使用JSON库）
            if (responseBody.contains("\"Code\":\"OK\"")) {
                String bizId = extractJsonValue(responseBody, "BizId");
                String requestId = extractJsonValue(responseBody, "RequestId");
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.SENT);
                message.setMessageId(bizId);
                message.setProviderMessageId(requestId);
                message.setProviderType(SmsProviderType.ALIYUN);
                
                return message;
            } else {
                String code = extractJsonValue(responseBody, "Code");
                String errorMessage = extractJsonValue(responseBody, "Message");
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                throw new SmsException("ALIYUN_API_ERROR", "Aliyun SMS API error: " + code + " - " + errorMessage, 
                    SmsProviderType.ALIYUN);
            }
            
        } catch (Exception e) {
            if (e instanceof SmsException) {
                throw e;
            }
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsException("RESPONSE_PARSE_ERROR", "Failed to parse Aliyun SMS response: " + e.getMessage(), 
                SmsProviderType.ALIYUN, e);
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
    
    private String getCurrentTimestamp() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date());
    }
} 