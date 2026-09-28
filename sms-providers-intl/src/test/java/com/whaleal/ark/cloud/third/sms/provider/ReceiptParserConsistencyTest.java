package com.whaleal.ark.cloud.third.sms.provider;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.provider.infobip.InfobipReceiptParser;
import com.whaleal.ark.cloud.third.sms.provider.twilio.TwilioReceiptParser;
import com.whaleal.ark.cloud.third.sms.provider.vonage.VonageReceiptParser;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回执解析器一致性测试：载荷取自厂商真实 DR webhook 文档格式。
 */
class ReceiptParserConsistencyTest {

    private final InfobipReceiptParser infobip = new InfobipReceiptParser();
    private final VonageReceiptParser vonage = new VonageReceiptParser();
    private final TwilioReceiptParser twilio = new TwilioReceiptParser();
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
}
