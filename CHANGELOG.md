# Changelog

## 1.0.1 — 2026-08-17

### Changed
- Maven `groupId`：`com.whaleal.third` → `io.github.whaleal-dev`（Java 包名不变）

### Added
- 吸收 [easy-sms](https://github.com/overtrue/easy-sms) 能力：
  - 按通道覆盖正文/模板：`SmsSendRequest.contentByProvider` / `templateIdByProvider` / `templateParamsByProvider`（failover 友好）
  - 新增国内厂商：短信宝、互亿无线、聚合数据、云之讯、SendCloud、华信、火山引擎
- `ProviderHttp.get`：支持短信宝 / 聚合等 GET 接口
- GitHub Packages 发布：默认 `distributionManagement` 指向本仓库；新增 `publish-github-packages.yml`
- CI/CD：`ci.yml`（PR/main 构建测试）；推送 **`release-*`** 分支自动发布到 GitHub Packages 并创建 Release

### Changed
- README / `docs/providers.md` 厂商表同步
- 项目 URL / Packages 目标统一为 [whaleal-dev/quick-sms](https://github.com/whaleal-dev/quick-sms)
- 新增 [docs/ci-cd.md](docs/ci-cd.md)；移除 Maven Central 自动发布 workflow（按需可再加）

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
- **文档站**：`docs/`（前言、SpringBoot/JavaSE 快速开始、进阶、API、厂商详解）
- 示例：`examples/`

### Notes
- Starter **不**传递厂商 jar；按需引入 `sms-providers-cn` / `sms-providers-intl`
- 天翼云 / 百度签名按官方 EOP / BCE；国际三云（阿里/腾讯/华为）签名与响应解析已补齐
- 新国内厂商 `ReportFetcher` SPI（Webhook 优先，可配 `statusReportUrl`）
