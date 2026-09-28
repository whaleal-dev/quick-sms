package com.whaleal.ark.cloud.third.sms.policy;

import com.whaleal.ark.cloud.third.sms.client.SmsChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权随机：按 weight 比例的概率抽取通道（无状态，多实例部署无需共享状态）。
 *
 * <p>采用 Efraimidis–Spirakis 加权抽样：为每个通道生成 {@code u^(1/w)}（u 为 (0,1) 均匀随机数）
 * 作为键并降序排列，结果即一次不放回的加权随机抽取，同时天然给出随机化的 failover 顺序。</p>
 *
 * @author whaleal-dev
 * @since 1.1.0
 */
public final class WeightedRandomStrategy implements ChannelStrategy {

    public static final WeightedRandomStrategy INSTANCE = new WeightedRandomStrategy();

    @Override
    public List<SmsChannel> order(List<SmsChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        int n = channels.size();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Weighted> keyed = new ArrayList<>(n);
        for (SmsChannel channel : channels) {
            int weight = Math.max(1, channel.getWeight());
            double u = random.nextDouble(1.0E-12, 1.0);
            keyed.add(new Weighted(channel, Math.pow(u, 1.0 / weight)));
        }
        keyed.sort(Comparator.comparingDouble((Weighted w) -> w.key).reversed());
        List<SmsChannel> ordered = new ArrayList<>(n);
        for (Weighted w : keyed) {
            ordered.add(w.channel);
        }
        return List.copyOf(ordered);
    }

    private record Weighted(SmsChannel channel, double key) {
    }
}
