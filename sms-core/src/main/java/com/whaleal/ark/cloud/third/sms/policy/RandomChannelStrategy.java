package com.whaleal.ark.cloud.third.sms.policy;

import com.whaleal.ark.cloud.third.sms.client.SmsChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机打乱通道顺序后再顺序 failover。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class RandomChannelStrategy implements ChannelStrategy {

    public static final RandomChannelStrategy INSTANCE = new RandomChannelStrategy();

    @Override
    public List<SmsChannel> order(List<SmsChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        List<SmsChannel> copy = new ArrayList<>(channels);
        Collections.shuffle(copy, ThreadLocalRandom.current());
        return Collections.unmodifiableList(copy);
    }
}
