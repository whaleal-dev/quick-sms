package com.whaleal.ark.cloud.third.sms.provider.qiniu;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 七牛云上行解析器。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class QiniuInboundParser implements InboundParser {

    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            return null;
        }
        return SmsInboundMessage.builder()
                .from(first(rawData, new String[]{"mobile", "from"}))
                .to(first(rawData, new String[]{"to", "dest"}))
                .content(first(rawData, new String[]{"content", "text"}))
                .messageType(SmsInboundMessage.MessageType.TEXT)
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }

    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && !rawData.isEmpty()
                && first(rawData, new String[]{"mobile", "from"}) != null
                && first(rawData, new String[]{"content", "text"}) != null;
    }

    private static String first(Map<String, Object> data, String[] keys) {
        for (String key : keys) {
            Object v = data.get(key);
            if (v != null) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.QINIU.name();
    }
}
