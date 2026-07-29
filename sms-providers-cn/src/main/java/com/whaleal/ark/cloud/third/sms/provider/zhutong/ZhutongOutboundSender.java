package com.whaleal.ark.cloud.third.sms.provider.zhutong;

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
import com.whaleal.ark.cloud.third.sms.util.SignatureUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 助通短信发送（内容短信）。
 * <p>凭证：apiKey=username，apiSecret=password（明文，接口侧使用 MD5）。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class ZhutongOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "https://api.mix2.zthysms.com/v2/sendSms";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String username = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String password = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        if (isBlank(username) || isBlank(password)) {
            throw new SmsCredentialsException("助通 username/password 不能为空");
        }
        if (isBlank(message.getContent())) {
            throw new SmsParameterException("助通短信内容不能为空");
        }
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        long tKey = System.currentTimeMillis() / 1000;
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", SignatureUtils.md5(SignatureUtils.md5(password) + tKey));
        body.put("tKey", String.valueOf(tKey));
        body.put("mobile", normalizeMobile(message.getTo()));
        body.put("content", message.getContent());
        try {
            String resp = ProviderHttp.postJson(url, body.toJSONString(), null, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("助通发送失败: " + e.getMessage(), SmsProviderType.ZHUTONG, e);
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.ZHUTONG.name();
    }

    @Override
    public boolean supportsTemplate() {
        return false;
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        int code = json.getIntValue("code");
        if (code == 200) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(json.getString("msgId"));
            message.setProviderType(SmsProviderType.ZHUTONG);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "zhutong",
                String.valueOf(code), json.getString("msg"));
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
