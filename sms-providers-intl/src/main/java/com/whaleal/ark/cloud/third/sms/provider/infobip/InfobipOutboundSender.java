package com.whaleal.ark.cloud.third.sms.provider.infobip;

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
 * Infobip 下行发送器。
 *
 * <p>默认使用最新 <b>SMS API V3</b>（{@code POST {baseUrl}/sms/3/messages}，
 * 2026 起官方文档将 V2 {@code /sms/2/text/advanced} 标记为 superseded）：</p>
 * <pre>
 * {
 *   "messages": [{
 *     "sender": "InfoSMS",
 *     "destinations": [{"to": "41793026727"}],
 *     "content": {"text": "..."},
 *     "webhooks": {"delivery": {"url": "..."}, "contentType": "application/json"}
 *   }]
 * }
 * </pre>
 * <p>认证：API key（推荐）用 {@code Authorization: App &lt;apiKey&gt;}；
 * 账号密码式（apiSecret 非空）回退 {@code Basic apiKey:apiSecret}。</p>
 * <p>V2 可通过 {@code config.config["apiVersion"]="v2"} 回退（存量账号兜底）。</p>
 *
 * @author whaleal-dev
 * @since 1.0.0
 * @see <a href="https://www.infobip.com/docs/api/channels/sms/send-sms-message">Infobip Send SMS message (V3)</a>
 */
@Slf4j
public class InfobipOutboundSender implements OutboundSender {

    private static final String DEFAULT_ENDPOINT = "https://api.infobip.com";
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final HttpClient httpClient;

