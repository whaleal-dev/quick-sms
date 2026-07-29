package com.whaleal.ark.cloud.third.sms.provider.chuanglan;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.report.fetcher.ReportFetcher;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 创蓝状态查询。
 * <p>多数国内通道以 Webhook 回执为准；主动查询未配置时返回 UNKNOWN，提示走回执解析。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class ChuanglanReportFetcher implements ReportFetcher {

    @Override
    public SmsReport fetchReport(String messageId, SmsProviderConfig config) {
        String queryUrl = config != null ? config.getStatusReportUrl() : null;
        if (queryUrl == null || queryUrl.isBlank()) {
            return SmsReport.builder()
                    .reportId(messageId)
                    .messageId(messageId)
                    .providerType(SmsProviderType.CHUANGLAN)
                    .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                    .statusDescription("请配置 statusReportUrl 或使用 Webhook 回执解析")
                    .lastUpdatedTime(LocalDateTime.now())
                    .rawData(Map.of("hint", "webhook_preferred", "messageId", messageId == null ? "" : messageId))
                    .build();
        }
        // 预留主动查询扩展点：业务可通过 statusReportUrl 自定义代理查询
        return SmsReport.builder()
                .reportId(messageId)
                .messageId(messageId)
                .providerType(SmsProviderType.CHUANGLAN)
                .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                .statusDescription("statusReportUrl 已配置，请对接自定义查询或等待回执")
                .lastUpdatedTime(LocalDateTime.now())
                .rawData(Map.of("statusReportUrl", queryUrl, "messageId", messageId == null ? "" : messageId))
                .build();
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.CHUANGLAN.name();
    }
}
