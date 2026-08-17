package com.whaleal.ark.cloud.third.sms.provider.yunzhixun;

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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 云之讯（UCPAAS）模板发送，参考 easy-sms YunzhixunGateway。
 * <p>凭证：apiKey=sid，apiSecret=token，appId=appid。</p>
 *
 * @author 恒哥
 * @since 2026-08-17
 */
public class YunzhixunOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "https://open.ucpaas.com/ol/sms/sendsms";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String sid = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String token = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        String appId = firstNonBlank(config.getAppId(), config.getAppKey());
        if (isBlank(sid) || isBlank(token) || isBlank(appId)) {
            throw new SmsCredentialsException("云之讯 sid/token/appId 不能为空");
        }
        String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateId)) {
            templateId = firstNonBlank(config.getTemplateId(), config.getTemplateCode());
        }
        if (isBlank(templateId)) {
            throw new SmsParameterException("云之讯 templateId 不能为空");
        }

        JSONObject body = new JSONObject();
        body.put("sid", sid);
        body.put("token", token);
        body.put("appid", appId);
        body.put("templateid", templateId);
        body.put("mobile", normalizeMobile(message.getTo()));
        body.put("param", formatParams(message));
        String uid = config.getStringConfig("uid", null);
        body.put("uid", uid == null ? "" : uid);

        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        try {
            String resp = ProviderHttp.postJson(url, body.toJSONString(), null, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("云之讯发送失败: " + e.getMessage(), SmsProviderType.YUNZHIXUN, e);
        }
    }

    private static String formatParams(SmsOutboundMessage message) {
        Map<String, String> params = message.getBusinessInfo() != null
                ? message.getBusinessInfo().getTemplateParams() : null;
        if (params == null || params.isEmpty()) {
            String raw = message.getBusinessInfo() != null ? null : null;
            return raw == null ? "" : raw;
        }
        // 云之讯常用逗号分隔有序参数；若只有一个名为 params 的值则直接用
        if (params.size() == 1 && params.containsKey("params")) {
            return params.get("params");
        }
        return params.values().stream().map(v -> v == null ? "" : v).collect(Collectors.joining(","));
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        String code = json.getString("code");
        if ("000000".equals(code)) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            Object data = json.get("data");
            if (data instanceof JSONObject dataObj) {
                message.setProviderMessageId(firstNonBlank(dataObj.getString("smsid"), dataObj.getString("sid")));
            }
            message.setProviderType(SmsProviderType.YUNZHIXUN);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "yunzhixun",
                code, json.getString("msg"));
        return message;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.YUNZHIXUN.name();
    }

    @Override
    public boolean supportsTemplate() {
        return true;
    }

    private static String normalizeMobile(String to) {
        if (to == null) return "";
        String m = to.trim();
        if (m.startsWith("+86")) return m.substring(3);
        if (m.startsWith("86") && m.length() > 11) return m.substring(2);
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
