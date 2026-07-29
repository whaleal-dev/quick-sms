package com.whaleal.ark.cloud.third.sms.provider.qiniu;

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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 七牛云短信（模板发送）。
 * <p>凭证：accessKeyId/accessKeySecret；templateId 必填；
 * 使用 Qiniu 管理凭证签名（简化版 HMAC-SHA1）。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@Slf4j
public class QiniuOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "https://sms.qiniuapi.com/v1/message";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String accessKey = firstNonBlank(config.getAccessKeyId(), config.getApiKey());
        String secretKey = firstNonBlank(config.getAccessKeySecret(), config.getApiSecret());
        if (isBlank(accessKey) || isBlank(secretKey)) {
            throw new SmsCredentialsException("七牛 AccessKey/SecretKey 不能为空");
        }
        String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateId)) {
            throw new SmsParameterException("七牛短信必须提供 templateId");
        }
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        JSONObject body = new JSONObject();
        body.put("template_id", templateId);
        body.put("mobiles", new String[]{normalizeMobile(message.getTo())});
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            body.put("parameters", message.getBusinessInfo().getTemplateParams());
        }
        String json = body.toJSONString();
        try {
            String auth = qiniuToken(accessKey, secretKey, url, json);
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Qiniu " + auth);
            String resp = ProviderHttp.postJson(url, json, headers, (int) getTimeoutMs());
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("七牛发送失败: " + e.getMessage(), SmsProviderType.QINIU, e);
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.QINIU.name();
    }

    @Override
    public boolean supportsTemplate() {
        return true;
    }

    private static String qiniuToken(String accessKey, String secretKey, String url, String body) throws Exception {
        // 简化：对 path + body 做 HMAC-SHA1，再 Base64 URL-safe
        java.net.URI uri = java.net.URI.create(url);
        String path = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()) + "\n";
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        mac.update(path.getBytes(StandardCharsets.UTF_8));
        if (body != null) {
            mac.update(body.getBytes(StandardCharsets.UTF_8));
        }
        String sign = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        return accessKey + ":" + sign;
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        if (json.containsKey("message_id") || json.containsKey("job_id")) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(firstNonBlank(json.getString("message_id"), json.getString("job_id")));
            message.setProviderType(SmsProviderType.QINIU);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) message.setExtraInfo(new HashMap<>());
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "qiniu",
                json.getString("code"),
                firstNonBlank(json.getString("error"), json.getString("message")));
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
