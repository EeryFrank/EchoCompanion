# 贡献指南

感谢参与 Echo Companion。提交内容应保持范围清晰、可审查，并维护客户端安全边界。

## 开始之前

- Bug 与功能建议请使用对应的 Issue 模板。
- 安全问题按根目录 `SECURITY.md` 报告，不要公开漏洞细节或密钥。
- 不要提交 API Key、Authorization 请求头、私人 endpoint、个人对话或包含这些信息的日志。
- 不要提交从 Verity / ARR 或其他第三方项目提取的代码、模型、纹理、声音、文本、UI、名称或品牌资产。
- 新增第三方代码或素材时，应在 Pull Request 中写明来源、许可证和再分发依据。

## 本地检查

使用 JDK 21 和仓库自带 Wrapper：

```powershell
.\gradlew.bat clean :common:test :fabric:build :neoforge:build
```

非 Windows 系统使用 `./gradlew`。若改动影响游戏界面、配置、Mixin 或加载器入口，还应在 Fabric 与 NeoForge 的 Minecraft 1.21.1 隔离实例中手工验证，并在 Pull Request 中如实记录结果。未执行的检查写明“未执行”，不要推测为通过。

## Pull Request 要求

- 解释问题、实现方式与用户可见变化。
- 保持 Fabric 与 NeoForge 行为一致，或明确说明差异。
- 为可独立测试的逻辑补充或更新测试。
- 涉及 endpoint、凭据、网络数据或持久化时，更新 README、SECURITY 或架构说明。
- 列出实际执行的命令、结果和手工验证范围。
- 不混入无关格式化、生成目录或本地配置文件。

提交 Pull Request 即表示贡献者有权按本仓库 MIT License 提供相关内容。
