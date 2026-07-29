package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSmsClientTest {

    @Test
    void send_withMockProvider_returnsSuccess() {
        SmsClient client = SmsClients.builder()
                .provider(SmsProviderType.MOCK)
                .build();

        SmsSendResult result = client.send(SmsSendRequest.builder()
                .to("+8613800138000")
                .content("hello sdk")
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessageId()).isNotBlank();
        assertThat(result.getProvider()).isEqualTo(SmsProviderType.MOCK);
        assertThat(result.getStatus()).isIn(
                SmsOutboundMessage.SendStatus.SENT,
                SmsOutboundMessage.SendStatus.SUBMITTED,
                SmsOutboundMessage.SendStatus.DELIVERED
        );
    }

    @Test
    void send_withoutPhone_returnsValidationError() {
        SmsClient client = SmsClients.builder().provider(SmsProviderType.MOCK).build();

        SmsSendResult result = client.send(SmsSendRequest.builder().content("x").build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("E001");
    }

    @Test
    void send_withoutCredentials_onRealProvider_returnsError() {
        SmsClient client = SmsClients.builder().provider(SmsProviderType.TWILIO).build();

        SmsSendResult result = client.send(SmsSendRequest.builder()
                .to("+8613800138000")
                .content("hello")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("E002");
    }

    @Test
    void send_withDynamicCredentials_onRealProvider_passesValidation() {
        SmsClient client = SmsClients.builder().provider(SmsProviderType.TWILIO).build();

        SmsSendResult result = client.send(SmsSendRequest.builder()
                .to("+8613800138000")
                .content("hello")
                .credentials(SmsCredentials.builder()
                        .apiKey("test-sid")
                        .apiSecret("test-token")
                        .build())
                .build());

        assertThat(result.getErrorCode()).isNotEqualTo("E002");
    }

    @Test
    void resolveCallbackUrl_requestOverridesConfig() {
        SmsSendRequest request = SmsSendRequest.builder()
                .callbackUrl("https://api.example.com/sms/receipt")
                .build();
        SmsProviderConfig config = SmsProviderConfig.builder()
                .deliveryReceiptUrl("https://global.example.com/receipt")
                .callbackUrl("https://global.example.com/callback")
                .build();

        assertThat(DefaultSmsClient.resolveCallbackUrl(request, config))
                .isEqualTo("https://api.example.com/sms/receipt");
    }

    @Test
    void resolveCallbackUrl_fallsBackToDeliveryReceiptUrl() {
        SmsProviderConfig config = SmsProviderConfig.builder()
                .deliveryReceiptUrl("https://global.example.com/receipt")
                .callbackUrl("https://global.example.com/callback")
                .build();

        assertThat(DefaultSmsClient.resolveCallbackUrl(SmsSendRequest.builder().build(), config))
                .isEqualTo("https://global.example.com/receipt");
    }

    @Test
    void resolveCallbackUrl_fallsBackToCallbackUrl() {
        SmsProviderConfig config = SmsProviderConfig.builder()
                .callbackUrl("https://global.example.com/callback")
                .build();

        assertThat(DefaultSmsClient.resolveCallbackUrl(SmsSendRequest.builder().build(), config))
                .isEqualTo("https://global.example.com/callback");
    }

    @Test
    void sendText_shortcut_withMock_returnsSuccess() {
        SmsClient client = SmsClients.builder().provider(SmsProviderType.MOCK).build();

        SmsSendResult result = client.sendText("+8613800138000", "hello shortcut");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessageId()).isNotBlank();
    }

    @Test
    void sendTemplate_shortcut_withMock_returnsSuccess() {
        SmsClient client = SmsClients.builder().provider(SmsProviderType.MOCK).build();

        SmsSendResult result = client.sendTemplate("+8613800138000", "TPL_001", Map.of("code", "1234"));

        assertThat(result.isSuccess()).isTrue();
    }
}
