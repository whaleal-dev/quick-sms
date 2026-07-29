package com.whaleal.ark.cloud.third.sms.provider.cloopen;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 容联云通讯模板短信。
 * <p>凭证：accessKeyId=accountSid，accessKeySecret=authToken；
 * apiKey 或 extra.appId=AppId；templateId + datas。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
@Slf4j
public class CloopenOutboundSender implements OutboundSender {

    private static final String DEFAULT_BASE = "https://app.cloopen.com:8883";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String accountSid = firstNonBlank(config.getAccessKeyId(), null);
        String authToken = firstNonBlank(config.getAccessKeySecret(), config.getApiSecret());
        String appId = firstNonBlank(config.getAppId(),
                firstNonBlank(config.getStringConfig("appId", null), config.getApiKey()));
        if (isBlank(accountSid) || isBlank(authToken)) {
            throw new SmsCredentialsException("容联 accountSid/authToken 不能为空（accessKeyId/accessKeySecret）");
        }
        if (isBlank(appId)) {
            throw new SmsCredentialsException("容联 appId 不能为空（config.appId 或 apiKey）");
        }
        String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateId)) {
            throw new SmsParameterException("容联模板短信必须提供 templateId");
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String sig = SignatureUtils.md5(accountSid + authToken + timestamp).toUpperCase();
        String base = firstNonBlank(config.getBaseUrl(), DEFAULT_BASE);
        String url = base + "/2013-12-26/Accounts/" + accountSid + "/SMS/TemplateSMS?sig=" + sig;
        String auth = Base64.getEncoder().encodeToString((accountSid + ":" + timestamp).getBytes(StandardCharsets.UTF_8));

        JSONObject body = new JSONObject();
        body.put("to", normalizeMobile(message.getTo()));
        body.put("appId", appId);
        body.put("templateId", templateId);
        JSONArray datas = new JSONArray();
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            // 容联按顺序传数组；若 map 有序值则按 values；同时支持 data0,data1...
            Map<String, String> params = message.getBusinessInfo().getTemplateParams();
            params.values().forEach(datas::add);
        }
        body.put("datas", datas);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", auth);
        try {
            String resp = ProviderHttp.postJson(url, body.toJSONString(), headers, (int) getTimeoutMs());
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("容联发送失败: " + e.getMessage(), SmsProviderType.CLOOPEN, e);
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.CLOOPEN.name();
    }

    @Override
    public boolean supportsTemplate() {
        return true;
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        String statusCode = json.getString("statusCode");
        if ("000000".equals(statusCode)) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            JSONObject data = json.getJSONObject("templateSMS");
            if (data != null) {
                message.setProviderMessageId(data.getString("smsMessageSid"));
            }
            message.setProviderType(SmsProviderType.CLOOPEN);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) message.setExtraInfo(new HashMap<>());
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "cloopen",
                statusCode, json.getString("statusMsg"));
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
