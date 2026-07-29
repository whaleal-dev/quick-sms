# Changelog

## 1.0.0 — 2026-07-29

### Added
- 模块拆分：`sms-api` / `sms-core` / `sms-runtime` / `sms-providers-cn` / `sms-providers-intl` / `sms-spring-boot-starter` / `sms-all`
- 快捷 API：`sendText` / `sendTemplate`；调用时动态凭证（无强制 yml）
- 通道 failover：`SmsChannel` + `OrderChannelStrategy` / `RandomChannelStrategy`
- 黑名单 / 限流 / HTTP 代理：`PhoneBlacklist`、`RateLimiter`、`proxyHost/proxyPort`
- Webhook 安全：`WebhookSecurity`、`SmsWebhookHandler.verifyWebhook`
- 统一错误码：`SmsErrorCodes` + `ProviderErrorMapper`
- 可观测性：`SmsMetrics`、内置 `MetricsCollector`、可选 Micrometer（`sms.send` / `sms.send.duration`）
- 国内厂商：阿里/腾讯/华为/三大运营商、云片、创蓝、容联、七牛、螺丝帽、SUBMAIL、天翼云、网易云信、百度、助通、Custom HTTP
- 国际厂商：Twilio、Vonage、AWS、MessageBird、Plivo、Infobip 及云厂商国际通道
- **文档站**：`docs/`（前言、SpringBoot/JavaSE 快速开始、进阶、API、厂商详解，风格参考 SMS4J）
- 示例：`examples/`

### Notes
- Starter **不**传递厂商 jar；按需引入 `sms-providers-cn` / `sms-providers-intl`
- 天翼云 / 百度签名对齐 SMS4J（EOP / BCE）；国际三云（阿里/腾讯/华为）签名与响应解析已补齐
- 新国内厂商 `ReportFetcher` SPI（Webhook 优先，可配 `statusReportUrl`）
