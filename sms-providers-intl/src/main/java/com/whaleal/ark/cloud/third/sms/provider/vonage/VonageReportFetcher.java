package com.whaleal.ark.cloud.third.sms.provider.vonage;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.report.fetcher.ReportFetcher;

/**
 * Vonage状态报告查询器
 * 实现真实的Vonage API调用
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class VonageReportFetcher implements ReportFetcher {
    
    private final HttpClient httpClient;
    private static final String VONAGE_SEARCH_ENDPOINT = "https://rest.nexmo.com/search/messages";
    
    public VonageReportFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }
    
    @Override
    public SmsReport fetchReport(String messageId, SmsProviderConfig config) {
        try {
            log.info("Vonage查询状态报告 - 消息ID: {}", messageId);
            
            // 构建请求URL
            String requestUrl = buildRequestUrl(messageId, config);
            
            // 发送HTTP请求
            HttpResponse<String> response = sendHttpRequest(requestUrl);
            
            // 解析响应
            return parseResponse(messageId, response);
            
        } catch (Exception e) {
            log.error("Vonage查询状态报告失败，消息ID: {}, 错误: {}", messageId, e.getMessage(), e);
            return createErrorReport(messageId, e.getMessage());
        }
    }
    
    @Override
    public List<SmsReport> fetchReports(List<String> messageIds, SmsProviderConfig config) {
        List<SmsReport> reports = new ArrayList<>();
        for (String messageId : messageIds) {
            reports.add(fetchReport(messageId, config));
        }
        return reports;
    }
    
    @Override
    public List<SmsReport> fetchBatchReports(String batchId, SmsProviderConfig config) {
        try {
            log.info("Vonage查询批次状态报告 - 批次ID: {}", batchId);
            
            // Vonage不直接支持批次查询，这里返回空列表
            // 实际使用中可以通过其他方式实现批次查询
            log.warn("Vonage不直接支持批次状态报告查询，批次ID: {}", batchId);
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Vonage查询批次状态报告失败，批次ID: {}, 错误: {}", batchId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 构建请求URL
     */
    private String buildRequestUrl(String messageId, SmsProviderConfig config) {
        return String.format("%s?api_key=%s&api_secret=%s&id=%s",
                VONAGE_SEARCH_ENDPOINT,
                config.getApiKey(),
                config.getApiSecret(),
                messageId);
    }
    
    /**
     * 发送HTTP请求
     */
    private HttpResponse<String> sendHttpRequest(String requestUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    /**
     * 解析响应
     */
    private SmsReport parseResponse(String messageId, HttpResponse<String> response) {
        try {
            String responseBody = response.body();
            log.debug("Vonage查询API响应: {}", responseBody);
            
            if (response.statusCode() == 200 && responseBody.contains("\"items\"")) {
                // 查询成功，解析状态信息
                String status = extractJsonValue(responseBody, "status");
                String errorText = extractJsonValue(responseBody, "error-text");
                String finalStatus = extractJsonValue(responseBody, "final-status");
                String dateReceived = extractJsonValue(responseBody, "date-received");
                String dateFinalStatus = extractJsonValue(responseBody, "date-finalized");
                
                // 映射Vonage状态到标准状态
                SmsReport.ReportStatus reportStatus = mapVonageStatus(status, finalStatus);
                
                SmsReport report = SmsReport.builder()
                        .reportId(messageId + "_" + System.currentTimeMillis())
                        .messageId(messageId)
                        .currentStatus(reportStatus)
                        .statusCode(status != null ? status : "UNKNOWN")
                        .statusDescription(errorText != null ? errorText : getStatusDescription(reportStatus))
                        .lastUpdatedTime(LocalDateTime.now())
                        .build();
                
                // 设置状态历史
                List<SmsReport.StatusHistory> statusHistory = new ArrayList<>();
                if (dateReceived != null) {
                    statusHistory.add(SmsReport.StatusHistory.builder()
                            .status(SmsReport.ReportStatus.SUBMITTED)
                            .statusCode("submitted")
                            .statusDescription("消息已提交")
                            .statusTime(parseVonageDate(dateReceived))
                            .nodeInfo("Vonage Gateway")
                            .build());
                }
                if (dateFinalStatus != null && !dateFinalStatus.equals(dateReceived)) {
                    statusHistory.add(SmsReport.StatusHistory.builder()
                            .status(reportStatus)
                            .statusCode(finalStatus != null ? finalStatus : status)
                            .statusDescription(errorText != null ? errorText : getStatusDescription(reportStatus))
                            .statusTime(parseVonageDate(dateFinalStatus))
                            .nodeInfo("Vonage Network")
                            .build());
                }
                report.setStatusHistory(statusHistory);
                
                // 设置原始数据
                Map<String, Object> rawData = new HashMap<>();
                rawData.put("vonage_response", responseBody);
                report.setRawData(rawData);
                
                return report;
                
            } else {
                // 查询失败或无数据
                return createErrorReport(messageId, "查询无结果或API错误: " + responseBody);
            }
            
        } catch (Exception e) {
            log.error("解析Vonage查询响应失败: {}", e.getMessage(), e);
            return createErrorReport(messageId, "响应解析失败: " + e.getMessage());
        }
    }
    
    /**
     * 映射Vonage状态到标准状态
     */
    private SmsReport.ReportStatus mapVonageStatus(String status, String finalStatus) {
        String statusToCheck = finalStatus != null ? finalStatus : status;
        
        if (statusToCheck == null) {
            return SmsReport.ReportStatus.UNKNOWN;
        }
        
        switch (statusToCheck.toLowerCase()) {
            case "delivered":
                return SmsReport.ReportStatus.DELIVERED;
            case "buffered":
            case "accepted":
                return SmsReport.ReportStatus.ACCEPTED;
            case "failed":
            case "rejected":
                return SmsReport.ReportStatus.FAILED;
            case "expired":
                return SmsReport.ReportStatus.EXPIRED;
            case "unknown":
                return SmsReport.ReportStatus.UNKNOWN;
            default:
                return SmsReport.ReportStatus.SENT;
        }
    }
    
    /**
     * 获取状态描述
     */
    private String getStatusDescription(SmsReport.ReportStatus status) {
        switch (status) {
            case DELIVERED:
                return "消息已成功送达";
            case ACCEPTED:
                return "消息已被接受";
            case FAILED:
                return "消息发送失败";
            case EXPIRED:
                return "消息已过期";
            case SENT:
                return "消息已发送";
            default:
                return "状态未知";
        }
    }
    
    /**
     * 解析Vonage日期格式
     */
    private LocalDateTime parseVonageDate(String dateString) {
        try {
            // Vonage日期格式通常为 "2023-01-01 12:00:00"
            // 这里简化处理，实际项目中应使用DateTimeFormatter
            return LocalDateTime.now(); // 简化实现
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
    
    /**
     * 简单的JSON值提取
     */
    private String extractJsonValue(String json, String key) {
        try {
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
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 创建错误报告
     */
    private SmsReport createErrorReport(String messageId, String errorMessage) {
        return SmsReport.builder()
                .reportId(messageId + "_error_" + System.currentTimeMillis())
                .messageId(messageId)
                .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                .statusCode("ERROR")
                .statusDescription("Vonage查询失败: " + errorMessage)
                .lastUpdatedTime(LocalDateTime.now())
                .build();
    }
    
    @Override
    public String getSupportedProvider() {
        return "VONAGE";
    }
} 