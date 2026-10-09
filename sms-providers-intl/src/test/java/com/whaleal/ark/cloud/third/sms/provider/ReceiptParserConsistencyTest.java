package com.whaleal.ark.cloud.third.sms.provider;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.provider.aws.AwsSnsReceiptParser;
import com.whaleal.ark.cloud.third.sms.provider.infobip.InfobipReceiptParser;
import com.whaleal.ark.cloud.third.sms.provider.twilio.TwilioReceiptParser;
import com.whaleal.ark.cloud.third.sms.provider.vonage.VonageReceiptParser;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 回执解析器一致性测试：载荷取自厂商真实 DR webhook 文档格式。
 */
class ReceiptParserConsistencyTest {

    private final InfobipReceiptParser infobip = new InfobipReceiptParser();
    private final VonageReceiptParser vonage = new VonageReceiptParser();
    private final TwilioReceiptParser twilio = new TwilioReceiptParser();
    private final AwsSnsReceiptParser aws = new AwsSnsReceiptParser();
    private final SmsProviderConfig config = new SmsProviderConfig();

    @Test
    void infobipDrWebhookResultsArray() {
        // Infobip DR webhook：results 数组 + status 对象（groupName）
        String payload = """
                {"results":[{"messageId":"9304qwerre3ddffshdaaaa",
                  "to":"41793026727","sentAt":"2026-01-01T12:00:00.000+0000",
                  "doneAt":"2026-01-01T12:00:05.123+0000",
                  "status":{"groupId":3,"groupName":"DELIVERED","id":5,
                            "name":"DELIVERED_TO_HANDSET","description":"Message delivered to handset"},
                  "error":{"groupId":0,"groupName":"OK","id":0,"name":"NO_ERROR","description":"No Error",
                           "permanent":false},
                  "price":{"pricePerMessage":0.01,"currency":"EUR"},"smsCount":1}]}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = JSON.parseObject(payload, Map.class);
        SmsReceipt r = infobip.parse(raw, config);

        assertNotNull(r);
        assertEquals("9304qwerre3ddffshdaaaa", r.getMessageId());
        assertEquals("41793026727", r.getTo());
        assertEquals(SmsReceipt.ReceiptStatus.DELIVERED, r.getReceiptStatus());
        assertEquals("DELIVERED_TO_HANDSET", r.getReceiptCode());
        assertNotNull(r.getDeliveredTime());
        assertEquals("0.01", r.getCostInfo().getAmount());
        assertEquals("EUR", r.getCostInfo().getCurrency());
    }

    @Test
    void infobipFailedAndLegacyFlatFormat() {
        // 失败回执（status 对象 groupName=REJECTED）
        String failed = """
                {"results":[{"messageId":"m-failed","to":"41793026727",
                  "status":{"groupId":6,"groupName":"REJECTED","name":"INVALID_DESTINATION_ADDRESS",
                            "description":"Invalid destination address"},
                  "error":{"groupId":10,"name":"INVALID_DESTINATION_ADDRESS","description":"bad number"}}]}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = JSON.parseObject(failed, Map.class);
        SmsReceipt r = infobip.parse(raw, config);
        assertEquals(SmsReceipt.ReceiptStatus.REJECTED, r.getReceiptStatus());
        assertEquals("INVALID_DESTINATION_ADDRESS", r.getErrorCode());

        // 旧文档扁平格式：status 为字符串 + price/currency 平铺
        String flat = """
                {"messageId":"m-flat","to":"41793026727","status":"delivered",
                 "doneAt":"2026-01-01T12:00:05.123+0000","price":"0.01","currency":"USD","smsCount":2}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw2 = JSON.parseObject(flat, Map.class);
        SmsReceipt r2 = infobip.parse(raw2, config);
        assertEquals(SmsReceipt.ReceiptStatus.DELIVERED, r2.getReceiptStatus());
        assertEquals(2, r2.getCostInfo().getMessageCount());
    }

    @Test
    void vonageMessagesApiV1Receipt() {
        // Messages API v1 DR：无 status 字段，type=message.delivered，error 为对象
        String payload = """
                {"message_uuid":"aaaaaaaa-bbbb-cccc-dddd-0123456789ab",
                 "to":"447700900000","from":"Whaleal",
                 "timestamp":"2026-01-01T14:00:00.000Z","text":"hi",
                 "type":"message.delivered"}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = JSON.parseObject(payload, Map.class);
        SmsReceipt r = vonage.parse(raw, config);
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-0123456789ab", r.getMessageId());
        assertEquals(SmsReceipt.ReceiptStatus.DELIVERED, r.getReceiptStatus());
    }

