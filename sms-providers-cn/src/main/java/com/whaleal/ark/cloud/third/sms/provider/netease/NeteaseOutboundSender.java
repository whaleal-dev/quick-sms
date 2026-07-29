package com.whaleal.ark.cloud.third.sms.provider.netease;

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
import java.util.UUID;

/**
 * 网易云信模板短信。
 * <p>凭证：apiKey=AppKey，apiSecret=AppSecret；templateId + templateParams。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class NeteaseOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "https://api.netease.im/sms/sendtemplate.action";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String appKey = firstNonBlank(config.getApiKey(), config.getAccessKeyId(), config.getAppKey());
        String appSecret = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret(), config.getAppSecret());
        if (isBlank(appKey) || isBlank(appSecret)) {
            throw new SmsCredentialsException("网易云信 AppKey/AppSecret 不能为空");
        }
        String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateId)) {
            templateId = config.getTemplateId();
        }
        if (isBlank(templateId)) {
            throw new SmsParameterException("网易云信 templateId 不能为空");
        }
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String curTime = String.valueOf(System.currentTimeMillis() / 1000);
        String checkSum = SignatureUtils.sha1(appSecret + nonce + curTime);

        Map<String, String> headers = new HashMap<>();
        headers.put("AppKey", appKey);
        headers.put("Nonce", nonce);
        headers.put("CurTime", curTime);
        headers.put("CheckSum", checkSum);

        Map<String, String> form = new HashMap<>();
        form.put("templateid", templateId);
        form.put("mobiles", "[\"" + normalizeMobile(message.getTo()) + "\"]");
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            form.put("params", JSON.toJSONString(message.getBusinessInfo().getTemplateParams().values()));
        }
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        try {
            String resp = ProviderHttp.postForm(url, form, headers, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("网易云信发送失败: " + e.getMessage(), SmsProviderType.NETEASE, e);
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.NETEASE.name();
    }

    @Override
    public boolean supportsTemplate() {
        return true;
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        int code = json.getIntValue("code");
        if (code == 200) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            Object obj = json.get("obj");
            message.setProviderMessageId(obj == null ? null : String.valueOf(obj));
            message.setProviderType(SmsProviderType.NETEASE);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "netease",
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
    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (!isBlank(v)) return v.trim();
        }
        return null;
    }
}
