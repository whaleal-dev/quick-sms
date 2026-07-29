# Quick SMS 示例

完整说明见文档站：[docs/](../docs/README.md)。

本目录**不参与**父工程 Maven reactor，方便复制到业务工程。

## 1. 纯 Java（Mock）

见 [`plain-java`](plain-java/) · 文档：[JavaSE 快速开始](../docs/quickstart-javase.md)

```java
SmsClient client = SmsClients.builder()
        .provider(SmsProviderType.MOCK)
        .rateLimiter(RateLimiter.perMinute(100))
        .build();
client.sendText("13800138000", "【QuickSMS】验证码 1234");
```

## 2. Spring Boot

见 [`spring-boot`](spring-boot/) · 文档：[Spring Boot 快速开始](../docs/quickstart-springboot.md)

```xml
<dependency>
  <groupId>com.whaleal.third</groupId>
  <artifactId>sms-all</artifactId>
  <version>1.0.0</version>
</dependency>
```

注入 `SmsClient` 后即可调用；凭证在请求中传入，**无需 yml**。

## 相关文档

| 文档 | 说明 |
|------|------|
| [前言](../docs/intro.md) | 设计理念 |
| [进阶能力](../docs/features.md) | Failover / 安全 / 指标 |
| [厂商接入](../docs/providers.md) | 凭证与回调 |
| [API](../docs/api.md) | 类型与错误码 |
