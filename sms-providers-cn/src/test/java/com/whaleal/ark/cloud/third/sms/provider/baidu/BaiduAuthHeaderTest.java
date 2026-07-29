package com.whaleal.ark.cloud.third.sms.provider.baidu;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 百度 BCE 签名结构冒烟测试。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
class BaiduAuthHeaderTest {

    @Test
    void buildHeaders_matchesSms4jShape() {
        Map<String, String> headers = BaiduOutboundSender.buildHeaders(
                "ak", "sk", "smsv3.bj.baidubce.com", "/api/v3/sendSms", "token123");
        assertThat(headers.get("Authorization")).startsWith("bce-auth-v1/ak/");
        assertThat(headers.get("Authorization")).contains("//");
        assertThat(headers).containsKeys("Host", "x-bce-date");
    }
}
