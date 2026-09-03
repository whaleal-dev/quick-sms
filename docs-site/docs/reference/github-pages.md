# GitHub Pages 维护说明

文档站由 Docusaurus（`docs-site/`）构建，经 GitHub Actions 发布。

## 线上地址

- GitHub Pages：https://whaleal-dev.github.io/quick-sms/
- 自定义域（若已配置）：https://whaleal.com/quick-sms/

## 仓库设置

1. Settings → Pages → **Source = GitHub Actions**
2. **不要**启用根目录 Jekyll（不要添加 `jekyll-gh-pages.yml`），否则首页会变成 README

## 工作流

| Workflow | 作用 |
|----------|------|
| `docs-pages.yml` | `main` 推送或手动触发时构建并部署 Pages |
| `docs-build.yml` | PR / 推送时校验 `docs-site` 能否构建 |

部署时环境变量：

- `DOCS_SITE_URL=https://whaleal.com`
- `DOCS_SITE_BASE_URL=/quick-sms/`

本地默认使用 `https://whaleal-dev.github.io` + `/quick-sms/`。

## 本地预览

```bash
cd docs-site
npm ci
npm start
```
