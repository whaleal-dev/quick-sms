package com.whaleal.ark.cloud.third.sms.error;

/**
 * SDK 统一错误码（厂商错误应映射到此集合）。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class SmsErrorCodes {

    private SmsErrorCodes() {
    }

    public static final String INVALID_REQUEST = "E001";
    public static final String MISSING_CREDENTIALS = "E002";
    public static final String BLACKLISTED = "E003";
    public static final String RATE_LIMITED = "E004";
    public static final String WEBHOOK_INVALID = "E005";
    public static final String WEBHOOK_REPLAY = "E006";

    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    public static final String SEND_ERROR = "SEND_ERROR";
    public static final String AUTH_FAILED = "AUTH_FAILED";
    public static final String INVALID_NUMBER = "INVALID_NUMBER";
    public static final String CONTENT_REJECTED = "CONTENT_REJECTED";
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String ALL_CHANNELS_FAILED = "ALL_CHANNELS_FAILED";
}
