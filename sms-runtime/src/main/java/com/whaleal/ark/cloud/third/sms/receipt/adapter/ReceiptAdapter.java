package com.whaleal.ark.cloud.third.sms.receipt.adapter;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderKeys;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.whaleal.ark.cloud.third.sms.receipt.parser.GenericReceiptParser;
import com.whaleal.ark.cloud.third.sms.receipt.parser.MockReceiptParser;
import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;
import com.whaleal.ark.cloud.third.sms.spi.SmsExtensionLoader;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 回执适配器 - 被动解析回执数据
 * 负责将各个提供商的回执数据解析为统一的SmsReceipt对象
 *
 * @author whaleal-dev
 * @since 1.0.0
 */
@Slf4j
public class ReceiptAdapter {

    private final Map<String, ReceiptParser> parserMap;
    private final GenericReceiptParser genericParser;

    public ReceiptAdapter() {
        this.parserMap = new HashMap<>();
        this.genericParser = new GenericReceiptParser();
        initializeParsers();
    }

    /**
     * 初始化各提供商的解析器
     */
    private void initializeParsers() {
        MockReceiptParser mock = new MockReceiptParser();
        parserMap.put(SmsProviderKeys.normalize(mock.getSupportedProvider()), mock);
        parserMap.putAll(SmsExtensionLoader.loadProviders(ReceiptParser.class, ReceiptParser::getSupportedProvider));
        log.info("回执解析器初始化完成，支持 {} 个提供商", parserMap.size());
    }

    /**
     * 解析回执数据
     *
     * @param providerType 提供商类型
     * @param rawData 原始回执数据
     * @param config 提供商配置
     * @return 解析后的回执对象
     */
    public SmsReceipt parseReceipt(SmsProviderType providerType, Map<String, Object> rawData, SmsProviderConfig config) {
        try {
            log.debug("开始解析回执数据，提供商: {}", providerType);

            ReceiptParser parser = parserMap.get(SmsProviderKeys.of(providerType));
            if (parser == null) {
                log.warn("未找到提供商 {} 的专用解析器，使用通用解析器", providerType);
                parser = genericParser;
            }

            SmsReceipt receipt = parser.parse(rawData, config);

            // 设置提供商类型
            receipt.setProviderType(providerType);

            log.debug("回执解析完成，消息ID: {}, 状态: {}", receipt.getMessageId(), receipt.getReceiptStatus());

            return receipt;

        } catch (Exception e) {
            log.error("解析回执数据失败，提供商: {}, 错误: {}", providerType, e.getMessage(), e);

            // 返回错误状态的回执
            return createErrorReceipt(providerType, rawData, e.getMessage());
        }
    }

    /**
     * 解析回执数据（自动检测提供商类型）
     *
     * @param rawData 原始回执数据
     * @param config 提供商配置
     * @return 解析后的回执对象
     */
    public SmsReceipt parseReceipt(Map<String, Object> rawData, SmsProviderConfig config) {
        SmsProviderType providerType = detectProviderType(rawData);
        return parseReceipt(providerType, rawData, config);
    }

    /**
     * 检测提供商类型
     *
     * @param rawData 原始数据
     * @return 提供商类型
     */
    private SmsProviderType detectProviderType(Map<String, Object> rawData) {
        // Mock特征检测
        if (rawData.containsKey("mock") || rawData.containsKey("test") ||
            (rawData.containsKey("messageId") && rawData.get("messageId").toString().startsWith("mock_"))) {
            return SmsProviderType.MOCK;
        }

        // Vonage特征检测
        if (rawData.containsKey("msisdn") && rawData.containsKey("to")) {
            return SmsProviderType.VONAGE;
        }

        // 阿里云特征检测
        if (rawData.containsKey("phone_number") && rawData.containsKey("send_time")) {
            return SmsProviderType.ALIYUN;
        }

        // 腾讯云特征检测
        if (rawData.containsKey("mobile") && rawData.containsKey("report_status")) {
            return SmsProviderType.TENCENT;
        }

        // 华为云特征检测
        if (rawData.containsKey("from") && rawData.containsKey("statusCode")) {
            return SmsProviderType.HUAWEI;
        }

        // Twilio特征检测
        if (rawData.containsKey("MessageSid") && rawData.containsKey("MessageStatus")) {
            return SmsProviderType.TWILIO;
        }

        // AWS SNS特征检测
        if (rawData.containsKey("Type") && "Notification".equals(rawData.get("Type"))) {
            return SmsProviderType.AWS;
        }

        // 中国移动特征检测
        if (rawData.containsKey("rptTime") && rawData.containsKey("msgId")) {
            return SmsProviderType.CHINA_MOBILE;
        }

        // 默认使用自定义HTTP
        return SmsProviderType.CUSTOM_HTTP;
    }

    /**
     * 创建错误回执
     *
     * @param providerType 提供商类型
     * @param rawData 原始数据
     * @param errorMessage 错误信息
     * @return 错误回执
     */
    private SmsReceipt createErrorReceipt(SmsProviderType providerType, Map<String, Object> rawData, String errorMessage) {
        return SmsReceipt.builder()
                .providerType(providerType)
                .receiptStatus(SmsReceipt.ReceiptStatus.UNKNOWN)
                .errorCode("PARSE_ERROR")
                .errorDescription("回执解析失败: " + errorMessage)
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
