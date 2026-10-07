# 开放 API 参考

Whaleal SMS 对业务系统暴露的全部接口就是三个。它们都被冻结成契约，由平台侧的单测锁形状（响应字段、字段类型都不可随意改动），因此可以放心接入。

## 基址 {#base-url}

```
https://smsapi.whaleal.com/open/v1
```

> ⚠️ **别把两个域名搞混**，这是接入时最容易踩的坑：
>
> | 域名 | 是什么 | 能不能调 API |
> |---|---|---|
> | `sms.whaleal.com` | 控制台 + 官网（前端静态站） | ❌ **不能**。它是 SPA，任意路径都会回落到首页 HTML |
> | `smsapi.whaleal.com` | 后端开放 API | ✅ 只有这个域名是接口 |
>
> 如果往 `sms.whaleal.com/open/v1/...` 发请求，你会拿到一段 HTML 而不是 JSON —— 那不是接口报错，是域名用错了。

## 鉴权

所有 `/open/v1/**` 请求都要带密钥，两种方式任选其一：

```
Authorization: Bearer sk_xxxxxxxx
X-API-Key: sk_xxxxxxxx
```

密钥与租户、绑定渠道的关系在**服务端强制校验**；密钥可以在控制台随时禁用、删除或重新签发。

| 情况 | 返回 |
|---|---|
| 没带密钥 | HTTP 401，`code` = `40100`，`msg` = `missing api key` |
| 密钥无效 / 已禁用 | HTTP 401，`code` = `40100`，`msg` = `invalid api key` |

> 🔴 **`sk_` 密钥是服务端凭据，绝不能下发到浏览器**。前端直连会把它暴露给任何一个打开 DevTools 的人。正确的做法是：**你的后端持有密钥**，前端调你自己的后端。

## 统一响应格式

所有接口返回同一个信封：

```json
{ "code": "0000", "msg": "success", "data": { } }
```

`code` 是**字符串**业务码，`"0000"` 为成功。失败时 HTTP 状态与业务码分离映射：

| 业务码 | HTTP 状态 | 含义 | 该怎么处理 |
|---|---|---|---|
| `40000` | 400 | 请求不合法：参数缺失 / 格式错误、密钥未绑定渠道、无 ACTIVE 渠道、编码口径不合法 | **不要重试**，改请求 |
| `40100` | 401 | 密钥缺失或无效 | 检查密钥；不要重试 |
| `40300` | 403 | 禁止访问 | 检查租户状态 |
| `40400` | 404 | 资源不存在（`messageId` 查无此条，或不属于本租户） | 检查 ID |
| `42900` | 429 | 超过套餐 QPS 限速 | **指数退避后重试** |
| `50000` | 500 | 平台内部错误 | 可退避重试；持续失败请联系支持 |

> 判断成功请认 `code == "0000"`，**不要只看 HTTP 200**。反过来也一样：`4xx` 里带业务码的响应体是 JSON，可以解析出原因。

---

## 1. 发送短信

```
POST /open/v1/sms/send
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `to` | string | 是 | 接收号码，**E.164 格式**（如 `+14155550123`） |
| `content` | string | 是 | 短信正文。**正文不落库存档**（合规要求），请自行留存业务侧记录 |
| `from` | string | 否 | 显式指定发信号码 / 签名。缺省时平台按该渠道绑定的 Sender **自动注入**（多 Sender 加权轮询，调用方不感知号码切换）；显式指定时全局优先 |

**响应 `data` 字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `messageId` | string | 平台消息 ID，**后续查状态与对账的唯一钥匙** |
| `status` | string | 受理状态，见下方状态表 |
| `provider` | string | 实际承运的供应商渠道代码 |
| `to` | string | 接收号码（回显） |
| `providerMessageId` | string \| null | 供应商侧消息 ID；**为 null 表示供应商未受理（不计费）** |
| `rawStatus` | string \| null | 供应商原始状态（归一前），排查用 |
| `errorCode` | string \| null | 失败时的供应商错误码 |
| `encoding` | string \| null | 本条实际生效的编码口径：`GSM7` / `UCS2` / `UTF8` / `GBK` |
| `segments` | int | 本条计费条数（分段数） |

**示例**

```bash
curl -X POST https://smsapi.whaleal.com/open/v1/sms/send \
  -H "Authorization: Bearer sk_xxxxxxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "to": "+14155550123",
    "content": "Your verification code is 824193"
  }'
