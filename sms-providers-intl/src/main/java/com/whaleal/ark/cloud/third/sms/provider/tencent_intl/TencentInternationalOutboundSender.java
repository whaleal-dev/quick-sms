package com.whaleal.ark.cloud.third.sms.provider.tencent_intl;

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
import com.whaleal.ark.cloud.third.sms.util.SignatureUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * 腾讯云国际/港澳台短信（TC3-HMAC-SHA256 + SendSms）。
 *
 * @author whaleal-dev
 * @author 恒哥
 */
@Slf4j
public class TencentInternationalOutboundSender implements OutboundSender {

    private static final String HOST = "sms.tencentcloudapi.com";
    private static final String ENDPOINT = "https://" + HOST;
    private static final String SERVICE = "sms";
    private static final String VERSION = "2021-01-11";
    private static final String ACTION = "SendSms";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        return sendTemplateMessage(message, config);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String secretId = firstNonBlank(config.getAccessKeyId(), config.getApiKey());
        String secretKey = firstNonBlank(config.getAccessKeySecret(), config.getApiSecret());
        if (isBlank(secretId) || isBlank(secretKey)) {
            throw new SmsCredentialsException("腾讯云国际 SecretId/SecretKey 不能为空");
        }
        if (isBlank(message.getTo())) {
            throw new SmsParameterException("接收号码不能为空", SmsProviderType.TENCENT_INTERNATIONAL);
        }
        String templateId = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateId)) {
            templateId = firstNonBlank(config.getTemplateId(), config.getTemplateCode());
        }
        if (isBlank(templateId)) {
            throw new SmsParameterException("腾讯云国际 TemplateId 不能为空", SmsProviderType.TENCENT_INTERNATIONAL);
        }

        String sdkAppId = config.getStringConfig("smsSdkAppId",
                firstNonBlank(config.getAppId(), config.getAccountId(), "1400000000"));
        String signName = firstNonBlank(config.getSignName(), config.getSignature(), "");

        JSONObject body = new JSONObject();
        body.put("SmsSdkAppId", sdkAppId);
        body.put("SignName", signName);
        body.put("TemplateId", templateId);
        body.put("PhoneNumberSet", new String[]{message.getTo()});
        if (!isBlank(message.getFrom())) {
            body.put("SenderId", message.getFrom());
        }
        JSONArray params = new JSONArray();
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            params.addAll(message.getBusinessInfo().getTemplateParams().values());
        }
        body.put("TemplateParamSet", params);
        String requestBody = body.toJSONString();

        try {
            String region = firstNonBlank(config.getRegion(), "ap-singapore");
            Map<String, String> headers = tc3Headers(requestBody, secretId, secretKey, region);
            String resp = httpPost(requestBody, headers, config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("腾讯云国际发送失败: " + e.getMessage(),
                    SmsProviderType.TENCENT_INTERNATIONAL, e);
        }
    }

    private Map<String, String> tc3Headers(String body, String secretId, String secretKey, String region)
            throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = sdf.format(new Date(Long.parseLong(timestamp) * 1000L));

        String canonicalRequest = "POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:"
                + HOST + "\n\ncontent-type;host\n" + SignatureUtils.sha256(body);
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n"
                + SignatureUtils.sha256(canonicalRequest);

        byte[] secretDate = SignatureUtils.hmacSha256Raw(date.getBytes(StandardCharsets.UTF_8),
                ("TC3" + secretKey).getBytes(StandardCharsets.UTF_8));
        byte[] secretService = SignatureUtils.hmacSha256Raw(SERVICE.getBytes(StandardCharsets.UTF_8), secretDate);
        byte[] secretSigning = SignatureUtils.hmacSha256Raw("tc3_request".getBytes(StandardCharsets.UTF_8), secretService);
        String signature = SignatureUtils.bytesToHex(
                SignatureUtils.hmacSha256Raw(stringToSign.getBytes(StandardCharsets.UTF_8), secretSigning));

        String authorization = ALGORITHM + " Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=content-type;host, Signature=" + signature;

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Host", HOST);
        headers.put("X-TC-Action", ACTION);
        headers.put("X-TC-Timestamp", timestamp);
        headers.put("X-TC-Version", VERSION);
        headers.put("X-TC-Region", region);
        return headers;
    }

    private String httpPost(String body, Map<String, String> headers, SmsProviderConfig config) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        int timeout = config.getRequestTimeout() != null ? config.getRequestTimeout() : 10000;
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        for (Map.Entry<String, String> h : headers.entrySet()) {
            conn.setRequestProperty(h.getKey(), h.getValue());
        }
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

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject root = JSON.parseObject(resp);
        JSONObject response = root.getJSONObject("Response");
        if (response == null) {
            response = root;
        }
        if (response.getJSONObject("Error") != null) {
            JSONObject err = response.getJSONObject("Error");
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            if (message.getExtraInfo() == null) {
                message.setExtraInfo(new HashMap<>());
            }
            ProviderErrorMapper.putMapped(message.getExtraInfo(), "tencent_international",
                    err.getString("Code"), err.getString("Message"));
            return message;
        }
        JSONArray set = response.getJSONArray("SendStatusSet");
        if (set != null && !set.isEmpty()) {
            JSONObject st = set.getJSONObject(0);
            boolean ok = "Ok".equalsIgnoreCase(st.getString("Code"));
            message.setSendStatus(ok ? SmsOutboundMessage.SendStatus.SUBMITTED : SmsOutboundMessage.SendStatus.FAILED);
            message.setProviderMessageId(st.getString("SerialNo"));
            message.setMessageId(st.getString("SerialNo"));
            message.setProviderType(SmsProviderType.TENCENT_INTERNATIONAL);
            if (!ok) {
                if (message.getExtraInfo() == null) {
                    message.setExtraInfo(new HashMap<>());
                }
                ProviderErrorMapper.putMapped(message.getExtraInfo(), "tencent_international",
                        st.getString("Code"), st.getString("Message"));
            }
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "tencent_international",
                "PARSE_ERROR", "empty SendStatusSet");
        return message;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.TENCENT_INTERNATIONAL.name();
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
