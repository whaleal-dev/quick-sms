# Quick SMS 文档

> 让发送短信变得更简单——国内与国际统一接入，面向 SaaS 多租户。

本目录是项目文档站，按「前言 → 快速开始 → 进阶 → API → 厂商」组织。

## 目录

| 章节 | 说明 |
|------|------|
| [前言](intro.md) | 为什么做、解决什么问题、适用场景 |
| [Spring Boot 快速开始](quickstart-springboot.md) | 最常用：依赖、Bean、Controller 发信与 Webhook |
| [JavaSE 快速开始](quickstart-javase.md) | 无 Spring：`SmsClients.builder()` |
| [进阶能力](features.md) | Failover、黑名单限流、Webhook 安全、指标、代理 |
| [API 详解](api.md) | `SmsClient` / `SmsSendRequest` / 错误码等 |
| [厂商接入说明](providers.md) | 每家凭证、模板/内容、回调字段 |
| [CI / CD](ci-cd.md) | GitHub Packages / **Maven Central** 发布 |

返回仓库首页：[README](../README.md) · 示例：[examples](../examples/README.md) · 变更：[CHANGELOG](../CHANGELOG.md)

## 30 秒示例

```java
SmsClient client = SmsClients.builder()
        .provider(SmsProviderType.MOCK)
        .build();
client.sendText("13800138000", "【QuickSMS】验证码 1234");
```

Maven 坐标：`io.github.whaleal-dev:sms-all:1.0.0`（发到 Central 后可直接依赖；或 starter + providers 按需组合）。

## 设计要点（务必读）

1. **不强制 `application.yml`**：供应商与秘钥在调用时传入（`SmsCredentials`）。
2. **starter 不传递厂商 jar**：请显式引入 `sms-providers-cn` / `sms-providers-intl`，或直接用 `sms-all`。
3. **SDK 不提供 HTTP 服务**：回执 URL 由你的应用接收，再用 `SmsWebhookHandler` 解析。
