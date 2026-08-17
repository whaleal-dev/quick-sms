package com.whaleal.ark.cloud.third.sms.util;

import com.whaleal.ark.cloud.third.sms.config.SmsProviderConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 厂商 HTTP 调用公共工具（form / json）。
 *
 * @author 恒哥
 * @since 2026-07-29
 */
public final class ProviderHttp {

    private ProviderHttp() {
    }

    /**
     * HTTP GET（query 拼到 URL），用于短信宝 / 聚合等。
     */
    public static String get(String url, Map<String, String> query, Map<String, String> headers, int timeoutMs)
            throws Exception {
        return get(url, query, headers, timeoutMs, null, null);
    }

    public static String get(String url, Map<String, String> query, Map<String, String> headers,
                             int timeoutMs, SmsProviderConfig config) throws Exception {
        if (config == null) {
            return get(url, query, headers, timeoutMs);
        }
        return get(url, query, headers, resolveTimeout(config, timeoutMs),
                config.getProxyHost(), config.getProxyPort());
    }

    public static String get(String url, Map<String, String> query, Map<String, String> headers,
                             int timeoutMs, String proxyHost, Integer proxyPort) throws Exception {
        String full = appendQuery(url, query);
        HttpURLConnection conn;
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost.trim(), proxyPort));
            conn = (HttpURLConnection) URI.create(full).toURL().openConnection(proxy);
        } else {
            conn = (HttpURLConnection) URI.create(full).toURL().openConnection();
        }
        conn.setRequestMethod("GET");
        conn.setDoOutput(false);
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) >= 0) {
                buf.write(tmp, 0, n);
            }
            return buf.toString(StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    public static String appendQuery(String url, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return url;
        }
        String encoded = encodeForm(query);
        if (encoded.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + encoded;
    }

    public static String postForm(String url, Map<String, String> form, Map<String, String> headers, int timeoutMs)
            throws Exception {
        return postForm(url, form, headers, timeoutMs, null, null);
    }

    public static String postForm(String url, Map<String, String> form, Map<String, String> headers,
                                  int timeoutMs, SmsProviderConfig config) throws Exception {
        if (config == null) {
            return postForm(url, form, headers, timeoutMs);
        }
        return postForm(url, form, headers, resolveTimeout(config, timeoutMs),
                config.getProxyHost(), config.getProxyPort());
    }

    public static String postForm(String url, Map<String, String> form, Map<String, String> headers,
                                  int timeoutMs, String proxyHost, Integer proxyPort) throws Exception {
        String body = encodeForm(form);
        return post(url, body, "application/x-www-form-urlencoded;charset=UTF-8",
                headers, timeoutMs, proxyHost, proxyPort);
    }

    public static String postJson(String url, String json, Map<String, String> headers, int timeoutMs)
            throws Exception {
        return postJson(url, json, headers, timeoutMs, null, null);
    }

    public static String postJson(String url, String json, Map<String, String> headers,
                                  int timeoutMs, SmsProviderConfig config) throws Exception {
        if (config == null) {
            return postJson(url, json, headers, timeoutMs);
        }
        return postJson(url, json, headers, resolveTimeout(config, timeoutMs),
                config.getProxyHost(), config.getProxyPort());
    }

    public static String postJson(String url, String json, Map<String, String> headers,
                                  int timeoutMs, String proxyHost, Integer proxyPort) throws Exception {
        return post(url, json == null ? "{}" : json, "application/json;charset=UTF-8",
                headers, timeoutMs, proxyHost, proxyPort);
    }

    public static String post(String url, String body, String contentType, Map<String, String> headers, int timeoutMs)
            throws Exception {
        return post(url, body, contentType, headers, timeoutMs, null, null);
    }

    public static String post(String url, String body, String contentType, Map<String, String> headers,
                              int timeoutMs, String proxyHost, Integer proxyPort) throws Exception {
        HttpURLConnection conn;
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost.trim(), proxyPort));
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection(proxy);
        } else {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        }
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) >= 0) {
                buf.write(tmp, 0, n);
            }
            return buf.toString(StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    public static String encodeForm(Map<String, String> form) {
        if (form == null || form.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String v = e.getValue() == null ? "" : e.getValue();
            joiner.add(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(v, StandardCharsets.UTF_8));
        }
        return joiner.toString();
    }

    public static Map<String, String> basicAuthHeader(String username, String password) {
        String token = java.util.Base64.getEncoder().encodeToString(
                (username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
        return Collections.singletonMap("Authorization", "Basic " + token);
    }

    private static int resolveTimeout(SmsProviderConfig config, int fallback) {
        if (config.getRequestTimeout() != null && config.getRequestTimeout() > 0) {
            return config.getRequestTimeout();
        }
        if (config.getConnectTimeout() != null && config.getConnectTimeout() > 0) {
            return config.getConnectTimeout();
        }
        return fallback;
    }
}
