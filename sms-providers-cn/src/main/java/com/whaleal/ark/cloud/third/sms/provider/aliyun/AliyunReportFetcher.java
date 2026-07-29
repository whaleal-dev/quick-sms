package com.whaleal.ark.cloud.third.sms.provider.aliyun;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.exception.SmsException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.whaleal.ark.cloud.third.sms.report.fetcher.ReportFetcher;

/**
 * 阿里云报告获取器
 */
@Slf4j
public class AliyunReportFetcher implements ReportFetcher {
    
    private static final String ENDPOINT = "https://dysmsapi.aliyuncs.com";
    private static final String ACTION = "QuerySendDetails";
    private static final String VERSION = "2017-05-25";
    private static final int TIMEOUT_SECONDS = 10;
    
    @Override
    public SmsReport fetchReport(String messageId, SmsProviderConfig config) {
        log.debug("查询阿里云短信状态报告: {}", messageId);
        
        try {
            // 构建请求参数
            Map<String, String> params = buildRequestParams(messageId, config);
            
            // 发送HTTP请求
            String response = sendHttpRequest(params);
            
            // 解析响应
            return parseResponse(response, messageId);
            
        } catch (Exception e) {
            log.error("查询阿里云短信状态报告失败: {}", messageId, e);
            throw new SmsException("ALIYUN_REPORT_ERROR", "查询阿里云短信状态报告失败: " + e.getMessage(), 
                SmsProviderType.ALIYUN, e);
        }
    }
    
    private Map<String, String> buildRequestParams(String messageId, SmsProviderConfig config) {
        Map<String, String> params = new TreeMap<>();
        
        // 公共参数
        params.put("AccessKeyId", config.getAccessKeyId());
        params.put("Action", ACTION);
        params.put("Format", "JSON");
        params.put("RegionId", "cn-hangzhou");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("Timestamp", getCurrentTimestamp());
        params.put("Version", VERSION);
        
        // 业务参数
        params.put("PhoneNumber", ""); // 需要从messageId解析
        params.put("SendDate", getCurrentDate());
        params.put("PageSize", "50");
        params.put("CurrentPage", "1");
        
        // 生成签名
        try {
            String signature = generateSignature(params, config);
            params.put("Signature", signature);
        } catch (Exception e) {
            throw new RuntimeException("生成签名失败", e);
        }
        
        return params;
    }
    
    private String generateSignature(Map<String, String> params, SmsProviderConfig config) throws Exception {
        // 构建待签名字符串
        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append("GET&%2F&");
        
        StringBuilder canonicalizedQueryString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if ("Signature".equals(entry.getKey())) continue;
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
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
            accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(secretKey);
        byte[] signData = mac.doFinal(stringToSign.toString().getBytes(StandardCharsets.UTF_8));
        
        return Base64.getEncoder().encodeToString(signData);
    }
    
    private String percentEncode(String value) throws Exception {
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
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                return response.toString();
            }
            
        } finally {
            connection.disconnect();
        }
    }
    
    private SmsReport parseResponse(String responseBody, String messageId) {
        log.debug("阿里云状态报告响应: {}", responseBody);
        
        // 简单JSON解析
        if (responseBody.contains("\"Code\":\"OK\"")) {
            return SmsReport.builder()
                    .reportId(UUID.randomUUID().toString())
                    .messageId(messageId)
                    .providerType(SmsProviderType.ALIYUN)
                    .currentStatus(SmsReport.ReportStatus.DELIVERED)
                    .lastUpdatedTime(LocalDateTime.now())
                    .rawData(Map.of("response", responseBody))
                    .build();
        } else {
            return SmsReport.builder()
                    .reportId(UUID.randomUUID().toString())
                    .messageId(messageId)
                    .providerType(SmsProviderType.ALIYUN)
                    .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                    .lastUpdatedTime(LocalDateTime.now())
                    .rawData(Map.of("response", responseBody))
                    .build();
        }
    }
    
    private String getCurrentTimestamp() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .format(LocalDateTime.now());
    }
    
    private String getCurrentDate() {
        return DateTimeFormatter.ofPattern("yyyyMMdd")
                .format(LocalDateTime.now());
    }
    
    @Override
    public String getSupportedProvider() {
        return "ALIYUN";
    }
} 