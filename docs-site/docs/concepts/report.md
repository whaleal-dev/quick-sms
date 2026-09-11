# 状态报告（Report / DLR）

状态报告（Delivery Report，DLR）回答一个问题：**这条出站短信现在处于什么状态？**

## 为什么需要 Report

出站 API 返回的 `messageId` 多半只表示「厂商队列已接收」。真正送达手机还要经过运营商网络，可能出现：

- 已送达（DELIVERED）
- 发送中 / 未知（PENDING / UNKNOWN）
- 失败：空号、关机、拒收、过期、内容拦截等

业务上常用 Report 做：

- 验证码是否真的发出（辅助风控，不替代短信本身）
- 通知类失败告警与补发
- 对账、计费核对

## 两种获取方式

| 方式 | 说明 | Quick SMS |
|------|------|-----------|
| **推送回执** | 厂商 Webhook 推送终态/中间态 | `parseReceipt` → `SmsReceipt` |
| **主动查询** | 用 messageId 调厂商查询 API | `fetchReport` / `fetchReports` → `SmsReport` |

多数生产系统以 **Webhook 回执为主、主动查询为辅**（补漏、对账、厂商不推中间态时）。

```text
出站成功拿到 messageId
        │
        ├─► Webhook Receipt（异步，推荐）
        │
        └─► fetchReport(messageId)（补洞 / 对账）
```

## Receipt 与 Report 的关系

在 Quick SMS 里二者拆开建模，但业务语义相近：

| 类型 | 触发 | 典型用途 |
|------|------|----------|
| `SmsReceipt` | 厂商推送到你的 URL | 实时更新发送状态 |
| `SmsReport` | 你主动查询 | 批查、重试、对账 |

状态枚举因厂商而异，SDK 会尽量归一到如 `DELIVERED` / `FAILED` / `EXPIRED` / `UNKNOWN` 等。

## 示例：主动查询

```java
SmsReport report = webhookHandler.fetchReport(
        SmsProviderType.ALIYUN,
        messageId,
        credentials);

SmsReport.ReportStatus status = report.getCurrentStatus();
```

## 设计建议

1. **以 messageId 为关联键**，在出站成功时落库，回执到来时更新。
2. **幂等处理**：同一回执可能重推。
3. **超时兜底**：长时间无回执可主动 `fetchReport`。
4. **不要阻塞发信线程**等回执；异步处理回调。

## 下一步

- [Webhook 回调](./webhook.md)
- [出站（Outbound）](./outbound.md)
