
package com.whaleal.ark.cloud.third.sms.provider.custom_http;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.whaleal.ark.cloud.third.sms.provider.custom_http.CustomHttpSmsRequest;
import com.whaleal.ark.cloud.third.sms.provider.custom_http.CustomHttpSmsResponse;
import com.whaleal.ark.cloud.third.sms.util.HttpUrlUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义HTTP短信发送工具类
 * 支持106短信、手机卡机房等各种自定义HTTP接口
 */
@Slf4j
public class CustomHttpSmsUtil {

    /**
     * 发送自定义HTTP短信
     */
    public CustomHttpSmsResponse sendSms(CustomHttpSmsRequest request) {
        try {
            log.info("开始发送自定义HTTP短信，目标号码：{}, 内容：{}", request.getTo(), request.getContent());

            // 构建请求URL
            String requestUrl = buildRequestUrl(request);

            // 构建请求体
            String requestBody = buildRequestBody(request);

            // 发送HTTP请求
            HttpResponse httpResponse = executeHttpRequest(request, requestUrl, requestBody);

            // 解析响应
            CustomHttpSmsResponse response = parseResponse(httpResponse, request);

            log.info("自定义HTTP短信发送完成，状态：{}，消息ID：{}", response.getSuccess(), response.getMessageId());

            return response;

        } catch (Exception e) {
            log.error("自定义HTTP短信发送异常", e);
            return CustomHttpSmsResponse.failure("SEND_ERROR", e.getMessage(), null);
        }
    }

    /**
     * 构建请求URL
     */
    private String buildRequestUrl(CustomHttpSmsRequest request) {
        String url = HttpUrlUtils.preferHttps(request.getApiUrl());

        // 如果是GET请求，需要将参数附加到URL
        if ("GET".equalsIgnoreCase(request.getRequestMethod())) {
            Map<String, String> params = buildRequestParams(request);
            if (!params.isEmpty()) {
                StringBuilder urlBuilder = new StringBuilder(url);
                urlBuilder.append(url.contains("?") ? "&" : "?");

                params.forEach((key, value) -> {
                    urlBuilder.append(key).append("=").append(value).append("&");
                });

                // 移除最后的&
                url = urlBuilder.substring(0, urlBuilder.length() - 1);
            }
        }

        return url;
    }

    /**
     * 构建请求体
     */
    private String buildRequestBody(CustomHttpSmsRequest request) {
        if ("GET".equalsIgnoreCase(request.getRequestMethod())) {
            return null;
        }

        String template = request.getRequestBodyTemplate();
        if (StrUtil.isBlank(template)) {
            // 如果没有模板，使用默认的JSON格式
            return buildDefaultJsonBody(request);
        }

        // 替换模板中的变量
        return replaceTemplateVariables(template, request);
    }

    /**
     * 构建默认JSON请求体
     */
    private String buildDefaultJsonBody(CustomHttpSmsRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("to", request.getTo());
        body.put("from", request.getFrom());
        body.put("content", request.getContent());
        body.put("type", request.getMessageType());

        // 添加平台参数
        if (request.getPlatformParams() != null) {
            body.putAll(request.getPlatformParams());
        }

        return JSONUtil.toJsonStr(body);
    }

    /**
     * 替换模板中的变量
     */
    private String replaceTemplateVariables(String template, CustomHttpSmsRequest request) {
        String result = template;

        // 基础变量替换
        result = result.replace("{to}", request.getTo());
        result = result.replace("{from}", request.getFrom());
        result = result.replace("{content}", request.getContent());
        result = result.replace("{type}", request.getMessageType());
        result = result.replace("{clientRef}", StrUtil.nullToDefault(request.getClientRef(), ""));

        // 平台参数变量替换
        if (request.getPlatformParams() != null) {
            for (Map.Entry<String, String> entry : request.getPlatformParams().entrySet()) {
                String key = "{" + entry.getKey() + "}";
                String value = StrUtil.nullToDefault(entry.getValue(), "");
                result = result.replace(key, value);
            }
        }

        return result;
    }

    /**
     * 构建请求参数（用于GET请求）
     */
    private Map<String, String> buildRequestParams(CustomHttpSmsRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("to", request.getTo());
        params.put("from", request.getFrom());
        params.put("content", request.getContent());
        params.put("type", request.getMessageType());

        // 添加平台参数
        if (request.getPlatformParams() != null) {
            params.putAll(request.getPlatformParams());
        }

        return params;
    }

    /**
     * 执行HTTP请求
     */
    private HttpResponse executeHttpRequest(CustomHttpSmsRequest request, String url, String body) {
        HttpRequest httpRequest;

        // 根据请求方法创建请求
        switch (request.getRequestMethod().toUpperCase()) {
            case "GET":
                httpRequest = HttpRequest.get(url);
                break;
            case "POST":
                httpRequest = HttpRequest.post(url);
                if (body != null) {
                    httpRequest.body(body);
                    httpRequest.contentType(ContentType.FORM_URLENCODED.toString());
                }
                break;
            case "POST_JSON":
                httpRequest = HttpRequest.post(url);
                if (body != null) {
                    httpRequest.body(body);
                    httpRequest.contentType(ContentType.JSON.toString());
                }
                break;
            default:
                throw new IllegalArgumentException("不支持的请求方法：" + request.getRequestMethod());
        }

        // 设置请求头
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(httpRequest::header);
        }

