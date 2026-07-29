package com.whaleal.ark.cloud.third.sms.enums;

import java.util.Optional;

/**
 * SMS 核心服务提供商类型枚举。
 * <p>
 * <b>扩展约定（2C）</b>：本枚举只收录稳定核心通道。
 * 新增厂商不必改此枚举——在 {@code sms-providers-cn} / {@code sms-providers-intl}
 * 的 {@code provider.<vendor>} 包中实现 SPI，{@code getSupportedProvider()} 返回小写 code（如 {@code yunpian}），
 * 请求侧可通过 {@code providerCode} 指定。参见 {@link SmsProviderKeys}。
 *
 * @author whaleal-dev
 * @author 恒哥
 */
public enum SmsProviderType {

    /**
     * Vonage (原Nexmo)
     */
    VONAGE("vonage", "Vonage", "全球优先"),

    /**
     * 阿里云短信服务（中国区域）
     */
    ALIYUN("aliyun", "阿里云短信", "中国优先"),

    /**
     * 阿里云国际短信服务
     */
    ALIYUN_INTERNATIONAL("aliyun_international", "阿里云国际短信", "亚太优先"),

    /**
     * 腾讯云短信服务（中国区域）
     */
    TENCENT("tencent", "腾讯云短信", "中国优先"),

    /**
     * 腾讯云国际短信服务
     */
    TENCENT_INTERNATIONAL("tencent_international", "腾讯云国际短信", "亚太优先"),

    /**
     * 华为云短信服务（中国区域）
     */
    HUAWEI("huawei", "华为云短信", "中国优先"),

    /**
     * 华为云国际短信服务
     */
    HUAWEI_INTERNATIONAL("huawei_international", "华为云国际短信", "亚太优先"),

    /**
     * Twilio
     */
    TWILIO("twilio", "Twilio", "全球优先"),

    /**
     * Amazon SNS
     */
    AWS("aws", "Amazon", "全球优先"),

    /**
     * 中国移动短信平台
     */
    CHINA_MOBILE("china_mobile", "中国移动", "中国优先"),

    /**
     * 中国电信短信平台
     */
    CHINA_TELECOM("china_telecom", "中国电信", "中国优先"),

    /**
     * 中国联通短信平台
     */
    CHINA_UNICOM("china_unicom", "中国联通", "中国优先"),

    /**
     * 自定义HTTP接口
     */
    CUSTOM_HTTP("custom_http", "自定义HTTP", "可配置"),

    /**
     * 测试模拟平台
     */
    MOCK("mock", "测试模拟", "测试环境"),

    /**
     * MessageBird
     */
    MESSAGEBIRD("messagebird", "MessageBird", "欧洲优先"),

    /**
     * Plivo
     */
    PLIVO("plivo", "Plivo", "美洲优先"),

    /**
     * Infobip
     */
    INFOBIP("infobip", "Infobip", "全球优先"),

    /** 云片 */
    YUNPIAN("yunpian", "云片", "中国优先"),

    /** 创蓝 / 253 云通讯 */
    CHUANGLAN("chuanglan", "创蓝", "中国优先"),

    /** 容联云通讯 */
    CLOOPEN("cloopen", "容联云", "中国优先"),

    /** 七牛云短信 */
    QINIU("qiniu", "七牛云", "中国优先"),

    /** 螺丝帽 */
    LUOSIMAO("luosimao", "螺丝帽", "中国优先"),

    /** SUBMAIL */
    SUBMAIL("submail", "SUBMAIL", "中国优先"),

    /** 天翼云短信 */
    CTYUN("ctyun", "天翼云", "中国优先"),

    /** 网易云信 */
    NETEASE("netease", "网易云信", "中国优先"),

    /** 百度云短信 */
    BAIDU("baidu", "百度云", "中国优先"),

    /** 助通短信 */
    ZHUTONG("zhutong", "助通", "中国优先");

    private final String code;
    private final String displayName;
    private final String region;

    SmsProviderType(String code, String displayName, String region) {
        this.code = code;
        this.displayName = displayName;
        this.region = region;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrimaryRegion() {
        return region;
    }

    /**
     * 根据代码获取枚举值
     * @param code 代码
     * @return 枚举值
     */
    public static SmsProviderType fromCode(String code) {
        return tryFromCode(code)
                .orElseThrow(() -> new IllegalArgumentException("未知的SMS提供商类型: " + code));
    }

    /**
     * 尝试按 code / 枚举名解析；扩展厂商未入枚举时返回 empty。
     */
    public static Optional<SmsProviderType> tryFromCode(String code) {
        String normalized = SmsProviderKeys.normalize(code);
        if (normalized == null) {
            return Optional.empty();
        }
        for (SmsProviderType type : values()) {
            if (type.code.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * 是否为云服务提供商
     * @return true表示是云服务提供商
     */
    public boolean isCloudProvider() {
        return this == ALIYUN || this == ALIYUN_INTERNATIONAL || 
               this == TENCENT || this == TENCENT_INTERNATIONAL || 
               this == HUAWEI || this == HUAWEI_INTERNATIONAL || 
               this == AWS;
    }

    /**
     * 是否为国际短信平台
     * @return true表示是国际短信平台
     */
    public boolean isInternationalPlatform() {
        return this == ALIYUN_INTERNATIONAL || this == TENCENT_INTERNATIONAL || 
               this == HUAWEI_INTERNATIONAL;
    }

    /**
     * 是否支持国际短信
     * @return true表示支持国际短信
     */
    public boolean isInternationalProvider() {
        return isInternationalPlatform() ||
               this == VONAGE || this == TWILIO || this == AWS || 
               this == MESSAGEBIRD || this == PLIVO || this == INFOBIP ||
               this == CUSTOM_HTTP; // 自定义HTTP接口可配置为国际短信
    }

    /**
     * 是否支持国内短信
     * @return true表示支持国内短信
     */
    public boolean isDomesticProvider() {
        return this == ALIYUN || this == TENCENT || this == HUAWEI ||
               this == CHINA_MOBILE || this == CHINA_TELECOM || this == CHINA_UNICOM ||
               this == YUNPIAN || this == CHUANGLAN || this == CLOOPEN ||
               this == QINIU || this == LUOSIMAO || this == SUBMAIL ||
               this == CTYUN || this == NETEASE || this == BAIDU || this == ZHUTONG ||
               this == CUSTOM_HTTP;
    }
}
