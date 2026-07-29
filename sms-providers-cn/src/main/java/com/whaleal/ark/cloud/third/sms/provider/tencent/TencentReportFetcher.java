package com.whaleal.ark.cloud.third.sms.provider.tencent;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.exception.SmsException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.whaleal.ark.cloud.third.sms.report.fetcher.ReportFetcher;

/**
 * 腾讯云报告获取器
 */
@Slf4j
public class TencentReportFetcher implements ReportFetcher {
    
    private static final String ENDPOINT = "https://sms.tencentcloudapi.com";
    private static final String ACTION = "PullSmsSendStatus";
    private static final String VERSION = "2021-01-11";
    private static final int TIMEOUT_SECONDS = 10;
    
    @Override
    public SmsReport fetchReport(String messageId, SmsProviderConfig config) {
        log.debug("查询腾讯云短信状态报告: {}", messageId);
        
        try {
            // 构建请求体
            String requestBody = buildRequestBody(messageId, config);
            
            // 发送HTTP请求
            String response = sendHttpRequest(requestBody, config);
            
            // 解析响应
            return parseResponse(response, messageId);
            
        } catch (Exception e) {
            log.error("查询腾讯云短信状态报告失败: {}", messageId, e);
            throw new SmsException("TENCENT_REPORT_ERROR", "查询腾讯云短信状态报告失败: " + e.getMessage(), 
                SmsProviderType.TENCENT, e);
        }
    }
    
    private String buildRequestBody(String messageId, SmsProviderConfig config) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"SmsSdkAppId\":\"").append(config.getStringConfig("sdkAppId", "")).append("\",");
        json.append("\"Limit\":10,");
        json.append("\"Offset\":0");
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
            connection.setRequestProperty("Authorization", buildAuthHeader(config));
            connection.setRequestProperty("X-TC-Action", ACTION);
            connection.setRequestProperty("X-TC-Version", VERSION);
            connection.setRequestProperty("X-TC-Region", "ap-beijing");
            
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
                
                return response.toString();
            }
            
        } finally {
            connection.disconnect();
        }
    }
    
    private String buildAuthHeader(SmsProviderConfig config) {
        // 简化实现，实际需要实现腾讯云签名算法
        String secretId = config.getStringConfig("secretId", "");
        String secretKey = config.getStringConfig("secretKey", "");
        return "TC3-HMAC-SHA256 Credential=" + secretId + "/2023-01-01/sms/tc3_request";
    }
    
    private SmsReport parseResponse(String responseBody, String messageId) {
        log.debug("腾讯云状态报告响应: {}", responseBody);
        
        // 简单JSON解析
        if (responseBody.contains("\"Response\"") && !responseBody.contains("\"Error\"")) {
            return SmsReport.builder()
                    .reportId(UUID.randomUUID().toString())
                    .messageId(messageId)
                    .providerType(SmsProviderType.TENCENT)
                    .currentStatus(SmsReport.ReportStatus.DELIVERED)
                    .lastUpdatedTime(LocalDateTime.now())
                    .rawData(Map.of("response", responseBody))
                    .build();
        } else {
            return SmsReport.builder()
                    .reportId(UUID.randomUUID().toString())
                    .messageId(messageId)
                    .providerType(SmsProviderType.TENCENT)
                    .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                    .lastUpdatedTime(LocalDateTime.now())
                    .rawData(Map.of("response", responseBody))
                    .build();
        }
    }
    
    @Override
    public String getSupportedProvider() {
        return "TENCENT";
    }
} 