    @Test
    void vonageFailedAndLegacy() {
        // Messages API v1 失败：type=message.undeliverable + error 对象
        String failed = """
                {"message_uuid":"u-2","to":"447700900001",
                 "timestamp":"2026-01-01T14:00:01.000Z",
                 "type":"message.undeliverable",
                 "error":{"type":"https://developer.nexmo.com/api/errors/messages/sms","title":"Undeliverable"}}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = JSON.parseObject(failed, Map.class);
        SmsReceipt r = vonage.parse(raw, config);
        assertEquals(SmsReceipt.ReceiptStatus.UNDELIVERABLE, r.getReceiptStatus());
        assertEquals("Undeliverable", r.getErrorCode());

        // 旧 SMS API DLR：平铺 status / err-code / message-timestamp
        String legacy = """
                {"msisdn":"447700900000","to":"Whaleal","network-code":"23410",
                 "messageId":"0A0000000123ABCD","price":"0.03330000","status":"delivered",
                 "err-code":"0","message-timestamp":"2026-01-01 14:00:02"}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw2 = JSON.parseObject(legacy, Map.class);
        SmsReceipt r2 = vonage.parse(raw2, config);
        assertEquals(SmsReceipt.ReceiptStatus.DELIVERED, r2.getReceiptStatus());
        assertEquals("0A0000000123ABCD", r2.getMessageId());
    }

