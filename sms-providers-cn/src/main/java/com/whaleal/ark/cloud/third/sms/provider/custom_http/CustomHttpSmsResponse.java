package com.whaleal.ark.cloud.third.sms.provider.custom_http;


import lombok.Data;

import java.util.Map;

/**
 * 自定义HTTP短信发送响应
 */
@Data
public class CustomHttpSmsResponse {

    /**
     * HTTP状态码
     */
    private Integer statusCode;

    /**
     * 响应状态消息
     */
    private String statusMessage;

    /**
     * 响应体
     */
    private String responseBody;

    /**
     * 响应头
     */
    private Map<String, String> responseHeaders;

    /**
     * 发送是否成功
     */
    private Boolean success;

    /**
     * 消息ID（由第三方平台返回）
     */
    private String messageId;

    /**
     * 错误代码
     */
    private String errorCode;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 剩余余额
     */
    private String remainingBalance;

    /**
     * 消息费用
     */
    private String messagePrice;

    /**
     * 网络信息
     */
    private String network;

    /**
     * 发送时间戳
     */
    private Long timestamp;

    /**
     * 原始响应数据（用于调试）
     */
    private String rawResponse;

    /**
     * 创建成功响应
     */
    public static CustomHttpSmsResponse success(String messageId, String responseBody) {
        CustomHttpSmsResponse response = new CustomHttpSmsResponse();
        response.setSuccess(true);
        response.setMessageId(messageId);
        response.setResponseBody(responseBody);
        response.setStatusCode(200);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    /**
     * 创建失败响应
     */
    public static CustomHttpSmsResponse failure(String errorCode, String errorMessage, String responseBody) {
        CustomHttpSmsResponse response = new CustomHttpSmsResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        response.setResponseBody(responseBody);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }
}
