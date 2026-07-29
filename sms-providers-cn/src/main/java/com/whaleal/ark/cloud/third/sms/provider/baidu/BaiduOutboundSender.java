package com.whaleal.ark.cloud.third.sms.provider.baidu;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.error.ProviderErrorMapper;
import com.whaleal.ark.cloud.third.sms.exception.SmsCredentialsException;
import com.whaleal.ark.cloud.third.sms.exception.SmsNetworkException;
import com.whaleal.ark.cloud.third.sms.exception.SmsParameterException;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;
import com.whaleal.ark.cloud.third.sms.util.ProviderHttp;
import com.whaleal.ark.cloud.third.sms.util.SignatureUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 百度云短信（BCE Auth）。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class BaiduOutboundSender implements OutboundSender {

    private static final String DEFAULT_HOST = "smsv3.bj.baidubce.com";
    private static final String DEFAULT_PATH = "/api/v3/sendSms";
    private static final DateTimeFormatter BCE_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String ak = firstNonBlank(config.getAccessKeyId(), config.getApiKey());
        String sk = firstNonBlank(config.getAccessKeySecret(), config.getApiSecret());
        if (isBlank(ak) || isBlank(sk)) {
            throw new SmsCredentialsException("百度云 accessKeyId/Secret 不能为空");
        }
        String template = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(template)) {
            template = firstNonBlank(config.getTemplateId(), config.getTemplateCode());
        }
        if (isBlank(template)) {
            throw new SmsParameterException("百度云 templateId 不能为空");
        }
        String signatureId = firstNonBlank(config.getSignName(), config.getSignature());
        if (isBlank(signatureId)) {
            throw new SmsParameterException("百度云 signatureId（signName）不能为空");
        }

        JSONObject body = new JSONObject();
        body.put("mobile", normalizeMobile(message.getTo()));
        body.put("template", template);
        body.put("signatureId", signatureId);
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            body.put("contentVar", message.getBusinessInfo().getTemplateParams());
        }
        String json = body.toJSONString();
        String host = firstNonBlank(config.getStringConfig("host", null), config.getRegion(), DEFAULT_HOST);
        String path = firstNonBlank(config.getStringConfig("action", null), DEFAULT_PATH);
        String clientToken = UUID.randomUUID().toString().replace("-", "");
        String base = firstNonBlank(config.getBaseUrl(), "https://" + host + path + "?clientToken=" + clientToken);

        Map<String, String> headers = buildHeaders(ak, sk, host, path, clientToken);
        try {
            String resp = ProviderHttp.postJson(base, json, headers, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("百度云发送失败: " + e.getMessage(), SmsProviderType.BAIDU, e);
        }
    }

    /**
     * BCE Auth：Authorization = authPrefix + "//" + signature；只签 host。
     */
    static Map<String, String> buildHeaders(String ak, String sk, String host, String path, String clientToken) {
        Instant now = Instant.now();
        String timestamp = BCE_UTC.format(now);
        String authPrefix = "bce-auth-v1/" + ak + "/" + timestamp + "/1800";
        String signingKey = SignatureUtils.hmacSha256(authPrefix, sk);
        String canonicalUri = urlEncode(path);
        String canonicalQuery = isBlank(clientToken) ? "" : "clientToken=" + urlEncode(clientToken);
        String canonicalHeaders = urlEncode("host") + ":" + urlEncode(host);
        String canonicalRequest = "POST\n" + canonicalUri + "\n" + canonicalQuery + "\n" + canonicalHeaders;
        String signature = SignatureUtils.hmacSha256(canonicalRequest, signingKey);
        String authorization = authPrefix + "/" + "/" + signature;

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Host", host);
        headers.put("x-bce-date", timestamp);
        headers.put("Content-Type", "application/json;charset=UTF-8");
        return headers;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.BAIDU.name();
    }

    @Override
    public boolean supportsTemplate() {
        return true;
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        String code = json.getString("code");
        if ("1000".equals(code) || "OK".equalsIgnoreCase(code) || json.getBooleanValue("success")) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(firstNonBlank(json.getString("requestId"), json.getString("code")));
            message.setProviderType(SmsProviderType.BAIDU);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "baidu",
                code, firstNonBlank(json.getString("message"), json.getString("msg")));
        return message;
    }

    private static String normalizeMobile(String to) {
        if (to == null) return "";
        String m = to.trim();
        if (m.startsWith("+86")) return m.substring(3);
        if (m.startsWith("86") && m.length() > 11) return m.substring(2);
        return m;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (!isBlank(v)) return v.trim();
        }
        return null;
    }
}
