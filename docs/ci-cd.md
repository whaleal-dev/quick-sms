# CI / CD 说明

仓库：[whaleal-dev/quick-sms](https://github.com/whaleal-dev/quick-sms)

**只发布到 GitHub Packages**（不用 Maven Central，也不用配 Secret）。

包地址：`https://maven.pkg.github.com/whaleal-dev/quick-sms`  
Packages 页：https://github.com/whaleal-dev/quick-sms/packages

## 需要配置什么？

**仓库 Actions 无需任何 Secrets**（用 `GITHUB_TOKEN`）。

## 工作流

| Workflow | 监听 | 作用 |
|----------|------|------|
| [ci.yml](../.github/workflows/ci.yml) | 分支 `main`、**`release-*`**（PR / push） | 编译测试 |
| [publish-github-packages.yml](../.github/workflows/publish-github-packages.yml) | 分支 **`release-*`** | 测试 → 发 Packages → 建 Release |

## 发布

```bash
git checkout -b release-1.0.1
git push -u origin release-1.0.1
```

分支名 `release-1.0.1` / `release-v1.0.1` → 发布版本 `1.0.1`。

## 消费方下载 JAR

公开包，**只需在 `pom.xml` 配置仓库地址 + 依赖**（无需 `settings.xml`）：

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/whaleal-dev/quick-sms</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.whaleal.third</groupId>
  <artifactId>sms-all</artifactId>
  <version>1.0.0</version>
</dependency>
```

详见仓库 README：[Maven（从 GitHub Packages 拉取）](../README.md#maven从-github-packages-拉取)。

返回：[文档首页](README.md)
