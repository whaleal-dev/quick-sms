package com.whaleal.ark.cloud.third.sms.provider.china_telecom;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.whaleal.ark.cloud.third.sms.report.fetcher.ReportFetcher;

/**
 * 中国电信报告获取器
 */
@Slf4j
public class ChinaTelecomReportFetcher implements ReportFetcher {
    
    @Override
    public SmsReport fetchReport(String messageId, SmsProviderConfig config) {
        log.debug("查询中国电信短信状态报告: {}", messageId);
        
        // 简化实现 - 实际应调用中国电信API
        return SmsReport.builder()
                .reportId(UUID.randomUUID().toString())
                .messageId(messageId)
                .providerType(SmsProviderType.CHINA_TELECOM)
                .currentStatus(SmsReport.ReportStatus.DELIVERED)
                .lastUpdatedTime(LocalDateTime.now())
                .rawData(Map.of("messageId", messageId))
                .build();
    }
    
    @Override
    public String getSupportedProvider() {
        return "CHINA_TELECOM";
    }
} 