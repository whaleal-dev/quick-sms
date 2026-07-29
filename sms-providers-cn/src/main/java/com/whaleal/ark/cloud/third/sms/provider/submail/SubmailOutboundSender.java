package com.whaleal.ark.cloud.third.sms.provider.submail;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import com.whaleal.ark.cloud.third.sms.error.ProviderErrorMapper;
import com.whaleal.ark.cloud.third.sms.exception.SmsCredentialsException;
import com.whaleal.ark.cloud.third.sms.exception.SmsNetworkException;
import com.whaleal.ark.cloud.third.sms.exception.SmsParameterException;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;
import com.whaleal.ark.cloud.third.sms.util.ProviderHttp;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * SUBMAIL 短信（内容发送）。
 * <p>凭证：apiKey=appid，apiSecret=appkey（签名模式 md5 简化为 appkey 直传）。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@Slf4j
public class SubmailOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "https://api-v4.mysubmail.com/sms/send.json";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String appId = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String appKey = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        if (isBlank(appId) || isBlank(appKey)) {
            throw new SmsCredentialsException("SUBMAIL appid/appkey 不能为空");
        }
        if (isBlank(message.getContent())) {
            throw new SmsParameterException("SUBMAIL 短信内容不能为空");
        }
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        Map<String, String> form = new HashMap<>();
        form.put("appid", appId);
        form.put("to", normalizeMobile(message.getTo()));
        form.put("content", message.getContent());
        form.put("signature", appKey);
        try {
            String resp = ProviderHttp.postForm(url, form, null, (int) getTimeoutMs());
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("SUBMAIL 发送失败: " + e.getMessage(), SmsProviderType.SUBMAIL, e);
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.SUBMAIL.name();
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        String status = json.getString("status");
        if ("success".equalsIgnoreCase(status)) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(json.getString("send_id"));
            message.setProviderType(SmsProviderType.SUBMAIL);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) message.setExtraInfo(new HashMap<>());
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "submail",
                json.getString("code"), json.getString("msg"));
        return message;
    }

    private static String normalizeMobile(String to) {
        if (to == null) return "";
        String m = to.trim();
        if (m.startsWith("+86")) return m.substring(3);
        if (m.startsWith("86") && m.length() > 11) return m.substring(2);
        return m;
    }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) return a.trim();
        if (!isBlank(b)) return b.trim();
        return null;
    }
}
