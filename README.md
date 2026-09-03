<p align="center"><img src="https://capsule-render.vercel.app/api?type=waving&color=0:6A5ACD,100:2E86C1&height=180&section=header&text=Quick%20SMS&fontSize=46&fontColor=ffffff&animation=fadeIn&desc=Multi-vendor%20SMS%20SDK%20for%20JDK%2021&descAlignY=68" alt="Quick SMS banner" /></p>
<p align="center"><a href="https://search.maven.org/artifact/io.github.whaleal-dev/sms-all"><img src="https://img.shields.io/maven-central/v/io.github.whaleal-dev/sms-all?color=2E86C1&label=Maven%20Central" alt="Maven Central" /></a> <a href="https://whaleal.com/quick-sms/"><img src="https://img.shields.io/badge/Docs-whaleal.com%2Fquick--sms-0A7EA4" alt="Docs" /></a> <a href="https://www.apache.org/licenses/LICENSE-2.0.txt"><img src="https://img.shields.io/badge/License-Apache%202.0-1F6FEB" alt="License" /></a> <img src="https://img.shields.io/badge/JDK-21-2EA043" alt="JDK 21" /> <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F" alt="Spring Boot 3.4" /> <a href="https://github.com/whaleal-dev/quick-sms/actions"><img src="https://img.shields.io/github/actions/workflow/status/whaleal-dev/quick-sms/ci.yml?branch=main&label=CI" alt="CI" /></a> <a href="https://github.com/whaleal-dev/quick-sms/stargazers"><img src="https://img.shields.io/github/stars/whaleal-dev/quick-sms?style=flat&color=yellow" alt="GitHub stars" /></a></p>

# Quick SMS

> **让发送短信变得更简单——同时覆盖国内与国际，面向 SaaS 多租户。**

多供应商短信聚合 SDK。不必再为每家厂商单独啃文档、写签名与 HTTP 工具；用统一的 `SmsClient` / `SmsWebhookHandler` 完成发信、回执、上行与状态查询。

