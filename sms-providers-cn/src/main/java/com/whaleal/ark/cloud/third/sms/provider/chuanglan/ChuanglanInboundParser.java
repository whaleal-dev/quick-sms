package com.whaleal.ark.cloud.third.sms.provider.chuanglan;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.inbound.entity.SmsInboundMessage;
import com.whaleal.ark.cloud.third.sms.inbound.parser.InboundParser;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 创蓝上行解析器。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class ChuanglanInboundParser implements InboundParser {

    @Override
    public SmsInboundMessage parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            return null;
        }
        return SmsInboundMessage.builder()
                .from(first(rawData, new String[]{"mobile", "phone"}))
                .to(first(rawData, new String[]{"destcode", "spNumber"}))
                .content(first(rawData, new String[]{"msg", "content"}))
                .messageType(SmsInboundMessage.MessageType.TEXT)
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }

    @Override
    public boolean isValidData(Map<String, Object> rawData) {
        return rawData != null && !rawData.isEmpty()
                && first(rawData, new String[]{"mobile", "phone"}) != null
                && first(rawData, new String[]{"msg", "content"}) != null;
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
        return SmsProviderType.CHUANGLAN.name();
    }
}
