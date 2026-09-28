package com.whaleal.ark.cloud.third.sms.provider.twilio;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.error.ProviderErrorMapper;
import com.whaleal.ark.cloud.third.sms.exception.SmsException;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Twilio 下行发送器。
 *
 * <p>使用 Programmable Messaging REST API（{@code POST {base}/2010-04-01/Accounts/{AccountSid}/Messages.json}），
 * 该版本为 Twilio 当前稳定版本；Basic 认证（API Key SID + Secret 或 Account SID + AuthToken）。</p>
 * <p>{@code config.baseUrl} 可覆盖默认 {@code https://api.twilio.com}（区域端点如
 * {@code https://api.dublin.ie1.twilio.com}）。</p>
 *
 * @author whaleal-dev
 * @author 恒哥
 * @since 1.0.0
 */
@Slf4j
public class TwilioOutboundSender implements OutboundSender {

    private static final String DEFAULT_BASE_URL = "https://api.twilio.com";
    private static final String API_VERSION_PATH = "/2010-04-01";
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final HttpClient httpClient;

    public TwilioOutboundSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) throws SmsException {
        try {
            log.info("Twilio发送短信 - 接收方: {}, 内容长度: {}", message.getTo(),
                    message.getContent() != null ? message.getContent().length() : 0);

            // 构建请求参数
            String requestBody = buildRequestBody(message, config);

            // 发送HTTP请求
            HttpResponse<String> response = sendHttpRequest(requestBody, config);

            // 解析响应
            return parseResponse(message, response);

        } catch (Exception e) {
            log.error("Twilio发送短信失败，接收方: {}, 错误: {}", message.getTo(), e.getMessage(), e);
            return createFailedMessage(message, e.getMessage());
        }
    }

    @Override
    public List<SmsOutboundMessage> sendMessages(List<SmsOutboundMessage> messages, SmsProviderConfig config) throws SmsException {
        log.info("Twilio批量发送短信 - 数量: {}", messages.size());

        // Twilio不支持批量发送，逐个发送
        return messages.stream()
                .map(msg -> {
                    try {
                        return sendMessage(msg, config);
                    } catch (SmsException e) {
                        return createFailedMessage(msg, e.getMessage());
                    }
                })
                .toList();
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) throws SmsException {
        log.info("Twilio发送模板短信 - 模板ID: {}",
                message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : "未知");

        // Twilio使用普通短信方式发送模板内容
        // 模板参数应该已经在业务层替换完成
        return sendMessage(message, config);
    }

    /**
     * 构建请求体
     */
    private String buildRequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        Map<String, String> params = new HashMap<>();
        params.put("From", message.getFrom() != null ? message.getFrom() : config.getDefaultFrom());
        params.put("To", message.getTo());
        params.put("Body", message.getContent());

        // 添加可选参数
        if (message.getSendConfig() != null) {
            // 设置回调URL
            if (message.getSendConfig().getCallbackUrl() != null) {
                params.put("StatusCallback", message.getSendConfig().getCallbackUrl());
            }

            // 设置有效期
            if (message.getSendConfig().getValidityPeriod() != null) {
                params.put("ValidityPeriod", message.getSendConfig().getValidityPeriod().toString());
            }
        }

        // 构建form-urlencoded格式
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(urlEncode(entry.getKey())).append("=").append(urlEncode(entry.getValue()));
        }

        return sb.toString();
    }

    /**
     * 发送HTTP请求
     */
    private HttpResponse<String> sendHttpRequest(String requestBody, SmsProviderConfig config) throws IOException, InterruptedException {
        // 构建API URL（baseUrl 可覆盖，支持 Twilio 区域端点）
        String accountSid = config.getApiKey(); // Twilio使用ApiKey作为AccountSid
        String base = stripEndingSlash(firstNonBlank(config.getOutboundBaseUrl(), DEFAULT_BASE_URL));
        String url = base + API_VERSION_PATH + "/Accounts/" + accountSid + "/Messages.json";

        // 构建认证头
        String auth = accountSid + ":" + config.getApiSecret();
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        // 构建请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", "SMS-SDK/1.0")
                .timeout(Duration.ofMillis(timeoutMs(config)))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        log.debug("Twilio API请求 - URL: {}, Body: {}", url, requestBody);

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 解析响应
     */
    private SmsOutboundMessage parseResponse(SmsOutboundMessage originalMessage, HttpResponse<String> response) {
        String responseBody = response.body();
        int statusCode = response.statusCode();

        log.debug("Twilio API响应 - 状态码: {}, 响应体: {}", statusCode, responseBody);

        if (statusCode >= 200 && statusCode < 300) {
            // 解析成功响应
            return parseSuccessResponse(originalMessage, responseBody);
        } else {
            // 解析错误响应
            return parseErrorResponse(originalMessage, responseBody, statusCode);
        }
    }

    /**
     * 解析成功响应（fastjson2，替代原正则解析）
     */
    private SmsOutboundMessage parseSuccessResponse(SmsOutboundMessage originalMessage, String responseBody) {
        try {
            JSONObject json = JSON.parseObject(responseBody);
            String messageSid = json.getString("sid");
            String status = json.getString("status");
            String price = json.getString("price");
            String priceUnit = json.getString("price_unit");

            // 设置消息ID
            if (originalMessage.getMessageId() == null) {
                originalMessage.setMessageId(UUID.randomUUID().toString());
            }
            originalMessage.setProviderMessageId(messageSid);

            // 设置发送状态
            originalMessage.setSendStatus(mapTwilioStatus(status));
            originalMessage.setSentTime(LocalDateTime.now());
            originalMessage.setProviderType(SmsProviderType.TWILIO);

            // 设置费用信息
            if (price != null && !price.equals("null")) {
                originalMessage.setCostInfo(SmsOutboundMessage.CostInfo.builder()
                        .amount(price)
                        .currency(priceUnit != null ? priceUnit : "USD")
                        .billingType("per_message")
                        .messageCount(1)
                        .unitPrice(price)
                        .billingTime(LocalDateTime.now())
                        .build());
            }

            // 设置扩展信息
            if (originalMessage.getExtraInfo() == null) {
                originalMessage.setExtraInfo(new HashMap<>());
            }
            originalMessage.getExtraInfo().put("twilio_sid", messageSid);
            originalMessage.getExtraInfo().put("twilio_status", status);
            originalMessage.getExtraInfo().put("api_response", responseBody);

            log.info("Twilio发送成功 - MessageSid: {}, Status: {}", messageSid, status);

            return originalMessage;

        } catch (Exception e) {
            log.error("解析Twilio成功响应失败: {}", e.getMessage(), e);
            return createFailedMessage(originalMessage, "响应解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析错误响应（fastjson2 + 统一错误映射）
     */
    private SmsOutboundMessage parseErrorResponse(SmsOutboundMessage originalMessage, String responseBody, int statusCode) {
        try {
            JSONObject json = JSON.parseObject(responseBody);
            String errorCode = json.getString("code");
            String errorMessage = json.getString("message");

            log.error("Twilio发送失败 - 状态码: {}, 错误代码: {}, 错误信息: {}", statusCode, errorCode, errorMessage);

            originalMessage.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            originalMessage.setProviderType(SmsProviderType.TWILIO);

            if (originalMessage.getExtraInfo() == null) {
                originalMessage.setExtraInfo(new HashMap<>());
            }
            originalMessage.getExtraInfo().put("http_status", statusCode);
            originalMessage.getExtraInfo().put("api_response", responseBody);
            ProviderErrorMapper.putMapped(originalMessage.getExtraInfo(), "twilio",
                    errorCode != null ? errorCode : "HTTP_" + statusCode,
                    errorMessage != null ? errorMessage : responseBody);

            return originalMessage;

        } catch (Exception e) {
            log.error("解析Twilio错误响应失败: {}", e.getMessage(), e);
            return createFailedMessage(originalMessage, "错误响应解析失败: " + e.getMessage());
        }
    }

    /**
     * 映射Twilio状态到内部状态
     */
    private SmsOutboundMessage.SendStatus mapTwilioStatus(String twilioStatus) {
        if (twilioStatus == null) {
            return SmsOutboundMessage.SendStatus.FAILED;
        }

        switch (twilioStatus.toLowerCase()) {
            case "queued":
            case "accepted":
            case "sending":
            case "scheduled":
                return SmsOutboundMessage.SendStatus.SUBMITTED;
            case "sent":
            case "delivered":
                return SmsOutboundMessage.SendStatus.SUBMITTED;
            case "failed":
            case "undelivered":
            case "canceled":
                return SmsOutboundMessage.SendStatus.FAILED;
            default:
                return SmsOutboundMessage.SendStatus.FAILED;
        }
    }

    /**
     * URL编码
     */
    private String urlEncode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private static String stripEndingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return null;
    }

    private static long timeoutMs(SmsProviderConfig config) {
        if (config.getRequestTimeout() != null && config.getRequestTimeout() > 0) {
            return config.getRequestTimeout();
        }
        return DEFAULT_TIMEOUT_MS;
    }

    @Override
    public String getSupportedProvider() {
        return "TWILIO";
    }
}