# Quick SMS 文档

> 让发送短信变得更简单——国内与国际统一接入，面向 SaaS 多租户。

**公开文档站（推荐）：** <https://docs.whaleal.com/quick-sms/>  
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

## Whaleal SMS 云平台

**托管式多渠道短信中转平台**（BYOL：用你自己的 CPaaS 账号，平台不经手资费）。文档站章节：

| 页面 | 说明 |
|------|------|
| [平台总览](https://docs.whaleal.com/quick-sms/docs/platform/overview) | 与 SDK 的关系、能力边界、选型 |
| [接入方式总览](https://docs.whaleal.com/quick-sms/docs/platform/getting-started) | 三条路径 + 五步跑通第一条短信 |
| [控制台使用指南](https://docs.whaleal.com/quick-sms/docs/platform/console) | 侧边栏每个页面解决什么问题 |
| [开放 API](https://docs.whaleal.com/quick-sms/docs/platform/open-api) | 鉴权、三个端点、错误码、重发红线 |
| [状态回调](https://docs.whaleal.com/quick-sms/docs/platform/callback) | 两条回调链路、Spring Boot 示例 |
| [套餐与配额](https://docs.whaleal.com/quick-sms/docs/platform/plans) | 四档套餐、QPS、用量对账 |
| [能力路线图](https://docs.whaleal.com/quick-sms/docs/platform/roadmap) | 已上线能力与规划中的能力 |
| [常见问题](https://docs.whaleal.com/quick-sms/docs/platform/faq) | 排错与对外口径 |

控制台：<https://sms.whaleal.com> · 开放 API 基址：`https://smsapi.whaleal.com/open/v1`

文档站额外提供通用短信技术说明：**出站 · 入站 · Report · Webhook**。

返回仓库首页：[README](../README.md)
