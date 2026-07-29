package com.whaleal.ark.cloud.third.sms.provider.tencent;

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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * 腾讯云短信发送器
 * 支持国内和国际短信统一接口
 * 
 * @author whaleal-dev
 * @author 恒哥
 */
public class TencentOutboundSender implements OutboundSender {
    
    private static final Logger logger = LoggerFactory.getLogger(TencentOutboundSender.class);
    
    private static final String ENDPOINT = "https://sms.tencentcloudapi.com";
    private static final String SERVICE = "sms";
    private static final String VERSION = "2021-01-11";
    private static final String ACTION = "SendSms";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final int TIMEOUT_SECONDS = 10;
    
    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        logger.info("Sending SMS via Tencent Cloud to: {}", message.getTo());
        
        validateConfig(config);
        
        try {
            // 构建请求体
            String requestBody = buildRequestBody(message, config);
            
            // 生成签名
            Map<String, String> headers = generateSignature(requestBody, config);
            
            // 发送HTTP请求
            String response = sendHttpRequest(requestBody, headers);
            
            // 解析响应
            return parseResponse(response, message);
            
        } catch (SmsException e) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to send SMS via Tencent Cloud", e);
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsNetworkException("Failed to send SMS via Tencent Cloud: " + e.getMessage(), 
                SmsProviderType.TENCENT, e);
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return SmsProviderType.TENCENT.name();
    }
    
    private void validateConfig(SmsProviderConfig config) {
        if (config == null) {
            throw new SmsParameterException("Tencent SMS config cannot be null");
        }
        if (config.getAccessKeyId() == null || config.getAccessKeyId().trim().isEmpty()) {
            throw new SmsCredentialsException("Tencent SecretId cannot be null or empty");
        }
        if (config.getAccessKeySecret() == null || config.getAccessKeySecret().trim().isEmpty()) {
            throw new SmsCredentialsException("Tencent SecretKey cannot be null or empty");
        }
    }
    
    private String buildRequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // 短信应用ID
        String smsSdkAppId = config.getStringConfig("smsSdkAppId", "1400000000");
        json.append("\"SmsSdkAppId\":\"").append(smsSdkAppId).append("\",");
        
        // 签名内容
        String signName = config.getSignName() != null ? config.getSignName() : "腾讯云";
        json.append("\"SignName\":\"").append(signName).append("\",");
        
        // 国际/港澳台短信SenderId
        if (message.getFrom() != null) {
            json.append("\"SenderId\":\"").append(message.getFrom()).append("\",");
        }
        
        // 模板ID
        String templateId = "1";
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateId() != null) {
            templateId = message.getBusinessInfo().getTemplateId();
        }
        json.append("\"TemplateId\":\"").append(templateId).append("\",");
        
        // 手机号码
        json.append("\"PhoneNumberSet\":[\"").append(message.getTo()).append("\"],");
        
        // 模板参数
        json.append("\"TemplateParamSet\":[");
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            boolean first = true;
            for (String value : message.getBusinessInfo().getTemplateParams().values()) {
                if (!first) json.append(",");
                json.append("\"").append(value).append("\"");
                first = false;
            }
        } else {
            // 如果没有模板参数，使用消息内容作为参数
            json.append("\"").append(message.getContent()).append("\"");
        }
        json.append("]");
        
        // 扩展信息
        if (message.getExtraInfo() != null && message.getExtraInfo().get("sessionContext") != null) {
            json.append(",\"SessionContext\":\"").append(message.getExtraInfo().get("sessionContext")).append("\"");
        }
        
        json.append("}");
        return json.toString();
    }
    
    private Map<String, String> generateSignature(String requestBody, SmsProviderConfig config) throws Exception {
        Map<String, String> headers = new HashMap<>();
        
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        
        // 构建规范请求串
        String canonicalRequest = "POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:" + 
            ENDPOINT.replace("https://", "") + "\n\ncontent-type;host\n" + 
            sha256Hex(requestBody);
        
        // 构建待签名字符串
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n" + 
            sha256Hex(canonicalRequest);
        
        // 计算签名
        byte[] secretDate = hmacSha256(("TC3" + config.getAccessKeySecret()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));
        
        // 构建Authorization
        String authorization = ALGORITHM + " Credential=" + config.getAccessKeyId() + "/" + 
            credentialScope + ", SignedHeaders=content-type;host, Signature=" + signature;
        
        headers.put("Authorization", authorization);
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Host", ENDPOINT.replace("https://", ""));
        headers.put("X-TC-Action", ACTION);
        headers.put("X-TC-Timestamp", timestamp);
        headers.put("X-TC-Version", VERSION);
        
        return headers;
    }
    
    private String sendHttpRequest(String requestBody, Map<String, String> headers) throws Exception {
        URL url = new URL(ENDPOINT);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT_SECONDS * 1000);
            connection.setReadTimeout(TIMEOUT_SECONDS * 1000);
            
            // 设置请求头
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            
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
                        SmsProviderType.TENCENT);
                }
                
                return response.toString();
            }
            
        } catch (java.net.SocketTimeoutException e) {
            throw new SmsTimeoutException("Request timeout after " + TIMEOUT_SECONDS + " seconds", 
                TIMEOUT_SECONDS * 1000, SmsProviderType.TENCENT, e);
        } finally {
            connection.disconnect();
        }
    }
    
    private SmsOutboundMessage parseResponse(String responseBody, SmsOutboundMessage message) {
        logger.debug("Tencent SMS API response: {}", responseBody);
        
        try {
            // 检查是否有错误
            if (responseBody.contains("\"Error\"")) {
                String code = extractJsonValue(responseBody, "Code");
                String errorMessage = extractJsonValue(responseBody, "Message");
                
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                if (message.getExtraInfo() == null) {
                    message.setExtraInfo(new HashMap<>());
                }
                message.getExtraInfo().put("error", "Tencent SMS API error: " + code + " - " + errorMessage);
                throw new SmsException(code != null ? code : "TENCENT_API_ERROR", 
                    "Tencent SMS API error: " + code + " - " + errorMessage, 
                    SmsProviderType.TENCENT);
            }
            
            // 解析成功响应
            String requestId = extractJsonValue(responseBody, "RequestId");
            
            // 解析SendStatusSet
            String serialNo = null;
            String code = null;
            String statusMessage = null;
            
            if (responseBody.contains("\"SendStatusSet\"")) {
                int statusSetStart = responseBody.indexOf("\"SendStatusSet\"");
                int arrayStart = responseBody.indexOf("[", statusSetStart);
                int objectStart = responseBody.indexOf("{", arrayStart);
                int objectEnd = responseBody.indexOf("}", objectStart);
                
                if (objectStart != -1 && objectEnd != -1) {
                    String statusObject = responseBody.substring(objectStart, objectEnd + 1);
                    serialNo = extractJsonValue(statusObject, "SerialNo");
                    code = extractJsonValue(statusObject, "Code");
                    statusMessage = extractJsonValue(statusObject, "Message");
                }
            }
            
            boolean success = "Ok".equals(code);
            
            // 更新消息状态
            message.setSendStatus(success ? SmsOutboundMessage.SendStatus.SUBMITTED : SmsOutboundMessage.SendStatus.FAILED);
            message.setMessageId(serialNo);
            message.setProviderMessageId(requestId);
            message.setProviderType(SmsProviderType.TENCENT);
            
            if (!success) {
                if (message.getExtraInfo() == null) {
                    message.setExtraInfo(new HashMap<>());
                }
                message.getExtraInfo().put("error", statusMessage);
            }
            
            return message;
            
        } catch (Exception e) {
            if (e instanceof SmsException) {
                throw e;
            }
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            if (message.getExtraInfo() == null) {
                message.setExtraInfo(new HashMap<>());
            }
            message.getExtraInfo().put("error", "Failed to parse response: " + e.getMessage());
            throw new SmsException("PARSE_ERROR", "Failed to parse Tencent SMS response: " + e.getMessage(), 
                SmsProviderType.TENCENT, e);
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
    
    private String sha256Hex(String s) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }
    
    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKeySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
} 