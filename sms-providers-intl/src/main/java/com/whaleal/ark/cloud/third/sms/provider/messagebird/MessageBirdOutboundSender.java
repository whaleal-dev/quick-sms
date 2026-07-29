package com.whaleal.ark.cloud.third.sms.provider.messagebird;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * MessageBird短信发送器
 *
 */
@Slf4j
public class MessageBirdOutboundSender implements OutboundSender {

    private static final int TIMEOUT_MS = 10000; // 10秒超时

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        try {
            log.info("开始发送MessageBird短信 - 接收方: {}", message.getTo());

            // 参数验证
            validateConfig(config);
            validateMessage(message);

            // 更新消息状态为发送中
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setSentTime(LocalDateTime.now());

            // 构建请求
            String requestBody = buildRequestBody(message, config);

            // 发送HTTP请求
            String response = sendHttpRequest(requestBody, config);

            // 解析响应
            return parseResponse(response, message);

        } catch (Exception e) {
            log.error("MessageBird短信发送失败 - 接收方: {}, 错误: {}",
                    message.getTo(), e.getMessage(), e);

            return createFailedMessage(message, e.getMessage());
        }
    }

    @Override
    public String getSupportedProvider() {
        return "MESSAGEBIRD";
    }

    /**
     * 验证配置参数
     */
    private void validateConfig(SmsProviderConfig config) {
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("MessageBird API Key不能为空");
        }
    }

    /**
     * 验证消息参数
     */
    private void validateMessage(SmsOutboundMessage message) {
        if (message.getTo() == null || message.getTo().trim().isEmpty()) {
            throw new IllegalArgumentException("接收号码不能为空");
        }
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("短信内容不能为空");
        }
    }

    /**
     * 构建请求体
     */
    private String buildRequestBody(SmsOutboundMessage message, SmsProviderConfig config) {
        Map<String, Object> params = new HashMap<>();

        // 基本参数
        params.put("recipients", message.getTo());
        params.put("body", message.getContent());

        // 发送方设置
        if (config.getSignName() != null && !config.getSignName().trim().isEmpty()) {
            params.put("originator", config.getSignName());
        } else if (message.getFrom() != null && !message.getFrom().trim().isEmpty()) {
            params.put("originator", message.getFrom());
        } else {
            params.put("originator", "MessageBird");
        }

        // 添加编码类型参数
        // MessageBird支持datacoding: plain(GSM-7), unicode(UCS-2), auto(自动检测)
        String encoding = message.getEncoding();
        if (encoding != null) {
            if ("unicode".equalsIgnoreCase(encoding) || "ucs2".equalsIgnoreCase(encoding)) {
                params.put("datacoding", "unicode");
            } else if ("text".equalsIgnoreCase(encoding) || "gsm7".equalsIgnoreCase(encoding)) {
                params.put("datacoding", "plain");
            } else {
                params.put("datacoding", "auto");
            }
        } else {
            // 默认使用auto，让MessageBird自动检测
            params.put("datacoding", "auto");
        }

        // 可选参数
        if (message.getSendConfig() != null) {
            // 状态报告URL
            if (message.getSendConfig().getCallbackUrl() != null) {
                params.put("reportUrl", message.getSendConfig().getCallbackUrl());
            }

            // 有效期设置
            if (message.getSendConfig().getValidityPeriod() != null) {
                params.put("validity", message.getSendConfig().getValidityPeriod());
            }
        }

        return formatToJson(params);
    }

    /**
     * 格式化为JSON字符串
     */
    private String formatToJson(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) sb.append(",");

            sb.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Number) {
                sb.append(value);
            } else {
                sb.append("\"").append(value.toString()).append("\"");
            }

            first = false;
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 发送HTTP请求
     */
    private String sendHttpRequest(String requestBody, SmsProviderConfig config) throws Exception {
        String apiUrl = config.getOutboundBaseUrl() != null ?
            config.getOutboundBaseUrl() + "/messages" : "https://rest.messagebird.com/messages";

        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            // 设置请求属性
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "AccessKey " + config.getApiKey());
            connection.setRequestProperty("User-Agent", "SMS-SDK/1.0");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            // 发送请求体
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // 读取响应
            int responseCode = connection.getResponseCode();
            StringBuilder response = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode == 200 || responseCode == 201 ?
                    connection.getInputStream() : connection.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            if (responseCode != 200 && responseCode != 201) {
                throw new RuntimeException("HTTP请求失败，状态码: " + responseCode + ", 响应: " + response.toString());
            }

            return response.toString();

        } finally {
            connection.disconnect();
        }
    }

    /**
     * 解析响应结果
     */
    private SmsOutboundMessage parseResponse(String response, SmsOutboundMessage message) {
        try {
            log.debug("MessageBird API响应: {}", response);

            // 检查是否包含错误
            if (response.contains("\"errors\":[") && !response.contains("\"errors\":[]")) {
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                String errorMessage = extractJsonValue(response, "description");
                if (errorMessage == null) {
                    errorMessage = "MessageBird API返回错误";
                }

                if (message.getExtraInfo() == null) {
                    message.setExtraInfo(new HashMap<>());
                }
                message.getExtraInfo().put("error", errorMessage);

                return message;
            }

            // 成功情况
            if (response.contains("\"id\":")) {
                message.setSendStatus(SmsOutboundMessage.SendStatus.SENT);

                // 提取消息ID
                String messageId = extractJsonValue(response, "id");
                if (messageId != null) {
                    message.setProviderMessageId(messageId);
                }

                // 提取发送方信息
                String originator = extractJsonValue(response, "originator");
                if (originator != null) {
                    message.setFrom(originator);
                }

                // 设置预估送达时间
                message.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(2));

            } else {
                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                if (message.getExtraInfo() == null) {
                    message.setExtraInfo(new HashMap<>());
                }
                message.getExtraInfo().put("error", "未知的响应格式");
            }

            // 保存原始响应
            if (message.getRawData() == null) {
                message.setRawData(new HashMap<>());
            }
            message.getRawData().put("messagebird_response", response);

            return message;

        } catch (Exception e) {
            log.error("MessageBird响应解析失败: {}", e.getMessage(), e);
            return createFailedMessage(message, "响应解析失败: " + e.getMessage());
        }
    }

    /**
     * 创建失败消息
     */
    public SmsOutboundMessage createFailedMessage(SmsOutboundMessage originalMessage, String errorMessage) {
        originalMessage.setMessageId(UUID.randomUUID().toString());
        originalMessage.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        originalMessage.setSentTime(LocalDateTime.now());

        if (originalMessage.getExtraInfo() == null) {
            originalMessage.setExtraInfo(new HashMap<>());
        }
        originalMessage.getExtraInfo().put("error", "MessageBird发送失败: " + errorMessage);

        return originalMessage;
    }

    /**
     * 从JSON字符串中提取值（简单实现）
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            // 尝试数字值
            searchKey = "\"" + key + "\":";
            startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return null;

            startIndex += searchKey.length();
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) {
                endIndex = json.indexOf("}", startIndex);
            }

            if (endIndex > startIndex) {
                return json.substring(startIndex, endIndex).replace("\"", "").trim();
            }
        } else {
            startIndex += searchKey.length();
            int endIndex = json.indexOf("\"", startIndex);

            if (endIndex > startIndex) {
                return json.substring(startIndex, endIndex);
            }
        }

        return null;
    }
}
