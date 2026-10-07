# 前言

## Quick SMS —— 让发送短信变得更简单

在日常开发中，短信发送非常常见（验证码、通知、营销）。并不是每家公司都有自建短信网关，第三方短信往往是最务实的方案。可是：

- 市面上厂商众多，协议、签名算法、模板规则各不相同
- 每接入一家就要重新阅读文档、编写工具类
- 中途换厂商、或多通道容灾时，改造成本很高
- SaaS / 多租户场景下，凭证不能写死在配置文件里

Quick SMS 希望把这些重复劳动收敛成一套 **统一 API + 可插拔厂商 SPI**：

| 你关心的事 | Quick SMS 怎么做 |
|------------|------------------|
| 发验证码 / 通知 | `sendText` / `sendTemplate` |
| 多家厂商 | `SmsProviderType` 或 SPI `providerCode` |
| 换通道 / 容灾 | `addChannel` + failover 策略 |
| 多租户秘钥 | 每次请求传 `SmsCredentials`，**不强制 yml** |
| 送达与用户回复 | `SmsWebhookHandler` 解析回执 / 上行 |
| 只要国内或只要国际 | 分模块引入 `sms-providers-cn` / `intl` |

如果你觉得它帮你省了时间，欢迎给仓库点 Star，也欢迎进 QQ 群交流：`1021755322`。

## 产品特点

- **国内 + 国际**：主流国内厂商与 Twilio、Vonage 等国际通道统一接入
- **动态凭证**：默认代码 / 请求传密钥，贴合多租户网关，不强制配置文件
- **全链路回调**：回执 / 上行 SPI 为一等能力，而不只是「能发出去」

## 官方托管平台：Whaleal SMS

Quick SMS 是 **SDK** —— 通道、密钥、回调、日志都在你自己的进程里。如果你更希望**把多家供应商收进一个后台统一管理**，官方提供了托管平台 **[Whaleal SMS](https://sms.whaleal.com)**，两者是**同一套引擎的两种交付形态**（平台的多厂商发信能力由 Quick SMS 提供）：

| 你关心的事 | Whaleal SMS 云平台怎么做 |
|---|---|
| 加一家渠道做灾备 | 控制台点选绑定，**权重与优先级随时可调，不用改代码发版** |
| 多渠道分发与容灾 | 权重相等 → 顺序主备；权重不等 → 平滑加权轮询，失败自动切换 |
| Java 之外还要接（PHP / Go / Node） | 一套 **HTTP API** 对接全部渠道，无语言绑定 |
| 排障要在多个供应商后台之间来回切 | 全渠道日志、报表、路由链集中在一处 |
| 密钥要轮换 | 凭据加密存放，**一处改完** |
| 想控制成本 | **纯订阅、不按条计费**；短信费仍由你自己的 CPaaS 账号直接结算 |

- 不想自己维护通道，用平台：[平台总览与选型](./platform/overview.md)
- 五步跑通第一条短信：[接入方式总览](./platform/getting-started.md)
- 接口细节：[开放 API 参考](./platform/open-api.md)
- 线上控制台：[sms.whaleal.com](https://sms.whaleal.com)

> 平台是**叠加的管理层**，不是 CPaaS 替代品：不售卖短信、不经手资费、不承诺送达、不改变底层链路。你的供应商合同、号码与资费全部保留，也可以随时切回 SDK 自建。

## 适用场景

- 中后台 / SaaS：每个租户不同短信通道与 AK/SK
- 出海业务：Twilio / Vonage 等与国内云并存
- 短信网关：统一接收回执与上行，再写入自有消息中心
- 本地联调：`MOCK` 供应商零外部依赖

## 下一步

1. **想直接用托管平台** → [Whaleal SMS 云平台](./platform/overview.md)（[五步接入](./platform/getting-started.md)）
2. 先读 [短信概念总览](./concepts/overview.md)（出站 / 入站 / Report / Webhook）
3. [Spring Boot 快速开始](./getting-started/quickstart-spring-boot.md)
4. 或 [JavaSE 快速开始](./getting-started/quickstart-java.md)
5. 按厂商核对凭证字段：[厂商接入说明](./guide/providers.md)
