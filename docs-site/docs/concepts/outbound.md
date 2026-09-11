# 出站（Outbound / MT）

出站短信（Mobile Terminated，MT）指：**由你的系统发起，经短信厂商投递给用户手机**。这是最常见的能力。

## 典型场景

- 登录 / 注册验证码
- 订单、物流、告警通知
- 营销活动（需合规与退订机制）

## 行业里的两种内容形态

| 形态 | 说明 | 国内常见要求 |
|------|------|----------------|
| **模板短信** | 预先在厂商控制台报备模板，发送时只传模板 ID + 变量 | 阿里云、腾讯云等强制或强烈建议 |
| **内容短信** | 直接传完整正文（含签名） | 短信宝、部分国际通道更灵活 |

Quick SMS 同时支持：

- `sendTemplate(to, templateId, params)`
- `sendText(to, content)`
- 多通道时用 `contentByProvider` / `templateIdByProvider` 按厂商覆盖

## 发送链路（简化）

```text
业务请求
  → 校验号码 / 黑名单 / 限流
  → 选择通道（指定厂商 或 failover）
  → 签名并调用厂商 HTTP API
  → 得到 messageId（受理成功）
  → （异步）等待回执 / Report 确认送达
```

## Quick SMS 用法

```java
SmsClient client = SmsClients.builder()
        .provider(SmsProviderType.ALIYUN)
        .build();

// 模板
client.send(SmsSendRequest.builder()
        .to("13800138000")
        .templateId("SMS_123456")
        .templateParams(Map.of("code", "1234"))
        .credentials(SmsCredentials.builder()
                .accessKeyId(ak)
                .accessKeySecret(sk)
                .build())
        .build());

// 纯文本
client.sendText("13800138000", "【QuickSMS】您的验证码是 1234");
```

## 关键字段（概念层）

| 字段 | 含义 |
|------|------|
| `to` | 目标号码（国内常 11 位；国际常 E.164） |
| `content` / `templateId` | 正文或模板 |
| `signName` | 短信签名（部分厂商独立字段） |
| `credentials` | 该次请求的 AK/SK（多租户必传） |
| `provider` / `providerCode` | 指定通道；不指定则走 failover |

## 常见误区

1. **把「提交成功」当成「送达」** — 应结合 [回执](./webhook.md) / [Report](./report.md)。
2. **把密钥写死在 yml** — SaaS 场景请每次传 `SmsCredentials`。
3. **忽略签名与模板报备** — 国内通道未报备常直接失败。

## 下一步

- [Spring Boot 快速开始](../getting-started/quickstart-spring-boot.md)
- [入站（Inbound）](./inbound.md)
