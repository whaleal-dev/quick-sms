package com.whaleal.ark.cloud.third.sms.provider.vonage;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.error.ProviderErrorMapper;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Vonage 下行发送器。
 * <p>默认使用官方推荐的 <b>Messages API v1</b>（{@code POST https://api.nexmo.com/v1/messages}）。
 * 旧版 SMS API（{@code rest.nexmo.com/sms/json}）可通过配置回退：
 * {@code config.apiMode=legacy} 或 {@code config.useLegacySmsApi=true}。</p>
 *
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 * @see <a href="https://developer.vonage.com/en/messages/guides/sms-migration-to-messages">SMS → Messages 迁移指南</a>
 */
@Slf4j
public class VonageOutboundSender implements OutboundSender {

    static final String MESSAGES_ENDPOINT = "https://api.nexmo.com/v1/messages";
    static final String LEGACY_SMS_ENDPOINT = "https://rest.nexmo.com/sms/json";

    private final HttpClient httpClient;

    public VonageOutboundSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        try {
            log.info("Vonage发送短信 - 接收方: {}, api={}",
                    message.getTo(), useLegacy(config) ? "legacy-sms" : "messages-v1");
            if (useLegacy(config)) {
                return sendLegacySms(message, config);
            }
            return sendMessagesApi(message, config);
        } catch (Exception e) {
            log.error("Vonage发送短信失败，接收方: {}, 错误: {}", message.getTo(), e.getMessage(), e);
            return createFailedMessage(message, e.getMessage());
        }
    }

    /**
     * Messages API v1：JSON + Basic Auth（或配置中的 Bearer JWT）。
     */
    private SmsOutboundMessage sendMessagesApi(SmsOutboundMessage message, SmsProviderConfig config)
            throws Exception {
        String apiKey = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String apiSecret = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        if (isBlank(apiKey) || isBlank(apiSecret)) {
            return failMapped(message, "E002", "Vonage apiKey/apiSecret 不能为空");
        }
        String from = firstNonBlank(message.getFrom(), config.getDefaultFrom());
        if (isBlank(from) || isBlank(message.getTo()) || isBlank(message.getContent())) {
            return failMapped(message, "E001", "from/to/content 不能为空");
        }

        JSONObject body = new JSONObject();
        body.put("message_type", "text");
        body.put("channel", "sms");
        body.put("from", from);
        body.put("to", normalizeE164(message.getTo()));
        body.put("text", message.getContent());
        // 覆盖默认状态回调
        String webhook = resolveWebhook(message, config);
        if (!isBlank(webhook)) {
            body.put("webhook_url", webhook);
            body.put("webhook_version", "v1");
        }
        // unicode：Messages API 由平台处理；可选 client_ref
        if (message.getBusinessInfo() != null
                && message.getBusinessInfo().getRelatedBusinessId() != null
                && !message.getBusinessInfo().getRelatedBusinessId().isBlank()) {
            body.put("client_ref", message.getBusinessInfo().getRelatedBusinessId().trim());
        }

        String endpoint = firstNonBlank(config.getBaseUrl(), MESSAGES_ENDPOINT);
        String json = body.toJSONString();
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMs(config)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));

        String bearer = firstNonBlank(
                config.getStringConfig("jwt", null),
                config.getStringConfig("accessToken", null),
                config.getAccessToken());
        if (!isBlank(bearer)) {
            req.header("Authorization", "Bearer " + bearer.trim());
        } else {
            req.header("Authorization", basicAuth(apiKey, apiSecret));
        }

        HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
        return parseMessagesResponse(message, response);
    }

    private SmsOutboundMessage parseMessagesResponse(SmsOutboundMessage message, HttpResponse<String> response) {
        int code = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        log.debug("Vonage Messages API HTTP {} body={}", code, body);

        JSONObject json = body.isBlank() ? new JSONObject() : JSON.parseObject(body);
        if (code >= 200 && code < 300) {
            String uuid = firstNonBlank(json.getString("message_uuid"), json.getString("message_id"));
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(uuid);
            if (message.getMessageId() == null) {
                message.setMessageId(uuid);
            }
            message.setProviderType(SmsProviderType.VONAGE);
            message.setSentTime(LocalDateTime.now());
            Map<String, Object> extra = message.getExtraInfo() == null ? new HashMap<>() : message.getExtraInfo();
            extra.put("api", "messages-v1");
            extra.put("httpStatus", code);
            message.setExtraInfo(extra);
            message.setRawData(Map.of("response", json, "httpStatus", code));
            return message;
        }

        String errTitle = firstNonBlank(json.getString("title"), json.getString("type"), "HTTP_" + code);
        String errDetail = firstNonBlank(json.getString("detail"), json.getString("error_text"), body);
        return failMapped(message, errTitle, errDetail);
    }

    /** 旧版 SMS API，仅兼容存量账号。 */
    private SmsOutboundMessage sendLegacySms(SmsOutboundMessage message, SmsProviderConfig config)
            throws Exception {
        String apiKey = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String apiSecret = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        Map<String, String> params = new HashMap<>();
        params.put("api_key", apiKey);
        params.put("api_secret", apiSecret);
        params.put("from", firstNonBlank(message.getFrom(), config.getDefaultFrom()));
        params.put("to", message.getTo());
        params.put("text", message.getContent());
        params.put("type", "unicode".equalsIgnoreCase(message.getEncoding()) ? "unicode" : "text");
        String webhook = resolveWebhook(message, config);
        if (!isBlank(webhook)) {
            params.put("callback", webhook);
        }

        String form = encodeForm(params);
        String endpoint = firstNonBlank(config.getBaseUrl(), LEGACY_SMS_ENDPOINT);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMs(config)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseLegacyResponse(message, response);
    }

    private SmsOutboundMessage parseLegacyResponse(SmsOutboundMessage original, HttpResponse<String> response) {
        JSONObject root = JSON.parseObject(response.body());
        JSONArray messages = root.getJSONArray("messages");
        if (messages == null || messages.isEmpty()) {
            return failMapped(original, "LEGACY_EMPTY", "No messages in response");
        }
        JSONObject first = messages.getJSONObject(0);
        String status = first.getString("status");
        if (status != null && !"0".equals(status)) {
            return failMapped(original,
                    firstNonBlank(first.getString("error-code"), status),
                    first.getString("error-text"));
        }
        original.setSendStatus(SmsOutboundMessage.SendStatus.SENT);
        original.setProviderMessageId(first.getString("message-id"));
        original.setProviderType(SmsProviderType.VONAGE);
        original.setSentTime(LocalDateTime.now());
        Map<String, Object> extra = original.getExtraInfo() == null ? new HashMap<>() : original.getExtraInfo();
        extra.put("api", "legacy-sms");
        extra.put("network", first.getString("network"));
        extra.put("message_price", first.getString("message-price"));
        original.setExtraInfo(extra);
        return original;
    }

    static boolean useLegacy(SmsProviderConfig config) {
        if (config == null) {
            return false;
        }
        if (config.getBooleanConfig("useLegacySmsApi", false)) {
            return true;
        }
        String mode = config.getStringConfig("apiMode", null);
        return mode != null && ("legacy".equalsIgnoreCase(mode) || "sms".equalsIgnoreCase(mode));
    }

    private static String resolveWebhook(SmsOutboundMessage message, SmsProviderConfig config) {
        if (message.getSendConfig() != null && !isBlank(message.getSendConfig().getCallbackUrl())) {
            return message.getSendConfig().getCallbackUrl();
        }
        return firstNonBlank(config.getDeliveryReceiptUrl(), config.getCallbackUrl(), config.getStatusReportUrl());
    }

    private SmsOutboundMessage failMapped(SmsOutboundMessage message, String code, String detail) {
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "vonage", code, detail);
        message.setProviderType(SmsProviderType.VONAGE);
        return message;
    }

    private static String basicAuth(String user, String pass) {
        String token = Base64.getEncoder().encodeToString(
                (user + ":" + (pass == null ? "" : pass)).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private static String encodeForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String normalizeE164(String to) {
        if (to == null) {
            return "";
        }
        String m = to.trim().replace(" ", "");
        if (m.startsWith("+")) {
            return m.substring(1); // Messages API 常用无 + 的数字串，两者皆可；去掉空格即可
        }
        return m;
    }

    private static long timeoutMs(SmsProviderConfig config) {
        if (config.getRequestTimeout() != null && config.getRequestTimeout() > 0) {
            return config.getRequestTimeout();
        }
        return 30_000L;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (!isBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.VONAGE.name();
    }
}
