package com.whaleal.ark.cloud.third.sms.provider.vonage;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Vonage 上行解析：兼容旧 SMS inbound 与 Messages API v1 inbound。
 *
 * @author whaleal-dev
 * @author 恒哥
 */
public class VonageInboundParser implements InboundParser {

    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        String content = first(rawData, "text", "message_content");
        if (content == null && rawData.get("message") instanceof Map<?, ?> msg) {
            Object t = msg.get("content");
            if (t == null) {
                t = msg.get("text");
            }
            content = t == null ? null : t.toString();
        }
        return SmsInboundMessage.builder()
                .messageId(first(rawData, "message_uuid", "messageId", "message-id"))
                .from(first(rawData, "from", "msisdn"))
                .to(first(rawData, "to"))
                .content(content)
                .messageType(SmsInboundMessage.MessageType.TEXT)
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }

    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && !rawData.isEmpty()
                && (rawData.containsKey("text")
                || rawData.containsKey("msisdn")
                || rawData.containsKey("from")
                || rawData.containsKey("message_uuid"));
    }

    private static String first(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.VONAGE.name();
    }
}
