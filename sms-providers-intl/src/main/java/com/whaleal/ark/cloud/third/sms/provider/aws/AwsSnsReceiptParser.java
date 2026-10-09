package com.whaleal.ark.cloud.third.sms.provider.aws;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * AWS SNS 短信「投递状态」回执解析。
 *
 * <p>🔴 <b>2026-10-09 修</b>：本类此前 <b>无条件返回 {@code DELIVERED}</b>（一个字段都不看）。
 * 这与平台侧「{@code delivered}/{@code failed} 即终态闸门」叠加后后果不可恢复 ——
 * 投递失败的消息会被宣告为已送达，之后真实的失败回执被永久挡住。现改为 <b>fail-closed</b>：
 * 只有拿到明确的成功信号才判 {@code DELIVERED}，读不出状态一律 {@code UNKNOWN}
 * （宁可「匹配不到 + 留一行日志」，也绝不默认「已送达」）。</p>
 *
 * <p>AWS 的真实形状（CloudWatch Logs 里的 SNS 短信投递状态记录）：
 * <pre>
 * {"notification":{"messageId":"34d9b400-…","timestamp":"…"},
 *  "delivery":{"destination":"+1…","providerResponse":"…","priceInUSD":…},
 *  "status":"SUCCESS"}                                    // 失败时 status=FAILURE
 * </pre>
 * 两个容易踩的点：
 * <ol>
 *   <li>短信 id 在 <b>{@code notification.messageId}</b>，不是顶层。若客户用 SNS HTTP(S)
 *       订阅转发，最外层还会多一个信封的 {@code MessageId} —— 那是<b>通知</b>的 id，
 *       不是短信的 id，拿它当匹配键只会永远匹配不到（但这是「匹配不到」，不是「误判终态」，
 *       危害等级完全不同，所以仍然保留为兜底键）。</li>
 *   <li>本类<b>只认识投递状态记录本身</b>，<b>不解包</b>信封的 {@code Message} 字段 ——
 *       那取决于客户怎么接线（SNS HTTP 订阅是 JSON 字符串、CloudWatch Logs 订阅还会
 *       base64 + gzip）。在那里猜格式，等于把「硬编码 DELIVERED」换成「按猜的格式硬编码
 *       DELIVERED」，是同一个错误换张脸。</li>
 * </ol>
 * </p>
 */
public class AwsSnsReceiptParser implements ReceiptParser {

    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }

        String messageId = first(rawData, "notification.messageId", "MessageId", "messageId");
        String status = first(rawData, "status", "delivery.deliveryStatus", "MessageStatus");
        String failureType = first(rawData, "delivery.failureType", "failureType");
        String providerResponse = first(rawData, "delivery.providerResponse", "providerResponse");

        return SmsReceipt.builder()
                .receiptId(messageId)
                .messageId(messageId)
                .to(first(rawData, "delivery.destination", "destination", "phoneNumber"))
                .receiptStatus(parseStatus(status))
                .receiptCode(status)
                .errorCode(failureType)
                .errorDescription(providerResponse != null ? providerResponse : failureType)
                .receivedTime(LocalDateTime.now())
                .providerType(SmsProviderType.AWS)
                .rawData(rawData)
                .build();
    }

    /**
     * AWS 投递状态记录只有两个值：{@code SUCCESS} / {@code FAILURE}。
     *
     * <p>匹配不上就 {@code UNKNOWN} —— 这里<b>没有</b>默认分支给 {@code DELIVERED}，
     * 那是本类原先的缺陷。</p>
     */
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
        // 状态词是 ASCII：必须 Locale.ROOT（tr_TR 下 "SUCCESS".toLowerCase() 会变形）
        String s = status.toLowerCase(Locale.ROOT);
        if (s.contains("fail")) {
            // 运营商投递失败（号码无效 / 被屏蔽 / 退订 / 超价…具体原因在 delivery.providerResponse）
            return SmsReceipt.ReceiptStatus.UNDELIVERABLE;
        }
        if (s.contains("success") || s.equals("delivered")) {
            return SmsReceipt.ReceiptStatus.DELIVERED;
        }
        return SmsReceipt.ReceiptStatus.UNKNOWN;
    }

    /** 取第一个非空值；键支持一层 {@code a.b} 嵌套（AWS 的 delivery/notification 都是对象） */
    private static String first(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            int dot = key.indexOf('.');
            Object value;
            if (dot > 0) {
                Object nested = data.get(key.substring(0, dot));
                value = nested instanceof Map<?, ?> map ? map.get(key.substring(dot + 1)) : null;
            } else {
                value = data.get(key);
            }
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.AWS.name();
    }
}
