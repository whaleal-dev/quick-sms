package com.whaleal.ark.cloud.third.sms.provider.huaxin;

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

/**
 * 华信短信平台（内容短信，参考 easy-sms HuaxinGateway）。
 * <p>凭证：apiKey=account，apiSecret=password；userId 用 appId；网关 IP/域名写入 baseUrl 或 region。</p>
 *
 * @author 恒哥
 * @since 2026-08-17
 */
public class HuaxinOutboundSender implements OutboundSender {

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String account = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String password = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        String userId = firstNonBlank(config.getAppId(), config.getStringConfig("userId", null));
        if (isBlank(account) || isBlank(password)) {
            throw new SmsCredentialsException("华信 account/password 不能为空");
        }
        String content = message.getContent();
        if (isBlank(content)) {
            throw new SmsParameterException("华信短信内容不能为空");
        }
        String url = resolveUrl(config);
        Map<String, String> form = new HashMap<>();
        if (!isBlank(userId)) {
            form.put("userid", userId);
        }
        form.put("account", account);
        form.put("password", password);
        form.put("mobile", normalizeMobile(message.getTo()));
        form.put("content", content);
        form.put("sendTime", "");
        form.put("action", "send");
        String extNo = config.getStringConfig("extNo", config.getStringConfig("ext_no", ""));
        form.put("extno", extNo == null ? "" : extNo);
        try {
            String resp = ProviderHttp.postForm(url, form, null, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("华信发送失败: " + e.getMessage(), SmsProviderType.HUAXIN, e);
        }
    }

    private static String resolveUrl(SmsProviderConfig config) {
        if (!isBlank(config.getBaseUrl())) {
            return config.getBaseUrl().trim();
        }
        String host = firstNonBlank(config.getRegion(), config.getStringConfig("ip", null));
        if (isBlank(host)) {
            throw new SmsParameterException("华信需配置 baseUrl 或 region(ip)");
        }
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host.contains("smsJson.aspx") ? host : host.replaceAll("/$", "") + "/smsJson.aspx";
        }
        return "http://" + host + "/smsJson.aspx";
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        String status = json.getString("returnstatus");
        if ("Success".equalsIgnoreCase(status)) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(json.getString("taskID"));
            message.setProviderType(SmsProviderType.HUAXIN);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "huaxin",
                status, json.getString("message"));
        return message;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.HUAXIN.name();
    }

    @Override
    public boolean supportsTemplate() {
        return false;
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
