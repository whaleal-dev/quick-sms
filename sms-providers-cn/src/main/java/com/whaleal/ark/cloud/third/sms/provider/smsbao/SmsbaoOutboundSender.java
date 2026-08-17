package com.whaleal.ark.cloud.third.sms.provider.smsbao;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 短信宝发送（内容短信，参考 easy-sms SmsbaoGateway）。
 * <p>凭证：apiKey=user，apiSecret=password（MD5 后提交）。</p>
 *
 * @author 恒哥
 * @since 2026-08-17
 */
public class SmsbaoOutboundSender implements OutboundSender {

    private static final String DEFAULT_SMS = "http://api.smsbao.com/sms";
    private static final String DEFAULT_WSMS = "http://api.smsbao.com/wsms";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String user = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String password = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        if (isBlank(user) || isBlank(password)) {
            throw new SmsCredentialsException("短信宝 user/password 不能为空");
        }
        String content = message.getContent();
        if (isBlank(content)) {
            throw new SmsParameterException("短信宝短信内容不能为空");
        }
        String to = message.getTo() == null ? "" : message.getTo().trim();
        boolean intl = looksInternational(to);
        String mobile = intl ? to.replace(" ", "") : normalizeMobile(to);
        String url = firstNonBlank(config.getBaseUrl(), intl ? DEFAULT_WSMS : DEFAULT_SMS);
        Map<String, String> query = new HashMap<>();
        query.put("u", user);
        query.put("p", SignatureUtils.md5(password));
        query.put("m", mobile);
        query.put("c", content);
        try {
            String resp = ProviderHttp.get(url, query, null, (int) getTimeoutMs(), config);
            return parse(resp == null ? "" : resp.trim(), message);
        } catch (Exception e) {
            throw new SmsNetworkException("短信宝发送失败: " + e.getMessage(), SmsProviderType.SMSBAO, e);
        }
    }

    private SmsOutboundMessage parse(String code, SmsOutboundMessage message) {
        if ("0".equals(code)) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(UUID.randomUUID().toString().replace("-", ""));
            message.setProviderType(SmsProviderType.SMSBAO);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "smsbao", code, errorMsg(code));
        return message;
    }

    private static String errorMsg(String code) {
        return switch (code) {
            case "-1" -> "参数不全";
            case "-2" -> "服务器空间不支持";
            case "30" -> "密码错误";
            case "40" -> "账号不存在";
            case "41" -> "余额不足";
            case "42" -> "帐户已过期";
            case "43" -> "IP地址限制";
            case "50" -> "内容含有敏感词";
            default -> "发送失败";
        };
    }

    private static boolean looksInternational(String to) {
        if (to == null) return false;
        String t = to.trim();
        if (t.startsWith("+") && !t.startsWith("+86")) return true;
        return t.startsWith("00") && !t.startsWith("0086");
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.SMSBAO.name();
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
