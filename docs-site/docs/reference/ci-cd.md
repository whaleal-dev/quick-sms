# CI / CD 说明

仓库：[whaleal-dev/quick-sms](https://github.com/whaleal-dev/quick-sms)

| 目标 | 地址 |
|------|------|
| Maven Central | 坐标 `io.github.whaleal-dev:*`（发成功后可在 [Central Search](https://central.sonatype.com) 查） |

## 工作流

| Workflow | 监听 | 作用 |
|----------|------|------|
| [ci.yml](https://github.com/whaleal-dev/quick-sms/blob/main/.github/workflows/ci.yml) | `main`、`release-*` | 编译测试 |
| [publish-maven-central.yml](https://github.com/whaleal-dev/quick-sms/blob/main/.github/workflows/publish-maven-central.yml) | **`release-*`** | 发 **Maven Central** |

> 推送 **`release-x.y.z`** 触发 Maven Central 发布。

---

## 发布到 Maven Central（推荐对外）

发到中央仓后，消费方**只需依赖坐标**（无需 `settings.xml`）。

### 一、一次性准备

#### 1. 认领命名空间

1. 打开 [central.sonatype.com](https://central.sonatype.com) 登录  
2. **Namespaces** 中确认已有（或申请）**`io.github.whaleal-dev`**  
3. 与当前工程 `groupId` 一致（根 `pom.xml` 已是该值）

> GitHub 组织命名空间通常需按 Portal 指引完成验证（与 org `whaleal-dev` 关联）。

#### 2. 生成 User Token

Portal → **Account** → **Generate User Token**  
得到用户名 + 密码（密码只显示一次，妥善保存）。

#### 3. 准备 GPG 签名

Maven Central **强制** GPG 签名。本机已按身份 **whaleal \<hbn.king@gmail.com\>** 生成密钥时，把下面两份内容填进 GitHub Secrets：

| Secret | 本地文件 |
|--------|----------|
| `MAVEN_GPG_PRIVATE_KEY` | `~/.gnupg-quick-sms/maven-central-private-key.asc` |
| `MAVEN_GPG_PASSPHRASE` | `~/.gnupg-quick-sms/maven-central-passphrase.txt` |

```bash
cat ~/.gnupg-quick-sms/maven-central-private-key.asc   # → MAVEN_GPG_PRIVATE_KEY
cat ~/.gnupg-quick-sms/maven-central-passphrase.txt    # → MAVEN_GPG_PASSPHRASE
```

若需重新生成：

```bash
gpg --batch --generate-key <<'EOF'
Key-Type: RSA
Key-Length: 4096
Subkey-Type: RSA
Subkey-Length: 4096
Name-Real: whaleal
Name-Email: hbn.king@gmail.com
Expire-Date: 0
Passphrase: 你的口令
%commit
EOF
gpg --list-secret-keys --keyid-format LONG
gpg --armor --export-secret-keys KEYID > ~/.gnupg-quick-sms/maven-central-private-key.asc
gpg --keyserver hkps://keys.openpgp.org --send-keys KEYID
```

> 私钥与口令**不要**提交进 git。

#### 4. 配置 GitHub Secrets

仓库 **Settings → Secrets and variables → Actions** 新增：

| Secret | 内容 |
|--------|------|
| `MAVEN_CENTRAL_USERNAME` | Portal User Token 用户名 |
| `MAVEN_CENTRAL_PASSWORD` | Portal User Token 密码 |
| `MAVEN_GPG_PRIVATE_KEY` | 上一步导出的私钥全文（含 `BEGIN/END PGP PRIVATE KEY BLOCK`） |
| `MAVEN_GPG_PASSPHRASE` | 生成密钥时设置的口令 |

### 二、CI 自动发布

```bash
# 确保 main 已包含待发布代码
git checkout main && git pull
git checkout -b release-1.0.0
git push -u origin release-1.0.0
```

分支 `release-1.0.0` / `release-v1.0.0` → 版本 **`1.0.0`**。

同一推送会触发：

1. **Publish Maven Central**（`mvn -Pcentral deploy`）
2. CI 构建测试

也可在 Actions 页手动跑 **Publish Maven Central**。

同步延迟：Portal 显示 Published 后，通常数十分钟内可在 Central / Maven 拉取。

### 三、本地手动发布（可选）

`~/.m2/settings.xml`：

```xml
<servers>
  <server>
    <id>central</id>
    <username>你的_User_Token_用户名</username>
    <password>你的_User_Token_密码</password>
  </server>
</servers>
```

本机需能 `gpg` 签名（密钥已导入、口令可用），然后：

```bash
# 可选：先改版本
mvn -B versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false -DprocessAllModules=true

mvn -B -DskipTests -Pcentral clean deploy
```

---

## 消费方依赖（发到 Central 之后）

```xml
<dependency>
  <groupId>io.github.whaleal-dev</groupId>
  <artifactId>sms-all</artifactId>
  <version>1.0.0</version>
</dependency>
```

无需 `<repositories>`、无需 `settings.xml`。本地开发也可用源码 `mvn install`。

返回：[文档前言](../intro.md) · [仓库 README](https://github.com/whaleal-dev/quick-sms#readme)
