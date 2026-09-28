package com.whaleal.ark.cloud.third.sms.policy;

import com.whaleal.ark.cloud.third.sms.client.SmsChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 平滑加权轮询（Smooth Weighted Round-Robin，nginx / SMS4J 同款算法）。
 *
 * <p>与朴素加权轮询（A A A B B）不同，权重 5:1:1 时分发序列为 A A B A C A B A——权重越高的
 * 通道被选中得越密集但彼此被打散，避免上游限流窗口被集中打爆。</p>
 *
 * <p>状态键：优先取 {@code name}，其次 {@code providerCode}。同一策略实例可复用于多个通道
 * 集合，通道增删后过期状态自动清理。<b>同一列表内请保证通道 name（或 providerCode）唯一，
 * 重名通道会共享同一份平滑状态。</b></p>
 *
 * @author whaleal-dev
 * @since 1.1.0
 */
public final class SmoothWeightedRoundRobinStrategy implements ChannelStrategy {

    public static final SmoothWeightedRoundRobinStrategy INSTANCE = new SmoothWeightedRoundRobinStrategy();

    /** 每通道当前累积权重 */
    private final Map<String, Integer> currentWeights = new ConcurrentHashMap<>();

    /** 选择计数（观测用） */
    private final AtomicLong selections = new AtomicLong();

    @Override
    public List<SmsChannel> order(List<SmsChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }

        final int n = channels.size();
        final int[] weights = new int[n];
        final int[] current = new int[n];
        int total = 0;

        for (int i = 0; i < n; i++) {
            weights[i] = Math.max(1, channels.get(i).getWeight());
            total += weights[i];
        }

        List<SmsChannel> ordered;
        synchronized (this) {
            Set<String> liveKeys = new HashSet<>(n * 2);
            for (int i = 0; i < n; i++) {
                String key = keyOf(channels.get(i));
                liveKeys.add(key);
                // 平滑加权：每轮先给各通道累加自身权重；未知通道（新加入）自然从该值起步
                current[i] = currentWeights.merge(key, weights[i], Integer::sum);
            }
            pruneStale(liveKeys);

            // 选累积权重最大者，选中后回退总权重
            int selected = 0;
            for (int i = 1; i < n; i++) {
                if (current[i] > current[selected]) {
                    selected = i;
                }
            }
            current[selected] -= total;

            for (int i = 0; i < n; i++) {
                currentWeights.put(keyOf(channels.get(i)), current[i]);
            }
            selections.incrementAndGet();

            // 首选平滑轮询选中的通道；其余按剩余累积权重降序作为 failover 顺序
            List<Integer> rest = new ArrayList<>(n - 1);
            for (int i = 0; i < n; i++) {
                if (i != selected) {
                    rest.add(i);
                }
            }
            rest.sort(Comparator.comparingInt((Integer i) -> current[i]).reversed());

            ordered = new ArrayList<>(n);
            ordered.add(channels.get(selected));
            for (Integer i : rest) {
                ordered.add(channels.get(i));
            }
        }
        return List.copyOf(ordered);
    }

    private String keyOf(SmsChannel channel) {
        if (channel.getName() != null && !channel.getName().isBlank()) {
            return channel.getName();
        }
        if (channel.getProviderCode() != null && !channel.getProviderCode().isBlank()) {
            return channel.getProviderCode();
        }
        return String.valueOf(System.identityHashCode(channel));
    }

    /** 通道列表变化后清理不再存在的状态键 */
    private void pruneStale(Set<String> liveKeys) {
        if (currentWeights.size() <= liveKeys.size() + 64) {
            return;
        }
        currentWeights.keySet().retainAll(liveKeys);
    }

    /** 仅测试与观测使用：当前累积权重快照 */
    public Map<String, Integer> snapshotWeights() {
        synchronized (this) {
            return Map.copyOf(currentWeights);
        }
    }

    public long selectionCount() {
        return selections.get();
    }
}
