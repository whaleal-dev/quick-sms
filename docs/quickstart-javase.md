# JavaSE / 纯 Java 快速开始

不依赖 Spring，用工厂 / Builder 发信。

## 1. 依赖

```xml
<dependency>
    <groupId>com.whaleal.third</groupId>
    <artifactId>sms-runtime</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>com.whaleal.third</groupId>
    <artifactId>sms-providers-cn</artifactId>
    <version>1.0.0</version>
</dependency>
<!-- 需要国际时加 sms-providers-intl -->
```

或直接：

```xml
<dependency>
    <groupId>com.whaleal.third</groupId>
    <artifactId>sms-all</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 2. 创建客户端

```java
import com.whaleal.ark.cloud.third.sms.client.*;
import com.whaleal.ark.cloud.third.sms.enums.SmsProviderType;

public class Main {
    public static void main(String[] args) {
        SmsClient client = SmsClients.builder()
                .provider(SmsProviderType.YUNPIAN)
                .build();

        SmsSendResult result = client.sendText(
                "13800138000",
                "【签名】您的验证码是 1234",
                SmsCredentials.builder().apiKey("your-apikey").build());

        System.out.printf("success=%s id=%s err=%s%n",
                result.isSuccess(), result.getMessageId(), result.getErrorCode());
    }
}
```

本地无密钥时用 `MOCK`：

```java
SmsClient client = SmsClients.builder()
        .provider(SmsProviderType.MOCK)
        .build();
client.sendText("13800138000", "hello mock");
```

## 3. 完整请求

```java
client.send(SmsSendRequest.builder()
        .provider(SmsProviderType.ALIYUN)
        .to("13800138000")
        .templateId("SMS_123456")
        .templateParams(Map.of("code", "1234"))
        .callbackUrl("https://api.example.com/sms/receipt")
        .credentials(SmsCredentials.builder()
                .accessKeyId("LTAI...")
                .accessKeySecret("secret")
                .build())
        .build());
```

## 4. 批量与异步

```java
List<SmsSendResult> batch = client.sendBatch(List.of(req1, req2));
CompletableFuture<SmsSendResult> future = client.sendAsync(req);
```

## 5. Webhook（无 Spring）

自行构造：

```java
SmsModuleManager manager = new SmsModuleManager();
SmsWebhookHandler handler = new DefaultSmsWebhookHandler(
        manager,
        new PhoneValidationAdapter(),
        SmsProviderConfig.builder().providerType(SmsProviderType.MOCK).build(),
        new WebhookSecurity("your-webhook-secret") // 可选
);

SmsReceipt receipt = handler.parseReceipt(SmsProviderType.YUNPIAN, payload);
```

在你自己的 HTTP 框架（Javalin / Undertow / 裸 Servlet）里接收厂商 POST 后调用即可。

## 6. 示例源码

见仓库 [`examples/plain-java`](../examples/plain-java)。

下一篇：[进阶能力](features.md) · [API 详解](api.md)
