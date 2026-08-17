package com.whaleal.ark.cloud.third.sms.provider.huyi;

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

import java.util.HashMap;
import java.util.Map;

/**
 * 互亿无线发送（内容短信，参考 easy-sms HuyiGateway）。
 * <p>凭证：apiKey=api_id(account)，apiSecret=api_key；可选 signature 写入 config.signName。</p>
 *
 * @author 恒哥
 * @since 2026-08-17
 */
public class HuyiOutboundSender implements OutboundSender {

    private static final String DEFAULT_URL = "http://106.ihuyi.com/webservice/sms.php?method=Submit";

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        String account = firstNonBlank(config.getApiKey(), config.getAccessKeyId());
        String apiKey = firstNonBlank(config.getApiSecret(), config.getAccessKeySecret());
        if (isBlank(account) || isBlank(apiKey)) {
            throw new SmsCredentialsException("互亿无线 api_id/api_key 不能为空");
        }
        String content = message.getContent();
        if (isBlank(content)) {
            throw new SmsParameterException("互亿无线短信内容不能为空");
        }
        String mobile = normalizeMobile(message.getTo());
        long time = System.currentTimeMillis() / 1000;
        String password = SignatureUtils.md5(account + apiKey + mobile + content + time);

        Map<String, String> form = new HashMap<>();
        form.put("account", account);
        form.put("mobile", mobile);
        form.put("content", content);
        form.put("time", String.valueOf(time));
        form.put("format", "json");
        form.put("password", password);
        if (!isBlank(config.getSignName())) {
            form.put("sign", config.getSignName());
        } else if (!isBlank(config.getSignature())) {
            form.put("sign", config.getSignature());
        }

        String url = firstNonBlank(config.getBaseUrl(), DEFAULT_URL);
        try {
            String resp = ProviderHttp.postForm(url, form, null, (int) getTimeoutMs(), config);
            return parse(resp, message);
        } catch (Exception e) {
            throw new SmsNetworkException("互亿无线发送失败: " + e.getMessage(), SmsProviderType.HUYI, e);
        }
    }

    private SmsOutboundMessage parse(String resp, SmsOutboundMessage message) {
        JSONObject json = JSON.parseObject(resp);
        int code = json.getIntValue("code");
        if (code == 2) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.SUBMITTED);
            message.setProviderMessageId(json.getString("smsid"));
            message.setProviderType(SmsProviderType.HUYI);
            return message;
        }
        message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
        if (message.getExtraInfo() == null) {
            message.setExtraInfo(new HashMap<>());
        }
        ProviderErrorMapper.putMapped(message.getExtraInfo(), "huyi",
                String.valueOf(code), json.getString("msg"));
        return message;
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.HUYI.name();
    }

    @Override
    public boolean supportsTemplate() {
        return false;
    }

    private static String normalizeMobile(String to) {
        if (to == null) return "";
        String m = to.trim();
        if (m.startsWith("+86")) return m.substring(3).trim();
        if (m.startsWith("+")) {
            // 国际：区号与号码空格分隔（与 easy-sms 一致）
            int i = 1;
            while (i < m.length() && Character.isDigit(m.charAt(i))) {
                i++;
            }
            String idd = m.substring(1, i);
            String num = m.substring(i).replace(" ", "");
            return idd + " " + num;
        }
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
