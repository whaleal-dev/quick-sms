package com.whaleal.ark.cloud.third.sms.report.fetcher;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.exception.SmsException;

import java.util.List;

/**
 * 状态报告查询器接口
 * 定义各个提供商状态报告查询的统一规范
 * 
 * <h3>参数顺序规范</h3>
 * 所有方法统一使用以下参数顺序：
 * <ol>
 *   <li>查询标识（messageId/messageIds/batchId）</li>
 *   <li>配置参数（config）</li>
 * </ol>
 * 
 * <h3>返回值规范</h3>
 * <ul>
 *   <li>单个查询：返回包含状态信息的 SmsReport</li>
 *   <li>批量查询：返回包含状态信息的 List&lt;SmsReport&gt;</li>
 *   <li>查询失败时，返回包含错误信息的报告对象，不抛出异常</li>
 * </ul>
 * 
 * <h3>异常处理规范</h3>
 * <ul>
 *   <li>配置错误、网络错误等系统级异常：抛出 SmsException 及其子类</li>
 *   <li>业务级错误（如消息ID不存在）：返回包含错误状态的报告对象</li>
 * </ul>
 * 
 * <h3>状态映射规范</h3>
 * <ul>
 *   <li>SUBMITTED: 已提交到运营商</li>
 *   <li>ACCEPTED: 厂商已接受</li>
 *   <li>QUEUED: 排队中</li>
 *   <li>PENDING: 处理中（发送中）</li>
 *   <li>SENT: 已发送到运营商</li>
 *   <li>DELIVERED: 已成功送达</li>
 *   <li>FAILED: 发送失败</li>
 *   <li>EXPIRED: 已过期</li>
 *   <li>REJECTED: 被拒绝</li>
 *   <li>UNDELIVERABLE: 无法送达</li>
 *   <li>UNKNOWN: 状态未知或查询失败</li>
 * </ul>
 * 
 * @author whaleal-dev
 * @since 1.0.0
 */
public interface ReportFetcher {
    
    /**
     * 查询单个消息的状态报告
     * 
     * <h4>参数规范</h4>
     * <ul>
     *   <li>messageId: 必填，消息的唯一标识符</li>
     *   <li>config: 必填，包含提供商认证和配置信息</li>
     * </ul>
     * 
     * <h4>返回值规范</h4>
     * <ul>
     *   <li>成功：返回包含最新状态的 SmsReport 对象</li>
     *   <li>失败：返回包含 currentStatus=UNKNOWN、错误信息的报告对象</li>
     * </ul>
     * 
     * <h4>异常规范</h4>
     * <ul>
     *   <li>SmsConfigException: 配置参数错误</li>
     *   <li>SmsTimeoutException: 查询超时</li>
     *   <li>SmsNetworkException: 网络连接错误</li>
     *   <li>SmsException: 其他系统级错误</li>
     * </ul>
     * 
     * @param messageId 消息ID，由发送时返回的唯一标识符
     * @param config 提供商配置，包含认证信息、超时设置等
     * @return 状态报告，包含当前状态、状态更新时间等信息
     * @throws SmsException 当发生系统级错误时抛出
     */
    SmsReport fetchReport(String messageId, SmsProviderConfig config) throws SmsException;
    
    /**
     * 批量查询状态报告
     * 
     * <h4>参数规范</h4>
     * <ul>
     *   <li>messageIds: 必填，消息ID列表</li>
     *   <li>config: 必填，包含提供商认证和配置信息</li>
     * </ul>
     * 
     * <h4>返回值规范</h4>
     * <ul>
     *   <li>返回与输入数量相同的报告列表</li>
     *   <li>每个报告包含对应消息的状态信息</li>
     *   <li>部分查询失败时，失败的报告标记为 UNKNOWN 状态</li>
     * </ul>
     * 
     * <h4>默认实现</h4>
     * 如果提供商不支持批量查询，默认实现会逐个调用单个查询方法。
     * 
     * @param messageIds 消息ID列表
     * @param config 提供商配置
     * @return 状态报告列表，包含每个消息的状态信息
     * @throws SmsException 当发生系统级错误时抛出
     */
    default List<SmsReport> fetchReports(List<String> messageIds, SmsProviderConfig config) throws SmsException {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new IllegalArgumentException("消息ID列表不能为空");
        }
        
