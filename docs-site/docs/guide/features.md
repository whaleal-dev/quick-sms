# 进阶能力

对应「进阶配置」章节：多通道、治理、安全与观测。全部通过 **代码配置**，无需 yml。

## 1. 多通道 failover

```java
SmsClient client = SmsClients.builder()
        .addChannel(SmsChannel.of(SmsProviderType.ALIYUN, credAliyun))
        .addChannel(SmsChannel.of(SmsProviderType.TENCENT, credTencent))
        .addChannel(SmsChannel.of("yunpian", credYunpian)) // 扩展 code 亦可
        .channelStrategy(OrderChannelStrategy.INSTANCE)
        // .channelStrategy(RandomChannelStrategy.INSTANCE)
        .build();

// 未指定 provider 时按策略尝试，直到成功
client.sendText("13800138000", "【签名】验证码 1234");
```

全部失败时错误码：`ALL_CHANNELS_FAILED`。

若请求里已指定 `provider` / `providerCode`，则**不走** failover 列表。

### 按通道覆盖正文 / 模板（参考 easy-sms）

多厂商 failover 时，各通道对「内容 vs 模板」要求不同。可用 Map 按 provider code 覆盖：

```java
client.send(SmsSendRequest.builder()
        .to("13800138000")
        .content("【签名】默认正文")
        .contentByProvider(Map.of(
                "yunpian", "【签名】云片验证码 1234",
                "smsbao", "【签名】短信宝验证码 1234"))
        .templateIdByProvider(Map.of("aliyun", "SMS_001"))
        .templateParamsByProvider(Map.of("aliyun", Map.of("code", "1234")))
        .build());
```

优先级：当前通道在 Map 中的值 → 全局 `content` / `templateId` / `templateParams`。

## 2. 黑名单与限流

```java
PhoneBlacklist blacklist = new PhoneBlacklist();
blacklist.block("13800138000");

SmsClient client = SmsClients.builder()
        .provider(SmsProviderType.MOCK)
        .blacklist(blacklist)
        .rateLimiter(RateLimiter.perMinute(30)) // 每号每分钟 30 次
        .build();
```

| 错误码 | 含义 |
|--------|------|
| `E003` | 号码在黑名单 |
| `E004` | 触发限流 |

## 3. HTTP 代理

在配置或合并后的 `SmsProviderConfig` 上设置：

```java
SmsProviderConfig.builder()
        .proxyHost("127.0.0.1")
        .proxyPort(7890)
        .build();
```

走 `ProviderHttp` 的国内厂商发送会自动使用代理。

## 4. Webhook 签名与防重放

签名串：`timestamp + "." + nonce + "." + rawBody`  
算法：HMAC-SHA256（小写 hex）

```java
WebhookSecurity security = new WebhookSecurity("shared-secret"); // 默认 5 分钟时间窗

SmsWebhookHandler handler = new DefaultSmsWebhookHandler(
        moduleManager, phoneValidationAdapter, baseConfig, security);

String err = handler.verifyWebhook(timestampMs, nonce, rawBody, signatureHeader);
if (err != null) {
    // E005 签名/时间窗；E006 重放
    return;
}
handler.parseReceipt(provider, payload);
```

## 5. 可观测性

### 内置 MetricsCollector

Builder 默认挂载 `CollectorSmsMetrics`，按厂商累计成功/失败与耗时。

### Micrometer（Spring）

引入 `spring-boot-starter-actuator` 且存在 `MeterRegistry` 时，自动注册：

- `sms.send`（tag：`provider` / `result` / `error`）
- `sms.send.duration`

也可手动：

```java
SmsClients.builder()
        .metrics(new MicrometerSmsMetrics(meterRegistry))
        .build();
```

## 6. 统一错误码

厂商原始错误会写入 `extraInfo`，并映射为：

| 码 | 说明 |
|----|------|
| `E001` | 参数非法 |
| `E002` | 缺少凭证 |
| `E003` / `E004` | 黑名单 / 限流 |
| `E005` / `E006` | Webhook 校验失败 / 重放 |
| `AUTH_FAILED` | 鉴权失败 |
| `INVALID_NUMBER` | 号码问题 |
| `CONTENT_REJECTED` | 内容/模板/签名拒收 |
| `QUOTA_EXCEEDED` | 余额/配额 |
| `PROVIDER_ERROR` | 其他厂商错误 |
| `ALL_CHANNELS_FAILED` | failover 全失败 |

详见 [API 详解 · 错误码](api.md#error-codes)。

## 7. 扩展新厂商（SPI）

1. 在 `sms-providers-cn`（或 intl）实现 `OutboundSender` 等接口  
2. `getSupportedProvider()` 返回规范 code（如 `myvendor`）  
3. 注册 `META-INF/services/...`  
4. 请求使用 `.providerCode("myvendor")`，**不必改** `SmsProviderType` 枚举  

下一篇：[API 详解](api.md) · [厂商接入](providers.md)
