package com.whaleal.ark.cloud.third.sms.policy;

import com.whaleal.ark.cloud.third.sms.client.SmsChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 顺序 failover：按配置顺序尝试，直到成功。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class OrderChannelStrategy implements ChannelStrategy {

    public static final OrderChannelStrategy INSTANCE = new OrderChannelStrategy();

    @Override
    public List<SmsChannel> order(List<SmsChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(channels));
    }
}
