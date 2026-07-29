package com.whaleal.ark.cloud.third.sms.provider.aws;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;
import com.whaleal.ark.cloud.third.sms.outbound.entity.SmsOutboundMessage;
import com.whaleal.ark.cloud.third.sms.exception.SmsException;
import com.whaleal.ark.cloud.third.sms.exception.SmsTimeoutException;
import com.whaleal.ark.cloud.third.sms.exception.SmsCredentialsException;
import com.whaleal.ark.cloud.third.sms.exception.SmsParameterException;
import com.whaleal.ark.cloud.third.sms.exception.SmsNetworkException;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

import com.whaleal.ark.cloud.third.sms.outbound.sender.OutboundSender;

/**
 * AWS SNS短信发送器
 * 支持全球短信发送
 *
 * @author whaleal-dev
 * @author 恒哥
 */
public class AwsOutboundSender implements OutboundSender {

    private static final Logger logger = LoggerFactory.getLogger(AwsOutboundSender.class);

    private static final String SERVICE = "sns";
    private static final String ACTION = "Publish";
    private static final String VERSION = "2010-03-31";
    private static final int TIMEOUT_SECONDS = 10;

    @Override
    public SmsOutboundMessage sendMessage(SmsOutboundMessage message, SmsProviderConfig config) {
        logger.info("Sending SMS via AWS SNS to: {}", message.getTo());

        validateConfig(config);

        try {
            // 构建请求参数
            Map<String, String> params = buildRequestParams(message, config);

            // 生成签名
            String endpoint = getEndpoint(config);
            Map<String, String> headers = generateSignature(params, endpoint, config);

            // 发送HTTP请求
            String response = sendHttpRequest(params, headers, endpoint);

            // 解析响应
            return parseResponse(response, message);

        } catch (SmsException e) {
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to send SMS via AWS SNS", e);
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsNetworkException("Failed to send SMS via AWS SNS: " + e.getMessage(),
                SmsProviderType.AWS, e);
        }
    }

    @Override
    public String getSupportedProvider() {
        return SmsProviderType.AWS.name();
    }

    private void validateConfig(SmsProviderConfig config) {
        if (config == null) {
            throw new SmsParameterException("AWS SNS config cannot be null");
        }
        if (config.getAccessKeyId() == null || config.getAccessKeyId().trim().isEmpty()) {
            throw new SmsCredentialsException("AWS AccessKeyId cannot be null or empty");
        }
        if (config.getAccessKeySecret() == null || config.getAccessKeySecret().trim().isEmpty()) {
            throw new SmsCredentialsException("AWS SecretAccessKey cannot be null or empty");
        }
        if (config.getRegion() == null || config.getRegion().trim().isEmpty()) {
            throw new SmsParameterException("AWS region cannot be null or empty");
        }
    }

    private String getEndpoint(SmsProviderConfig config) {
        return "https://sns." + config.getRegion() + ".amazonaws.com";
    }

    private Map<String, String> buildRequestParams(SmsOutboundMessage message, SmsProviderConfig config) {
        Map<String, String> params = new TreeMap<>();

        params.put("Action", ACTION);
        params.put("Version", VERSION);
        params.put("PhoneNumber", message.getTo());
        params.put("Message", message.getContent());

        // 消息类型
        if (message.getMessageType() == SmsOutboundMessage.MessageType.MARKETING) {
            params.put("MessageAttributes.entry.1.Name", "AWS.SNS.SMS.SMSType");
            params.put("MessageAttributes.entry.1.Value.DataType", "String");
            params.put("MessageAttributes.entry.1.Value.StringValue", "Promotional");
        } else {
            params.put("MessageAttributes.entry.1.Name", "AWS.SNS.SMS.SMSType");
            params.put("MessageAttributes.entry.1.Value.DataType", "String");
            params.put("MessageAttributes.entry.1.Value.StringValue", "Transactional");
        }

        // 发送方ID
        if (message.getFrom() != null) {
            params.put("MessageAttributes.entry.2.Name", "AWS.SNS.SMS.SenderID");
            params.put("MessageAttributes.entry.2.Value.DataType", "String");
            params.put("MessageAttributes.entry.2.Value.StringValue", message.getFrom());
        }

        return params;
    }

