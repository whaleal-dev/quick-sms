# Webhook 回调

Webhook 是短信厂商把 **异步事件推到你指定 URL** 的机制。在短信场景里，回调里最常见的是：

- **回执（Receipt）**：出站短信的送达/失败状态
- **上行（Inbound）**：用户发来的短信

也可能包含其他事件（视厂商而定），但接入模型相同：**你的 HTTP 接口 → 验签 → 解析 → 业务处理 → 按约定响应**。

## 为什么 SDK 自己不收回调

Quick SMS 是 **客户端库**，跑在你的 JVM 里，没有公网监听端口。回调 URL 必须由你的 Web 应用、网关或 Serverless 提供，再调用：

```text
厂商 ──POST──► 你的 Controller / Filter
                    │
                    ├─ verifyWebhook(...)     // 可选但强烈建议
                    ├─ parseReceipt(...)      // 回执
                    └─ parseInbound(...)      // 上行
```

## 推荐接入流程

1. **控制台配置**  
   回执 URL、上行 URL（可分开也可按 path 区分），如：  
   - `https://api.example.com/hooks/sms/receipt/{provider}`  
   - `https://api.example.com/hooks/sms/inbound/{provider}`

2. **接收原始请求**  
   保留 raw body（验签常用原始字节/字符串），再解析为 `Map` 或表单字段。

3. **安全校验**（Quick SMS 约定）  
   - 签名串：`timestamp + "." + nonce + "." + rawBody`  
   - 算法：HMAC-SHA256（小写 hex）  
   - 默认时间窗约 5 分钟，并防 nonce 重放  

```java
String err = handler.verifyWebhook(timestampMs, nonce, rawBody, signatureHeader);
if (err != null) {
    // E005 签名/时间窗；E006 重放
    return ResponseEntity.status(401).body(err);
}
```

4. **解析为统一模型**

```java
SmsReceipt receipt = handler.parseReceipt(provider, payload);
// 或
SmsInboundMessage inbound = handler.parseInbound(provider, payload);
```

5. **返回厂商要求的应答**  
   不同厂商对响应码/正文要求不同（`OK`、`success`、JSON 等）。解析成功后务必按文档返回，否则可能反复重推。

## Spring Boot 示例骨架

```java
@RestController
@RequestMapping("/hooks/sms")
public class SmsHookController {

    private final SmsWebhookHandler webhookHandler;

    public SmsHookController(SmsWebhookHandler webhookHandler) {
        this.webhookHandler = webhookHandler;
    }

    @PostMapping("/receipt/{provider}")
    public String receipt(@PathVariable String provider,
                          @RequestBody Map<String, Object> body) {
        webhookHandler.parseReceipt(
                SmsProviderType.valueOf(provider.toUpperCase()), body);
        return "OK";
    }

    @PostMapping("/inbound/{provider}")
    public String inbound(@PathVariable String provider,
                          @RequestBody Map<String, Object> body) {
        webhookHandler.parseInbound(
                SmsProviderType.valueOf(provider.toUpperCase()), body);
        return "OK";
    }
}
```

生产环境请补上：验签、幂等、异步落库、按厂商定制响应体。

## 和 Report 查询的配合

| 机制 | 何时用 |
|------|--------|
| Webhook 回执 | 主路径，实时更新状态 |
| `fetchReport` | 丢回调、延迟、对账补洞 |

详见 [状态报告（Report）](./report.md)。

## 安全清单

- [ ] HTTPS
- [ ] 签名 + 时间窗 + nonce
- [ ] 回调 IP 白名单（若厂商提供）
- [ ] 幂等（按 messageId / 事件 ID 去重）
- [ ] 日志脱敏（手机号、正文）

## 进阶

签名与防重放的代码配置见 [进阶能力](../guide/features.md)。
