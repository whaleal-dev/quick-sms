# docs-site

Quick SMS 官方 Docusaurus 文档站。发布地址：https://whaleal-dev.github.io/quick-sms/

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

## 目录

- `docs/concepts/` 出站 / 入站 / Report / Webhook 等通用短信技术
- `docs/getting-started/` Spring Boot / JavaSE 快速开始
- `docs/guide/` 进阶能力、API、厂商
- `docs/reference/` CI/CD 与 Pages 维护说明

仓库根目录 `docs/` 保留为 Markdown 速查；以本目录为公开文档站源。
