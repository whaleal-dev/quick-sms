package com.whaleal.ark.cloud.third.sms.policy;

import com.whaleal.ark.cloud.third.sms.client.SmsChannel;

import java.util.List;

/**
 * 通道选择策略。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public interface ChannelStrategy {

    /**
     * @return 按尝试顺序排列的通道列表
     */
    List<SmsChannel> order(List<SmsChannel> channels);
}