- 版本：`1.0.0`
- 坐标：`io.github.whaleal-dev:sms-all`
- GitHub：[whaleal-dev/quick-sms](https://github.com/whaleal-dev/quick-sms)
- 官网：[whaleal.com](https://whaleal.com)
- 维护者：恒哥 · QQ 群：短信网关 `1021755322`

如果本项目帮到了你，欢迎 Star 支持。

---

## 为什么做 Quick SMS

日常开发里短信发送极其常见，但第三方短信服务商众多，各家协议、签名、模板规则不同。接入一家就要读一遍文档、写一遍工具类；换通道或做多租户时，凭证与回调又变成新的负担。

Quick SMS 的目标是：

- **统一 API**：一行 `sendText` / `sendTemplate` 搞定常见场景
- **国内 + 国际**：不止国内云厂商，也覆盖 Twilio、Vonage 等主流国际通道
- **SaaS 友好**：**不强制 yml**，凭证在调用时动态传入，适合多租户
- **网关级能力**：回执 / 上行 / 状态查询 SPI、通道 failover、Webhook 安全、限流黑名单

📚 **完整文档：** [文档站](https://whaleal.com/quick-sms/) · 源码 [`docs-site/`](docs-site/README.md) · Markdown 速查 [`docs/`](docs/README.md)

---

## 特性一览

| 能力 | 说明 |
|------|------|
| 多厂商聚合 | 国内约 24 家 + 国际约 9 家，SPI 扩展 |
| 快捷发信 | `sendText` / `sendTemplate` 一行发送 |
| 按通道内容 | `contentByProvider` / `templateIdByProvider`（参考 easy-sms） |
| 动态凭证 | 请求级 `SmsCredentials`，秘钥不落盘 |
| 通道容灾 | 顺序 / 随机 failover |
| 回执 · 上行 · 查状态 | 统一 Webhook 门面 + 各厂商 Parser/Fetcher |
| 安全与治理 | Webhook 签名防重放、黑名单、限流、HTTP 代理 |
| 可观测性 | 内置 MetricsCollector；可选 Micrometer（`sms.send`） |
| 模块可选 | starter 不带厂商 jar；`sms-all` 一键全量 |

---

## 支持厂商一览

### 国内（`sms-providers-cn`）

| 厂商 | 枚举 / code |
|------|-------------|
| 阿里云 | `ALIYUN` / `aliyun` |
| 腾讯云 | `TENCENT` / `tencent` |
| 华为云 | `HUAWEI` / `huawei` |
| 云片 | `YUNPIAN` / `yunpian` |
| 创蓝 / 253 | `CHUANGLAN` / `chuanglan` |
| 容联云 | `CLOOPEN` / `cloopen` |
| 七牛云 | `QINIU` / `qiniu` |
| 螺丝帽 | `LUOSIMAO` / `luosimao` |
| SUBMAIL | `SUBMAIL` / `submail` |
| 天翼云 | `CTYUN` / `ctyun` |
| 网易云信 | `NETEASE` / `netease` |
| 百度云 | `BAIDU` / `baidu` |
| 助通 | `ZHUTONG` / `zhutong` |
| 短信宝 | `SMSBAO` / `smsbao` |
| 互亿无线 | `HUYI` / `huyi` |
| 聚合数据 | `JUHE` / `juhe` |
| 云之讯 | `YUNZHIXUN` / `yunzhixun` |
| SendCloud | `SENDCLOUD` / `sendcloud` |
| 华信 | `HUAXIN` / `huaxin` |
| 火山引擎 | `VOLCENGINE` / `volcengine` |
| 中国移动 / 电信 / 联通 | `CHINA_*` |
| 自定义 HTTP | `CUSTOM_HTTP` |
| Mock（本地） | `MOCK` |

### 国际（`sms-providers-intl`）

| 厂商 | 枚举 |
|------|------|
| Twilio | `TWILIO` |
| Vonage | `VONAGE` |
| MessageBird | `MESSAGEBIRD` |
| Plivo | `PLIVO` |
| Infobip | `INFOBIP` |
| Amazon SNS | `AWS` |
| 阿里云国际 | `ALIYUN_INTERNATIONAL` |
| 腾讯云国际 | `TENCENT_INTERNATIONAL` |
| 华为云国际 | `HUAWEI_INTERNATIONAL` |

凭证字段、模板 vs 内容、回调样例见 **[厂商接入说明](docs/providers.md)**。

---

## 30 秒上手

### Maven 引入依赖

坐标：`io.github.whaleal-dev:sms-all:<version>`（Java 包名仍为 `com.whaleal...`，不变）。

发到 **Maven Central** 后，**只需依赖**（无需 `<repositories>`、无需 `settings.xml`）：

```xml
<!-- 推荐：国内 + 国际全量 -->
<dependency>
  <groupId>io.github.whaleal-dev</groupId>
  <artifactId>sms-all</artifactId>
  <version>1.0.0</version>
</dependency>
```

或按需：`sms-spring-boot-starter` + `sms-providers-cn` / `sms-providers-intl`。

> 推送分支 `release-x.y.z` 会自动发布到 Maven Central。查版本：[Central Search](https://central.sonatype.com) · 发布说明见 [CI / CD](docs/ci-cd.md)。

#### 本地开发：源码 `mvn install`

```bash
git clone https://github.com/whaleal-dev/quick-sms.git
cd quick-sms
mvn clean install -DskipTests
```

业务项目直接依赖 `io.github.whaleal-dev:sms-all:1.0.0`（与根 pom 版本一致）即可。需 **JDK 21**。

### 纯 Java（无需 yml）

```java
SmsClient client = SmsClients.builder()
        .provider(SmsProviderType.YUNPIAN)
        .build();

SmsSendResult result = client.sendText(
        "13800138000",
        "【签名】您的验证码是 1234",
        SmsCredentials.builder().apiKey("your-apikey").build());

System.out.println(result.isSuccess() + " " + result.getMessageId());
```

### Spring Boot

```java
@RestController
@RequiredArgsConstructor
public class SmsController {
    private final SmsClient smsClient; // 默认 MOCK，可自定义 @Bean

    @PostMapping("/send")
    public SmsSendResult send(@RequestParam String phone) {
        return smsClient.sendText(phone, "【QuickSMS】hello",
                SmsCredentials.builder().apiKey("your-apikey").build());
    }
}
```

> **设计约定：** Quick SMS **不强制 yml**，凭证在代码 / 请求中传入，更适合多租户与配置中心。

更多步骤：

- [Spring Boot 集成](docs/quickstart-springboot.md)
- [JavaSE / 纯 Java 集成](docs/quickstart-javase.md)
- [进阶能力](docs/features.md)
- [API 详解](docs/api.md)

---

## 模块结构

```
quick-sms/
├── sms-api                 # 门面、SPI、DTO、枚举
├── sms-core                # SPI 加载、签名/指标/策略工具
├── sms-runtime             # 适配器、Mock、SmsClients
├── sms-providers-cn        # 国内厂商
├── sms-providers-intl      # 国际厂商
├── sms-spring-boot-starter # 自动配置（不带厂商）
├── sms-all                 # starter + cn + intl
├── docs-site/              # Docusaurus 文档站（GitHub Pages）
├── docs/                   # Markdown 速查（与文档站内容互补）
└── examples/               # 示例代码
```

| 场景 | 依赖 |
|------|------|
| 只要国内 | `starter` + `sms-providers-cn` |
| 只要国际 | `starter` + `sms-providers-intl` |
| 全量网关 | `sms-all` |
| 纯 Java | `sms-runtime` + 所需 providers |

---

## 文档导航

公开站点：[https://whaleal.com/quick-sms/](https://whaleal.com/quick-sms/)（出站 / 入站 / Report / Webhook 等概念见「短信概念」）。

| 文档 | 内容 |
|------|------|
| [前言](docs/intro.md) | 设计理念与适用场景 |
| [Spring Boot 快速开始](docs/quickstart-springboot.md) | 依赖、Bean、发信、Webhook Controller |
| [JavaSE 快速开始](docs/quickstart-javase.md) | Builder、无 Spring 用法 |
| [进阶配置](docs/features.md) | Failover、限流、安全、指标、代理 |
| [API 详解](docs/api.md) | 核心类型与错误码 |
| [厂商接入](docs/providers.md) | 各厂商凭证与回调 |
| [CI / CD](docs/ci-cd.md) | 自动构建与发布到 Maven Central |
| [文档站维护](docs-site/README.md) | 本地预览与 Pages 发布 |
| [示例](examples/README.md) | 可复制代码 |
| [变更记录](CHANGELOG.md) | 版本说明 |

---

## 构建

```bash
mvn clean test package
```

Java 21 · Spring Boot 3.4.x

---

## 社区交流

| 群名称 | 群号 |
|--------|------|
| 短信网关 | `1021755322` |

<p align="center">
  <img src="docs/qq-group.png" alt="QQ 群二维码：短信网关 1021755322" width="280" />
</p>

<p align="center">扫一扫加入群聊</p>

---

## 仓库与发布

**源码仓库：** [github.com/whaleal-dev/quick-sms](https://github.com/whaleal-dev/quick-sms)

完整流程见 **[CI / CD 说明](docs/ci-cd.md)**。

| 场景 | 触发 | Workflow | Secrets |
|------|------|----------|---------|
| 构建测试 | PR / push `main`、`release-*` | [ci.yml](.github/workflows/ci.yml) | 无 |
| **发布 Maven Central** | 分支 **`release-*`** | [publish-maven-central.yml](.github/workflows/publish-maven-central.yml) | 见下 |

```bash
git checkout -b release-1.0.0 && git push -u origin release-1.0.0
```

需配置 Secrets：`MAVEN_CENTRAL_USERNAME`、`MAVEN_CENTRAL_PASSWORD`、`MAVEN_GPG_PRIVATE_KEY`、`MAVEN_GPG_PASSPHRASE`。命名空间须为 **`io.github.whaleal-dev`**。

消费方引入方式见 **[Maven 引入依赖](#maven-引入依赖)**。

---

## 贡献与规范

协作、作者、PR 见工作区 [`conventions/`](../../conventions/README.md)。

维护者：**恒哥** `[恒哥]`

## 许可证

Copyright © 2026 [whaleal-dev](https://github.com/whaleal-dev) · [whaleal.com](https://whaleal.com)

基于 [Apache License 2.0](LICENSE) 发布。