    public InfobipOutboundSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        try {
            log.info("Infobip发送短信 - 接收方: {}, apiVersion={}", message.getTo(), apiVersion(config));

            if (isBlank(config.getApiKey())) {
                return failMapped(message, "E002", "Infobip apiKey 不能为空");
            }
            if (isBlank(message.getTo()) || isBlank(message.getContent())) {
                return failMapped(message, "E001", "to/content 不能为空");
            }

            String body = "v2".equals(apiVersion(config))
                    ? buildV2RequestBody(message, config)
                    : buildV3RequestBody(message, config);
            HttpResponse<String> response = sendHttpRequest(body, config);
            return parseResponse(message, response, config);
        } catch (Exception e) {
            log.error("Infobip短信发送失败 - 接收方: {}, 错误: {}", message.getTo(), e.getMessage(), e);
            return createFailedMessage(message, e.getMessage());
        }
    }

    @Override
    public String getSupportedProvider() {
        return "INFOBIP";
    }

    static String apiVersion(SmsProviderConfig config) {
        String v = config.getStringConfig("apiVersion", "v3");
        return "v2".equalsIgnoreCase(v) ? "v2" : "v3";
    }

    // ---------- 请求构建 ----------

    /** V3：sender + destinations[] + content.text + webhooks.delivery.url */
    private String buildV3RequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        JSONObject content = new JSONObject();
        content.put("text", message.getContent());

        JSONObject msg = new JSONObject();
        msg.put("sender", firstNonBlank(message.getFrom(), config.getSignName(),
                config.getDefaultFrom(), "Infobip"));
        msg.put("destinations", JSONArray.of(new JSONObject().fluentPut("to", message.getTo().trim())));
        msg.put("content", content);

        String webhook = resolveWebhook(message, config);
        if (!isBlank(webhook)) {
            JSONObject delivery = new JSONObject();
            delivery.put("url", webhook);
            JSONObject webhooks = new JSONObject();
            webhooks.put("delivery", delivery);
            webhooks.put("contentType", "application/json");
            msg.put("webhooks", webhooks);
        }

        JSONObject root = new JSONObject();
        root.put("messages", JSONArray.of(msg));
        return root.toJSONString();
    }

    /** V2（legacy 兜底）：from + to + text + notifyUrl */
    private String buildV2RequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        JSONObject msg = new JSONObject();
        msg.put("from", firstNonBlank(message.getFrom(), config.getSignName(),
                config.getDefaultFrom(), "Infobip"));
        msg.put("to", message.getTo().trim());
        msg.put("text", message.getContent());
        String webhook = resolveWebhook(message, config);
        if (!isBlank(webhook)) {
            msg.put("notifyUrl", webhook);
        }

        JSONObject root = new JSONObject();
        root.put("messages", JSONArray.of(msg));
        return root.toJSONString();
    }

    // ---------- HTTP ----------

    private HttpResponse<String> sendHttpRequest(String requestBody, SmsProviderConfig config) throws Exception {
        String base = firstNonBlank(config.getOutboundBaseUrl(), DEFAULT_ENDPOINT);
        String url = stripEndingSlash(base) + ("/v2".equals(apiVersion(config))
                ? "/sms/2/text/advanced" : "/sms/3/messages");

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs(config)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "SMS-SDK/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

        // 认证：apiSecret 非空 = 账号密码式 Basic；否则 API key 走官方推荐的 App scheme
        if (!isBlank(config.getApiSecret())) {
            String auth = config.getApiKey() + ":" + config.getApiSecret();
            req.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        } else {
            req.header("Authorization", "App " + config.getApiKey().trim());
        }

        log.debug("Infobip API请求 - URL: {}, Body: {}", url, requestBody);
        return httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ---------- 响应解析 ----------

    private SmsOutboundMessage parseResponse(SmsOutboundMessage message, HttpResponse<String> response,
                                             SmsProviderConfig config) {
        int code = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        log.debug("Infobip API响应 - 状态码: {}, 响应体: {}", code, body);

        JSONObject json = body.isBlank() ? new JSONObject() : JSON.parseObject(body);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        message.getExtraInfo().put("httpStatus", code);
        message.getExtraInfo().put("apiVersion", apiVersion(config));
        if (message.getRawData() == null) {
            message.setRawData(new HashMap<>());
        }
        message.getRawData().put("infobip_response", body);

        // 非 2xx：requestError（{requestError:{serviceException:{messageId,text}}}
        // 或 V3 纯错误对象 {messageId, text}）
        if (code < 200 || code >= 300) {
            JSONObject serviceException = json.getJSONObject("requestError") == null
                    ? null : json.getJSONObject("requestError").getJSONObject("serviceException");
            String errCode = firstNonBlank(
                    serviceException != null ? serviceException.getString("messageId") : null,
                    json.getString("messageId"), "HTTP_" + code);
            String errText = firstNonBlank(
                    serviceException != null ? serviceException.getString("text") : null,
                    json.getString("text"), body);
            return failMapped(message, errCode, errText);
        }

        JSONArray messages = json.getJSONArray("messages");
        if (messages == null || messages.isEmpty()) {
            return failMapped(message, "EMPTY_MESSAGES", "响应缺少 messages 数组: " + body);
        }
        JSONObject first = messages.getJSONObject(0);
        String messageId = first.getString("messageId");
        JSONObject status = first.getJSONObject("status");
        String groupName = status != null ? status.getString("groupName") : null;
        String statusName = status != null ? status.getString("name") : null;
        String description = status != null ? status.getString("description") : null;

        if (messageId != null && !messageId.isBlank()) {
            message.setProviderMessageId(messageId);
        }

        // groupId 5 = REJECTED；PENDING/ACCEPTED/DELIVERED 等均视为已提交，终态以回执为准
        if ("REJECTED".equalsIgnoreCase(groupName)) {
            return failMapped(message, statusName != null ? statusName : "REJECTED",
                    description != null ? description : "消息被拒绝");
        }

        message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
        message.setSentTime(LocalDateTime.now());
        message.setProviderType(SmsProviderType.INFOBIP);
        message.getExtraInfo().put("status", groupName);
        message.getExtraInfo().put("statusName", statusName);
        return message;
    }

    // ---------- 工具 ----------

    private SmsOutboundMessage failMapped(SmsOutboundMessage message, String code, String detail) {
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "infobip", code, detail);
        message.setProviderType(SmsProviderType.INFOBIP);
        return message;
    }

    private static String resolveWebhook(SmsOutboundMessage message, SmsProviderConfig config) {
        if (message.getSendConfig() != null && !isBlank(message.getSendConfig().getCallbackUrl())) {
            return message.getSendConfig().getCallbackUrl();
        }
        return firstNonBlank(config.getDeliveryReceiptUrl(), config.getNotifyUrl(),
                config.getCallbackUrl(), config.getStatusReportUrl());
    }

    private static String stripEndingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static long timeoutMs(SmsProviderConfig config) {
        if (config.getRequestTimeout() != null && config.getRequestTimeout() > 0) {
            return config.getRequestTimeout();
        }
        return DEFAULT_TIMEOUT_MS;
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
}
