# 厂商接入说明

> 凭证一律通过 `SmsCredentials` / `SmsSendRequest.credentials` 传入，**不要求** `application.yml`。  
> 风格参考 [SMS4J 支持厂商](https://sms4j.com/doc2/supplier/jieshao.html)：先总览，再按厂商说明关键字段。

返回：[文档首页](README.md)

---

## 总览约定

| 项 | 说明 |
|----|------|
| 国内模块 | `sms-providers-cn` |
| 国际模块 | `sms-providers-intl` |
| 通道标识 | `SmsProviderType` 或 SPI `providerCode` |
| 内容短信 | 填 `content`（部分厂商正文需自带【签名】） |
| 模板短信 | 填 `templateId` + `templateParams` |
| 回执 / 上行 | `SmsWebhookHandler.parseReceipt` / `parseInbound` |
| 主动查状态 | `fetchReport`；未深对接的厂商可能返回 `UNKNOWN`（以 Webhook 为准） |
| 代理 | `SmsProviderConfig.proxyHost` / `proxyPort` |

### 国内厂商速查

| 厂商 | code | 凭证 | 形态 |
|------|------|------|------|
| 阿里云 | `aliyun` | accessKeyId / Secret | 模板 |
| 腾讯云 | `tencent` | SecretId / SecretKey | 模板 |
| 华为云 | `huawei` | appKey / appSecret + sender | 模板 |
| 云片 | `yunpian` | apiKey | **内容** |
| 创蓝 | `chuanglan` | account / password | **内容** |
| 容联云 | `cloopen` | accountSid + token 等 | 模板 |
| 七牛 | `qiniu` | AK / SK | 模板 |
| 螺丝帽 | `luosimao` | apiKey | **内容** |
| SUBMAIL | `submail` | appid + appkey | 内容/模板 |
| 天翼云 | `ctyun` | AK / SK | 模板 |
| 网易云信 | `netease` | AppKey / AppSecret | 模板 |
| 百度云 | `baidu` | AK / SK + signatureId | 模板 |
| 助通 | `zhutong` | username / password | **内容** |
| 移动/电信/联通 | `china_*` | 见各 Sender | 厂商协议 |
| 自定义 HTTP | `custom_http` | 按配置 | 自定义 |

### 国际厂商速查

| 厂商 | code | 凭证要点 |
|------|------|----------|
| Twilio | `twilio` | AccountSid + AuthToken，`from` 号码 |
| Vonage | `vonage` | **默认 Messages API v1**（`api.nexmo.com/v1/messages`）；凭证 apiKey+Secret（Basic）或配置 `jwt`/`accessToken`（Bearer）。旧 SMS API：`config.apiMode=legacy` |
| MessageBird / Plivo / Infobip | 对应枚举 | apiKey 等 |
| AWS SNS | `aws` | accessKey + secret + region |
| 阿里/腾讯/华为国际 | `*_international` | 同云厂商 AK，国际节点 |

---

## 国内厂商详解

### 阿里云 `ALIYUN`

- **凭证：** `accessKeyId` + `accessKeySecret`
- **发送：** 模板；`signName` + `templateId` + `templateParams`
- **回执字段示例：** `biz_id`、`phone_number`、`report_status`
- **上行字段示例：** `phone_number`、`sms_content`、`dest_code`
- **说明：** 回执 URL 一般在控制台配置；国际请用 `ALIYUN_INTERNATIONAL`

```java
client.send(SmsSendRequest.builder()
        .provider(SmsProviderType.ALIYUN)
        .to("13800138000")
        .templateId("SMS_123456")
        .templateParams(Map.of("code", "1234"))
        .credentials(SmsCredentials.builder()
                .accessKeyId("LTAI...").accessKeySecret("...").build())
        .build());
```

### 腾讯云 `TENCENT`

- **凭证：** `accessKeyId`=SecretId，`accessKeySecret`=SecretKey
- **额外：** `config.smsSdkAppId`（或扩展配置）
- **发送：** 模板；`SignName` + `TemplateId` + `TemplateParamSet`
- **国际：** `TENCENT_INTERNATIONAL`（默认 region `ap-singapore`）

### 华为云 `HUAWEI`

- **凭证：** `appKey` / `appSecret`（亦可用 apiKey/Secret）
- **通道号：** `defaultFrom` 或 config `sender`
- **发送：** 模板；`templateId` + `templateParas` + `signature`
- **鉴权：** WSSE；`PasswordDigest = Base64(SHA-256(nonce + created + appSecret))`，Created 为 UTC ISO8601
- **国际：** `HUAWEI_INTERNATIONAL`（算法一致）

### 云片 `YUNPIAN`

- **凭证：** `apiKey`
- **发送：** **内容短信**，正文需含【签名】
- **回执：** `sid`、`mobile`、`report_status`

```java
client.sendText("13800138000", "【签名】您的验证码是 1234",
        SmsCredentials.builder().apiKey("xxxx").build());
```

### 创蓝 / 253 `CHUANGLAN`

- **凭证：** `apiKey`=account，`apiSecret`=password
- **发送：** 内容短信（正文含签名）
- **回执：** `msgid`、`status`

### 容联云 `CLOOPEN`

- **凭证：** accountSid、authToken 等（见 `CloopenOutboundSender`）
- **发送：** 模板为主

### 七牛云 `QINIU`

- **凭证：** accessKey / secretKey
- **发送：** 模板

### 螺丝帽 `LUOSIMAO`

- **凭证：** `apiKey`（Basic Auth：`api:key-xxx`）
- **发送：** 内容短信

### SUBMAIL `SUBMAIL`

- **凭证：** appid + appkey
- **发送：** 内容或模板（视接口）

### 天翼云 `CTYUN`

- **凭证：** accessKeyId / accessKeySecret
- **发送：** 模板；`signName` + `templateCode`
- **签名：** EOP（对齐 SMS4J `CtyunUtils`）
- **默认 URL：** `https://sms-global.ctapi.ctyun.cn/sms/api/v1`

### 网易云信 `NETEASE`

- **凭证：** AppKey / AppSecret
- **发送：** 模板；Header：`AppKey` / `Nonce` / `CurTime` / `CheckSum`
- **CheckSum：** SHA1(AppSecret + Nonce + CurTime)

### 百度云 `BAIDU`

- **凭证：** AK / SK
- **发送：** 模板；`template` + `signatureId`（用 `signName` 字段承载）+ `contentVar`
- **签名：** BCE Auth（对齐 SMS4J，`Authorization = prefix//signature`）

### 助通 `ZHUTONG`

- **凭证：** username=`apiKey`，password=`apiSecret`
- **发送：** 内容；密码需 MD5 链式（见实现）
- **成功：** 响应 `code == 200`

### 三大运营商 `CHINA_MOBILE` / `CHINA_TELECOM` / `CHINA_UNICOM`

- 协议与字段因运营商云 MAS / 行业网关而异，请对照控制台文档与对应 `*OutboundSender`
- 回执 / 上行 SPI 已注册

### 自定义 HTTP `CUSTOM_HTTP`

- 通过配置拼装自有网关；适合内部短信平台或过渡方案

### Mock `MOCK`

- 位于 `sms-runtime`，无需凭证，本地联调默认通道

---

## 国际厂商详解

### Twilio `TWILIO`

- **凭证：** AccountSid=`apiKey`，AuthToken=`apiSecret`
- **from：** 发信号码或 Messaging Service
- **callback：** 发信时可带 `callbackUrl`，SDK 写入厂商参数
- **回执 / 上行：** 解析已实现

### Vonage / MessageBird / Plivo / Infobip

- **Vonage（已升级）**：默认 `POST https://api.nexmo.com/v1/messages`，JSON：`channel=sms` + `message_type=text`；鉴权 Basic（apiKey:apiSecret）或 Bearer JWT（`config.jwt` / `accessToken`）。状态回调字段 `webhook_url`。若需旧接口：`SmsProviderConfig` 设 `apiMode=legacy` 或 `useLegacySmsApi=true`（`rest.nexmo.com/sms/json`）。
- 其余国际主流：凭证多为 apiKey(+Secret)；支持 per-message callback 的会写入请求

### AWS SNS `AWS`

- **凭证：** accessKeyId / Secret + `region`

### 云厂商国际

| 枚举 | 要点 |
|------|------|
| `ALIYUN_INTERNATIONAL` | `SendMessageToGlobe` / 模板；新加坡等节点 |
| `TENCENT_INTERNATIONAL` | TC3 + SendSms；`ap-singapore` |
| `HUAWEI_INTERNATIONAL` | WSSE(SHA-256) + batchSendSms |

号码建议带国际区号，如 `+8613800138000`。

---

## 回调与 Webhook

```
业务服务                              厂商
   │── send(..., callbackUrl) ─────────►│
   │                                    │
   │◄──── POST 送达回执 / 上行 ──────────│
   │     parseReceipt / parseInbound    │
```

**安全校验（可选）：**

```java
String err = webhookHandler.verifyWebhook(ts, nonce, rawBody, signature);
// null 通过；E005 / E006 失败
```

签名内容：`timestamp + "." + nonce + "." + rawBody`，HMAC-SHA256 hex。

---

## Failover 示例

```java
SmsClient client = SmsClients.builder()
        .addChannel(SmsChannel.of(SmsProviderType.ALIYUN, credA))
        .addChannel(SmsChannel.of(SmsProviderType.YUNPIAN, credB))
        .rateLimiter(RateLimiter.perMinute(60))
        .build();
```

详见 [进阶能力](features.md)。

---

## 扩展自有厂商

1. 实现 `OutboundSender`（及可选 Receipt/Inbound/Report）
2. `META-INF/services` 注册
3. `getSupportedProvider()` 返回小写 code，如 `myvendor`
4. 调用：`.providerCode("myvendor")`

无需修改核心枚举（2C 约定）。