        // 设置超时时间
        httpRequest.timeout(request.getTimeout() * 1000);

        log.info("发送HTTP请求：{} {}", request.getRequestMethod(), url);
        log.debug("请求体：{}", body);

        return httpRequest.execute();
    }

    /**
     * 解析响应
     */
    private CustomHttpSmsResponse parseResponse(HttpResponse httpResponse, CustomHttpSmsRequest request) {
        CustomHttpSmsResponse response = new CustomHttpSmsResponse();

        response.setStatusCode(httpResponse.getStatus());
        response.setStatusMessage(httpResponse.body());
        response.setResponseBody(httpResponse.body());
        response.setRawResponse(httpResponse.body());
        response.setTimestamp(System.currentTimeMillis());

        // 解析响应头
        Map<String, String> responseHeaders = new HashMap<>();
        httpResponse.headers().forEach((key, values) -> {
            if (!values.isEmpty()) {
                responseHeaders.put(key, values.get(0));
            }
        });
        response.setResponseHeaders(responseHeaders);

        // 判断是否成功
        boolean isHttpSuccess = httpResponse.getStatus() >= 200 && httpResponse.getStatus() < 300;

        if (isHttpSuccess) {
            // 尝试解析响应体获取详细信息
            parseResponseDetails(response, httpResponse.body());
        } else {
            response.setSuccess(false);
            response.setErrorCode(String.valueOf(httpResponse.getStatus()));
            response.setErrorMessage("HTTP请求失败：" + httpResponse.body());
        }

        return response;
    }

    /**
     * 解析响应详细信息
     */
    private void parseResponseDetails(CustomHttpSmsResponse response, String responseBody) {
        try {
            // 尝试解析为JSON
            if (JSONUtil.isJson(responseBody)) {
                JSONObject jsonResponse = JSONUtil.parseObj(responseBody);

                // 常见的成功标识字段
                Object success = jsonResponse.get("success");
                Object status = jsonResponse.get("status");
                Object code = jsonResponse.get("code");

                // 判断是否成功
                boolean isSuccess = false;
                if (success != null) {
                    isSuccess = Boolean.parseBoolean(success.toString()) || "1".equals(success.toString());
                } else if (status != null) {
                    isSuccess = "success".equalsIgnoreCase(status.toString()) || "0".equals(status.toString());
                } else if (code != null) {
                    isSuccess = "0".equals(code.toString()) || "200".equals(code.toString());
                } else {
                    // 如果没有明确的状态字段，默认认为成功
                    isSuccess = true;
                }

                response.setSuccess(isSuccess);

                // 提取消息ID
                String messageId = extractMessageId(jsonResponse);
                response.setMessageId(messageId);

                // 提取错误信息
                if (!isSuccess) {
                    String errorCode = extractValue(jsonResponse, "errorCode", "error_code", "code");
                    String errorMessage = extractValue(jsonResponse, "errorMessage", "error_message", "message", "msg");
                    response.setErrorCode(errorCode);
                    response.setErrorMessage(errorMessage);
                }

                // 提取其他信息
                response.setRemainingBalance(extractValue(jsonResponse, "balance", "remaining_balance"));
                response.setMessagePrice(extractValue(jsonResponse, "price", "cost", "fee"));
                response.setNetwork(extractValue(jsonResponse, "network", "carrier"));

            } else {
                // 非JSON响应，简单判断是否包含成功关键字
                String lowerBody = responseBody.toLowerCase();
                boolean isSuccess = lowerBody.contains("success") || lowerBody.contains("ok") || lowerBody.contains("sent");
                response.setSuccess(isSuccess);

                if (!isSuccess) {
                    response.setErrorMessage("发送失败：" + responseBody);
                }
            }

        } catch (Exception e) {
            log.warn("解析响应详细信息失败：{}", e.getMessage());
            // 解析失败时默认认为成功（因为HTTP状态码是成功的）
            response.setSuccess(true);
        }
    }

    /**
     * 提取消息ID
     */
    private String extractMessageId(JSONObject jsonResponse) {
        // 常见的消息ID字段名
        String[] messageIdFields = {"messageId", "message_id", "msgId", "msg_id", "id", "smsId", "sms_id"};

        for (String field : messageIdFields) {
            Object value = jsonResponse.get(field);
            if (value != null) {
                return value.toString();
            }
        }

        return null;
    }

    /**
     * 提取指定字段的值
     */
    private String extractValue(JSONObject jsonResponse, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = jsonResponse.get(fieldName);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
}
