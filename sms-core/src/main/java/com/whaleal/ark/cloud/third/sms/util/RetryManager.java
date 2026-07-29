package com.whaleal.ark.cloud.third.sms.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 重试管理器
 * 提供统一的重试机制，支持指数退避和抖动
 * 
 * @author whaleal-dev
 * @since 1.0.0
 */
@Slf4j
public class RetryManager {
    
    private final int maxRetries;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double backoffMultiplier;
    private final boolean enableJitter;
    
    public RetryManager() {
        this(3, Duration.ofSeconds(1), Duration.ofMinutes(5), 2.0, true);
    }
    
    public RetryManager(int maxRetries, Duration initialDelay, Duration maxDelay, 
                       double backoffMultiplier, boolean enableJitter) {
        this.maxRetries = maxRetries;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.backoffMultiplier = backoffMultiplier;
        this.enableJitter = enableJitter;
    }
    
    /**
     * 执行带重试的操作
     * 
     * @param operation 要执行的操作
     * @param retryCondition 重试条件判断
     * @param operationName 操作名称（用于日志）
     * @return 操作结果
     */
    public <T> T executeWithRetry(Supplier<T> operation, 
                                 java.util.function.Predicate<Exception> retryCondition,
                                 String operationName) {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("重试执行操作: {} (第{}/{}次)", operationName, attempt, maxRetries);
                } else {
                    log.debug("首次执行操作: {}", operationName);
                }
                
                T result = operation.get();
                
                if (attempt > 0) {
                    log.info("操作重试成功: {} (第{}次尝试)", operationName, attempt + 1);
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt == maxRetries) {
                    log.error("操作最终失败: {} (已重试{}次)", operationName, maxRetries, e);
                    break;
                }
                
                if (!retryCondition.test(e)) {
                    log.error("操作失败且不满足重试条件: {}", operationName, e);
                    break;
                }
                
                Duration delay = calculateDelay(attempt);
                log.warn("操作失败，{}ms后重试: {} (第{}/{}次), 错误: {}", 
                        delay.toMillis(), operationName, attempt + 1, maxRetries, e.getMessage());
                
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
        
        throw new RuntimeException("操作执行失败: " + operationName, lastException);
    }
    
    /**
     * 计算延迟时间（支持指数退避和抖动）
     */
    private Duration calculateDelay(int attempt) {
        // 计算基础延迟（指数退避）
        long baseDelayMs = (long) (initialDelay.toMillis() * Math.pow(backoffMultiplier, attempt));
        
        // 限制最大延迟
        baseDelayMs = Math.min(baseDelayMs, maxDelay.toMillis());
        
        // 添加抖动避免雷群效应
        if (enableJitter) {
            double jitterFactor = 0.1; // 10%的抖动
            long jitter = (long) (baseDelayMs * jitterFactor * (ThreadLocalRandom.current().nextDouble() - 0.5));
            baseDelayMs += jitter;
        }
        
        return Duration.ofMillis(Math.max(baseDelayMs, 0));
    }
    
    /**
     * 判断异常是否应该重试
     */
    public static boolean shouldRetry(Exception e) {
        if (e == null) return false;
        
        String message = e.getMessage();
        if (message == null) message = "";
        
        // 网络相关错误应该重试
        if (e instanceof java.net.SocketTimeoutException ||
            e instanceof java.net.ConnectException ||
            e instanceof java.io.IOException) {
            return true;
        }
        
        // HTTP 5xx错误应该重试
        if (message.contains("5") && (message.contains("Server Error") || message.contains("Internal"))) {
            return true;
        }
        
        // 超时错误应该重试
        if (message.toLowerCase().contains("timeout") || 
            message.toLowerCase().contains("timed out")) {
            return true;
        }
        
        // 限流错误应该重试
        if (message.contains("429") || 
            message.toLowerCase().contains("rate limit") ||
            message.toLowerCase().contains("too many requests")) {
            return true;
        }
        
        // 临时不可用错误应该重试
        if (message.contains("503") || 
            message.toLowerCase().contains("service unavailable") ||
            message.toLowerCase().contains("temporarily unavailable")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 创建重试信息
     */
    public static RetryInfo createRetryInfo(int currentAttempt, int maxRetries, Exception lastError) {
        return RetryInfo.builder()
                .currentAttempt(currentAttempt)
                .maxRetries(maxRetries)
                .hasMoreRetries(currentAttempt < maxRetries)
                .lastError(lastError != null ? lastError.getMessage() : null)
                .nextRetryTime(currentAttempt < maxRetries ? 
                        LocalDateTime.now().plusSeconds((long) Math.pow(2, currentAttempt)) : null)
                .build();
    }
    
    /**
     * 重试信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RetryInfo {
        private int currentAttempt;
        private int maxRetries;
        private boolean hasMoreRetries;
        private String lastError;
        private LocalDateTime nextRetryTime;
        
        public double getRetryProgress() {
            return maxRetries > 0 ? (double) currentAttempt / maxRetries : 0.0;
        }
        
        public boolean isExhausted() {
            return currentAttempt >= maxRetries;
        }
    }
    
    /**
     * 构建器模式
     */
    public static class Builder {
        private int maxRetries = 3;
        private Duration initialDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofMinutes(5);
        private double backoffMultiplier = 2.0;
        private boolean enableJitter = true;
        
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }
        
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }
        
        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }
        
        public Builder enableJitter(boolean enableJitter) {
            this.enableJitter = enableJitter;
            return this;
        }
        
        public RetryManager build() {
            return new RetryManager(maxRetries, initialDelay, maxDelay, backoffMultiplier, enableJitter);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
} 