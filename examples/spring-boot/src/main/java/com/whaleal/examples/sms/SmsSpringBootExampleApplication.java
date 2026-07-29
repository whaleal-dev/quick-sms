package com.whaleal.examples.sms;

import com.whaleal.ark.cloud.third.sms.client.SmsClient;
import com.whaleal.ark.cloud.third.sms.client.SmsCredentials;
import com.whaleal.ark.cloud.third.sms.client.SmsSendResult;
import com.whaleal.ark.cloud.third.sms.client.SmsWebhookHandler;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Boot 最小示例：注入默认 MOCK {@link SmsClient}。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@SpringBootApplication
public class SmsSpringBootExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsSpringBootExampleApplication.class, args);
    }

    @RestController
    static class DemoController {
        private final SmsClient smsClient;
        private final SmsWebhookHandler webhookHandler;

        DemoController(SmsClient smsClient, SmsWebhookHandler webhookHandler) {
            this.smsClient = smsClient;
            this.webhookHandler = webhookHandler;
        }

        @PostMapping("/demo/send")
        public SmsSendResult send(@RequestParam(defaultValue = "13800138000") String to) {
            return smsClient.sendText(to, "【QuickSMS】hello from spring");
        }

        /** 真实厂商示例：调用时传入凭证，无需 yml */
        @PostMapping("/demo/send-yunpian")
        public SmsSendResult sendYunpian(@RequestParam String to, @RequestParam String apiKey) {
            return smsClient.sendText(to, "【签名】验证码 1234",
                    SmsProviderType.YUNPIAN.getCode(),
                    SmsCredentials.builder().apiKey(apiKey).build());
        }
    }
}
