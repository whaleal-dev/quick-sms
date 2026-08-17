package com.whaleal.ark.cloud.third.sms.provider.sendcloud;

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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * SendCloud 短信（模板，参考 easy-sms SendcloudGateway）。
 * <p>凭证：apiKey=smsUser，apiSecret=smsKey。</p>
 *
 * @author 恒哥
 * @since 2026-08-17
 */
public class SendcloudOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "http://www.sendcloud.net/smsapi/send";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String smsUser = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String smsKey = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        if (isBlank(smsUser) || isBlank(smsKey)) {
            throw new SmsCredentialsException("SendCloud smsUser/smsKey 不能为空");
        }
        String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateId)) {
            templateId = firstNonBlank(config.getTemplateId(), config.getTemplateCode());
        }
        if (isBlank(templateId)) {
            throw new SmsParameterException("SendCloud templateId 不能为空");
        }

        Map<String, String> form = new TreeMap<>();
        form.put("smsUser", smsUser);
        form.put("templateId", templateId);
        form.put("msgType", looksInternational(message.getTo()) ? "2" : "0");
        form.put("phone", normalizePhone(message.getTo()));
        form.put("vars", formatVars(message));
        if ("true".equalsIgnoreCase(config.getStringConfig("timestamp", "false"))) {
            form.put("timestamp", String.valueOf(System.currentTimeMillis()));
        }
        form.put("signature", sign(form, smsKey));

        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        try {
            String resp = ProviderHttp.postForm(url, form, null, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("SendCloud 发送失败: " + e.getMessage(), SmsProviderType.SENDCLOUD, e);
        }
    }

    private static String formatVars(SmsOutboundMessage message) {
        Map<String, String> params = message.getBusinessInfo() != null
                ? message.getBusinessInfo().getTemplateParams() : null;
        JSONObject obj = new JSONObject();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                String key = e.getKey() == null ? "" : e.getKey().trim();
                if (!(key.startsWith("%") && key.endsWith("%") && key.length() > 2)) {
                    key = "%" + key.replace("%", "") + "%";
                }
                obj.put(key, e.getValue());
            }
        }
        return obj.toJSONString();
    }

    private static String sign(Map<String, String> params, String key) {
        StringJoinerLike joiner = new StringJoinerLike();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            if ("signature".equals(e.getKey())) {
                continue;
            }
            joiner.add(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        String query = URLDecoder.decode(joiner.toString(), StandardCharsets.UTF_8);
        return SignatureUtils.md5(key + "&" + query + "&" + key);
    }

    /** 简易 query 拼接（与 http_build_query 接近）。 */
    private static final class StringJoinerLike {
        private final StringBuilder sb = new StringBuilder();

        void add(String k, String v) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        boolean ok = Boolean.TRUE.equals(json.getBoolean("result"));
        if (ok) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            Object info = json.get("info");
            if (info instanceof JSONObject infoObj) {
                message.setProviderMessageId(infoObj.getString("smsIds"));
            }
            message.setProviderType(SmsProviderType.SENDCLOUD);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "sendcloud",
                String.valueOf(json.get("statusCode")), json.getString("message"));
        return message;
    }

    private static boolean looksInternational(String to) {
        if (to == null) return false;
        String t = to.trim();
        return t.startsWith("+") && !t.startsWith("+86");
    }

    private static String normalizePhone(String to) {
        if (to == null) return "";
        String m = to.trim();
        if (m.startsWith("+")) {
            return "00" + m.substring(1);
        }
        return m;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.SENDCLOUD.name();
    }

    @Override
    public boolean supportsTemplate() {
        return true;
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
