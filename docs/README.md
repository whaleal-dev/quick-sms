# Quick SMS 文档

> 让发送短信变得更简单——国内与国际统一接入，面向 SaaS 多租户。

**公开文档站（推荐）：** https://whaleal-dev.github.io/quick-sms/  
源码目录：[`docs-site/`](../docs-site/README.md)（Docusaurus，与 aihub 同套发布方式）。

本目录保留 Markdown 速查，内容与文档站同步维护时以 `docs-site/docs/` 为准。

## 目录（速查）

| 章节 | 说明 |
|------|------|
| [前言](intro.md) | 为什么做、解决什么问题、适用场景 |
| [Spring Boot 快速开始](quickstart-springboot.md) | 依赖、Bean、Controller 发信与 Webhook |
| [JavaSE 快速开始](quickstart-javase.md) | 无 Spring：`SmsClients.builder()` |
| [进阶能力](features.md) | Failover、黑名单限流、Webhook 安全、指标、代理 |
| [API 详解](api.md) | `SmsClient` / `SmsSendRequest` / 错误码等 |
| [厂商接入说明](providers.md) | 每家凭证、模板/内容、回调字段 |
| [CI / CD](ci-cd.md) | Maven Central 发布（`release-*`） |

文档站额外提供通用短信技术说明：**出站 · 入站 · Report · Webhook**。

返回仓库首页：[README](../README.md)
