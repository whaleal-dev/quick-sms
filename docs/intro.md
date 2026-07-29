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

## 适用场景

- 中后台 / SaaS：每个租户不同短信通道与 AK/SK
- 出海业务：Twilio / Vonage 等与国内云并存
- 短信网关：统一接收回执与上行，再写入自有消息中心
- 本地联调：`MOCK` 供应商零外部依赖

## 下一步

1. [Spring Boot 快速开始](quickstart-springboot.md)
2. 或 [JavaSE 快速开始](quickstart-javase.md)
3. 按厂商核对凭证字段：[厂商接入说明](providers.md)