```

```json
{
  "code": "0000",
  "msg": "success",
  "data": {
    "messageId": "6a1f9c8e2b...",
    "status": "submitted",
    "provider": "twilio",
    "to": "+14155550123",
    "providerMessageId": "SM8f10c8...",
    "rawStatus": null,
    "errorCode": null,
    "encoding": "GSM7",
    "segments": 1
  }
}
```

### 为什么 `encoding` 与 `segments` 重要

平台**不持久化正文**，事后无法重算这条短信算几条。所以：

- `encoding` 与 `segments` 是**逐条对账的唯一依据**，以接口回传值为准；
- `segments` 是「这条在账单上算几条」—— 长短信（超过单条长度）会大于 1；
- 编码口径上线前的历史消息，`encoding` 为 `null`、`segments` 为 `0`（平台如实报告"无口径信息"，**不会替你猜一个 1**）。

### 超时了要不要重发？——不要 {#no-resend}

这是接入阶段**代价最高的一个错误**，请务必让团队都知道：

> 🔴 **平台不做请求去重**（不提供幂等键）。同一个请求重发一次，就是**真发出去第二条短信**，并按条计费。
>
> 发送接口是**异步受理**：返回 `0000` 只表示平台已把短信提交给供应商，此刻它**可能已经发出**。所以**网络超时、连接重置，都不等于没发**。

正确的处理：

| 情况 | 该做什么 |
|---|---|
| 拿到了 `messageId` | 用状态查询或等[状态回调](callback.md)，**不要重发** |
| 超时、没拿到 `messageId` | 到控制台 **消息日志** 页，按「时间窗 + 接收号码」自查是否已发出，**也不要重发** |

客户端超时不会让平台回滚已提交的短信；重复发送的条数照常计费。

---

## 2. 查询发送状态 {#query-status}

```
GET /open/v1/sms/{messageId}
```

响应字段与发送接口**完全一致**。只允许查询**本租户**的消息 —— 跨租户或不存在返回 `40400`。

**状态取值**

| 状态 | 含义 |
|---|---|
| `submitted` | 已提交供应商（发送接口受理成功的初始态） |
| `delivered` | 已送达终端（供应商回执确认） |
| `failed` | 发送失败（提交即失败，或供应商回执失败） |
| `rejected` | 被拒绝（供应商拒收，`errorCode` 携带原因） |

```bash
curl https://smsapi.whaleal.com/open/v1/sms/6a1f9c8e2b... \
  -H "Authorization: Bearer sk_xxxxxxxx"
```

> 这个接口也是很好的**探活路径**：平台自身的可用性监控就是探它。

---

## 3. 查询当月用量

```
GET /open/v1/usage
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `period` | string | 计费周期键，UTC `yyyyMM`（如 `202609`） |
| `plan` | string | 当前套餐：`FREE` / `GROWTH` / `PRO` / `ULTRA` |
| `qps` | int | 当前套餐的提交速率上限（req/s）；`≤0` 表示不限 |
| `messageCount` | int | 当月**提交次数**（发了几次请求） |
| `billedSegments` | int | 当月**计费条数**（长短信一条算多条；仅供应商已受理的计入） |

> 这两个计数**必然不等**，它们回答的是两个不同问题。与控制台计费页同一数据源。对账方法见 [套餐与配额](plans.md#usage-reconcile)。

> 注意这些计数字段在 JSON 里是**数字而不是字符串**。这一点被平台侧的单测专门锁住了 —— 因为某些序列化配置会把长整数输出成 `"4"`，客户拿去做算术会静默算错。

---

## 速率限制

租户级限速，**单发 / 批量 / 定时 / 开放 API 统一计入**，按固定 1 秒窗口计数。

| 套餐 | QPS 上限 | 渠道数上限 | 日志保留期 |
|---|---|---|---|
| `FREE` | 10 | 1 | 7 天 |
| `GROWTH` | 50 | 3 | 7 天 |
| `PRO` | 100 | 10 | 30 天 |
| `ULTRA` | 500 | 不限 | 90 天 |

超限返回 HTTP 429 + `code` = `42900`，客户端应做**指数退避重试**。

> 除套餐限速外，网关还有一层**按客户端 IP 的兜底限流**（上限远高于上表任何档位，只用于拦异常流量）。它也返回 429 + `42900`，但 `msg` 以 `edge rate limit exceeded` 开头 —— 可据此区分是哪一层挡下的。

---

## Java 接入要点

平台没有官方 Java 客户端，用任意 HTTP 客户端都行（Spring 的 `RestClient`、OkHttp、Java 11+ 的 `HttpClient`）。三个必须做对的点：

```java
// 1) 密钥从环境变量取，绝不硬编码
String key = System.getenv("SMS_API_KEY");

// 2) 发送：只解析 code == "0000"，并把 messageId 落库
//    ⚠️ 超时不要重发 —— 捕获超时后走「消息日志自查」，不是重试
// 3) 尊重档位 QPS，收到 42900 做指数退避
```

**如果你的项目已经在用 Quick SMS SDK**，可以这样分工：SDK 负责**直连你自己的 CPaaS 通道**（用于对延迟敏感的验证码主链路），平台负责**其余多渠道与统一观测**，两者共用同一批供应商账号，互不冲突。

---

## 接口一致性

接口定义在平台后端源码中有对应实现与契约测试：控制器 `OpenSmsController`、鉴权 `ApiKeyAuthFilter`、发送核 `SendService`、回调 `CallbackPusher`、形状锁 `OpenApiShapeTest`。**接口行为变更必须同步更新文档**，所以这里写的就是线上跑的。

线上版（可分享给同事）：[sms.whaleal.com/developers/api](https://sms.whaleal.com/developers/api)

---

下一篇：[状态回调接入](callback.md) · [套餐与配额](plans.md)
