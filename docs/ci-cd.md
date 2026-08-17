# CI / CD 说明

仓库：[whaleal-dev/quick-sms](https://github.com/whaleal-dev/quick-sms)

**只发布到 GitHub Packages**（不用 Maven Central，也不用配 Central / GPG 密钥）。

包地址：`https://maven.pkg.github.com/whaleal-dev/quick-sms`  
Packages 页：https://github.com/whaleal-dev/quick-sms/packages

## 需要配置什么？

### GitHub 仓库（Actions）

**不用配任何 Secrets。**

打标签发布时用内置 `GITHUB_TOKEN`（workflow 已开 `packages: write`）。

### 本地（仅当你要本机 `mvn deploy`）

`~/.m2/settings.xml` 增加（server id 必须是 `github`）：

```xml
<server>
  <id>github</id>
  <username>你的GitHub用户名</username>
  <password>PAT</password>
</server>
```

PAT 权限勾选：`write:packages`（读依赖再加 `read:packages`）。

> 若只在 CI 打标签发布，本地可以不配。

## 工作流

| Workflow | 触发 | 作用 |
|----------|------|------|
| [ci.yml](../.github/workflows/ci.yml) | PR / push `main` | 编译测试 |
| [publish-github-packages.yml](../.github/workflows/publish-github-packages.yml) | 标签 `v1.0.1` | 测试 → 发 Packages → 建 Release |

## 发布

```bash
git tag v1.0.1 && git push origin v1.0.1
```

返回：[文档首页](README.md)