        return messageIds.stream()
                .map(messageId -> {
                    try {
                        return fetchReport(messageId, config);
                    } catch (SmsException e) {
                        // 将异常转换为未知状态的报告对象
                        return createUnknownReport(messageId, e.getMessage());
                    }
                })
                .toList();
    }
    
    /**
     * 查询批次状态报告
     * 
     * <h4>参数规范</h4>
     * <ul>
     *   <li>batchId: 必填，批次的唯一标识符</li>
     *   <li>config: 必填，包含提供商认证和配置信息</li>
     * </ul>
     * 
     * <h4>返回值规范</h4>
     * <ul>
     *   <li>返回该批次中所有消息的状态报告列表</li>
     *   <li>如果批次不存在，返回空列表</li>
     * </ul>
     * 
     * <h4>默认实现</h4>
     * 如果提供商不支持批次查询，默认实现会抛出 UnsupportedOperationException。
     * 
     * @param batchId 批次ID，由批量发送时返回的唯一标识符
     * @param config 提供商配置
     * @return 状态报告列表，包含该批次中所有消息的状态信息
     * @throws SmsException 当发生系统级错误时抛出
     * @throws UnsupportedOperationException 当提供商不支持批次查询时抛出
     */
    default List<SmsReport> fetchBatchReports(String batchId, SmsProviderConfig config) throws SmsException {
        throw new UnsupportedOperationException("该提供商不支持批次状态查询");
    }
    
    /**
     * 验证消息ID是否有效
     * 
     * <h4>验证规则</h4>
     * <ul>
     *   <li>消息ID不能为空</li>
     *   <li>消息ID格式符合提供商要求</li>
     * </ul>
     * 
     * @param messageId 待验证的消息ID
     * @return true: 消息ID有效，false: 消息ID无效
     */
    default boolean isValidMessageId(String messageId) {
        return messageId != null && !messageId.trim().isEmpty();
    }
    
    /**
     * 验证配置是否有效
     * 
     * <h4>验证规则</h4>
     * <ul>
     *   <li>配置对象不能为空</li>
     *   <li>必要的认证信息不能为空</li>
     *   <li>URL、端点等配置信息格式正确</li>
     * </ul>
     * 
     * @param config 待验证的提供商配置
     * @return true: 配置有效，false: 配置无效
     */
    default boolean isValidConfig(SmsProviderConfig config) {
        if (config == null) {
            return false;
        }
        
        // 基础验证：至少需要一个认证信息
        return (config.getApiKey() != null && !config.getApiKey().trim().isEmpty())
                || (config.getAccessKeyId() != null && !config.getAccessKeyId().trim().isEmpty())
                || (config.getApiSecret() != null && !config.getApiSecret().trim().isEmpty());
    }
    
    /**
     * 创建未知状态的报告对象
     * 
     * @param messageId 消息ID
     * @param errorMessage 错误信息
     * @return 未知状态的报告对象
     */
    default SmsReport createUnknownReport(String messageId, String errorMessage) {
        return SmsReport.builder()
                .reportId(messageId)
                .messageId(messageId)
                .currentStatus(SmsReport.ReportStatus.UNKNOWN)
                .statusCode("QUERY_ERROR")
                .statusDescription("状态查询失败")
                .errorCode("QUERY_ERROR")
                .errorDescription("状态查询失败: " + errorMessage)
                .build();
    }
    
    /**
     * 获取查询器支持的提供商类型
     * 
     * <h4>返回值规范</h4>
     * 返回提供商的标准名称，应与 SmsProviderType 枚举值对应。
     * 
     * @return 提供商类型名称，如 "TWILIO"、"ALIYUN" 等
     */
    String getSupportedProvider();
    
    /**
     * 获取查询器的超时配置
     * 
     * <h4>默认值</h4>
     * 如果未特别配置，默认返回 10000 毫秒（10秒）。
     * 
     * @return 超时时间（毫秒）
     */
    default long getTimeoutMs() {
        return 10000L; // 默认10秒超时
    }
    
    /**
     * 是否支持批量查询
     * 
     * <h4>默认实现</h4>
     * 默认返回 false，表示使用逐个查询的方式实现批量。
     * 如果提供商支持真正的批量查询API，应重写此方法返回 true。
     * 
     * @return true: 支持批量查询，false: 不支持批量查询
     */
    default boolean supportsBatchQuery() {
        return false;
    }
    
    /**
     * 是否支持批次查询
     * 
     * <h4>默认实现</h4>
     * 默认返回 false，表示不支持批次查询。
     * 如果提供商支持批次状态查询API，应重写此方法返回 true。
     * 
     * @return true: 支持批次查询，false: 不支持批次查询
     */
    default boolean supportsBatchReports() {
        return false;
    }
    
    /**
     * 是否支持实时查询
     * 
     * <h4>默认实现</h4>
     * 默认返回 true，表示支持实时查询。
     * 如果提供商只支持延迟查询，应重写此方法返回 false。
     * 
     * @return true: 支持实时查询，false: 只支持延迟查询
     */
    default boolean supportsRealTimeQuery() {
        return true;
    }
} 