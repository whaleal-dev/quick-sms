# 状态回调接入

发送接口返回 `submitted` 只代表**平台已受理**。要知道消息最终是送达、失败还是被拒，必须接回调 —— 或者用 `messageId` 主动查状态。

这一页讲清楚**两条容易混淆的回调链路**，以及怎么把它们接进你的 Spring Boot / Java 应用。

## 两条链路，别混

平台在 `sms.whaleal.com` 和你的供应商之间有两条方向相反的回调：

```text
   你的系统 ──①POST /open/v1/sms/send──► Whaleal SMS ──► Twilio / Vonage / Infobip ──► 用户
                                              ▲                        │
                                              │                        │
                       ② 供应商回执地址       │                        │
                       （你填到供应商后台）    └────────────────────────┘
                       
   你的系统 ◄──③ 状态回调地址（你填在开发者中心）── Whaleal SMS
                        （消息到达终态时推送）
```

| 链路 | 方向 | 谁来配置 | 用途 |
|---|---|---|---|
| **② 供应商回执地址** | 供应商 → 平台 | 平台给出 URL，**你复制到供应商后台** | 平台据此把消息状态更新到终态 |
| **③ 状态回调地址** | 平台 → 你的系统 | **你在开发者中心填自己的 URL** | 你的业务系统收到终态通知 |

**②没配，③就永远不会有终态可推。** 这是很常见的漏配：只填了③，然后发现回调一直不来 —— 原因是供应商回执没进平台，消息状态停在 `submitted`。

## 配置步骤

进控制台 **开发者中心**（`/app/developer`），在「回执与状态回调」区块：

1. **供应商回执地址** —— 页面会直接给出形如下面的 URL，把它填到 Twilio / Vonage / Infobip 后台的回执回调处：

```text
https://smsapi.whaleal.com/webhook/sms/{provider}?token=<回调令牌>
```

   > 注意把 `{provider}` 换成实际供应商代码（如 `twilio`）。
   >
   > ⚠️ 这个地址**必须指向后端域名**。不要指向前端站点 —— 前端有 SPA 兜底路由，`/webhook` 不会被转发到后端。

2. **状态回调地址（可选）** —— 填你自己的服务地址，形如：

```text
https://your-app.example.com/sms/callback
```

   不填则平台不推送，你只能靠轮询状态接口。

**验收判据**：你的服务至少收到一次终态推送，且请求头带 `X-Callback-Token`。

## 推送载荷

消息到达终态（`delivered` / `failed` / `rejected`）时，平台异步 POST 到这个地址：

```json
{
  "messageId": "6a1f9c8e2b...",
  "providerMessageId": "SM8f10c8...",
  "status": "delivered",
  "rawStatus": "delivered",
  "to": "+14155550123",
  "provider": "twilio",
  "errorCode": null,
  "encoding": "GSM7",
  "segments": 1,
  "timestamp": "2026-10-07T11:32:15.482Z"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `messageId` | string | 平台消息 ID（**与你发送时拿到的同一个**，用它做关联） |
| `providerMessageId` | string \| null | 供应商侧消息 ID |
| `status` | string | 终态：`delivered` / `failed` / `rejected` |
| `rawStatus` | string \| null | 供应商原始状态（如 Twilio `delivered`、Vonage `expired`） |
| `to` | string | 接收号码 |
| `provider` | string | 承运供应商 |
| `errorCode` | string \| null | 失败 / 拒绝时的错误码 |
| `encoding` | string \| null | 本条编码口径 |
| `segments` | int | 本条计费条数 |
| `timestamp` | string | 推送时间（ISO-8601 UTC） |

> 回调里带回了 `encoding` 与 `segments`，所以你可以**在收到回调的当下就完成对账**，不必再逐条回查状态接口。

## 验签：`X-Callback-Token`

平台推送时会在请求头带上：

```
X-Callback-Token: <你在开发者中心看到的回调令牌>
```

**服务端必须校验这个头**，否则任何人都能伪造回调把你的消息标记成 `delivered`。

> 同一个令牌也用在**供应商回执地址**的 `?token=` 参数里，用于防止伪造回执。它是同一个值，但用途不同：一个是你验平台，一个是平台验供应商。

## 重试与幂等

| 行为 | 规则 |
|---|---|
| **成功判据** | 你的服务返回 **2xx** |
| **重试** | 非 2xx 或超时，按 **1s / 5s / 15s** 退避重试，共 3 次 |
| **仍然失败** | 放弃，该条标记 `callbackResult=failed`（可联系平台排查） |
| **幂等** | 极端情况下重试可能造成**重复推送** —— 请**按 `messageId` 去重** |

推送走独立线程池，不会阻塞发送链路 —— 你的回调慢，不会拖慢别人的发信。

## Spring Boot 接收示例

```java
@RestController
public class SmsCallbackController {

    private final SmsCallbackService service;

    public SmsCallbackController(SmsCallbackService service) {
        this.service = service;
    }

    @PostMapping("/sms/callback")
    public ResponseEntity<Void> onCallback(
            @RequestHeader(value = "X-Callback-Token", required = false) String token,
            @RequestBody Map<String, Object> payload) {

        // 1) 验签 —— 不通过就不要往外说原因，直接 401
        if (!service.verifyToken(token)) {
            return ResponseEntity.status(401).build();
        }

        String messageId = String.valueOf(payload.get("messageId"));
        String status = String.valueOf(payload.get("status"));

        // 2) 幂等 —— 同一个 messageId 可能被推第二次
        if (!service.firstTime(messageId, status)) {
            return ResponseEntity.ok().build();   // 已处理过，也要回 2xx，否则会被反复重试
        }

        // 3) 落库 / 触发业务动作（更新订单、发内部告警…）
        service.apply(payload);

        // 4) 必须回 2xx，否则平台会重试
        return ResponseEntity.ok().build();
    }
}
```

几个容易写错的点：

| 写法 | 问题 |
|---|---|
| 处理完直接 return（无返回体）= 200 | ✅ 正确 |
| 校验失败返回 200 | ❌ 伪造请求被当成处理成功 |
| 重复推送时返回 4xx | ❌ 会触发更多重试；重复不是错误，返回 2xx 即可 |
| 在回调里做慢操作（发另一条短信、调外部接口） | ⚠️ 会拖到超时被判定失败；先落库、异步处理 |

## 令牌重置的影响

开发者中心的**「重置令牌」会让旧的回执地址立即失效** —— 供应商那边还用旧 token 推回执会被拒。

所以重置之后，**必须回到每家供应商后台把回执地址更新一遍**。这个操作不要在业务高峰期做。

## 排错

| 现象 | 排查方向 |
|---|---|
| 状态一直停在 `submitted` | **供应商回执地址**没填对，或供应商后台没保存 |
| 回调服务一次都没被调用 | 状态回调地址为空（未配置）；或地址不可公网访问 |
| 回调收到但验签不过 | 令牌被重置过；或用了旧令牌 |
| 收到重复回调 | 正常现象（重试机制），按 `messageId` 幂等 |
| 回调地址改完没生效 | 确认已点保存；确认返回过 2xx |
| 想在不接回调的情况下确认送达 | 用 `GET /open/v1/sms/{messageId}` 轮询，见 [开放 API](open-api.md#query-status) |

---

上一篇：[开放 API 参考](open-api.md) · 下一篇：[套餐与配额](plans.md)
