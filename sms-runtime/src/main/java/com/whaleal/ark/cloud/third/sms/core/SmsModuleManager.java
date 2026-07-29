package com.whaleal.ark.cloud.third.sms.core;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.receipt.adapter.ReceiptAdapter;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.whaleal.ark.cloud.third.sms.inbound.adapter.InboundAdapter;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.report.adapter.ReportAdapter;
import com.whaleal.ark.cloud.third.sms.report.entity.SmsReport;
import com.whaleal.ark.cloud.third.sms.outbound.adapter.OutboundAdapter;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * SMS模块管理器
 * 统一管理四个独立模块：Receipt（回执）、Inbound（上行）、Report（状态报告）、Outbound（下行）
 * 
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class SmsModuleManager {
    
    private final ReceiptAdapter receiptAdapter;
    private final InboundAdapter inboundAdapter;
    private final ReportAdapter reportAdapter;
    private final OutboundAdapter outboundAdapter;
    
    public SmsModuleManager() {
        this(new ReceiptAdapter(), new InboundAdapter(), new ReportAdapter(), new OutboundAdapter());
    }

    public SmsModuleManager(ReceiptAdapter receiptAdapter,
                            InboundAdapter inboundAdapter,
                            ReportAdapter reportAdapter,
                            OutboundAdapter outboundAdapter) {
        this.receiptAdapter = receiptAdapter;
        this.inboundAdapter = inboundAdapter;
        this.reportAdapter = reportAdapter;
        this.outboundAdapter = outboundAdapter;
        log.info("SMS模块管理器初始化完成");
    }
    
    // ==================== Receipt模块（被动解析回执） ====================
    
    /**
     * 解析回执数据
     * 
     * @param providerType 提供商类型
     * @param rawData 原始回执数据
     * @param config 提供商配置
     * @return 解析后的回执对象
     */
    public SmsReceipt parseReceipt(SmsProviderType providerType, Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析回执数据，提供商: {}", providerType);
        return receiptAdapter.parseReceipt(providerType, rawData, config);
    }
    
    /**
     * 解析回执数据（自动检测提供商）
     */
    public SmsReceipt parseReceipt(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("自动检测提供商并解析回执数据");
        return receiptAdapter.parseReceipt(rawData, config);
    }
    
    // ==================== Inbound模块（被动解析上行短信） ====================
    
    /**
     * 解析上行短信数据
     * 
     * @param providerType 提供商类型
     * @param rawData 原始上行短信数据
     * @param config 提供商配置
     * @return 解析后的上行短信对象
     */
    public SmsInboundMessage parseInbound(SmsProviderType providerType, Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("解析上行短信数据，提供商: {}", providerType);
        return inboundAdapter.parseInbound(providerType, rawData, config);
    }
    
    /**
     * 解析上行短信数据（自动检测提供商）
     */
    public SmsInboundMessage parseInbound(Map<String, Object> rawData, SmsProviderConfig config) {
        log.debug("自动检测提供商并解析上行短信数据");
        return inboundAdapter.parseInbound(rawData, config);
    }
    
    // ==================== Report模块（主动查询状态报告） ====================
    
    /**
     * 查询单个消息的状态报告
     * 
     * @param providerType 提供商类型
     * @param messageId 消息ID
     * @param config 提供商配置
     * @return 状态报告
     */
    public SmsReport fetchReport(SmsProviderType providerType, String messageId, SmsProviderConfig config) {
        log.debug("查询状态报告，提供商: {}, 消息ID: {}", providerType, messageId);
        return reportAdapter.fetchReport(providerType, messageId, config);
    }
    
    /**
     * 批量查询状态报告
     */
    public List<SmsReport> fetchReports(SmsProviderType providerType, List<String> messageIds, SmsProviderConfig config) {
        log.debug("批量查询状态报告，提供商: {}, 消息数量: {}", providerType, messageIds.size());
        return reportAdapter.fetchReports(providerType, messageIds, config);
    }
    
    /**
     * 查询批次状态报告
     */
    public List<SmsReport> fetchBatchReports(SmsProviderType providerType, String batchId, SmsProviderConfig config) {
        log.debug("查询批次状态报告，提供商: {}, 批次ID: {}", providerType, batchId);
        return reportAdapter.fetchBatchReports(providerType, batchId, config);
    }
    
    // ==================== Outbound模块（主动发送短信） ====================
    
    /**
     * 发送单条短信
     * 
     * @param providerType 提供商类型
     * @param message 短信消息
     * @param config 提供商配置
     * @return 发送后的短信消息（包含发送结果）
     */
    public SmsOutboundMessage sendMessage(SmsProviderType providerType, SmsOutboundMessage message, SmsProviderConfig config) {
        log.debug("发送短信，提供商: {}, 接收方: {}", providerType, message.getTo());
        return outboundAdapter.sendMessage(providerType, message, config);
    }

    /**
     * 按扩展 providerCode 发送（无需枚举）。
     */
    public SmsOutboundMessage sendMessage(String providerCode, SmsOutboundMessage message, SmsProviderConfig config) {
        log.debug("发送短信，提供商 code: {}, 接收方: {}", providerCode, message.getTo());
        return outboundAdapter.sendMessage(providerCode, message, config);
    }
    
    /**
     * 批量发送短信
     */
    public List<SmsOutboundMessage> sendMessages(SmsProviderType providerType, List<SmsOutboundMessage> messages, SmsProviderConfig config) {
        log.debug("批量发送短信，提供商: {}, 消息数量: {}", providerType, messages.size());
        return outboundAdapter.sendMessages(providerType, messages, config);
    }
    
    /**
     * 发送模板短信
     */
    public SmsOutboundMessage sendTemplateMessage(SmsProviderType providerType, SmsOutboundMessage message, SmsProviderConfig config) {
        log.debug("发送模板短信，提供商: {}, 模板ID: {}", providerType, 
                message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : "未知");
        return outboundAdapter.sendTemplateMessage(providerType, message, config);
    }

    public SmsOutboundMessage sendTemplateMessage(String providerCode, SmsOutboundMessage message, SmsProviderConfig config) {
        // 扩展厂商：走普通发送入口，由适配器按 code 查找 SPI
        log.debug("发送模板短信，提供商 code: {}", providerCode);
        SmsProviderConfig keyed = config == null
                ? SmsProviderConfig.builder().providerCode(providerCode).build()
                : config.toBuilder().providerCode(providerCode).build();
        return outboundAdapter.sendMessage(providerCode, message, keyed);
    }
    
    // ==================== 模块支持检查 ====================
    
    /**
     * 检查Receipt模块是否支持指定提供商
     */
    public boolean isReceiptSupported(SmsProviderType providerType) {
        return receiptAdapter.isSupported(providerType);
    }
    
    /**
     * 检查Inbound模块是否支持指定提供商
     */
    public boolean isInboundSupported(SmsProviderType providerType) {
        return inboundAdapter.isSupported(providerType);
    }
    
    /**
     * 检查Report模块是否支持指定提供商
     */
    public boolean isReportSupported(SmsProviderType providerType) {
        return reportAdapter.isSupported(providerType);
    }
    
    /**
     * 检查Outbound模块是否支持指定提供商
     */
    public boolean isOutboundSupported(SmsProviderType providerType) {
        return outboundAdapter.isSupported(providerType);
    }
    
    /**
     * 获取模块支持情况概览
     */
    public ModuleSupportInfo getModuleSupportInfo(SmsProviderType providerType) {
        return ModuleSupportInfo.builder()
                .providerType(providerType)
                .receiptSupported(isReceiptSupported(providerType))
                .inboundSupported(isInboundSupported(providerType))
                .reportSupported(isReportSupported(providerType))
                .outboundSupported(isOutboundSupported(providerType))
                .build();
    }
    
    /**
     * 模块支持信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModuleSupportInfo {
        private SmsProviderType providerType;
        private boolean receiptSupported;
        private boolean inboundSupported;
        private boolean reportSupported;
        private boolean outboundSupported;
        
        public boolean isFullySupported() {
            return receiptSupported && inboundSupported && reportSupported && outboundSupported;
        }
        
        public int getSupportedModuleCount() {
            int count = 0;
            if (receiptSupported) count++;
            if (inboundSupported) count++;
            if (reportSupported) count++;
            if (outboundSupported) count++;
            return count;
        }
    }
} 