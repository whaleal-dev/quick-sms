package com.whaleal.ark.cloud.third.sms.provider.vonage;

/**
 * @author 恒哥
 */
public enum VonageSmsErrorCodeEnum {
    SUCCESS("0", "Success", "消息已成功接受并将进行发送"),
    THROTTLED("1", "Throttled", "短信发送速度超过账户限制"),
    MISSING_PARAMETERS("2", "Missing Parameters", "请求缺少必需参数，如from、to、api_key、api_secret或text"),
    INVALID_PARAMETERS("3", "Invalid Parameters", "一个或多个参数的值无效"),
    INVALID_CREDENTIALS("4", "Invalid Credentials", "API密钥和/或密钥不正确、无效或已禁用"),
    INTERNAL_ERROR("5", "Internal Error", "平台在处理消息时发生错误"),
    INVALID_MESSAGE("6", "Invalid Message", "平台无法处理该消息，例如无法识别的号码前缀"),
    NUMBER_BARRED("7", "Number Barred", "目标号码在受限号码列表中"),
    PARTNER_ACCOUNT_BARRED("8", "Partner Account Barred", "Vonage账户已被暂停，请联系支持人员"),
    PARTNER_QUOTA_VIOLATION("9", "Partner Quota Violation", "余额不足，无法发送消息，请充值后重试"),
    TOO_MANY_EXISTING_BINDS("10", "Too Many Existing Binds", "与平台的同时连接数超过账户分配限制"),
    NETWORK_FAILURE("11", "Network failure", "网络故障"),
    MESSAGE_TOO_LONG("12", "Message Too Long", "消息长度超过最大允许长度"),
    INVALID_MESSAGE_CLASS("13", "Invalid message class", "无效的消息类别"),
    INVALID_SIGNATURE("14", "Invalid Signature", "提供的签名无法验证"),
    INVALID_SENDER_ADDRESS("15", "Invalid Sender Address", "在from字段中使用了未经授权的发送者ID，在北美地区通常需要Vonage长虚拟号码或短码"),
    SERVICE_UNAVAILABLE("16", "Service unavailable", "服务不可用"),
    RATE_LIMIT_EXCEEDED("17", "Rate limit exceeded", "超过速率限制"),
    DUPLICATE_MESSAGE("18", "Duplicate message", "重复的消息"),
    INVALID_STATUS_REPORT_URL("19", "Invalid status report url", "无效的状态报告 URL"),
    INVALID_SCHEDULE_TIME("20", "Invalid schedule time", "无效的预定时间"),
    REJECTED_BY_OPERATOR("21", "Rejected by operator", "被运营商拒绝"),
    INVALID_NETWORK_CODE("22", "Invalid Network Code", "提供的网络代码未被识别或与目标地址的国家不匹配"),
    INVALID_CALLBACK_URL("23", "Invalid Callback URL", "提供的回调URL过长或包含非法字符"),
    NON_WHITELISTED_DESTINATION("29", "Non-Whitelisted Destination", "Vonage账户仍处于演示模式，演示模式下必须将目标号码添加到白名单中，请充值以解除限制"),
    SIGNATURE_AND_API_SECRET_DISALLOWED("32", "Signature And API Secret Disallowed", "签名请求不能同时提供api_secret"),
    NUMBER_DE_ACTIVATED("33", "Number De-activated", "目标号码已停用，可能无法接收消息");

    private final String code;
    private final String englishMessage;
    private final String chineseMessage;

    VonageSmsErrorCodeEnum(String code, String englishMessage, String chineseMessage) {
        this.code = code;
        this.englishMessage = englishMessage;
        this.chineseMessage = chineseMessage;
    }

    public String getCode() {
        return code;
    }

    public String getEnglishMessage() {
        return englishMessage;
    }

    public String getChineseMessage() {
        return chineseMessage;
    }

    public static VonageSmsErrorCodeEnum fromCode(String code) {
        for (VonageSmsErrorCodeEnum errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return null;
    }
}