# 短信概念总览

短信业务通常不止「把字发出去」。一套可运营的短信能力，至少覆盖四类方向：

| 概念 | 常见叫法 | 方向 | 作用 |
|------|----------|------|------|
| **出站** | MT / Outbound / Submit | 你 → 运营商/厂商 → 用户 | 发验证码、通知、营销 |
| **入站** | MO / Inbound | 用户 → 厂商 → 你 | 用户回复短信、上行关键词 |
| **状态报告** | DLR / Report / Delivery Report | 厂商 → 你（推或拉） | 知道是否送达、失败原因 |
| **Webhook** | 回调 / Callback | 厂商 HTTP POST → 你的 URL | 异步推送回执、上行等 |

```text
                    ┌──────────────┐
   你的业务系统 ──► │  出站 Submit  │ ──► 短信厂商 / 运营商 ──► 用户手机
                    └──────────────┘
                           ▲
                           │ 主动查询 Report（部分厂商）
                           │
用户回复 / 送达结果 ──► 厂商 ──HTTP Webhook──► 你的回调接口
                              │                 ├─ 回执（Receipt）
                              │                 └─ 上行（Inbound）
```

## 和 Quick SMS 的对应关系

| 行业概念 | SDK 入口 |
|----------|----------|
| 出站发送 | `SmsClient.send` / `sendText` / `sendTemplate` |
| 入站上行 | `SmsWebhookHandler.parseInbound` |
| 回执（推送） | `SmsWebhookHandler.parseReceipt` |
| 状态报告（拉取） | `SmsWebhookHandler.fetchReport` / `fetchReports` |
| 回调安全 | `SmsWebhookHandler.verifyWebhook` |

> SDK **不提供** HTTP 服务。你需要在自己的应用里暴露回调 URL，收到请求后再交给 `SmsWebhookHandler` 解析。

## 为什么要分清这四件事

1. **出站成功 ≠ 用户已收到**：HTTP 200 / `messageId` 通常只表示「厂商已受理」。
2. **回执与 Report 互补**：多数场景靠 Webhook 推送回执；查询接口用于补洞、对账。
3. **上行是另一条链路**：验证码场景少用；客服、退订、互动营销必须单独接。
4. **安全不可省**：回调 URL 暴露在公网时，要做签名校验与防重放。

## 阅读顺序

1. [出站（Outbound）](./outbound.md)
2. [入站（Inbound）](./inbound.md)
3. [状态报告（Report）](./report.md)
4. [Webhook 回调](./webhook.md)
