package com.whaleal.ark.cloud.third.sms.provider.china_mobile;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

/**
 * 中国移动回执解析器
 */
public class ChinaMobileReceiptParser implements ReceiptParser {
    
    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        return SmsReceipt.builder()
                .receiptId(getString(rawData, "msgId"))
                .messageId(getString(rawData, "msgId"))
                .to(getString(rawData, "destTermId"))
                .receiptStatus(parseStatus(getString(rawData, "stat")))
                .receiptCode(getString(rawData, "stat"))
                .receivedTime(LocalDateTime.now())
                .rawData(rawData)
                .build();
    }
    
    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) return SmsReceipt.ReceiptStatus.UNKNOWN;
        // 🔴 2026-10-09 修：原实现是 `"DELIVRD".equals(status) ? DELIVERED : FAILED`
        //    —— 除 DELIVRD 外**一律**判 FAILED。中间态 ACCEPTD（已受理）会被判成
        //    **终态失败**，而平台以 delivered/failed 为终态闸门 ⇒ 之后真实的 DELIVRD
        //    被永久挡住，一条成功投递的消息永远报「失败」。
        //    改为按 SMPP deliver-report 的 stat 词表逐词映射（与阿里云解析器同一套），
        //    认不出的词返回 UNKNOWN（非终态），绝不默认终态。
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "DELIVRD" -> SmsReceipt.ReceiptStatus.DELIVERED;
            // 中间态：已受理/在途，不是终态
            case "ACCEPTD", "ENROUTE" -> SmsReceipt.ReceiptStatus.SENT;
            case "UNDELIV" -> SmsReceipt.ReceiptStatus.FAILED;
            case "DELETED" -> SmsReceipt.ReceiptStatus.FAILED;
            case "EXPIRED" -> SmsReceipt.ReceiptStatus.EXPIRED;
            case "REJECTD" -> SmsReceipt.ReceiptStatus.REJECTED;
            default -> SmsReceipt.ReceiptStatus.UNKNOWN;
        };
    }
    
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String getSupportedProvider() {
        return "CHINA_MOBILE";
    }
} 