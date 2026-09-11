# 入站（Inbound / MO）

入站短信（Mobile Originated，MO）指：**用户主动给某个号码发短信，厂商再把内容推给你**。

## 典型场景

- 用户回复「TD」退订营销短信
- 关键词互动（如回复 `1` 确认预约）
- 客服短信号码收件
- 双向上行会话（部分物联网 / 国际场景）

## 和出站的区别

| | 出站 MT | 入站 MO |
|--|---------|---------|
| 发起方 | 你的系统 | 用户手机 |
| 你关心的结果 | 是否送达 | 用户说了什么 |
| 常见接入 | 同步 API 提交 | **Webhook 推送**（少数可拉） |
| SDK API | `SmsClient` | `SmsWebhookHandler.parseInbound` |

## 数据里通常有什么

厂商回调体字段各异，概念上可统一为：

| 概念字段 | 含义 |
|----------|------|
| 来源号码 | 用户手机号 |
| 目标号码 | 你的接入号 / 长号 / 短号 |
| 正文 | 用户回复内容 |
| 时间 | 上行时间 |
| 关联 ID | 可选，部分厂商可关联此前出站 |

Quick SMS 解析为 `SmsInboundMessage`。

## 接入步骤

1. 在厂商控制台开通上行，配置回调 URL（如 `https://api.example.com/sms/inbound`）。
2. 你的服务接收 HTTP POST（form / JSON，视厂商而定）。
3. （推荐）先 `verifyWebhook` 校验签名。
4. 调用 `parseInbound(provider, payload)` 得到统一模型。
5. 按业务处理：退订入库、工单、自动回复等。
6. 按厂商要求返回约定响应（如纯文本 `OK`、JSON `{"code":0}`）。

```java
@PostMapping("/sms/inbound/{provider}")
public String inbound(@PathVariable String provider,
                      @RequestBody Map<String, Object> payload) {
    SmsInboundMessage msg = webhookHandler.parseInbound(
            SmsProviderType.valueOf(provider.toUpperCase()), payload);
    // msg.getFrom() / getContent() ...
    return "OK";
}
```

## 合规提示

- 营销类短信必须支持退订，并及时生效。
- 入站内容可能含隐私，日志与存储需脱敏。
- 国际通道短码 / 长码规则与国内不同，以厂商文档为准。

## 下一步

- [Webhook 回调](./webhook.md)
- [状态报告（Report）](./report.md)
