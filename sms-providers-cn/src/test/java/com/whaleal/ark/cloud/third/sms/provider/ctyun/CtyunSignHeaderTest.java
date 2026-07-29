package com.whaleal.ark.cloud.third.sms.provider.ctyun;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 天翼云签名结构冒烟测试（不对线上账号）。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
class CtyunSignHeaderTest {

    @Test
    void signHeader_containsRequiredFields() {
        Map<String, String> headers = CtyunOutboundSender.signHeader(
                "{\"action\":\"SendSms\"}", "ak-test", "sk-test");
        assertThat(headers).containsKeys("ctyun-eop-request-id", "Eop-date", "Eop-Authorization");
        assertThat(headers.get("Eop-Authorization")).startsWith("ak-test Headers=");
        assertThat(headers.get("Eop-Authorization")).contains("Signature=");
    }
}
