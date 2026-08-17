package com.whaleal.ark.cloud.third.sms.provider.volcengine;

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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 火山引擎短信（模板，参考 easy-sms VolcengineGateway / Volcengine 签名 v4）。
 * <p>
 * 凭证：accessKeyId / accessKeySecret；额外：signName、smsAccount（appId 或 stringConfig smsAccount）。
 * </p>
 *
 * @author 恒哥
 * @since 2026-08-17
 * @see <a href="https://www.volcengine.com/docs/6361/66704">火山引擎短信文档</a>
 */
public class VolcengineOutboundSender implements OutboundSender {

    private static final String ACTION = "SendSms";
    private static final String VERSION = "2020-01-01";
    private static final String SERVICE = "volcSMS";
    private static final String ALGORITHM = "HMAC-SHA256";
    private static final String DEFAULT_REGION = "cn-north-1";
    private static final String CN_HOST = "https://sms.volcengineapi.com";
    private static final String SG_HOST = "https://sms.byteplusapi.com";
    private static final DateTimeFormatter X_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String ak = firstNonBlank(config.getAccessKeyId(), config.getApiKey());
        String sk = firstNonBlank(config.getAccessKeySecret(), config.getApiSecret());
        if (isBlank(ak) || isBlank(sk)) {
            throw new SmsCredentialsException("火山引擎 accessKeyId/Secret 不能为空");
        }
        String signName = firstNonBlank(config.getSignName(), config.getSignature());
        String smsAccount = firstNonBlank(config.getAppId(),
                firstNonBlank(config.getStringConfig("smsAccount", null),
                        config.getStringConfig("sms_account", null)));
        String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateId)) {
            templateId = firstNonBlank(config.getTemplateId(), config.getTemplateCode());
        }
        if (isBlank(signName) || isBlank(smsAccount) || isBlank(templateId)) {
            throw new SmsParameterException("火山引擎需 signName、smsAccount(appId)、templateId");
        }

        Map<String, String> templateParam = message.getBusinessInfo() != null
                ? message.getBusinessInfo().getTemplateParams() : null;
        JSONObject payload = new JSONObject();
        payload.put("SmsAccount", smsAccount);
        payload.put("Sign", signName);
        payload.put("TemplateID", templateId);
        payload.put("TemplateParam", JSON.toJSONString(templateParam == null ? Map.of() : templateParam));
        payload.put("PhoneNumbers", normalizeMobile(message.getTo()));
        String tag = config.getStringConfig("tag", null);
        if (!isBlank(tag)) {
            payload.put("Tag", tag);
        }
        String body = payload.toJSONString();

        String region = firstNonBlank(config.getRegion(), DEFAULT_REGION);
        String endpoint = resolveEndpoint(config, region);
        String query = "Action=" + ACTION + "&Version=" + VERSION;
        String url = endpoint + "/?" + query;

        Instant now = Instant.now();
        String xDate = X_DATE.format(now);
        String shortDate = xDate.substring(0, 8);
        String payloadHash = SignatureUtils.sha256(body);
        String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                + "host:" + hostOf(endpoint) + "\n"
                + "x-content-sha256:" + payloadHash + "\n"
                + "x-date:" + xDate + "\n";
        String signedHeaders = "content-type;host;x-content-sha256;x-date";
        String canonicalRequest = "POST\n/\n" + canonicalQuery(query) + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String credentialScope = shortDate + "/" + region + "/" + SERVICE + "/request";
        String stringToSign = ALGORITHM + "\n" + xDate + "\n" + credentialScope + "\n"
                + SignatureUtils.sha256(canonicalRequest);
        byte[] signingKey = signingKey(sk, shortDate, region);
        String signature = SignatureUtils.bytesToHex(
                SignatureUtils.hmacSha256Raw(stringToSign.getBytes(StandardCharsets.UTF_8), signingKey));
        String authorization = ALGORITHM + " Credential=" + ak + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "quick-sms");
        headers.put("Host", hostOf(endpoint));
        headers.put("X-Date", xDate);
        headers.put("X-Content-Sha256", payloadHash);
        headers.put("Authorization", authorization);

        try {
            String resp = ProviderHttp.postJson(url, body, headers, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("火山引擎发送失败: " + e.getMessage(), SmsProviderType.VOLCENGINE, e);
        }
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        JSONObject meta = json.getJSONObject("ResponseMetadata");
        if (meta != null && meta.getJSONObject("Error") != null) {
            JSONObject err = meta.getJSONObject("Error");
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            if (message.getExtraInfo() == null) {
                message.setExtraInfo(new HashMap<>());
            }
            ProviderErrorMapper.putMapped(message.getExtraInfo(), "volcengine",
                    err.getString("Code"), err.getString("Message"));
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
        JSONObject result = json.getJSONObject("Result");
        if (result != null) {
            message.setProviderMessageId(firstNonBlank(result.getString("MessageID"), result.getString("MessageId")));
        }
        message.setProviderType(SmsProviderType.VOLCENGINE);
        return message;
    }

    private static byte[] signingKey(String secret, String shortDate, String region) {
        byte[] kDate = SignatureUtils.hmacSha256Raw(shortDate.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
        byte[] kRegion = SignatureUtils.hmacSha256Raw(region.getBytes(StandardCharsets.UTF_8), kDate);
        byte[] kService = SignatureUtils.hmacSha256Raw(SERVICE.getBytes(StandardCharsets.UTF_8), kRegion);
        return SignatureUtils.hmacSha256Raw("request".getBytes(StandardCharsets.UTF_8), kService);
    }

    private static String canonicalQuery(String query) {
        // Action=SendSms&Version=2020-01-01 已按 ASCII 序
        Map<String, String> map = new TreeMap<>();
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i > 0) {
                map.put(part.substring(0, i), part.substring(i + 1));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String resolveEndpoint(SmsProviderConfig config, String region) {
        if (!isBlank(config.getBaseUrl())) {
            return trimSlash(config.getBaseUrl());
        }
        if (region != null && region.toLowerCase(Locale.ROOT).startsWith("ap-")) {
            return SG_HOST;
        }
        return CN_HOST;
    }

    private static String hostOf(String endpoint) {
        String e = endpoint.replace("https://", "").replace("http://", "");
        int slash = e.indexOf('/');
        return slash > 0 ? e.substring(0, slash) : e;
    }

    private static String trimSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String normalizeMobile(String to) {
        if (to == null) return "";
        return to.trim().replace(" ", "");
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.VOLCENGINE.name();
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
