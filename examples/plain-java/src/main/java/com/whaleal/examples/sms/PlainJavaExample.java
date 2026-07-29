package com.whaleal.examples.sms;

import com.whaleal.ark.cloud.third.sms.client.SmsClient;
import com.whaleal.ark.cloud.third.sms.client.SmsClients;
import com.whaleal.ark.cloud.third.sms.client.SmsSendResult;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.guard.RateLimiter;

/**
 * 纯 Java 最小示例（Mock 通道）。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class PlainJavaExample {

    private PlainJavaExample() {
    }

    public static void main(String[] args) {
        SmsClient client = SmsClients.builder()
                .provider(SmsProviderType.MOCK)
                .rateLimiter(RateLimiter.perMinute(100))
                .build();

        SmsSendResult result = client.sendText("13800138000", "【QuickSMS】验证码 1234");
        System.out.printf("success=%s messageId=%s%n", result.isSuccess(), result.getMessageId());
    }
}
