# 贡献指南

感谢参与 Echo Companion。提交内容应保持范围清晰、可审查，并维护客户端安全边界。

## 开始之前

- Bug 与功能建议请使用对应的 Issue 模板。
- 安全问题按根目录 `SECURITY.md` 报告，不要公开漏洞细节或密钥。
- 不要提交 API Key、Authorization 请求头、私人 endpoint、个人对话或包含这些信息的日志。
- 模组玩法受 Verity 启发，但所有贡献必须保持独立原创；不要提交从 Verity / ARR 或其他第三方项目提取的代码、模型、纹理、声音、文本、UI、名称或品牌资产。
- 新增第三方代码或素材时，应在 Pull Request 中写明来源、许可证和再分发依据。
- 阅读根目录 `LICENSE_POLICY.md` 与 `THIRD_PARTY_NOTICES.md`，先确认改动所属的许可证范围。

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
- 涉及依赖、许可证、媒体来源或品牌时，更新许可证政策、第三方声明或发布文档，并列出来源证据。
- 列出实际执行的命令、结果和手工验证范围。
- 不混入无关格式化、生成目录或本地配置文件。

## 贡献许可证

提交 Pull Request 即表示贡献者确认自己拥有必要权利，并同意已接受内容按其仓库范围授权：

- 项目原创代码、测试、构建/CI 配置、功能性数据、翻译与文档：`GPL-3.0-only`；
- 视觉、音频、Logo、图标或其他品牌/创意资产：不会自动接受或开放授权；提交前必须提供来源、生成过程、输入材料和权利证明，并取得 EeryFrank 的书面许可；
- 经接受且未另行明确授权的新自有资产：`LicenseRef-EeryFrank-Assets-Permission-Required`，仅允许未修改资产随未经修改的官方完整包分发，其他使用需事先书面授权。

第三方内容必须保留原许可证和署名，并在 Pull Request 中提供可核验的来源。历史版本已经授予的 MIT 与 LGPL 权利继续有效，但新的贡献不能据此默认按旧许可证提交。完整边界见根目录 `LICENSE_POLICY.md`。
