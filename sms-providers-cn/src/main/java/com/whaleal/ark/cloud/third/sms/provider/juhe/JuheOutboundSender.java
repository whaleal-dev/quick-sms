package com.whaleal.ark.cloud.third.sms.provider.juhe;

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
import java.util.StringJoiner;

/**
 * 聚合数据短信（模板，参考 easy-sms JuheGateway）。
 * <p>凭证：apiKey=app_key；模板变量格式 #code#=1234。</p>
 *
 * @author 恒哥
 * @since 2026-08-17
 */
public class JuheOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "http://v.juhe.cn/sms/send";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String appKey = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        if (isBlank(appKey)) {
            throw new SmsCredentialsException("聚合数据 app_key 不能为空");
        }
        String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateId)) {
            templateId = firstNonBlank(config.getTemplateId(), config.getTemplateCode());
        }
        if (isBlank(templateId)) {
            throw new SmsParameterException("聚合数据 templateId 不能为空");
        }
        Map<String, String> query = new HashMap<>();
        query.put("mobile", normalizeMobile(message.getTo()));
        query.put("tpl_id", templateId);
        query.put("tpl_value", formatVars(message));
        query.put("dtype", "json");
        query.put("key", appKey);
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        try {
            String resp = ProviderHttp.get(url, query, null, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("聚合数据发送失败: " + e.getMessage(), SmsProviderType.JUHE, e);
        }
    }

    private static String formatVars(SmsOutboundMessage message) {
        Map<String, String> params = message.getBusinessInfo() != null
                ? message.getBusinessInfo().getTemplateParams() : null;
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> e : params.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim();
            if (!(key.startsWith("#") && key.endsWith("#") && key.length() > 2)) {
                key = "#" + key.replace("#", "") + "#";
            }
            String value = e.getValue() == null ? "" : e.getValue();
            joiner.add(key + "=" + value);
        }
        return joiner.toString();
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        int errorCode = json.getIntValue("error_code");
        if (errorCode == 0) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            JSONObject result = json.getJSONObject("result");
            if (result != null) {
                message.setProviderMessageId(result.getString("sid"));
            }
            message.setProviderType(SmsProviderType.JUHE);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "juhe",
                String.valueOf(errorCode), json.getString("reason"));
        return message;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.JUHE.name();
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
