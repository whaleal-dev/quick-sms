# Spring Boot 快速开始

按 Quick SMS 的「无强制 yml」约定，介绍 Spring Boot 下的依赖、Bean 与发信步骤。

## 1. 创建项目

使用 Spring Boot 3.4+、Java 21，例如工程名 `sms-demo-springboot`。

## 2. 添加依赖

**方式 A：全量（推荐试用）**

```xml
<dependency>
    <groupId>io.github.whaleal-dev</groupId>
    <artifactId>sms-all</artifactId>
    <version>1.0.0</version>
</dependency>
```

**方式 B：按需（生产推荐）**

```xml
<!-- 自动配置 SmsClient / SmsWebhookHandler，不带厂商 -->
<dependency>
    <groupId>io.github.whaleal-dev</groupId>
    <artifactId>sms-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 国内厂商 SPI -->
<dependency>
    <groupId>io.github.whaleal-dev</groupId>
    <artifactId>sms-providers-cn</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 需要国际时再加 -->
<dependency>
    <groupId>io.github.whaleal-dev</groupId>
    <artifactId>sms-providers-intl</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 可选：Micrometer 指标 sms.send / sms.send.duration -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

## 3. 配置说明（重要）

**不需要** 在 `application.yml` 里写短信 AK/SK。

默认自动配置会提供：

- `SmsClient`：默认通道为 `MOCK`（本地可跑通）
- `SmsWebhookHandler`：用于解析回执 / 上行

生产环境有两种用法：

### 3.1 调用时传凭证（多租户首选）

保持默认 Bean，每次发送带上 `credentials` 与 `provider`：

```java
smsClient.send(SmsSendRequest.builder()
        .provider(SmsProviderType.ALIYUN)
        .to(phone)
        .templateId("SMS_123456")
        .templateParams(Map.of("code", "1234"))
        .credentials(SmsCredentials.builder()
                .accessKeyId(tenantAk)
                .accessKeySecret(tenantSk)
                .build())
        .build());
```

### 3.2 自定义默认 Bean（单租户）

```java
@Configuration
public class SmsConfiguration {

    @Bean
    public SmsClient smsClient() {
        return SmsClients.builder()
                .provider(SmsProviderType.YUNPIAN)
                .signName("你的签名")
                .deliveryReceiptUrl("https://api.example.com/sms/webhook/receipt")
                .build();
    }
}
```

秘钥仍建议放在请求里或配置中心注入的 `SmsCredentials`，避免写进仓库。

## 4. 编写 Controller

```java
@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
public class SmsDemoController {

    private final SmsClient smsClient;
    private final SmsWebhookHandler webhookHandler;

    /** 快捷发内容短信 */
    @PostMapping("/send-text")
    public SmsSendResult sendText(@RequestParam String phone,
                                  @RequestParam String apiKey) {
        return smsClient.sendText(phone, "【签名】您的验证码是 1234",
                SmsCredentials.builder().apiKey(apiKey).build());
    }

    /** 模板短信 */
    @PostMapping("/send-template")
    public SmsSendResult sendTemplate(@RequestParam String phone) {
        return smsClient.send(SmsSendRequest.builder()
                .provider(SmsProviderType.ALIYUN)
                .to(phone)
                .templateId("SMS_123456")
                .templateParams(Map.of("code", "8888"))
                .credentials(SmsCredentials.builder()
                        .accessKeyId(System.getenv("ALIYUN_AK"))
                        .accessKeySecret(System.getenv("ALIYUN_SK"))
                        .build())
                .build());
    }

    /** 厂商回执回调 */
    @PostMapping("/webhook/{provider}/receipt")
    public String receipt(@PathVariable String provider,
                          @RequestBody Map<String, Object> payload) {
        SmsProviderType type = SmsProviderType.fromCode(provider);
        SmsReceipt receipt = webhookHandler.parseReceipt(type, payload);
        // 落库 / 更新业务状态 …
        return "OK";
    }
}
```

## 5. 运行

启动应用后：

```bash
curl -X POST 'http://localhost:8080/sms/send-text?phone=13800138000&apiKey=xxx'
```

本地可先不传真实 key，用默认 `MOCK`：

```java
smsClient.sendText("13800138000", "hello"); // 默认 MOCK，无需凭证
```

## 6. 多通道 failover（可选）

```java
@Bean
public SmsClient smsClient() {
    return SmsClients.builder()
            .addChannel(SmsChannel.of(SmsProviderType.ALIYUN, credA))
            .addChannel(SmsChannel.of(SmsProviderType.TENCENT, credB))
            .channelStrategy(OrderChannelStrategy.INSTANCE) // 或 RandomChannelStrategy
            .rateLimiter(RateLimiter.perMinute(60))
            .build();
}
```

更多见 [进阶能力](features.md)。

## 常见问题

| 问题 | 处理 |
|------|------|
| 找不到厂商实现 | 是否引入了 `sms-providers-cn` / `intl`？starter 本身不含厂商 |
| E002 缺少凭证 | 非 MOCK 必须传 `credentials` |
| 回执解析为空 | 确认 payload 字段与厂商文档一致；见 [providers.md](providers.md) |

下一篇：[JavaSE 快速开始](quickstart-javase.md) · [厂商接入](providers.md)
