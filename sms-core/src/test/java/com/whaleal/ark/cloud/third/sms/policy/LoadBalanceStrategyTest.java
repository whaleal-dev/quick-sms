package com.whaleal.ark.cloud.third.sms.policy;

import com.whaleal.ark.cloud.third.sms.client.SmsChannel;
import com.whaleal.ark.cloud.third.sms.client.SmsCredentials;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 负载均衡策略单元测试：比例正确性、平滑性、failover 顺序。
 */
class LoadBalanceStrategyTest {

    private SmsChannel channel(String name, int weight) {
        return SmsChannel.builder()
                .providerCode(name)
                .credentials(SmsCredentials.builder().apiKey("k").apiSecret("s").build())
                .name(name)
                .weight(weight)
                .build();
    }

    @Test
    void smoothWeightedRR_producesExactRatio() {
        SmoothWeightedRoundRobinStrategy strategy = new SmoothWeightedRoundRobinStrategy();
        List<SmsChannel> channels = List.of(
                channel("a", 5), channel("b", 1), channel("c", 1));

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 70; i++) { // 70 = 7 轮完整周期 × (5+1+1)
            counts.merge(strategy.order(channels).get(0).getName(), 1, Integer::sum);
        }
        assertEquals(50, counts.get("a"));
        assertEquals(10, counts.get("b"));
        assertEquals(10, counts.get("c"));
    }

    @Test
    void smoothWeightedRR_isSmoothNotBursty() {
        SmoothWeightedRoundRobinStrategy strategy = new SmoothWeightedRoundRobinStrategy();
        List<SmsChannel> channels = List.of(
                channel("a", 5), channel("b", 1), channel("c", 1));

        // 平滑性核心指标：任意连续 7 次首选（一个完整周期）中，权重 5 的通道恰好 5 次，
        // 低权重通道始终有穿插——绝不会出现朴素轮询的 aaaaa b c 式连续打爆
        int rounds = 70;
        String[] picks = new String[rounds];
        for (int i = 0; i < rounds; i++) {
            picks[i] = strategy.order(channels).get(0).getName();
        }
        for (int start = 0; start + 7 <= rounds; start++) {
            int a = 0;
            for (int i = start; i < start + 7; i++) {
                if ("a".equals(picks[i])) {
                    a++;
                }
            }
            assertEquals(5, a, "任意 7 连选窗口内权重 5 通道应恰好 5 次，起点=" + start);
        }
        // 不存在权重 5 连发（朴素轮询会连发 5 次）
        int maxRun = 1;
        for (int i = 1; i < rounds; i++) {
            if (picks[i].equals(picks[i - 1])) {
                maxRun++;
            } else {
                maxRun = 1;
            }
            assertTrue(maxRun <= 4, "单通道连选不应达到权重上限，实际=" + maxRun);
        }
    }

    @Test
    void smoothWeightedRR_neverSelectsFailedChannelFirstWhenUniformWeight() {
        SmoothWeightedRoundRobinStrategy strategy = new SmoothWeightedRoundRobinStrategy();
        List<SmsChannel> channels = List.of(channel("x", 1), channel("y", 1), channel("z", 1));

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            counts.merge(strategy.order(channels).get(0).getName(), 1, Integer::sum);
        }
        // 等权退化为标准轮询：每通道恰好 10 次
        assertEquals(10, counts.get("x"));
        assertEquals(10, counts.get("y"));
        assertEquals(10, counts.get("z"));
    }

    @Test
    void orderReturnsAllChannelsForFailover() {
        SmoothWeightedRoundRobinStrategy strategy = new SmoothWeightedRoundRobinStrategy();
        List<SmsChannel> channels = List.of(channel("a", 5), channel("b", 1), channel("c", 1));
        List<SmsChannel> ordered = strategy.order(channels);
        assertEquals(3, ordered.size());
        assertEquals(channels.size(), ordered.stream().distinct().count());
    }

    @Test
    void weightedRandom_followsRatio() {
        WeightedRandomStrategy strategy = new WeightedRandomStrategy();
        List<SmsChannel> channels = List.of(channel("a", 3), channel("b", 1));

        Map<String, Integer> counts = new HashMap<>();
        int rounds = 40_000;
        for (int i = 0; i < rounds; i++) {
            counts.merge(strategy.order(channels).get(0).getName(), 1, Integer::sum);
        }
        double ratio = (double) counts.get("a") / counts.get("b");
        assertTrue(ratio > 2.6 && ratio < 3.4, "加权随机比例应接近 3:1，实际=" + ratio);
    }

    @Test
    void defaultWeightIsOne() {
        assertEquals(1, SmsChannel.builder()
                .providerCode("p")
                .credentials(SmsCredentials.builder().apiKey("k").build())
                .build()
                .getWeight());
    }
}
