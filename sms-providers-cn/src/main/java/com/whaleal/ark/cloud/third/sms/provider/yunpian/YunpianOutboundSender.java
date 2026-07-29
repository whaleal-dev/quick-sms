package com.whaleal.ark.cloud.third.sms.provider.yunpian;

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
 * 云片短信发送（内容短信）。
 * <p>凭证：apiKey；正文需自带签名【】。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@Slf4j
public class YunpianOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "https://sms.yunpian.com/v2/sms/single_send.json";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String apiKey = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        if (isBlank(apiKey)) {
            throw new SmsCredentialsException("云片 apiKey 不能为空");
        }
        String text = resolveContent(message, config);
        if (isBlank(text)) {
            throw new SmsParameterException("云片短信内容不能为空");
        }
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        Map<String, String> form = new HashMap<>();
        form.put("apikey", apiKey);
        form.put("mobile", normalizeMobile(message.getTo()));
        form.put("text", text);
        try {
            String resp = ProviderHttp.postForm(url, form, null, (int) getTimeoutMs());
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("云片发送失败: " + e.getMessage(), SmsProviderType.YUNPIAN, e);
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.YUNPIAN.name();
    }

    @Override
    public boolean supportsTemplate() {
        return false;
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        int code = json.getIntValue("code");
        if (code == 0) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(String.valueOf(json.get("sid")));
            message.setProviderType(SmsProviderType.YUNPIAN);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "yunpian",
                String.valueOf(code), json.getString("msg"));
        return message;
    }

    private static String resolveContent(SmsOutboundMessage message, SmsProviderConfig config) {
        if (message.getContent() != null && !message.getContent().isBlank()) {
            return message.getContent();
        }
        return null;
    }

    private static String normalizeMobile(String to) {
        if (to == null) {
            return "";
        }
        String m = to.trim();
        if (m.startsWith("+86")) {
            return m.substring(3);
        }
        if (m.startsWith("86") && m.length() > 11) {
            return m.substring(2);
        }
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) return a.trim();
        if (!isBlank(b)) return b.trim();
        return null;
    }
}