    private Map<String, String> generateSignature(Map<String, String> params, String endpoint, SmsProviderConfig config) throws Exception {
        Map<String, String> headers = new HashMap<>();

        String timestamp = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").format(new Date());
        String date = timestamp.substring(0, 8);

        // 构建规范请求
        StringBuilder canonicalQueryString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                canonicalQueryString.append("&");
            }
            canonicalQueryString.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                .append("=").append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            first = false;
        }

        String host = endpoint.replace("https://", "");
        String canonicalRequest = "POST\n/\n\ncontent-type:application/x-www-form-urlencoded; charset=utf-8\nhost:" +
            host + "\nx-amz-date:" + timestamp + "\n\ncontent-type;host;x-amz-date\n" +
            sha256Hex(canonicalQueryString.toString());

        // 构建待签名字符串
        String credentialScope = date + "/" + config.getRegion() + "/" + SERVICE + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" +
            sha256Hex(canonicalRequest);

        // 计算签名
        byte[] kSecret = ("AWS4" + config.getAccessKeySecret()).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, date);
        byte[] kRegion = hmacSha256(kDate, config.getRegion());
        byte[] kService = hmacSha256(kRegion, SERVICE);
        byte[] kSigning = hmacSha256(kService, "aws4_request");
        String signature = bytesToHex(hmacSha256(kSigning, stringToSign));

        // 构建Authorization头
        String authorization = "AWS4-HMAC-SHA256 Credential=" + config.getAccessKeyId() + "/" +
            credentialScope + ", SignedHeaders=content-type;host;x-amz-date, Signature=" + signature;

        headers.put("Authorization", authorization);
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        headers.put("Host", host);
        headers.put("X-Amz-Date", timestamp);

        return headers;
    }

    private String sendHttpRequest(Map<String, String> params, Map<String, String> headers, String endpoint) throws Exception {
        // 构建请求体
        StringBuilder requestBody = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                requestBody.append("&");
            }
            requestBody.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                .append("=").append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            first = false;
        }

        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT_SECONDS * 1000);
            connection.setReadTimeout(TIMEOUT_SECONDS * 1000);

            // 设置请求头
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }

            // 发送请求体
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode == 200 ? connection.getInputStream() : connection.getErrorStream(),
                    StandardCharsets.UTF_8))) {

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                if (responseCode != 200) {
                    throw new SmsNetworkException("HTTP request failed with code: " + responseCode + ", response: " + response.toString(),
                        SmsProviderType.AWS);
                }

                return response.toString();
            }

        } catch (java.net.SocketTimeoutException e) {
            throw new SmsTimeoutException("Request timeout after " + TIMEOUT_SECONDS + " seconds",
                TIMEOUT_SECONDS * 1000, SmsProviderType.AWS);
        } finally {
            connection.disconnect();
        }
    }

    private SmsOutboundMessage parseResponse(String responseBody, SmsOutboundMessage message) {
        logger.debug("AWS SNS API response: {}", responseBody);

        try {
            // 检查是否有错误
            if (responseBody.contains("<Error>")) {
                String code = extractXmlValue(responseBody, "Code");
                String errorMessage = extractXmlValue(responseBody, "Message");

                message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
                throw new SmsException("AWS_SNS_API_ERROR", "AWS SNS API error: " + code + " - " + errorMessage,
                    SmsProviderType.AWS);
            }

            // 解析成功响应
            String awsMessageId = extractXmlValue(responseBody, "MessageId");
            String requestId = extractXmlValue(responseBody, "RequestId");

            message.setSendStatus(SmsOutboundMessage.SendStatus.SENT);
            message.setProviderMessageId(awsMessageId);  // ✅ 设置AWS消息ID作为提供商ID，不覆盖原始messageId
            message.setProviderType(SmsProviderType.AWS);

            return message;

        } catch (Exception e) {
            if (e instanceof SmsException) {
                throw e;
            }
            message.setSendStatus(SmsOutboundMessage.SendStatus.FAILED);
            throw new SmsException("RESPONSE_PARSE_ERROR", "Failed to parse AWS SNS response: " + e.getMessage(),
                SmsProviderType.AWS, e);
        }
    }

    private String extractXmlValue(String xml, String tag) {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";
        int startIndex = xml.indexOf(startTag);
        if (startIndex == -1) {
            return null;
        }
        startIndex += startTag.length();
        int endIndex = xml.indexOf(endTag, startIndex);
        if (endIndex == -1) {
            return null;
        }
        return xml.substring(startIndex, endIndex);
    }

    private String sha256Hex(String s) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKeySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
