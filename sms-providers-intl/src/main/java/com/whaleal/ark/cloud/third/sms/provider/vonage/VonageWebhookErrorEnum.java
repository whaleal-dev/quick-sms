package com.whaleal.ark.cloud.third.sms.provider.vonage;

/**
 * @author 恒哥
 */

import lombok.Getter;

import jakarta.validation.constraints.NotNull;

/**
 * Vonage Webhook 错误码
 */
public enum VonageWebhookErrorEnum {
    /**
     * 异常代码 异常值
     **/
    CODE_0(0, "Message was delivered successfully", "消息已成功传递"),

    CODE_1(1, "Message was not delivered, and no reason could be determined", "消息未送达，无法确定任何理由"),

    CODE_2(2, "Message was not delivered because handset was temporarily unavailable - retry", "消息未送达，因为手机暂时不可用 - 重试"),

    CODE_3(3, "The number is no longer active and should be removed from your database", "该号码不再处于活动状态，应从您的数据库中删除"),

    CODE_4(4, "This is a permanent error:the number should be removed from your database and the user must contact their network operator to remove the bar", "这是一个永久错误：号码应从您的数据库中删除，用户必须联系其网络运营商以删除栏"),

    CODE_5(5, "There is an issue relating to portability of the number and you should contact the network operator to resolve it", "有一个与号码的可移植性有关的问题，您应该联系网络运营商来解决它"),

    CODE_6(6, "The message has been blocked by a carrier's anti-spam filter", "邮件已被运营商的反垃圾邮件过滤器阻止"),

    CODE_7(7, "The handset was not available at the time the message was sent - retry", "发送消息时手机不可用 - 重试"),

    CODE_8(8, "The message failed due to a network error - retry", "消息因网络错误而失败 - 重试"),

    CODE_9(9, "The user has specifically requested not to receive messages from a specific service", "用户已明确要求不接收来自特定服务的消息"),

    CODE_10(10, "There is an error in a message parameter, e.g. wrong encoding flag", "消息参数中存在错误，例如编码标记错误"),

    CODE_11(11, "Vonage cannot find a suitable route to deliver the message - contact support@nexmo.com", "Vonage 找不到传递消息的合适途径 - 联系support@nexmo.com"),

    CODE_12(12, "A route to the number cannot be found - confirm the recipient's number", "找不到号码的路线 - 确认收件人的号码"),

    CODE_13(13, "The target cannot receive your message due to their age", "目标无法接收您的消息，因为他们的年龄"),

    CODE_14(14, "The recipient should ask their carrier to enable SMS on their plan", "收件人应要求其运营商在其计划中启用短信"),

    CODE_15(15, "The recipient is on a prepaid plan and does not have enough credit to receive your message", "收件人处于预付费计划中，没有足够的信用额度来接收您的邮件"),

    CODE_50(50, "The message failed due to entity-id being incorrect or not provided. More information on country specific regulations", "消息因不正确或未提供而失败。有关国家具体法规的更多信息entity-id"),

    CODE_51(51, "The message failed because the header ID (from phone number) was incorrect or missing. More information on country specific regulations", "消息失败，因为标题 ID（电话号码）不正确或缺失。有关国家具体法规的更多信息from"),

    CODE_52(52, "The message failed due to content-id being incorrect or not provided. More information on country specific regulations", "消息因不正确或未提供而失败。有关国家具体法规的更多信息content-id"),

    CODE_53(53, "The message failed due to consent not being authorized. More information on country specific regulations", "该消息因未获授权而失败。有关国家具体法规的更多信息"),

    CODE_54(54, "Unexpected regulation error - contact support@nexmo.com", "意外的调节错误 - 联系support@nexmo.com"),

    CODE_99(99, "Typically refers to an error in the route - contact support@nexmo.com", "通常指路线中的错误 - 联系support@nexmo.com");

    @Getter
    private Integer code;
    @Getter
    private String text;

    @Getter
    private String zhText;

    VonageWebhookErrorEnum(Integer code, String text, String zhText) {
        this.code = code;
        this.text = text;
        this.zhText = zhText;
    }

    public static String getTextByCode(@NotNull Integer code) {
        for (VonageWebhookErrorEnum e : VonageWebhookErrorEnum.values()) {
            if (code.equals(e.getCode())) {
                return e.getText();
            }
        }
        return "";
    }

    public static String getAllTextByCode(@NotNull Integer code) {
        for (VonageWebhookErrorEnum e : VonageWebhookErrorEnum.values()) {
            if (code.equals(e.getCode())) {
                return "en:  " + e.getText() + "    zh:  " + e.getZhText();
            }
        }
        return "";
    }


    public static void main(String[] args) {
        System.out.println(getTextByCode(1));
    }
}
