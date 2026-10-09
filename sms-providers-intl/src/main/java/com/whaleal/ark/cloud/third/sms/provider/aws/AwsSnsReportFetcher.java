package com.whaleal.ark.cloud.third.sms.provider.aws;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.whaleal.ark.cloud.third.sms.report.fetcher.ReportFetcher;

/**
 * AWS SNS报告获取器。
 *
 * <p>⚠️ <b>这是一个非功能桩</b>（见下方 {@code 简化实现}）：不调用任何 AWS API，直接返回
 * {@code DELIVERED}。它在 platform 侧<b>零引用</b>（平台走回执 webhook，不走 ReportFetcher），
 * 所以暂时无害 —— 但<b>任何人把它接上之前必须先实现真实查询</b>，否则就是「失败被吃成已送达」，
 * 与 {@link AwsSnsReceiptParser} 2026-10-09 修掉的那个缺陷同型（那个已修好，这个是同类地雷）。</p>
 */
@Slf4j
public class AwsSnsReportFetcher implements ReportFetcher {

    @Override
    public SmsReport fetchReport(String messageId, SmsProviderConfig config) {
        log.debug("查询AWS SNS短信状态报告: {}", messageId);

        // 简化实现 - 实际应调用AWS SNS API
        return SmsReport.builder()
                .reportId(UUID.randomUUID().toString())
                .messageId(messageId)
                .providerType(SmsProviderType.AWS)
                .currentStatus(SmsReport.ReportStatus.DELIVERED)
                .lastUpdatedTime(LocalDateTime.now())
                .rawData(Map.of("messageId", messageId))
                .build();
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.AWS.name();
    }
}
