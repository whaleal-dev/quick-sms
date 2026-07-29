package com.whaleal.ark.cloud.third.sms.provider.huawei_intl;

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
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * 华为云国际短信（WSSE + batchSendSms/v1）。
 *
 * @author whaleal-dev
 * @author 恒哥
 */
@Slf4j
public class HuaweiInternationalOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL =
            "https://smsapi.ap-southeast-1.myhuaweicloud.com:443/sms/batchSendSms/v1";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String appKey = firstNonBlank(config.getAppKey(), config.getApiKey(), config.getAccessKeyId());
        String appSecret = firstNonBlank(config.getAppSecret(), config.getApiSecret(), config.getAccessKeySecret());
        if (isBlank(appKey) || isBlank(appSecret)) {
            throw new SmsCredentialsException("华为云国际 appKey/appSecret 不能为空");
        }
        String sender = firstNonBlank(config.getDefaultFrom(), config.getStringConfig("sender", null), message.getFrom());
        if (isBlank(sender)) {
            throw new SmsParameterException("华为云国际通道号 sender 不能为空", SmsProviderType.HUAWEI_INTERNATIONAL);
        }
        String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateId)) {
            templateId = firstNonBlank(config.getTemplateId(), config.getTemplateCode());
        }
        if (isBlank(templateId)) {
            throw new SmsParameterException("华为云国际 templateId 不能为空", SmsProviderType.HUAWEI_INTERNATIONAL);
        }
        if (isBlank(message.getTo())) {
            throw new SmsParameterException("接收号码不能为空", SmsProviderType.HUAWEI_INTERNATIONAL);
        }

        JSONObject body = new JSONObject();
        body.put("from", sender);
        body.put("to", new String[]{normalizeTo(message.getTo())});
        body.put("templateId", templateId);
        String signature = firstNonBlank(config.getSignName(), config.getSignature());
        if (!isBlank(signature)) {
            body.put("signature", signature);
        }
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            JSONArray paras = new JSONArray();
            paras.addAll(message.getBusinessInfo().getTemplateParams().values());
            body.put("templateParas", paras);
        }
        String json = body.toJSONString();
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        try {
            String resp = httpPost(url, json, appKey, appSecret, config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("华为云国际发送失败: " + e.getMessage(),
                    SmsProviderType.HUAWEI_INTERNATIONAL, e);
        }
    }

    private String httpPost(String url, String body, String appKey, String appSecret, SmsProviderConfig config)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        int timeout = config.getRequestTimeout() != null ? config.getRequestTimeout() : 10000;
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization",
                "WSSE realm=\"SDP\",profile=\"UsernameToken\",type=\"Appkey\"");
        conn.setRequestProperty("X-WSSE", buildWsse(appKey, appSecret));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    /** PasswordDigest = Base64(SHA256(nonce + created + appSecret)) */
    private static String buildWsse(String appKey, String appSecret) throws Exception {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        String created = df.format(new Date());
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest((nonce + created + appSecret).getBytes(StandardCharsets.UTF_8));
        String passwordDigest = Base64.getEncoder().encodeToString(digest);
        return String.format(
                "UsernameToken Username=\"%s\",PasswordDigest=\"%s\",Nonce=\"%s\",Created=\"%s\"",
                appKey, passwordDigest, nonce, created);
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        String code = json.getString("code");
        if ("000000".equals(code)) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            Object result = json.get("result");
            message.setProviderMessageId(result == null ? null : String.valueOf(result));
            message.setMessageId(message.getProviderMessageId());
            message.setProviderType(SmsProviderType.HUAWEI_INTERNATIONAL);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "huawei_international",
                code, json.getString("description"));
        return message;
    }

    private static String normalizeTo(String to) {
        if (to == null) {
            return "";
        }
        String m = to.trim();
        if (!m.startsWith("+")) {
            return "+" + m;
        }
        return m;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.HUAWEI_INTERNATIONAL.name();
    }

    @Override
    public boolean supportsTemplate() {
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (!isBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }
}
