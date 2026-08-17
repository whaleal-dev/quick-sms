# CI / CD 说明

仓库：[whaleal-dev/quick-sms](https://github.com/whaleal-dev/quick-sms)

**只发布到 GitHub Packages**（不用 Maven Central）。

包地址：`https://maven.pkg.github.com/whaleal-dev/quick-sms`  
Packages 页：https://github.com/whaleal-dev/quick-sms/packages

## 需要配置什么？

| 角色 | 配置 |
|------|------|
| **发布（Actions）** | **无需** Secrets（用 `GITHUB_TOKEN`） |
| **消费（下载 JAR）** | **需要** `settings.xml` 认证 + `pom.xml` 仓库地址（见下） |

> GitHub Packages 即使公开包，Maven 拉取也**不能匿名**，必须用 PAT（`read:packages`）。

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

**1. `~/.m2/settings.xml`（必配）**

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_PAT</password> <!-- read:packages -->
  </server>
</servers>
```

**2. 项目 `pom.xml`**

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

`repository.id` 与 `server.id` 必须同为 `github`。

详见仓库 README：[Maven（从 GitHub Packages 拉取）](../README.md#maven从-github-packages-拉取)。

返回：[文档首页](README.md)
