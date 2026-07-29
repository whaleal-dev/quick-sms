package com.whaleal.ark.cloud.third.sms.provider.vonage;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Vonage 回执解析：兼容旧 SMS API DLR 与 Messages API v1 status webhook。
 *
 * @author whaleal-dev
 * @author 恒哥
 */
@Slf4j
public class VonageReceiptParser implements ReceiptParser {

    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            throw new IllegalArgumentException("无效的Vonage回执数据");
        }

        String messageId = first(rawData,
                "message_uuid", "messageId", "message-id", "message_id");
        String to = first(rawData, "to", "msisdn");
        String status = first(rawData, "status", "message_status");
        String err = first(rawData, "error", "err-code", "error-code", "detail");

        return SmsReceipt.builder()
                .receiptId(messageId)
                .messageId(messageId)
                .to(to)
                .receiptStatus(parseStatus(status))
                .receiptCode(status)
                .receiptDescription(err)
                .errorCode(err)
                .deliveredTime(parseDateTime(first(rawData,
                        "timestamp", "message-timestamp", "usage.price")))
                .receivedTime(LocalDateTime.now())
                .providerType(SmsProviderType.VONAGE)
                .costInfo(SmsReceipt.CostInfo.builder()
                        .amount(first(rawData, "price", "usage.price"))
                        .currency(first(rawData, "currency", "usage.currency"))
                        .billingType("per_message")
                        .messageCount(1)
                        .build())
                .rawData(rawData)
                .build();
    }

    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) {
            return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
        return switch (status.toLowerCase()) {
            case "delivered", "submitted", "accepted" -> SmsReceipt.ReceiptStatus.DELIVERED;
            case "failed", "rejected", "undeliverable" ->
                    "rejected".equalsIgnoreCase(status)
                            ? SmsReceipt.ReceiptStatus.REJECTED
                            : "undeliverable".equalsIgnoreCase(status)
                            ? SmsReceipt.ReceiptStatus.UNDELIVERABLE
                            : SmsReceipt.ReceiptStatus.FAILED;
            case "expired" -> SmsReceipt.ReceiptStatus.EXPIRED;
            case "read", "seen" -> SmsReceipt.ReceiptStatus.DELIVERED;
            default -> SmsReceipt.ReceiptStatus.UNKNOWN;
        };
    }

    private LocalDateTime parseDateTime(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            if (timestamp.contains("T")) {
                return LocalDateTime.parse(timestamp.replace("Z", ""),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("解析Vonage时间失败: {}", timestamp);
            return null;
        }
    }

    private static String first(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            if (key.contains(".")) {
                // 简单支持 usage.price 一层嵌套
                String[] parts = key.split("\\.", 2);
                Object nested = data.get(parts[0]);
                if (nested instanceof Map<?, ?> map) {
                    Object v = map.get(parts[1]);
                    if (v != null) {
                        return v.toString();
                    }
                }
                continue;
            }
            Object value = data.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && !rawData.isEmpty()
                && (rawData.containsKey("status")
                || rawData.containsKey("message_uuid")
                || rawData.containsKey("messageId")
                || rawData.containsKey("message-id"));
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.VONAGE.name();
    }
}
