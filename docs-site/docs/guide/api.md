# API 详解

## 核心类型

| 类型 | 模块 | 作用 |
|------|------|------|
| `SmsClient` | api | 发信门面：`send` / `sendBatch` / `sendAsync` / `sendText` / `sendTemplate` |
| `SmsClients` | runtime | 工厂：`SmsClients.builder()` |
| `SmsClientBuilder` | runtime | 配置默认通道、failover、限流等 |
| `SmsSendRequest` | api | 手机号、内容/模板、凭证、provider、callbackUrl |
| `SmsSendResult` | api | success、messageId、providerMessageId、errorCode |
| `SmsCredentials` | api | apiKey/Secret 或 accessKeyId/Secret |
| `SmsChannel` | api | failover 通道（provider + credentials） |
| `SmsWebhookHandler` | api | 回执 / 上行 / 查状态 / 号码校验 / verifyWebhook |
| `SmsProviderType` | api | 核心通道枚举 |
| `SmsProviderConfig` | api | 非敏感默认项 + 代理超时等 |
| `SmsErrorCodes` | api | 统一错误码 |
| `SmsMetrics` | api | 可观测性钩子 |

## SmsClient

```java
SmsSendResult send(SmsSendRequest request);
SmsSendResult send(SmsSendRequest request, SmsProviderConfig config);
List<SmsSendResult> sendBatch(List<SmsSendRequest> requests);
CompletableFuture<SmsSendResult> sendAsync(SmsSendRequest request);

// 快捷方法
SmsSendResult sendText(String to, String content);
SmsSendResult sendText(String to, String content, SmsCredentials credentials);
SmsSendResult sendText(String to, String content, String providerCode, SmsCredentials credentials);
SmsSendResult sendTemplate(String to, String templateId, Map<String, String> params);
SmsSendResult sendTemplate(String to, String templateId, Map<String, String> params, SmsCredentials credentials);
```

## SmsSendRequest 常用字段

| 字段 | 说明 |
|------|------|
| `to` | 接收方手机号 |
| `content` | 正文（内容通道） |
| `templateId` / `templateParams` | 模板通道 |
| `from` | 发送方 / SenderId / 通道号（视厂商） |
| `provider` | `SmsProviderType` |
| `providerCode` | 扩展厂商编码（与 SPI 一致） |
| `credentials` | 动态凭证 |
| `callbackUrl` | 本条消息回执 URL（优先于 Client 默认） |
| `referenceId` | 业务关联号 |

**callback 优先级：** `request.callbackUrl` > `deliveryReceiptUrl` > `callbackUrl`

## SmsCredentials

```java
// Twilio / 云片 / 创蓝 等
SmsCredentials.builder().apiKey("...").apiSecret("...").build();

// 阿里 / 腾讯 / 华为 / 百度 / 天翼云 等
SmsCredentials.builder().accessKeyId("...").accessKeySecret("...").build();
```

具体字段映射见 [厂商接入说明](providers.md)。

## SmsWebhookHandler

```java
SmsReceipt parseReceipt(SmsProviderType provider, Map<String, Object> payload);
SmsReceipt parseReceipt(Map<String, Object> payload); // 尝试自动识别

SmsInboundMessage parseInbound(SmsProviderType provider, Map<String, Object> payload);

SmsReport fetchReport(SmsProviderType provider, String messageId, SmsCredentials credentials);

String verifyWebhook(long timestampMs, String nonce, String rawBody, String signature);
```

SDK **不监听端口**；由你的 Controller 收 POST 后再调用。

## 错误码 {#error-codes}

| 代码 | 说明 |
|------|------|
| `E001` | 请求参数不合法 |
| `E002` | 缺少发送凭证 |
| `E003` | 黑名单 |
| `E004` | 限流 |
| `E005` | Webhook 签名/时间窗失败 |
| `E006` | Webhook 重放 |
| `AUTH_FAILED` | 鉴权失败 |
| `INVALID_NUMBER` | 号码非法 |
| `CONTENT_REJECTED` | 内容/模板/签名拒收 |
| `QUOTA_EXCEEDED` | 余额或配额不足 |
| `TIMEOUT` / `NETWORK_ERROR` | 超时 / 网络 |
| `PROVIDER_ERROR` | 其他厂商错误 |
| `SEND_ERROR` | 发送过程异常 |
| `ALL_CHANNELS_FAILED` | 全部 failover 通道失败 |

`SmsSendResult.errorCode` 优先使用 `mappedErrorCode`（经 `ProviderErrorMapper`）。

## 模块依赖关系

```
sms-api
  ↑
sms-core
  ↑
  ├─ sms-runtime → sms-spring-boot-starter
  ├─ sms-providers-cn
  └─ sms-providers-intl

sms-all = starter + cn + intl
```

下一篇：[厂商接入说明](providers.md)
