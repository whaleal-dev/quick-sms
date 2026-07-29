package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.metrics.SmsMetrics;
import com.whaleal.ark.cloud.third.sms.policy.RandomChannelStrategy;
import com.whaleal.ark.cloud.third.sms.util.MetricsCollector;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author 恒哥
 * @since 2026-07-29
 */
class P2MetricsAndStrategyTest {

    @Test
    void randomStrategy_preservesSize() {
        var channels = java.util.List.of(
                SmsChannel.of(SmsProviderType.MOCK, null),
                SmsChannel.of(SmsProviderType.YUNPIAN, null),
                SmsChannel.of(SmsProviderType.CHUANGLAN, null));
        var ordered = RandomChannelStrategy.INSTANCE.order(channels);
        assertThat(ordered).hasSize(3).containsExactlyInAnyOrderElementsOf(channels);
    }

    @Test
    void metrics_areRecordedOnSend() {
        AtomicInteger hits = new AtomicInteger();
        SmsMetrics probe = (provider, success, durationMs, errorCode) -> hits.incrementAndGet();

        SmsClient client = SmsClients.builder()
                .provider(SmsProviderType.MOCK)
                .metrics(probe)
                .build();

        assertThat(client.sendText("13900001111", "m").isSuccess()).isTrue();
        assertThat(hits.get()).isGreaterThanOrEqualTo(1);
        assertThat(MetricsCollector.getInstance().getProviderMetrics(SmsProviderType.MOCK).getTotalSendCount().sum())
                .isGreaterThanOrEqualTo(1);
    }
}
