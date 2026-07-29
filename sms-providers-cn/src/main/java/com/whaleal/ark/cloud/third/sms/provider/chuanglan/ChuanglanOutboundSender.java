package com.whaleal.ark.cloud.third.sms.provider.chuanglan;

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
 * 创蓝 / 253 云通讯发送。
 * <p>凭证：apiKey=account，apiSecret=password；正文自带签名。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@Slf4j
public class ChuanglanOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "https://smssh1.253.com/msg/v1/send/json";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String account = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String password = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        if (isBlank(account) || isBlank(password)) {
            throw new SmsCredentialsException("创蓝 account/password 不能为空");
        }
        String msg = message.getContent();
        if (isBlank(msg)) {
            throw new SmsParameterException("创蓝短信内容不能为空");
        }
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        JSONObject body = new JSONObject();
        body.put("account", account);
        body.put("password", password);
        body.put("msg", msg);
        body.put("phone", normalizeMobile(message.getTo()));
        body.put("report", "true");
        try {
            String resp = ProviderHttp.postJson(url, body.toJSONString(), null, (int) getTimeoutMs());
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("创蓝发送失败: " + e.getMessage(), SmsProviderType.CHUANGLAN, e);
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.CHUANGLAN.name();
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        String code = json.getString("code");
        if ("0".equals(code)) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(json.getString("msgId"));
            message.setProviderType(SmsProviderType.CHUANGLAN);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "chuanglan",
                code, json.getString("errorMsg"));
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
