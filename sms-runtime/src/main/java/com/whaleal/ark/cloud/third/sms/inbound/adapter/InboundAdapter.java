package com.whaleal.ark.cloud.third.sms.inbound.adapter;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderKeys;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.inbound.parser.GenericInboundParser;
import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;
import com.whaleal.ark.cloud.third.sms.inbound.parser.MockInboundParser;
import com.whaleal.ark.cloud.third.sms.spi.SmsExtensionLoader;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 上行短信适配器 - 被动解析上行短信数据
 * 负责将各个提供商的上行短信数据解析为统一的SmsInboundMessage对象
 *
 * @author whaleal-dev
 * @since 1.0.0
 */
@Slf4j
public class InboundAdapter {

    private final Map<String, InboundParser> parserMap;
    private final GenericInboundParser genericParser;

    public InboundAdapter() {
        this.parserMap = new HashMap<>();
        this.genericParser = new GenericInboundParser();
        initializeParsers();
    }

    /**
     * 初始化各提供商的解析器
     */
    private void initializeParsers() {
        MockInboundParser mock = new MockInboundParser();
        parserMap.put(SmsProviderKeys.normalize(mock.getSupportedProvider()), mock);
        parserMap.putAll(SmsExtensionLoader.loadProviders(InboundParser.class, InboundParser::getSupportedProvider));
        log.info("入站消息解析器初始化完成，支持 {} 个提供商", parserMap.size());
    }

    /**
     * 解析上行短信数据
     *
     * @param providerType 提供商类型
     * @param rawData 原始上行短信数据
     * @param config 提供商配置
     * @return 解析后的上行短信对象
     */
    public SmsInboundMessage parseInbound(SmsProviderType providerType, Map<String, Object> rawData, SmsProviderConfig config) {
        try {
            log.debug("开始解析上行短信数据，提供商: {}", providerType);

            InboundParser parser = parserMap.get(SmsProviderKeys.of(providerType));
            if (parser == null) {
                log.warn("未找到提供商 {} 的专用解析器，使用通用解析器", providerType);
                parser = genericParser;
            }

            SmsInboundMessage inbound = parser.parse(rawData, config);

            // 设置提供商类型
            inbound.setProviderType(providerType);

            log.debug("上行短信解析完成，消息ID: {}, 内容: {}", inbound.getMessageId(), inbound.getContent());

            return inbound;

        } catch (Exception e) {
            log.error("解析上行短信数据失败，提供商: {}, 错误: {}", providerType, e.getMessage(), e);

            // 返回错误状态的上行短信
            return createErrorInbound(providerType, rawData, e.getMessage());
        }
    }

    /**
     * 解析上行短信数据（自动检测提供商类型）
     *
     * @param rawData 原始上行短信数据
     * @param config 提供商配置
     * @return 解析后的上行短信对象
     */
    public SmsInboundMessage parseInbound(Map<String, Object> rawData, SmsProviderConfig config) {
        SmsProviderType providerType = detectProviderType(rawData);
        return parseInbound(providerType, rawData, config);
    }

    /**
     * 检测提供商类型
     *
     * @param rawData 原始数据
     * @return 提供商类型
     */
    private SmsProviderType detectProviderType(Map<String, Object> rawData) {
        // Vonage特征检测
        if (rawData.containsKey("msisdn") && rawData.containsKey("text")) {
            return SmsProviderType.VONAGE;
        }

        // 阿里云特征检测
        if (rawData.containsKey("phone_number") && rawData.containsKey("sms_content")) {
            return SmsProviderType.ALIYUN;
        }

        // 腾讯云特征检测
        if (rawData.containsKey("mobile") && rawData.containsKey("content")) {
            return SmsProviderType.TENCENT;
        }

        // 华为云特征检测
        if (rawData.containsKey("from") && rawData.containsKey("body")) {
            return SmsProviderType.HUAWEI;
        }

        // Twilio特征检测
        if (rawData.containsKey("From") && rawData.containsKey("Body")) {
            return SmsProviderType.TWILIO;
        }

        // AWS SNS特征检测
        if (rawData.containsKey("originationNumber") && rawData.containsKey("messageBody")) {
            return SmsProviderType.AWS;
        }

        // 中国移动特征检测
        if (rawData.containsKey("srcTermId") && rawData.containsKey("msgContent")) {
            return SmsProviderType.CHINA_MOBILE;
        }

        // 中国电信特征检测
        if (rawData.containsKey("mobile") && rawData.containsKey("content") && rawData.containsKey("sign")) {
            return SmsProviderType.CHINA_TELECOM;
        }

        // 中国联通特征检测
        if (rawData.containsKey("username") && rawData.containsKey("mobile") && rawData.containsKey("content")) {
            return SmsProviderType.CHINA_UNICOM;
        }

        // 默认使用自定义HTTP
        return SmsProviderType.CUSTOM_HTTP;
    }

    /**
     * 创建错误上行短信
     *
     * @param providerType 提供商类型
     * @param rawData 原始数据
     * @param errorMessage 错误信息
     * @return 错误上行短信
     */
    private SmsInboundMessage createErrorInbound(SmsProviderType providerType, Map<String, Object> rawData, String errorMessage) {
        return SmsInboundMessage.builder()
                .providerType(providerType)
                .messageType(SmsInboundMessage.MessageType.UNKNOWN)
                .content("解析失败: " + errorMessage)
                .rawData(rawData)
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
        return java.util.Set.copyOf(parserMap.keySet());
    }

    /** @deprecated 请使用 {@link #getSupportedProviderCodes()} */
    @Deprecated
    public SmsProviderType[] getSupportedProviders() {
        return parserMap.keySet().stream()
                .map(SmsProviderType::tryFromCode)
                .flatMap(java.util.Optional::stream)
                .toArray(SmsProviderType[]::new);
    }

    public boolean isSupported(String providerCode) {
        String key = SmsProviderKeys.normalize(providerCode);
        return key != null && parserMap.containsKey(key);
    }
}
