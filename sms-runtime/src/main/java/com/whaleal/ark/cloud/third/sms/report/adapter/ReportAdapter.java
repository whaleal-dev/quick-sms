package com.whaleal.ark.cloud.third.sms.report.adapter;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderKeys;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.report.fetcher.MockReportFetcher;
import com.whaleal.ark.cloud.third.sms.report.fetcher.ReportFetcher;
import com.whaleal.ark.cloud.third.sms.spi.SmsExtensionLoader;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态报告适配器 - 主动查询状态报告
 * 负责向各个提供商主动查询短信发送状态报告
 *
 * @author whaleal-dev
 * @since 1.0.0
 */
@Slf4j
public class ReportAdapter {

    private final Map<String, ReportFetcher> fetcherMap;

    public ReportAdapter() {
        this.fetcherMap = new HashMap<>();
        initializeFetchers();
    }

    /**
     * 初始化各提供商的状态查询器
     */
    private void initializeFetchers() {
        MockReportFetcher mock = new MockReportFetcher();
        fetcherMap.put(SmsProviderKeys.normalize(mock.getSupportedProvider()), mock);
        fetcherMap.putAll(SmsExtensionLoader.loadProviders(ReportFetcher.class, ReportFetcher::getSupportedProvider));
        log.info("状态报告获取器初始化完成，支持 {} 个提供商", fetcherMap.size());
    }

    /**
     * 查询单个消息的状态报告
     *
     * @param providerType 提供商类型
     * @param messageId 消息ID
     * @param config 提供商配置
     * @return 状态报告
     */
    public SmsReport fetchReport(SmsProviderType providerType, String messageId, SmsProviderConfig config) {
        try {
            log.debug("开始查询状态报告，提供商: {}, 消息ID: {}", providerType, messageId);

            ReportFetcher fetcher = fetcherMap.get(SmsProviderKeys.of(providerType));
            if (fetcher == null) {
                log.warn("未找到提供商 {} 的状态查询器", providerType);
                return createUnsupportedReport(providerType, messageId);
            }

            SmsReport report = fetcher.fetchReport(messageId, config);

            // 设置提供商类型
            report.setProviderType(providerType);

            log.debug("状态报告查询完成，消息ID: {}, 状态: {}", messageId, report.getCurrentStatus());

            return report;

        } catch (Exception e) {
            log.error("查询状态报告失败，提供商: {}, 消息ID: {}, 错误: {}", providerType, messageId, e.getMessage(), e);

            // 返回错误状态的报告
            return createErrorReport(providerType, messageId, e.getMessage());
        }
    }

    /**
     * 批量查询状态报告
     *
     * @param providerType 提供商类型
     * @param messageIds 消息ID列表
     * @param config 提供商配置
     * @return 状态报告列表
     */
    public List<SmsReport> fetchReports(SmsProviderType providerType, List<String> messageIds, SmsProviderConfig config) {
        try {
            log.debug("开始批量查询状态报告，提供商: {}, 消息数量: {}", providerType, messageIds.size());

            ReportFetcher fetcher = fetcherMap.get(SmsProviderKeys.of(providerType));
            if (fetcher == null) {
                log.warn("未找到提供商 {} 的状态查询器", providerType);
                return messageIds.stream()
                        .map(id -> createUnsupportedReport(providerType, id))
                        .toList();
            }

            List<SmsReport> reports = fetcher.fetchReports(messageIds, config);

            // 设置提供商类型
            reports.forEach(report -> report.setProviderType(providerType));

            log.debug("批量状态报告查询完成，查询数量: {}, 返回数量: {}", messageIds.size(), reports.size());

            return reports;

        } catch (Exception e) {
            log.error("批量查询状态报告失败，提供商: {}, 错误: {}", providerType, e.getMessage(), e);

            // 返回错误状态的报告列表
            return messageIds.stream()
                    .map(id -> createErrorReport(providerType, id, e.getMessage()))
                    .toList();
        }
    }

    /**
     * 查询批次状态报告
     *
     * @param providerType 提供商类型
     * @param batchId 批次ID
     * @param config 提供商配置
     * @return 状态报告列表
     */
    public List<SmsReport> fetchBatchReports(SmsProviderType providerType, String batchId, SmsProviderConfig config) {
        try {
            log.debug("开始查询批次状态报告，提供商: {}, 批次ID: {}", providerType, batchId);

            ReportFetcher fetcher = fetcherMap.get(SmsProviderKeys.of(providerType));
            if (fetcher == null) {
                log.warn("未找到提供商 {} 的状态查询器", providerType);
                return List.of(createUnsupportedReport(providerType, batchId));
            }

            List<SmsReport> reports = fetcher.fetchBatchReports(batchId, config);

            // 设置提供商类型
            reports.forEach(report -> report.setProviderType(providerType));

            log.debug("批次状态报告查询完成，批次ID: {}, 返回数量: {}", batchId, reports.size());

            return reports;

        } catch (Exception e) {
            log.error("查询批次状态报告失败，提供商: {}, 批次ID: {}, 错误: {}", providerType, batchId, e.getMessage(), e);

            // 返回错误状态的报告
            return List.of(createErrorReport(providerType, batchId, e.getMessage()));
        }
    }

    /**
     * 创建不支持的报告
     *
     * @param providerType 提供商类型
     * @param messageId 消息ID
     * @return 不支持的报告
     */
    private SmsReport createUnsupportedReport(SmsProviderType providerType, String messageId) {
        return SmsReport.builder()
                .reportId(messageId)
                .messageId(messageId)
                .providerType(providerType)
                .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                .statusCode("UNSUPPORTED")
                .statusDescription("该提供商不支持状态查询")
                .build();
    }

    /**
     * 创建错误报告
     *
     * @param providerType 提供商类型
     * @param messageId 消息ID
     * @param errorMessage 错误信息
     * @return 错误报告
     */
    private SmsReport createErrorReport(SmsProviderType providerType, String messageId, String errorMessage) {
        return SmsReport.builder()
                .reportId(messageId)
                .messageId(messageId)
                .providerType(providerType)
                .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                .statusCode("QUERY_ERROR")
                .statusDescription("状态查询失败")
                .errorCode("QUERY_ERROR")
                .errorDescription("状态查询失败: " + errorMessage)
                .build();
    }

    /**
     * 检查是否支持指定提供商
     *
     * @param providerType 提供商类型
     * @return 是否支持
     */
    public boolean isSupported(SmsProviderType providerType) {
        return isSupported(SmsProviderKeys.of(providerType));
    }

    /**
     * 获取支持的提供商列表
     *
     * @return 支持的提供商类型数组
     */
    public java.util.Set<String> getSupportedProviderCodes() {
        return java.util.Set.copyOf(fetcherMap.keySet());
    }

    /** @deprecated 请使用 {@link #getSupportedProviderCodes()} */
    @Deprecated
    public SmsProviderType[] getSupportedProviders() {
        return fetcherMap.keySet().stream()
                .map(SmsProviderType::tryFromCode)
                .flatMap(java.util.Optional::stream)
                .toArray(SmsProviderType[]::new);
    }

    public boolean isSupported(String providerCode) {
        String key = SmsProviderKeys.normalize(providerCode);
        return key != null && fetcherMap.containsKey(key);
    }
}
