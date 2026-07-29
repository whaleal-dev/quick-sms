package com.whaleal.ark.cloud.third.sms.config;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderKeys;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;
import java.util.Objects;

/**
 * SMS提供商配置类
 * 
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SmsProviderConfig {
    
    /**
     * 提供商类型（核心通道枚举；扩展厂商可只填 {@link #providerCode}）
     */
    private SmsProviderType providerType;

    /**
     * 提供商编码（扩展厂商用，无需改 {@link SmsProviderType}）。
     * <p>与 SPI {@code getSupportedProvider()} 返回值一致，建议小写，如 {@code yunpian}。</p>
     */
    private String providerCode;
    
    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;
    
    /**
     * 提供商名称
     */
    private String name;
    
    /**
     * 基础URL
     */
    private String baseUrl;

    /**
     * 对外发信使用的 baseUrl（优先 HTTPS，受 {@link #sslEnabled} 控制）
     */
    public String getOutboundBaseUrl() {
        return com.whaleal.ark.cloud.third.sms.util.HttpUrlUtils.preferHttps(baseUrl, sslEnabled);
    }
    
    /**
     * API密钥
     */
    private String apiKey;
    
    /**
     * API密码/Token
     */
    private String apiSecret;
    
    /**
     * 访问密钥ID (AWS等)
     */
    private String accessKeyId;
    
    /**
     * 访问密钥密码 (AWS等)
     */
    private String accessKeySecret;
    
    /**
     * 区域 (AWS等)
     */
    private String region;
    
    /**
     * 签名名称
     */
    private String signName;
    
    /**
     * 默认发送方号码
     */
    private String defaultFrom;
    
    /**
     * 短信送达回执URL（Delivery Receipt）
     * 用于接收短信送达状态回执通知，表示短信是否成功送达到用户手机
     * 这是最终的送达确认，告知短信是否真正到达用户设备
     */
    private String deliveryReceiptUrl;

    private String statusReportUrl;

    private String inboundSmsUrl;

    private String callbackUrl;
    
    /**
     * 连接超时时间(毫秒)
     */
    @Builder.Default
    private Integer connectTimeout = 10000;
    
    /**
     * 读取超时时间(毫秒)
     */
    @Builder.Default
    private Integer readTimeout = 10000;
    
    /**
     * 请求超时时间(毫秒) - 短信发送专用
     */
    @Builder.Default
    private Integer requestTimeout = 10000;
    
    /**
     * 重试次数
     */
    @Builder.Default
    private Integer retryCount = 3;
    
    /**
     * 是否启用SSL
     */
    @Builder.Default
    private Boolean sslEnabled = true;

    /**
     * HTTP 代理主机（可选；配合 {@link #proxyPort}）
     */
    private String proxyHost;

    /**
     * HTTP 代理端口（可选）
     */
    private Integer proxyPort;
    
    /**
     * 扩展配置参数
     */
    private Map<String, Object> config;
    
    // 中国联通特有参数
    private String appId;
    private String accessToken;
    private String signature;
    private String templateCode;
    
    // 中国电信特有参数
    private String appKey;
    private String appSecret;
    private String templateId;
    
    // MessageBird特有参数
    private String accessKey;
    private String channelId;
    private String dataCoding;
    private String reportUrl;
    
    // Plivo特有参数
    private String authId;
    private String authToken;
    private String powerpackId;
    
    // Infobip特有参数
    private String accountId;
    private String projectId;
    private String entityId;
    private String notifyUrl;
    
    /**
     * 获取配置值
     * @param key 配置键
     * @return 配置值
     */
    public Object getConfigValue(String key) {
        return config != null ? config.get(key) : null;
    }
    
    /**
     * 获取字符串配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String getStringConfig(String key, String defaultValue) {
        Object value = getConfigValue(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * 获取整型配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Integer getIntConfig(String key, Integer defaultValue) {
        Object value = getConfigValue(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value != null ? Integer.parseInt(value.toString()) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 获取布尔配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Boolean getBooleanConfig(String key, Boolean defaultValue) {
        Object value = getConfigValue(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        try {
            return value != null ? Boolean.parseBoolean(value.toString()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * 验证配置是否有效
     * @return 错误信息，null表示验证通过
     */
    public String validate() {
        if (providerType == null) {
            return "提供商类型不能为空";
        }
        
        if (!enabled) {
            return null; // 如果禁用则不需要验证其他参数
        }
        
        // 验证基本参数
        if (isEmpty(name)) {
            return "提供商名称不能为空";
        }
        
        // 验证认证信息
        boolean hasAuth = !isEmpty(apiKey) || !isEmpty(accessKeyId) || !isEmpty(apiSecret);
        if (!hasAuth) {
            return "至少需要配置一个认证信息(apiKey/accessKeyId/apiSecret)";
        }
        
        // 验证超时配置
        if (connectTimeout != null && connectTimeout <= 0) {
            return "连接超时时间必须大于0";
        }
        if (readTimeout != null && readTimeout <= 0) {
            return "读取超时时间必须大于0";
        }
        if (requestTimeout != null && requestTimeout <= 0) {
            return "请求超时时间必须大于0";
        }
        
        return null;
    }
    
    /**
     * SPI / 适配器查找键：优先 {@link #providerCode}，否则取枚举 code。
     */
    public String resolveProviderKey() {
        return SmsProviderKeys.resolve(providerType, providerCode);
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmsProviderConfig that = (SmsProviderConfig) o;
        return Objects.equals(providerType, that.providerType) &&
               Objects.equals(providerCode, that.providerCode) &&
               Objects.equals(apiKey, that.apiKey) &&
               Objects.equals(apiSecret, that.apiSecret) &&
               Objects.equals(accessKeyId, that.accessKeyId) &&
               Objects.equals(accessKeySecret, that.accessKeySecret) &&
               Objects.equals(region, that.region) &&
               Objects.equals(baseUrl, that.baseUrl);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(providerType, providerCode, apiKey, apiSecret, accessKeyId, 
                          accessKeySecret, region, baseUrl);
    }
} 