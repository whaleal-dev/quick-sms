package com.whaleal.ark.cloud.third.sms.report.entity;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 短信状态报告 - 发送链路状态反馈
 * Report表示短信在发送链路中的各个状态节点信息
 * 
 * @author whaleal-dev
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsReport {
    
    /**
     * 报告ID
     */
    private String reportId;
    
    /**
     * 原始消息ID
     */
    private String messageId;
    
    /**
     * 批次ID
     */
    private String batchId;
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 提供商类型
     */
    private SmsProviderType providerType;
    
    /**
     * 接收方手机号
     */
    private String to;
    
    /**
     * 当前状态
     */
    private ReportStatus currentStatus;
    
    /**
     * 原始状态代码
     */
    private String statusCode;
    
    /**
     * 状态描述
     */
    private String statusDescription;
    
    /**
     * 错误代码（如果有错误）
     */
    private String errorCode;
    
    /**
     * 错误描述（如果有错误）
     */
    private String errorDescription;
    
    /**
     * 状态历史记录
     */
    private List<StatusHistory> statusHistory;
    
    /**
     * 提交时间
     */
    private LocalDateTime submittedTime;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdatedTime;
    
    /**
     * 预计送达时间
     */
    private LocalDateTime estimatedDeliveryTime;
    
    /**
     * 重试信息
     */
    private RetryInfo retryInfo;
    
    /**
     * 路由信息
     */
    private RouteInfo routeInfo;
    
    /**
     * 费用信息
     */
    private CostInfo costInfo;
    
    /**
     * 原始报告数据
     */
    private Map<String, Object> rawData;
    
    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
    
    /**
     * 报告状态枚举
     */
    public enum ReportStatus {
        SUBMITTED("submitted", "已提交"),
        ACCEPTED("accepted", "已接受"),
        QUEUED("queued", "排队中"),
        PENDING("pending", "处理中"),
        SENT("sent", "已发送"),
        DELIVERED("delivered", "已送达"),
        FAILED("failed", "发送失败"),
        EXPIRED("expired", "已过期"),
        REJECTED("rejected", "被拒绝"),
        UNDELIVERABLE("undeliverable", "无法送达"),
        UNKNOWN("unknown", "未知状态");
        
        private final String code;
        private final String description;
        
        ReportStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getDescription() { return description; }
    }
    
    /**
     * 状态历史记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistory {
        /**
         * 状态
         */
        private ReportStatus status;
        
        /**
         * 状态代码
         */
        private String statusCode;
        
        /**
         * 状态描述
         */
        private String statusDescription;
        
        /**
         * 状态时间
         */
        private LocalDateTime statusTime;
        
        /**
         * 节点信息
         */
        private String nodeInfo;
        
        /**
         * 附加信息
         */
        private Map<String, Object> additionalInfo;
    }
    
    /**
     * 重试信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryInfo {
        /**
         * 重试次数
         */
        private Integer retryCount;
        
        /**
         * 最大重试次数
         */
        private Integer maxRetries;
        
        /**
         * 下次重试时间
         */
        private LocalDateTime nextRetryTime;
        
        /**
         * 重试间隔（秒）
         */
        private Integer retryInterval;
        
        /**
         * 重试原因
         */
        private String retryReason;
    }
    
    /**
     * 路由信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteInfo {
        /**
         * 路由渠道
         */
        private String routeChannel;
        
        /**
         * 路由优先级
         */
        private Integer routePriority;
        
        /**
         * 网关信息
         */
        private String gatewayInfo;
        
        /**
         * 运营商信息
         */
        private String carrierInfo;
        
        /**
         * 路由时间
         */
        private LocalDateTime routeTime;
    }
    
    /**
     * 费用信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostInfo {
        /**
         * 费用金额
         */
        private String amount;
        
        /**
         * 货币单位
         */
        private String currency;
        
        /**
         * 计费类型
         */
        private String billingType;
        
        /**
         * 消息条数
         */
        private Integer messageCount;
        
        /**
         * 计费时间
         */
        private LocalDateTime billingTime;
    }
} 