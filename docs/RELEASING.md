# 构建、测试与发布

本文件是维护者清单。完成某一条命令或配置 CI，并不自动证明游戏内功能已经通过验证。

## 环境

- 64 位 JDK 21；用 `java -version` 与 Gradle 输出确认实际选中的运行时。
- 使用仓库内 Gradle Wrapper 8.14.1。
- 为 Fabric 与 NeoForge 分别准备隔离的 Minecraft 1.21.1 客户端实例；不要在用户的日常整合包中直接清理或覆盖文件。

## 自动化检查

Windows PowerShell：

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean :common:test :fabric:build :neoforge:build
```

Linux / macOS：

```bash
java -version
./gradlew --version
./gradlew clean :common:test :fabric:build :neoforge:build
```

也可以使用根项目聚合命令：

```bash
./gradlew clean test build
```

任何失败、跳过或缓存导致的不确定结果都应在发布前处理；需要新鲜证据时可增加 `--rerun-tasks`。

### Windows 中文路径说明

部分 Windows 环境中的 Gradle test worker 无法正确处理含中文字符的绝对 classpath，症状是所有已编译测试类都报告 `ClassNotFoundException`。这类环境问题仍然是一次失败，不能当作测试通过。先确认准备使用的盘符未被占用，再从临时 ASCII 盘符重新运行，并确保最终解除映射：

```powershell
$echoRoot = (Resolve-Path '.').Path
$echoDrive = 'T:'
if (Test-Path "$echoDrive\") { throw "$echoDrive is already in use" }

cmd /c "subst $echoDrive `"$echoRoot`""
if ($LASTEXITCODE -ne 0) { throw 'Unable to create temporary drive mapping' }

$echoPushed = $false
try {
    Push-Location "$echoDrive\"
    $echoPushed = $true
    .\gradlew.bat --no-daemon clean :common:test :fabric:build :neoforge:build --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'Gradle validation failed' }
} finally {
    if ($echoPushed) { Pop-Location }
    cmd /c "subst $echoDrive /D"
}
```

不要使用已经映射的盘符，也不要移除不是由本次检查创建的映射。GitHub Actions 的 Linux 工作区不依赖这一 Windows 规避方法。

## 手工验收

对 Fabric 与 NeoForge 各执行一次：

1. 只安装该加载器对应的不带 `-sources`、`-dev-shadow` 后缀的 JAR。
2. 使用 Java 21 启动 Minecraft 1.21.1，确认能到达主菜单且没有相关错误日志。
3. 从“选项”进入“Echo AI 设置”，确认 SCRIPTED / REMOTE 切换、输入遮罩、显示/隐藏与保存按钮可用。
4. 保持默认 `rememberKey=false` 保存，确认配置文件没有 `apiKey` 字段，并在重启后确认 Key 为空。
5. 若测试 `rememberKey=true`，只使用临时 Key；确认明文警告可见，测试后撤销 Key 并安全删除本地测试凭据。
6. 在世界中从暂停菜单打开对话；确认 SCRIPTED 能回复、清空会话可用、HUD 模式标记正确。
7. 使用受控的 OpenAI-compatible 测试 endpoint 验证 REMOTE 成功路径、连接测试、超时与无效响应。
8. 分别验证“失败回退伪 AI”开启与关闭时的行为；不得把回退回复记录为远程成功。
9. 在多人服务器上确认对话仍为客户端本地功能，模型回复不会触发服务器命令、实体操作或世界修改。
10. 检查日志、截图、测试报告、JAR 和 Git 历史，确认没有 API Key、Authorization 头或私人对话。

无法自动化的项目应在发布记录中明确标为需要手工验证，不能用“构建成功”替代。

## 产物

最终用户 JAR 位于：

- `fabric/build/libs/echo-companion-fabric-1.21.1-<version>.jar`
- `neoforge/build/libs/echo-companion-neoforge-1.21.1-<version>.jar`

不要把 `*-dev-shadow.jar` 当作最终发布包。`*-sources.jar` 可以按需作为独立源码附件，但不能替代运行 JAR。

可用以下命令生成 SHA-256：

```powershell
Get-FileHash -Algorithm SHA256 fabric\build\libs\*.jar
Get-FileHash -Algorithm SHA256 neoforge\build\libs\*.jar
```

```bash
sha256sum fabric/build/libs/*.jar neoforge/build/libs/*.jar
```

发布说明应逐项列出文件名、加载器、Minecraft 版本、Java 要求、校验值、已完成验证及仍需手工验证的内容。

## GitHub 发布流程

1. 更新 `gradle.properties` 中的 `mod_version`，检查模组元数据与 README 支持矩阵一致。
2. 在无关改动已排除的工作树中运行自动化检查与双加载器手工验收。
3. 推送候选提交，让 `.github/workflows/build.yml` 在 GitHub 上完成测试和两个加载器构建。
4. 审核 CI 日志与下载产物；不要仅依据绿色状态跳过游戏内验收。
5. 创建与版本一致的标签（例如 `v0.1.0`）和 GitHub Release，附加两个最终 JAR 与 SHA-256。
6. 在发布说明中披露 API Key 明文记忆行为、REMOTE 的第三方数据传输，以及与 Verity / ARR 无资产复用的原创边界。
