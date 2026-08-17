package com.whaleal.ark.cloud.third.sms.provider.huaxin;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.report.fetcher.ReportFetcher;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 华信状态查询（Webhook 优先）。
 *
 * @author 恒哥
 * @since 2026-08-17
 */
public class HuaxinReportFetcher implements ReportFetcher {

    @Override
    public SmsReport fetchReport(String messageId, SmsProviderConfig config) {
        String queryUrl = config != null ? config.getStatusReportUrl() : null;
        if (queryUrl == null || queryUrl.isBlank()) {
            return SmsReport.builder()
                    .reportId(messageId)
                    .messageId(messageId)
                    .providerType(SmsProviderType.HUAXIN)
                    .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                    .statusDescription("请配置 statusReportUrl 或使用 Webhook 回执解析")
                    .lastUpdatedTime(LocalDateTime.now())
                    .rawData(Map.of("hint", "webhook_preferred", "messageId", messageId == null ? "" : messageId))
                    .build();
        }
        return SmsReport.builder()
                .reportId(messageId)
                .messageId(messageId)
                .providerType(SmsProviderType.HUAXIN)
                .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                .statusDescription("statusReportUrl 已配置，请对接自定义查询或等待回执")
                .lastUpdatedTime(LocalDateTime.now())
                .rawData(Map.of("statusReportUrl", queryUrl, "messageId", messageId == null ? "" : messageId))
                .build();
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.HUAXIN.name();
    }
}
