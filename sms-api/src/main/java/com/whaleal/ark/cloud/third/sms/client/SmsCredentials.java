package com.whaleal.ark.cloud.third.sms.client;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.util.TextUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短信发送凭证（运行时动态传入，不写入配置文件）
 *
 * @author whaleal-dev
 * @author 恒哥
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsCredentials {

    private String apiKey;
    private String apiSecret;
    private String accessKeyId;
    private String accessKeySecret;
    /** 华为等 */
    private String appKey;
    private String appSecret;
    /** MessageBird 等 */
    private String accessKey;
    /** Vonage JWT / OAuth Bearer */
    private String accessToken;

    public boolean hasAuth() {
        return TextUtils.hasText(apiKey)
                || TextUtils.hasText(apiSecret)
                || TextUtils.hasText(accessKeyId)
                || TextUtils.hasText(accessKeySecret)
                || TextUtils.hasText(appKey)
                || TextUtils.hasText(appSecret)
                || TextUtils.hasText(accessKey)
                || TextUtils.hasText(accessToken);
    }

    /**
     * 将凭证合并到基础配置（仅覆盖非空字段，避免冲掉 base 中已有值）。
     */
    public SmsProviderConfig mergeWith(SmsProviderConfig base) {
        if (base == null) {
            base = SmsProviderConfig.builder().build();
        }
        SmsProviderConfig.SmsProviderConfigBuilder b = base.toBuilder();
        if (TextUtils.hasText(apiKey)) {
            b.apiKey(apiKey);
        }
        if (TextUtils.hasText(apiSecret)) {
            b.apiSecret(apiSecret);
        }
        if (TextUtils.hasText(accessKeyId)) {
            b.accessKeyId(accessKeyId);
        }
        if (TextUtils.hasText(accessKeySecret)) {
            b.accessKeySecret(accessKeySecret);
        }
        if (TextUtils.hasText(appKey)) {
            b.appKey(appKey);
        }
        if (TextUtils.hasText(appSecret)) {
            b.appSecret(appSecret);
        }
        if (TextUtils.hasText(accessKey)) {
            b.accessKey(accessKey);
        }
        if (TextUtils.hasText(accessToken)) {
            b.accessToken(accessToken);
        }
        return b.build();
    }
}