    @Test
    void vonageFlatSubmittedIsNotDelivered() {
        // 生产实录（2026-10-09 04:19:47 UTC）：Vonage 在短信发出 0.67s 后就推了这条
        // status=submitted 的回调，当时被映射成 DELIVERED ⇒ 每条短信被提前宣告「已送达」，
        // 且平台终态闸门永久挡住后续真实的 rejected/undeliverable。
        // submitted 是 Vonage SMS 生命周期的正常首条回调（accepted → submitted → delivered）。
        String payload = """
                {"message_uuid":"7cf2e7b6-3473-431e-a96b-dea3328c3fb7",
                 "to":"8613391115462","from":"Whaleal",
                 "timestamp":"2026-10-09T04:19:47.000Z",
                 "status":"submitted",
                 "usage":{"price":"0.0333","currency":"EUR"}}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = JSON.parseObject(payload, Map.class);
        SmsReceipt r = vonage.parse(raw, config);

        assertEquals("7cf2e7b6-3473-431e-a96b-dea3328c3fb7", r.getMessageId());
        assertEquals("8613391115462", r.getTo());
        assertEquals(SmsReceipt.ReceiptStatus.SENT, r.getReceiptStatus());
        // 原始状态必须原样保留：平台侧靠它落库 rawStatus，是事后唯一能看出
        // 「解析结果与供应商原话不一致」的证据。
        assertEquals("submitted", r.getReceiptCode());
    }

    @Test
    void vonageFlatAndTypedBranchesAgreeOnTheSameWord() {
        // 同一语义、两种 key 形状（旧 SMS API DLR 的扁平 status vs Messages API v1 的
        // type=message.xxx）必须给出同一个枚举值。2026-10-09 的生产事故正是这两条分支
        // 对 submitted/accepted 的口径不一致造成的——扁平分支把它们当终态，typed 分支当中间态。
        for (String word : List.of("delivered", "accepted", "submitted",
                "rejected", "undeliverable", "expired")) {
            SmsReceipt flat = vonage.parse(Map.of("message_uuid", "u-1", "status", word), config);
            SmsReceipt typed = vonage.parse(Map.of("message_uuid", "u-1", "type", "message." + word), config);
            assertEquals(typed.getReceiptStatus(), flat.getReceiptStatus(),
                    "扁平 status=" + word + " 与 type=message." + word + " 的映射必须一致");
        }
    }

    @Test
    void vonageStatusParsingIsLocaleIndependent() {
        // 状态词是 ASCII：默认 locale 为 tr_TR 时 "SUBMITTED".toLowerCase() 得到
        // "submıtted"（无点 i），所有 case 落空 ⇒ 静默退化成 UNKNOWN。必须走 Locale.ROOT。
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            SmsReceipt r = vonage.parse(Map.of("message_uuid", "u-2", "status", "SUBMITTED"), config);
            assertEquals(SmsReceipt.ReceiptStatus.SENT, r.getReceiptStatus());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void twilioFormCallback() {
        // Twilio status callback：form 参数（WebhookController 已解为 Map）
        Map<String, Object> raw = Map.of(
                "MessageSid", "SM8f10c9c24c9f4beab57be35d1e2f8d1f",
                "MessageStatus", "delivered",
                "To", "+14155550123",
                "From", "+14155550100");
        SmsReceipt r = twilio.parse(raw, config);
        assertEquals("SM8f10c9c24c9f4beab57be35d1e2f8d1f", r.getMessageId());
        assertEquals(SmsReceipt.ReceiptStatus.DELIVERED, r.getReceiptStatus());
        assertNotNull(r.getProviderType());
    }

    @Test
    void awsSnsDeliveryStatusRecord() {
        // AWS 官方文档形状（CloudWatch Logs 的 SNS 短信投递状态记录）：
        // 短信 id 在 notification.messageId（不是顶层），状态是顶层 status=SUCCESS/FAILURE
        String success = """
                {"notification":{"messageId":"34d9b400-c6dd-5444-820d-fbeb0f1f54cf",
                                 "timestamp":"2016-06-28 00:40:34.558"},
                 "delivery":{"destination":"+14155550123","phoneCarrier":"My Phone Carrier",
                             "providerResponse":"Message has been accepted by phone carrier",
                             "priceInUSD":0.00645,"dwellTimeMs":599},
                 "status":"SUCCESS"}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = JSON.parseObject(success, Map.class);
        SmsReceipt ok = aws.parse(raw, config);
        assertEquals("34d9b400-c6dd-5444-820d-fbeb0f1f54cf", ok.getMessageId());
        assertEquals("+14155550123", ok.getTo());
        assertEquals(SmsReceipt.ReceiptStatus.DELIVERED, ok.getReceiptStatus());
        assertEquals("SUCCESS", ok.getReceiptCode());

        String failure = """
                {"notification":{"messageId":"1077257a-92f3-5ca3-bc97-6a915b310625",
                                 "timestamp":"2016-06-28 00:40:34.559"},
                 "delivery":{"destination":"+14155550123","mnc":0,"mcc":0,
                             "providerResponse":"Unknown error attempting to reach phone",
                             "dwellTimeMs":1420},
                 "status":"FAILURE"}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> raw2 = JSON.parseObject(failure, Map.class);
        SmsReceipt bad = aws.parse(raw2, config);
        assertEquals("1077257a-92f3-5ca3-bc97-6a915b310625", bad.getMessageId());
        assertEquals(SmsReceipt.ReceiptStatus.UNDELIVERABLE, bad.getReceiptStatus());
        assertEquals("Unknown error attempting to reach phone", bad.getErrorDescription());
    }

    @Test
    void awsNeverClaimsDeliveredWithoutASuccessSignal() {
        // 🔴 回归（本类此前的缺陷：**无条件**返回 DELIVERED，一个字段都不看）。
        // SNS HTTP(S) 订阅转发时，最外层是信封（Type/MessageId/Message…），真正的状态在
        // Message 字符串里。本类**不解包**（那取决于客户怎么接线），所以必须判 UNKNOWN ——
        // 关键是「不得再默认已送达」。
        Map<String, Object> envelope = Map.of(
                "Type", "Notification",
                "MessageId", "e0b1a2c3-envelope-id",
                "TopicArn", "arn:aws:sns:us-east-1:1111111111:sms-delivery",
                "Message", "{\"notification\":{\"messageId\":\"x\"},\"status\":\"FAILURE\"}");
        assertEquals(SmsReceipt.ReceiptStatus.UNKNOWN, aws.parse(envelope, config).getReceiptStatus());

        // 只有 id、没有状态 —— 也不得判已送达
        assertEquals(SmsReceipt.ReceiptStatus.UNKNOWN,
                aws.parse(Map.of("MessageId", "only-an-id"), config).getReceiptStatus());

        // 空/无数据：返回 null，不产出任何回执
        assertNull(aws.parse(null, config));
        assertNull(aws.parse(Map.of(), config));
    }

    @Test
    void awsStatusWordsAreLocaleIndependent() {
        // 状态词是 ASCII，必须走 Locale.ROOT（tr_TR 下 "SUCCESS".toLowerCase() 会变形）
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(SmsReceipt.ReceiptStatus.DELIVERED,
                    aws.parse(Map.of("status", "SUCCESS"), config).getReceiptStatus());
            assertEquals(SmsReceipt.ReceiptStatus.UNDELIVERABLE,
                    aws.parse(Map.of("status", "FAILURE"), config).getReceiptStatus());
        } finally {
            Locale.setDefault(original);
        }
    }
}
