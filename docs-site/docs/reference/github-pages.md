# GitHub Pages 部署

公开文档地址：

**https://whaleal.com/quick-sms/**

不要用 GitHub 的 `jekyll-gh-pages.yml`。那个模板从仓库根目录跑 Jekyll，首页会变成 `README.md`。

发布工作流是 [`.github/workflows/docs-pages.yml`](https://github.com/whaleal-dev/quick-sms/blob/main/.github/workflows/docs-pages.yml)：推送到 `main` 就会构建 `docs-site/` 并部署。Actions 里名叫 **Deploy GitHub Pages**。

## 仓库设置

Settings → Pages：

1. **Source** 选 **GitHub Actions**（首次可用 workflow 里 `configure-pages` 的 `enablement: true` 自动开启）
2. 不要选 Deploy from a branch，也不要选 Jekyll / Static HTML
3. 自定义域名保持 `whaleal.com`（与 aihub 同域，路径 `/quick-sms/`）

## 站点配置

| 项 | 值 |
|----|------|
| `url` | `https://whaleal.com` |
| `baseUrl` | `/quick-sms/` |
| 构建目录 | `docs-site/build` |
| 工作流 | `.github/workflows/docs-pages.yml` |

## 本地验证（与线上同一条命令）

```bash
npm --prefix docs-site ci
npm --prefix docs-site run typecheck
npm --prefix docs-site run build
```

Docusaurus 开启了 `onBrokenLinks: throw`，坏链会导致 Pages 构建失败。

## 发布后核对

1. https://whaleal.com/quick-sms/ 能打开，且是带导航的文档站（不是 README）
2. https://whaleal.com/quick-sms/docs/intro 能打开
3. https://whaleal.com/quick-sms/docs/concepts/overview 能打开
