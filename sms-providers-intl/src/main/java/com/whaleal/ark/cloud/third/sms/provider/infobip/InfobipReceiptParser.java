package com.whaleal.ark.cloud.third.sms.provider.infobip;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Infobip 回执解析器。
 *
 * <p>真实 DR webhook（V2 与 V3 {@code webhooks.delivery} 格式一致）：</p>
 * <pre>
 * {
 *   "results": [ {
 *     "messageId": "xxx",
 *     "to": "41793026727",
 *     "sentAt": "2026-01-01T12:00:00.000+0000",
 *     "doneAt": "2026-01-01T12:00:05.000+0000",
 *     "status": { "groupId": 3, "groupName": "DELIVERED",
 *                 "name": "DELIVERED_TO_HANDSET", "description": "..." },
 *     "error": { "groupId": 0, "name": "NO_ERROR", "description": "..." },
 *     "price": { "pricePerMessage": 0.01, "currency": "EUR" },
 *     "smsCount": 1
 *   } ]
 * }
 * </pre>
 * <p>兼容旧文档的扁平格式（status 为 "delivered"/"not_delivered" 字符串、
 * price/currency/smsCount 平铺在顶层）。</p>
 *
 * @author whaleal-dev
 * @since 1.0.0
 */
@Slf4j
public class InfobipReceiptParser implements ReceiptParser {

    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }

        // DR webhook：results 数组取第一条（平台单条发送场景恒为 1 条）
        Map<String, Object> data = rawData;
        Object results = rawData.get("results");
        if (results instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) first;
            data = m;
        }

        String messageId = str(data, "messageId");
        String to = str(data, "to");

        // status 可能是对象（groupName/name/description）或旧格式字符串
        String groupName = null;
        String name = null;
        String description = null;
        Object status = data.get("status");
        if (status instanceof Map<?, ?> sm) {
            groupName = strVal(sm.get("groupName"));
            name = strVal(sm.get("name"));
            description = strVal(sm.get("description"));
        } else if (status != null) {
            groupName = status.toString();
        }

        String errDesc = nestedDescription(data.get("error"));

        SmsReceipt.CostInfo costInfo = parseCostInfo(data);

        return SmsReceipt.builder()
                .receiptId(messageId)
                .messageId(messageId)
                .to(to)
                .receiptStatus(parseStatus(groupName))
                .receiptCode(name != null ? name : groupName)
                .receiptDescription(description != null ? description : errDesc)
                .errorCode(isFailed(groupName) ? nestedName(data.get("error")) : null)
                .errorDescription(errDesc)
                .deliveredTime(parseDateTime(str(data, "doneAt")))
                .receivedTime(LocalDateTime.now())
                .providerType(SmsProviderType.INFOBIP)
                .costInfo(costInfo)
                .rawData(rawData)
                .build();
    }

    /**
     * groupName → 统一回执状态。
     * Infobip 组名：PENDING / ACCEPTED / SENT / DELIVERED / NOT_DELIVERED / EXPIRED / REJECTED。
     */
    private SmsReceipt.ReceiptStatus parseStatus(String groupName) {
        if (groupName == null) {
            return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
        return switch (groupName.toUpperCase()) {
            case "DELIVERED" -> SmsReceipt.ReceiptStatus.DELIVERED;
            case "SENT" -> SmsReceipt.ReceiptStatus.SENT;
            case "ACCEPTED" -> SmsReceipt.ReceiptStatus.SENT;
            case "NOT_DELIVERED", "UNDELIVERABLE" -> SmsReceipt.ReceiptStatus.UNDELIVERABLE;
            case "EXPIRED" -> SmsReceipt.ReceiptStatus.EXPIRED;
            case "REJECTED" -> SmsReceipt.ReceiptStatus.REJECTED;
            case "PENDING" -> SmsReceipt.ReceiptStatus.UNKNOWN;
            default -> SmsReceipt.ReceiptStatus.UNKNOWN;
        };
    }

    private static boolean isFailed(String groupName) {
        return groupName != null && (groupName.equalsIgnoreCase("NOT_DELIVERED")
                || groupName.equalsIgnoreCase("EXPIRED") || groupName.equalsIgnoreCase("REJECTED"));
    }

    /** 旧文档扁平格式：doneAt = 2020-01-01T12:00:00.000+0000（无冒号时区偏移） */
    private LocalDateTime parseDateTime(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp).toLocalDateTime();
        } catch (Exception e) {
            try {
                // Infobip 实际回执 +0000（无冒号），ISO_OFFSET_DATE_TIME 不接受
                return OffsetDateTime.parse(timestamp,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]Z")).toLocalDateTime();
            } catch (Exception ex) {
                try {
                    return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (Exception ex2) {
                    log.warn("解析Infobip时间失败: {}", timestamp);
                    return null;
                }
            }
        }
    }

    /** price 对象 {pricePerMessage, currency} 或平铺 price/currency/smsCount */
    private SmsReceipt.CostInfo parseCostInfo(Map<String, Object> data) {
        String amount = null;
        String currency = null;
        Object price = data.get("price");
        if (price instanceof Map<?, ?> pm) {
            amount = strVal(pm.get("pricePerMessage"));
            currency = strVal(pm.get("currency"));
        } else if (price != null) {
            amount = price.toString();
            currency = str(data, "currency");
        }
        if (amount == null && currency == null) {
            return null;
        }
        return SmsReceipt.CostInfo.builder()
                .amount(amount)
                .currency(currency)
                .billingType("per_message")
                .messageCount(getInteger(data, "smsCount", 1))
                .build();
    }

    /** error 对象 → description */
    private static String nestedDescription(Object error) {
        if (error instanceof Map<?, ?> em) {
            return strVal(em.get("description")) != null ? strVal(em.get("description"))
                    : strVal(em.get("text"));
        }
        return error != null ? error.toString() : null;
    }

    /** error 对象 → name（如 NO_ERROR / INVALID_DESTINATION_ADDRESS） */
    private static String nestedName(Object error) {
        if (error instanceof Map<?, ?> em) {
            return strVal(em.get("name"));
        }
        return null;
    }

    private static String str(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private static String strVal(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Integer getInteger(Map<String, Object> data, String key, Integer defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.INFOBIP.name();
    }
}
