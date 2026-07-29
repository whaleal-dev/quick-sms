package com.whaleal.ark.cloud.third.sms.provider.ctyun;

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

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 天翼云短信（EOP 签名）。
 * <p>凭证：accessKeyId + accessKeySecret；templateCode；signName。</p>
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public class CtyunOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "https://sms-global.ctapi.ctyun.cn/sms/api/v1";
    private static final ZoneId GMT8 = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String ak = firstNonBlank(config.getAccessKeyId(), config.getApiKey());
        String sk = firstNonBlank(config.getAccessKeySecret(), config.getApiSecret());
        if (isBlank(ak) || isBlank(sk)) {
            throw new SmsCredentialsException("天翼云 accessKey/secret 不能为空");
        }
        String templateCode = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateCode)) {
            templateCode = firstNonBlank(config.getTemplateCode(), config.getTemplateId());
        }
        if (isBlank(templateCode)) {
            throw new SmsParameterException("天翼云 templateCode 不能为空");
        }
        String sign = firstNonBlank(config.getSignName(), config.getSignature());
        if (isBlank(sign)) {
            throw new SmsParameterException("天翼云签名不能为空");
        }

        JSONObject body = new JSONObject();
        body.put("action", config.getStringConfig("action", "SendSms"));
        body.put("phoneNumber", normalizeMobile(message.getTo()));
        body.put("templateCode", templateCode);
        body.put("signName", sign);
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            body.put("templateParam", JSON.toJSONString(message.getBusinessInfo().getTemplateParams()));
        }
        String json = body.toJSONString();
        Map<String, String> headers = signHeader(json, ak, sk);
        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        try {
            String resp = ProviderHttp.postJson(url, json, headers, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("天翼云发送失败: " + e.getMessage(), SmsProviderType.CTYUN, e);
        }
    }

    /**
     * EOP：kTime/kAk/kDate 链式 HMAC，签名串含 request-id / eop-date / body sha256。
     */
    static Map<String, String> signHeader(String body, String key, String secret) {
        ZonedDateTime now = ZonedDateTime.now(GMT8);
        String signatureDate = now.format(DATE_FMT);
        String signatureTime = now.format(TIME_FMT);
        String uuid = UUID.randomUUID().toString();
        String contentHash = SignatureUtils.sha256(body == null ? "" : body);

        byte[] kTime = SignatureUtils.hmacSha256Raw(signatureTime.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
        byte[] kAk = SignatureUtils.hmacSha256Raw(key.getBytes(StandardCharsets.UTF_8), kTime);
        byte[] kDate = SignatureUtils.hmacSha256Raw(signatureDate.getBytes(StandardCharsets.UTF_8), kAk);

        String toSign = String.format("ctyun-eop-request-id:%s\neop-date:%s\n", uuid, signatureTime)
                + "\n\n" + contentHash;
        String signature = Base64.getEncoder().encodeToString(
                SignatureUtils.hmacSha256Raw(toSign.getBytes(StandardCharsets.UTF_8), kDate));

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("ctyun-eop-request-id", uuid);
        headers.put("Eop-date", signatureTime);
        headers.put("Eop-Authorization",
                key + " Headers=ctyun-eop-request-id;eop-date Signature=" + signature);
        return headers;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.CTYUN.name();
    }

    @Override
    public boolean supportsTemplate() {
        return true;
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        String code = firstNonBlank(json.getString("code"), json.getString("statusCode"));
        if ("OK".equalsIgnoreCase(code) || "200".equals(code) || "0".equals(code)) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(firstNonBlank(json.getString("requestId"), json.getString("msgId")));
            message.setProviderType(SmsProviderType.CTYUN);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "ctyun",
                code, firstNonBlank(json.getString("message"), json.getString("msg")));
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
