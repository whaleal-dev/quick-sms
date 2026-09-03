# Quick SMS

> **让发送短信变得更简单——同时覆盖国内与国际，面向 SaaS 多租户。**

多供应商短信聚合 SDK。不必再为每家厂商单独啃文档、写签名与 HTTP 工具；用统一的 `SmsClient` / `SmsWebhookHandler` 完成发信、回执、上行与状态查询。

**组织：** [whaleal-dev](https://github.com/whaleal-dev) · **仓库：** [quick-sms](https://github.com/whaleal-dev/quick-sms) · **官网：** [whaleal.com](https://whaleal.com) · **维护者：** 恒哥 · **QQ 群：** 短信网关 `1021755322`

如果本项目帮到了你，欢迎 Star 支持。

---

## 为什么做 Quick SMS

日常开发里短信发送极其常见，但第三方短信服务商众多，各家协议、签名、模板规则不同。接入一家就要读一遍文档、写一遍工具类；换通道或做多租户时，凭证与回调又变成新的负担。

Quick SMS 的目标是：

- **统一 API**：一行 `sendText` / `sendTemplate` 搞定常见场景
- **国内 + 国际**：不止国内云厂商，也覆盖 Twilio、Vonage 等主流国际通道
- **SaaS 友好**：**不强制 yml**，凭证在调用时动态传入，适合多租户
- **网关级能力**：回执 / 上行 / 状态查询 SPI、通道 failover、Webhook 安全、限流黑名单

📚 **完整文档：** [`docs/`](docs/README.md)

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

发到 **Maven Central** 后，推荐只写依赖（无需仓库、无需 `settings.xml`）：

```xml
<dependency>
  <groupId>io.github.whaleal-dev</groupId>
  <artifactId>sms-all</artifactId>
  <version>1.0.0</version>
</dependency>
```

发布到中央仓的步骤见 **[CI / CD：Maven Central](docs/ci-cd.md#发布到-maven-central推荐对外)**。  
在尚未出现在 Central 之前，可用下面两种方式。

#### 方式一：从 GitHub Packages 拉取

JAR 在 [GitHub Packages](https://github.com/whaleal-dev/quick-sms/packages)。

> **注意：** 即便包是公开的，GitHub Packages 的 Maven 仓库仍要求认证，不能像中央仓库那样匿名下载。需同时配置 `settings.xml`（PAT）与 `pom.xml`（仓库地址）。

**1. 配置认证**（`~/.m2/settings.xml`）

创建 [Personal Access Token (classic)](https://github.com/settings/tokens)，勾选 **`read:packages`**。  
`<server><id>` 必须与下面仓库的 `<id>` 一致（均为 `github`）。

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT</password>
    </server>
  </servers>
</settings>
```

**2. 配置仓库地址并添加依赖**（项目 `pom.xml`）

```xml
<repositories>
  <repository>
    <id>github</id>
    <name>GitHub Packages (quick-sms)</name>
    <url>https://maven.pkg.github.com/whaleal-dev/quick-sms</url>
  </repository>
</repositories>

<!-- 推荐：国内 + 国际全量 -->
<dependency>
  <groupId>io.github.whaleal-dev</groupId>
  <artifactId>sms-all</artifactId>
  <version>1.0.0</version>
</dependency>
```

或按需：`sms-spring-boot-starter` + `sms-providers-cn` / `sms-providers-intl`。

> 版本以 Packages / Release / Central 页面为准。

#### 方式二：源码 `mvn install`（无需 Packages 认证）

适合本地开发、不想配置 PAT 的场景：把模块安装到本机 `~/.m2/repository`，业务项目直接依赖即可。

```bash
git clone https://github.com/whaleal-dev/quick-sms.git
cd quick-sms
mvn clean install -DskipTests
```

安装成功后，业务项目 `pom.xml` **不必**再配 GitHub Packages 仓库，直接写依赖：

```xml
<dependency>
  <groupId>io.github.whaleal-dev</groupId>
  <artifactId>sms-all</artifactId>
  <version>1.0.0</version> <!-- 与根 pom 中 <version> 一致 -->
</dependency>
```

说明：

- 需本机已安装 **JDK 21** 与 Maven
- `install` 会安装全部子模块（`sms-api` / `sms-runtime` / `sms-providers-*` / `sms-all` 等）
- 若只想打本地包不跑测试，可用上面的 `-DskipTests`；完整校验用 `mvn clean install`

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
├── docs/                   # 文档站
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

| 文档 | 内容 |
|------|------|
| [前言](docs/intro.md) | 设计理念与适用场景 |
| [Spring Boot 快速开始](docs/quickstart-springboot.md) | 依赖、Bean、发信、Webhook Controller |
| [JavaSE 快速开始](docs/quickstart-javase.md) | Builder、无 Spring 用法 |
| [进阶配置](docs/features.md) | Failover、限流、安全、指标、代理 |
| [API 详解](docs/api.md) | 核心类型与错误码 |
| [厂商接入](docs/providers.md) | 各厂商凭证与回调 |
| [CI / CD](docs/ci-cd.md) | 自动构建与发布到 GitHub Packages |
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
| GitHub Packages | 分支 **`release-*`** | [publish-github-packages.yml](.github/workflows/publish-github-packages.yml) | 无 |
| **Maven Central** | 分支 **`central-*`** | [publish-maven-central.yml](.github/workflows/publish-maven-central.yml) | 见下 |

**发中央仓（推荐对外）：**

```bash
git checkout -b central-1.0.0 && git push -u origin central-1.0.0
```

需在仓库配置 Secrets：`MAVEN_CENTRAL_USERNAME`、`MAVEN_CENTRAL_PASSWORD`、`MAVEN_GPG_PRIVATE_KEY`、`MAVEN_GPG_PASSPHRASE`。命名空间须为 **`io.github.whaleal-dev`**。

**发 GitHub Packages：**

```bash
git checkout -b release-1.0.0 && git push -u origin release-1.0.0
```

消费方引入方式见 **[Maven 引入依赖](#maven-引入依赖)**。

---

## 贡献与规范

协作、作者、PR 见工作区 [`conventions/`](../../conventions/README.md)。

维护者：**恒哥** `[恒哥]`

## 许可证

Copyright © 2026 [whaleal-dev](https://github.com/whaleal-dev) · [whaleal.com](https://whaleal.com)

基于 [Apache License 2.0](LICENSE) 发布。
