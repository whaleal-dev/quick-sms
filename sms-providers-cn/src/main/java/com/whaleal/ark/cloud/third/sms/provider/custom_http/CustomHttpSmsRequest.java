package com.whaleal.ark.cloud.third.sms.provider.custom_http;


import lombok.Data;

import java.util.Map;

/**
 * 自定义HTTP短信发送请求
 * 支持106短信、手机卡机房等自定义HTTP接口
 */
@Data
public class CustomHttpSmsRequest {

    /**
     * 平台配置参数
     */
    private Map<String, String> platformParams;

    /**
     * API接口地址
     */
    private String apiUrl;

    /**
     * 请求方法 GET/POST/POST_JSON
     */
    private String requestMethod;

    /**
     * 请求头
     */
    private Map<String, String> headers;

    /**
     * 请求体模板
     */
    private String requestBodyTemplate;

    /**
     * 发送参数
     */
    private String from;
    private String to;
    private String content;
    private String messageType = "text";

    /**
     * 超时时间（秒）
     */
    private Integer timeout = 30;

    /**
     * 重试次数
     */
    private Integer retryCount = 3;

    /**
     * 客户端引用
     */
    private String clientRef;
}
