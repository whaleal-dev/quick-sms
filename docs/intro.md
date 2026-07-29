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

## 与 SMS4J、easy-sms 的关系

| 项目 | 定位 | Quick SMS 借鉴点 |
|------|------|------------------|
| [SMS4J](https://github.com/dromara/SMS4J) | Java 国内短信聚合，yml blends 体验优秀 | 国内厂商清单、快捷发送、多通道思想 |
| [easy-sms](https://github.com/overtrue/easy-sms) | PHP 多网关 + Strategy | 通道策略、统一报文思路 |
| **Quick SMS** | 国内 + **国际**、回执上行全链路、**动态凭证** | — |

**刻意不同：**

- SMS4J 主推配置文件 blends；我们默认 **代码 / 请求传凭证**，更贴合多租户网关。
- 我们把 **国际通道** 与 **回执 / 上行 SPI** 作为一等能力，而不只是「能发出去」。

两者可以并存：单应用国内验证码可继续用 SMS4J；需要国际、Webhook 网关、租户级密钥时用 Quick SMS。

## 适用场景

- 中后台 / SaaS：每个租户不同短信通道与 AK/SK
- 出海业务：Twilio / Vonage 等与国内云并存
- 短信网关：统一接收回执与上行，再写入自有消息中心
- 本地联调：`MOCK` 供应商零外部依赖

## 下一步

1. [Spring Boot 快速开始](quickstart-springboot.md)
2. 或 [JavaSE 快速开始](quickstart-javase.md)
3. 按厂商核对凭证字段：[厂商接入说明](providers.md)
