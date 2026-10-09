package com.whaleal.ark.cloud.third.sms.provider.zhutong;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.receipt.entity.SmsReceipt;
import com.whaleal.ark.cloud.third.sms.receipt.parser.ReceiptParser;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * 助通回执解析器。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class ZhutongReceiptParser implements ReceiptParser {

    @Override
    public SmsReceipt parse(Map<String, Object> rawData, SmsProviderConfig config) {
        if (!isValidData(rawData)) {
            return null;
        }
        String status = first(rawData, new String[]{"code", "status"});
        return SmsReceipt.builder()
                .receiptId(first(rawData, new String[]{"msgId", "records"}))
                .messageId(first(rawData, new String[]{"msgId", "records"}))
                .to(first(rawData, new String[]{"mobile", "phone"}))
                .receiptStatus(parseStatus(status))
                .receiptCode(status)
                .errorCode(first(rawData, new String[]{"error", "err_code", "errorCode", "code"}))
                .errorDescription(first(rawData, new String[]{"error_msg", "msg", "errorMsg", "description"}))
                .receivedTime(LocalDateTime.now())
                .providerType(SmsProviderType.ZHUTONG)
                .rawData(rawData)
                .build();
    }

    private SmsReceipt.ReceiptStatus parseStatus(String status) {
        if (status == null) {
            return SmsReceipt.ReceiptStatus.UNKNOWN;
        }
        String s = status.toLowerCase(Locale.ROOT);
        // 🔴 否定词必须先判：子串 "deliv" 同时命中 "undeliv" 与 "not_delivered"，
        //    排在成功分支之后会把「投递失败」判成「已送达」—— 而平台侧 delivered/failed
        //    是终态闸门，误报终态不可恢复（真失败被永久挡住）。
        if (s.contains("fail") || s.contains("undeliv") || s.contains("notdeliv")
                || s.contains("not_deliver") || s.contains("error")) {
            return SmsReceipt.ReceiptStatus.FAILED;
        }
        if (s.contains("success") || s.contains("deliv") || "0".equals(s) || "true".equals(s)) {
            return SmsReceipt.ReceiptStatus.DELIVERED;
        }
        if (s.contains("reject")) {
            return SmsReceipt.ReceiptStatus.REJECTED;
        }
        if (s.contains("expir")) {
            return SmsReceipt.ReceiptStatus.EXPIRED;
        }
        return SmsReceipt.ReceiptStatus.UNKNOWN;
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
        return SmsProviderType.ZHUTONG.name();
    }
}
