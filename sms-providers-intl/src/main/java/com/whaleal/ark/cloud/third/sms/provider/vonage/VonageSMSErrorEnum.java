package com.whaleal.ark.cloud.third.sms.provider.vonage;

/**
 * @author 恒哥
 */

import lombok.Getter;

import jakarta.validation.constraints.NotNull;

/**
 * Vonage SMS API 错误码
 */
public enum VonageSMSErrorEnum {
    /**
     * 异常代码 异常值
     **/
    CODE_1(1, "Throttled. You are sending SMS faster than the account limit.", "扼杀。您发送短信的速度比帐户限制快。"),
    CODE_2(2, "Missing Parameters. Your request is missing one of the required parameters from, to, api_key, api_secret or text.", "缺少参数。您的请求缺少所需的参数之一 ， 或 。fromtoapi_keyapi_secrettext"),
    CODE_3(3, "Invalid Parameters. The value of one or more parameters is invalid.", "参数无效。一个或多个参数的值无效。"),
    CODE_4(4, "Invalid Credentials. Your API key and/or secret are incorrect, invalid or disabled.", "凭据无效。您的 API 密钥和/或密钥不正确、无效或已禁用。"),
    CODE_5(5, "Internal Error. An error has occurred in the platform whilst processing this message.", "内部错误。处理此消息时，平台中出现错误。"),
    CODE_6(6, "Invalid Message. The platform was unable to process this message, for example, an un-recognized number prefix.", "无效消息。平台无法处理此消息，例如，未识别的数字前缀。"),
    CODE_7(7, "Number Barred. The number you are trying to send messages to is blacklisted and may not receive them.", "禁止的数字。您尝试向其发送邮件的号码已列入黑名单，可能未收到。"),
    CODE_8(8, "Partner Account Barred. Your Nexmo account has been suspended.", "合作伙伴帐户被禁用。您的 Nexmo 帐户已被暂停。"),
    CODE_9(9, "Partner Quota Violation. You do not have sufficient credit to send the message.", "合作伙伴配额冲突。您没有足够的信用来发送邮件。"),
    CODE_10(10, "Too Many Existing Binds. The number of simultaneous connections to the platform exceeds your account allocation.", "现有绑定太多。与平台同时连接的数量超过您的帐户分配。"),
    CODE_11(11, "Account Not Enabled For HTTP. This account is not provisioned for the SMS API.", "未为 HTTP 启用的帐户。此帐户未为 SMS API 预配。"),
    CODE_12(12, "Message Too Long. The message length exceeds the maximum allowed.", "消息太长。消息长度超过允许的最大长度。"),
    CODE_14(14, "Invalid Signature. The signature supplied could not be verified.", "签名无效。无法验证提供的签名。"),
    CODE_15(15, "Invalid Sender Address. You are using a non-authorized sender ID in the from field.", "无效的发件人地址。您正在字段中使用未授权的发件人 ID。from"),
    CODE_22(22, "Invalid Network Code. The network code supplied was either not recognized, or does not match the country of the destination address.", "无效的网络代码。提供的网络代码要么未识别，要么与目标地址的国家/地区不匹配。"),
    CODE_23(23, "Invalid Callback Url. The callback URL supplied was either too long or contained illegal characters.", "无效回调 URL。提供的回调 URL 太长或包含非法字符。"),
    CODE_29(29, "Non-Whitelisted Destination. Your Nexmo account is still in demo mode. While in demo mode you must add target numbers to your whitelisted destination list.", "非白名单目标。您的 Nexmo 帐户仍处于演示模式。在演示模式下，您必须将目标号码添加到白名单的目标列表中。"),
    CODE_32(32, "Signature And API Secret Disallowed. A signed request may not also present an api_secret.", "签名和 API 机密禁止。已签名的请求可能不也显示 。api_secret"),
    CODE_33(33, "Number De-activated. The number you are trying to send messages to is de-activated and may not receive them.", "号码已停用。您尝试向其发送邮件的号码已停用，可能无法接收。");

    @Getter
    private Integer code;
    @Getter
    private String text;

    @Getter
    private String zhText;

    VonageSMSErrorEnum(Integer code, String text, String zhText) {
        this.code = code;
        this.text = text;
        this.zhText = zhText;
    }

    public static String getTextByCode(@NotNull Integer code) {
        for (VonageSMSErrorEnum e : VonageSMSErrorEnum.values()) {
            if (code.equals(e.getCode())) {
                return e.getText();
            }
        }
        return "";
    }

    public static String getAllTextByCode(@NotNull Integer code) {
        for (VonageSMSErrorEnum e : VonageSMSErrorEnum.values()) {
            if (code.equals(e.getCode())) {
                return "en:  " + e.getText() + "     zh:  " + e.getZhText();
            }
        }
        return "";
    }


    public static void main(String[] args) {
        System.out.println(getTextByCode(1));
    }
}
