# docs-site

Quick SMS 官方 Docusaurus 文档站。发布地址：<https://docs.whaleal.com/quick-sms/>

## 本地

```bash
cd docs-site
npm install
npm start
```

## 构建（与 GitHub Pages 相同）

```bash
npm run typecheck
npm run build
```

推送到 `main` 后由 `.github/workflows/docs-pages.yml` 自动发布。

## GA4

本站已接入 GA4（与主站同一媒体资源，2026-10-18）。要点：

- 实现：`src/ga/analytics.ts`（Consent Mode v2 默认拒绝 + 主机名闸门 + 手动 page_view），
  由 `src/theme/Root.tsx` 挂载；同意横幅在 `src/components/ConsentBanner.tsx`。
- 只有生产域名 `docs.whaleal.com` 放行：本地 dev 与 `*.github.io` 预览域不加载、不弹条。
- **改 GA ID / 排查埋点 / 新站接入的完整口径见 `project/conventions/analytics-ga4.md`**（跨仓总台账）。

## 目录

- `docs/concepts/` 出站 / 入站 / Report / Webhook 等通用短信技术
- `docs/getting-started/` Spring Boot / JavaSE 快速开始
- `docs/platform/` **Whaleal Messaging 云平台**（托管多渠道中转平台）接入：总览 / 接入方式 / 控制台指南 / 开放 API / 状态回调 / 套餐配额 / 能力路线图 / FAQ
- `docs/guide/` 进阶能力、API、厂商
- `docs/reference/` CI/CD 与 Pages 维护说明

仓库根目录 `docs/` 保留为 Markdown 速查；以本目录为公开文档站源。
