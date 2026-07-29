package com.whaleal.ark.cloud.third.sms.provider.huawei;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 国内华为 WSSE PasswordDigest 算法校验。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
class HuaweiWsseDigestTest {

    @Test
    void passwordDigest_isBase64Sha256OfNonceCreatedSecret() throws Exception {
        String nonce = "abc123";
        String created = "2026-07-29T03:00:00Z";
        String secret = "app-secret";

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String expected = Base64.getEncoder().encodeToString(
                md.digest((nonce + created + secret).getBytes(StandardCharsets.UTF_8)));

        assertThat(HuaweiOutboundSender.passwordDigest(nonce, created, secret)).isEqualTo(expected);
        // 旧实现是 Base64(明文)，必须不等于正确摘要
        String wrongLegacy = Base64.getEncoder().encodeToString(
                (nonce + created + secret).getBytes(StandardCharsets.UTF_8));
        assertThat(HuaweiOutboundSender.passwordDigest(nonce, created, secret)).isNotEqualTo(wrongLegacy);
    }
}
