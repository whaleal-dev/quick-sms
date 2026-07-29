package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.error.SmsErrorCodes;
import com.whaleal.ark.cloud.third.sms.guard.PhoneBlacklist;
import com.whaleal.ark.cloud.third.sms.guard.RateLimiter;
import com.whaleal.ark.cloud.third.sms.webhook.WebhookSecurity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0/P1：failover、黑名单、限流、Webhook 安全。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
class P0P1ClientFeaturesTest {

    @Test
    void blacklist_blocksSend() {
        PhoneBlacklist blacklist = new PhoneBlacklist();
        blacklist.block("13800138000");
        SmsClient client = SmsClients.builder()
                .provider(SmsProviderType.MOCK)
                .blacklist(blacklist)
                .build();

        SmsSendResult result = client.sendText("13800138000", "hi");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(SmsErrorCodes.BLACKLISTED);
    }

    @Test
    void rateLimiter_blocksBurst() {
        SmsClient client = SmsClients.builder()
                .provider(SmsProviderType.MOCK)
                .rateLimiter(new RateLimiter(1, 60_000L))
                .build();

        assertThat(client.sendText("13900000001", "a").isSuccess()).isTrue();
        SmsSendResult second = client.sendText("13900000001", "b");
        assertThat(second.isSuccess()).isFalse();
        assertThat(second.getErrorCode()).isEqualTo(SmsErrorCodes.RATE_LIMITED);
    }

    @Test
    void failover_fallsBackToMockWhenFirstChannelMissingCreds() {
        SmsClient client = SmsClients.builder()
                .addChannel(SmsChannel.of(SmsProviderType.TWILIO, null))
                .addChannel(SmsChannel.of(SmsProviderType.MOCK, null))
                .build();

        SmsSendResult result = client.send(SmsSendRequest.builder()
                .to("13800138000")
                .content("failover-test")
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProvider()).isEqualTo(SmsProviderType.MOCK);
    }

    @Test
    void webhookSecurity_rejectsBadSignatureAndReplay() {
        WebhookSecurity security = new WebhookSecurity("test-secret");
        long ts = System.currentTimeMillis();
        String nonce = "n-1";
        String body = "{\"ok\":true}";
        String sig = security.sign(ts, nonce, body);

        DefaultSmsWebhookHandler handler = new DefaultSmsWebhookHandler(
                new com.whaleal.ark.cloud.third.sms.core.SmsModuleManager(),
                new com.whaleal.ark.cloud.third.sms.validation.PhoneValidationAdapter(),
                com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig.builder()
                        .providerType(SmsProviderType.MOCK)
                        .build(),
                security);

        assertThat(handler.verifyWebhook(ts, nonce, body, sig)).isNull();
        assertThat(handler.verifyWebhook(ts, nonce, body, sig)).isEqualTo(SmsErrorCodes.WEBHOOK_REPLAY);
        assertThat(handler.verifyWebhook(ts, "n-2", body, "deadbeef"))
                .isEqualTo(SmsErrorCodes.WEBHOOK_INVALID);
    }
}
