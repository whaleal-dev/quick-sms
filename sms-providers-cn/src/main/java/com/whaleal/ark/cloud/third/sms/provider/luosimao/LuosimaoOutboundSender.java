package com.whaleal.ark.cloud.third.sms.provider.luosimao;

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
 * 螺丝帽短信。
 * <p>凭证：apiKey；Basic 认证用户名为 api。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@Slf4j
public class LuosimaoOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "https://sms-api.luosimao.com/v1/send.json";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String apiKey = firstNonBlank(config.getApiKey(), config.getAccessKeySecret());
        if (isBlank(apiKey)) {
            throw new SmsCredentialsException("螺丝帽 apiKey 不能为空");
        }
        if (isBlank(message.getContent())) {
            throw new SmsParameterException("螺丝帽短信内容不能为空");
        }
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        Map<String, String> form = new HashMap<>();
        form.put("mobile", normalizeMobile(message.getTo()));
        form.put("message", message.getContent());
        try {
            String resp = ProviderHttp.postForm(url, form, ProviderHttp.basicAuthHeader("api", apiKey), (int) getTimeoutMs());
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("螺丝帽发送失败: " + e.getMessage(), SmsProviderType.LUOSIMAO, e);
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.LUOSIMAO.name();
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        int error = json.getIntValue("error");
        if (error == 0) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(json.getString("batch_id"));
            message.setProviderType(SmsProviderType.LUOSIMAO);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) message.setExtraInfo(new HashMap<>());
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "luosimao",
                String.valueOf(error), json.getString("msg"));
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
