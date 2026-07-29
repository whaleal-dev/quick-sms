package com.whaleal.ark.cloud.third.sms.provider.aliyun_intl;

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
import com.whaleal.ark.cloud.third.sms.util.SignatureUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 阿里云国际短信（SendMessageToGlobe / 模板 SendMessageWithTemplate）。
 *
 * @author whaleal-dev
 * @author 恒哥
 */
@Slf4j
public class AliyunInternationalOutboundSender implements OutboundSender {

    private static final String DEFAULT_ENDPOINT = "https://dysmsapi.ap-southeast-1.aliyuncs.com";
    private static final String VERSION = "2018-05-01";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateId() != null
                && !message.getBusinessInfo().getTemplateId().isBlank()) {
            return sendTemplateMessage(message, config);
        }
        validateAuth(config);
        if (isBlank(message.getTo()) || isBlank(message.getContent())) {
            throw new SmsParameterException("国际短信号码与内容不能为空", SmsProviderType.ALIYUN_INTERNATIONAL);
        }
        Map<String, String> params = baseParams(config);
        params.put("Action", "SendMessageToGlobe");
        params.put("To", message.getTo());
        params.put("Message", message.getContent());
        if (!isBlank(message.getFrom())) {
            params.put("From", message.getFrom());
        }
        return doSend(params, config, message);
    }

    @Override
    public SmsOutboundMessage sendTemplateMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        validateAuth(config);
        String templateCode = message.getBusinessInfo() != null ? message.getBusinessInfo().getTemplateId() : null;
        if (isBlank(templateCode)) {
            throw new SmsParameterException("国际模板 TemplateCode 不能为空", SmsProviderType.ALIYUN_INTERNATIONAL);
        }
        Map<String, String> params = baseParams(config);
        params.put("Action", "SendMessageWithTemplate");
        params.put("To", message.getTo());
        params.put("TemplateCode", templateCode);
        if (message.getBusinessInfo() != null && message.getBusinessInfo().getTemplateParams() != null) {
            params.put("TemplateParam", JSON.toJSONString(message.getBusinessInfo().getTemplateParams()));
        }
        String from = firstNonBlank(message.getFrom(), config.getDefaultFrom(), config.getSignName());
        if (!isBlank(from)) {
            params.put("From", from);
        }
        return doSend(params, config, message);
    }

    private Map<String, String> baseParams(SmsProviderConfig config) {
        Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", firstNonBlank(config.getAccessKeyId(), config.getApiKey()));
        params.put("Format", "JSON");
        params.put("RegionId", firstNonBlank(config.getRegion(), "ap-southeast-1"));
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("Timestamp", utcNow());
        params.put("Version", VERSION);
        return params;
    }

    private SmsOutboundMessage doSend(Map<String, String> params, SmsProviderConfig config, SmsOutboundMessage message) {
        try {
            String secret = firstNonBlank(config.getAccessKeySecret(), config.getApiSecret()) + "&";
            String signature = SignatureUtils.hmacSha1Base64(stringToSign(params), secret);
            params.put("Signature", signature);
            String endpoint = firstNonBlank(config.getBaseUrl(), DEFAULT_ENDPOINT);
            String resp = httpGet(endpoint + "/?" + encodeQuery(params), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("阿里云国际发送失败: " + e.getMessage(),
                    SmsProviderType.ALIYUN_INTERNATIONAL, e);
        }
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        String code = json.getString("ResponseCode");
        if (code == null) {
            code = json.getString("Code");
        }
        if ("OK".equalsIgnoreCase(code)) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(firstNonBlank(json.getString("MessageId"), json.getString("BizId")));
            message.setMessageId(message.getProviderMessageId());
            message.setProviderType(SmsProviderType.ALIYUN_INTERNATIONAL);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "aliyun_international",
                code, firstNonBlank(json.getString("ResponseDescription"), json.getString("Message")));
        return message;
    }

    private static String stringToSign(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder canonical = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                canonical.append("&");
            }
            canonical.append(percentEncode(e.getKey())).append("=").append(percentEncode(e.getValue()));
            first = false;
        }
        return "GET&%2F&" + percentEncode(canonical.toString());
    }

    private static String encodeQuery(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder q = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                q.append("&");
            }
            q.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            first = false;
        }
        return q.toString();
    }

    private static String httpGet(String url, SmsProviderConfig config) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        int timeout = config.getRequestTimeout() != null ? config.getRequestTimeout() : 10000;
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
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

    private static String percentEncode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String utcNow() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date());
    }

    private void validateAuth(SmsProviderConfig config) {
        if (isBlank(firstNonBlank(config.getAccessKeyId(), config.getApiKey()))
                || isBlank(firstNonBlank(config.getAccessKeySecret(), config.getApiSecret()))) {
            throw new SmsCredentialsException("阿里云国际 AccessKey 不能为空");
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.ALIYUN_INTERNATIONAL.name();
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
