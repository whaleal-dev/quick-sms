package com.whaleal.ark.cloud.third.sms.provider.custom_http;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.whaleal.ark.cloud.third.sms.report.fetcher.ReportFetcher;

/**
 * 自定义HTTP报告获取器
 */
@Slf4j
public class CustomHttpReportFetcher implements ReportFetcher {
    
    @Override
    public SmsReport fetchReport(String messageId, SmsProviderConfig config) {
        log.debug("查询自定义HTTP短信状态报告: {}", messageId);
        
        // 简化实现 - 实际应根据配置调用自定义API
        return SmsReport.builder()
                .reportId(UUID.randomUUID().toString())
                .messageId(messageId)
                .providerType(SmsProviderType.CUSTOM_HTTP)
                .currentStatus(SmsReport.ReportStatus.DELIVERED)
                .lastUpdatedTime(LocalDateTime.now())
                .rawData(Map.of("messageId", messageId))
                .build();
    }
    
    @Override
    public String getSupportedProvider() {
        return "CUSTOM_HTTP";
    }
} 