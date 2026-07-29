package com.whaleal.ark.cloud.third.sms.util;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 性能监控和指标收集器
 * 收集SMS SDK的各种性能指标和统计信息
 * 
 * @author whaleal-dev
 * @since 1.0.0
 */
@Slf4j
public class MetricsCollector {
    
    private static final MetricsCollector INSTANCE = new MetricsCollector();
    
    // 读写锁
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 各提供商的指标
    private final Map<SmsProviderType, ProviderMetrics> providerMetrics = new ConcurrentHashMap<>();
    
    // 全局指标
    private final GlobalMetrics globalMetrics = new GlobalMetrics();
    
    private MetricsCollector() {
        // 初始化各提供商指标
        for (SmsProviderType provider : SmsProviderType.values()) {
            providerMetrics.put(provider, new ProviderMetrics(provider));
        }
    }
    
    public static MetricsCollector getInstance() {
        return INSTANCE;
    }
    
    /**
     * 记录发送操作
     */
    public void recordSendOperation(SmsProviderType provider, boolean success, Duration duration, String messageId) {
        lock.readLock().lock();
        try {
            ProviderMetrics metrics = providerMetrics.get(provider);
            if (metrics != null) {
                metrics.recordSend(success, duration, messageId);
            }
            globalMetrics.recordSend(success, duration);
            
            log.debug("记录发送指标 - 提供商: {}, 成功: {}, 耗时: {}ms", 
                    provider, success, duration.toMillis());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 记录接收操作（回执、上行短信等）
     */
    public void recordReceiveOperation(SmsProviderType provider, String operationType, boolean success) {
        lock.readLock().lock();
        try {
            ProviderMetrics metrics = providerMetrics.get(provider);
            if (metrics != null) {
                metrics.recordReceive(operationType, success);
            }
            globalMetrics.recordReceive(operationType, success);
            
            log.debug("记录接收指标 - 提供商: {}, 类型: {}, 成功: {}", 
                    provider, operationType, success);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 记录异常
     */
    public void recordException(SmsProviderType provider, String exceptionType, String message) {
        lock.readLock().lock();
        try {
            ProviderMetrics metrics = providerMetrics.get(provider);
            if (metrics != null) {
                metrics.recordException(exceptionType, message);
            }
            globalMetrics.recordException(exceptionType);
            
            log.debug("记录异常指标 - 提供商: {}, 异常类型: {}", provider, exceptionType);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 记录成本信息
     */
    public void recordCost(SmsProviderType provider, double amount, String currency) {
        lock.readLock().lock();
        try {
            ProviderMetrics metrics = providerMetrics.get(provider);
            if (metrics != null) {
                metrics.recordCost(amount, currency);
            }
            globalMetrics.recordCost(amount, currency);
            
            log.debug("记录成本指标 - 提供商: {}, 金额: {} {}", provider, amount, currency);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取提供商指标
     */
    public ProviderMetrics getProviderMetrics(SmsProviderType provider) {
        lock.readLock().lock();
        try {
            return providerMetrics.get(provider);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取全局指标
     */
    public GlobalMetrics getGlobalMetrics() {
        lock.readLock().lock();
        try {
            return globalMetrics;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取所有提供商的汇总指标
     */
    public Map<SmsProviderType, ProviderMetrics> getAllProviderMetrics() {
        lock.readLock().lock();
        try {
            return new ConcurrentHashMap<>(providerMetrics);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 重置指标
     */
    public void resetMetrics() {
        lock.writeLock().lock();
        try {
            providerMetrics.values().forEach(ProviderMetrics::reset);
            globalMetrics.reset();
            log.info("所有指标已重置");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 重置特定提供商的指标
     */
    public void resetProviderMetrics(SmsProviderType provider) {
        lock.writeLock().lock();
        try {
            ProviderMetrics metrics = providerMetrics.get(provider);
            if (metrics != null) {
                metrics.reset();
                log.info("提供商 {} 的指标已重置", provider);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 提供商指标
     */
    @lombok.Data
    public static class ProviderMetrics {
        private final SmsProviderType provider;
        private final LocalDateTime startTime;
        
        // 发送指标
        private final LongAdder totalSendCount = new LongAdder();
        private final LongAdder successSendCount = new LongAdder();
        private final LongAdder failedSendCount = new LongAdder();
        private final AtomicLong totalSendDuration = new AtomicLong(0);
        private final AtomicLong minSendDuration = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxSendDuration = new AtomicLong(0);
        
        // 接收指标
        private final Map<String, LongAdder> receiveCountByType = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> receiveSuccessCountByType = new ConcurrentHashMap<>();
        
        // 异常指标
        private final Map<String, LongAdder> exceptionCountByType = new ConcurrentHashMap<>();
        
        // 成本指标
        private final Map<String, Double> totalCostByCurrency = new ConcurrentHashMap<>();
        
        // 最近的消息ID
        private volatile String lastMessageId;
        private volatile LocalDateTime lastOperationTime;
        
        public ProviderMetrics(SmsProviderType provider) {
            this.provider = provider;
            this.startTime = LocalDateTime.now();
            this.lastOperationTime = LocalDateTime.now();
        }
        
        public void recordSend(boolean success, Duration duration, String messageId) {
            totalSendCount.increment();
            if (success) {
                successSendCount.increment();
            } else {
                failedSendCount.increment();
            }
            
            long durationMs = duration.toMillis();
            totalSendDuration.addAndGet(durationMs);
            
            // 更新最小和最大耗时
            minSendDuration.updateAndGet(current -> Math.min(current, durationMs));
            maxSendDuration.updateAndGet(current -> Math.max(current, durationMs));
            
            this.lastMessageId = messageId;
            this.lastOperationTime = LocalDateTime.now();
        }
        
        public void recordReceive(String operationType, boolean success) {
            receiveCountByType.computeIfAbsent(operationType, k -> new LongAdder()).increment();
            if (success) {
                receiveSuccessCountByType.computeIfAbsent(operationType, k -> new LongAdder()).increment();
            }
            this.lastOperationTime = LocalDateTime.now();
        }
        
        public void recordException(String exceptionType, String message) {
            exceptionCountByType.computeIfAbsent(exceptionType, k -> new LongAdder()).increment();
            this.lastOperationTime = LocalDateTime.now();
        }
        
        public void recordCost(double amount, String currency) {
            totalCostByCurrency.merge(currency, amount, Double::sum);
        }
        
        public double getSuccessRate() {
            long total = totalSendCount.sum();
            return total > 0 ? (double) successSendCount.sum() / total : 0.0;
        }
        
        public double getAverageSendDuration() {
            long total = totalSendCount.sum();
            return total > 0 ? (double) totalSendDuration.get() / total : 0.0;
        }
        
        public void reset() {
            totalSendCount.reset();
            successSendCount.reset();
            failedSendCount.reset();
            totalSendDuration.set(0);
            minSendDuration.set(Long.MAX_VALUE);
            maxSendDuration.set(0);
            receiveCountByType.clear();
            receiveSuccessCountByType.clear();
            exceptionCountByType.clear();
            totalCostByCurrency.clear();
            lastMessageId = null;
            lastOperationTime = LocalDateTime.now();
        }
    }
    
    /**
     * 全局指标
     */
    @lombok.Data
    public static class GlobalMetrics {
        private final LocalDateTime startTime;
        
        // 全局发送指标
        private final LongAdder totalSendCount = new LongAdder();
        private final LongAdder successSendCount = new LongAdder();
        private final LongAdder failedSendCount = new LongAdder();
        private final AtomicLong totalSendDuration = new AtomicLong(0);
        
        // 全局接收指标
        private final Map<String, LongAdder> receiveCountByType = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> receiveSuccessCountByType = new ConcurrentHashMap<>();
        
        // 全局异常指标
        private final Map<String, LongAdder> exceptionCountByType = new ConcurrentHashMap<>();
        
        // 全局成本指标
        private final Map<String, Double> totalCostByCurrency = new ConcurrentHashMap<>();
        
        private volatile LocalDateTime lastOperationTime;
        
        public GlobalMetrics() {
            this.startTime = LocalDateTime.now();
            this.lastOperationTime = LocalDateTime.now();
        }
        
        public void recordSend(boolean success, Duration duration) {
            totalSendCount.increment();
            if (success) {
                successSendCount.increment();
            } else {
                failedSendCount.increment();
            }
            
            totalSendDuration.addAndGet(duration.toMillis());
            this.lastOperationTime = LocalDateTime.now();
        }
        
        public void recordReceive(String operationType, boolean success) {
            receiveCountByType.computeIfAbsent(operationType, k -> new LongAdder()).increment();
            if (success) {
                receiveSuccessCountByType.computeIfAbsent(operationType, k -> new LongAdder()).increment();
            }
            this.lastOperationTime = LocalDateTime.now();
        }
        
        public void recordException(String exceptionType) {
            exceptionCountByType.computeIfAbsent(exceptionType, k -> new LongAdder()).increment();
            this.lastOperationTime = LocalDateTime.now();
        }
        
        public void recordCost(double amount, String currency) {
            totalCostByCurrency.merge(currency, amount, Double::sum);
        }
        
        public double getGlobalSuccessRate() {
            long total = totalSendCount.sum();
            return total > 0 ? (double) successSendCount.sum() / total : 0.0;
        }
        
        public double getGlobalAverageSendDuration() {
            long total = totalSendCount.sum();
            return total > 0 ? (double) totalSendDuration.get() / total : 0.0;
        }
        
        public void reset() {
            totalSendCount.reset();
            successSendCount.reset();
            failedSendCount.reset();
            totalSendDuration.set(0);
            receiveCountByType.clear();
            receiveSuccessCountByType.clear();
            exceptionCountByType.clear();
            totalCostByCurrency.clear();
            lastOperationTime = LocalDateTime.now();
        }
    }
} 