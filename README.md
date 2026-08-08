# Echo Companion

## Introduction / 项目介绍

### English

Echo Companion is an original client-side AI dialogue companion mod for Minecraft Java Edition 1.21.1, built for both Fabric and NeoForge. It provides an in-game dialogue screen, HUD status indicators, AI settings, and two switchable modes:

- `SCRIPTED`: fully offline rule-based “pseudo-AI” dialogue with no API key required.
- `REMOTE`: responses generated through a player-configured OpenAI-compatible Chat Completions endpoint, with optional fallback to `SCRIPTED` when the remote service fails.

The original idea for this project came from the Verity mod and its approach to bringing an AI companion experience into Minecraft. Echo Companion is an independent, original implementation and does not copy or redistribute Verity's code, assets, dialogue, branding, or other content.

The current implementation is a client-side dialogue companion, not a pathfinding NPC. It cannot execute commands, take over player controls, or modify the server world.

### 中文

Echo Companion 是一个面向 Minecraft Java Edition 1.21.1 的原创客户端 AI 对话伴侣模组，同时构建 Fabric 与 NeoForge 版本。它提供游戏内对话界面、HUD 状态提示、AI 设置以及两种可切换模式：

- `SCRIPTED`：完全离线的规则式“伪 AI”对话，无需 API Key。
- `REMOTE`：通过玩家自行配置的 OpenAI-compatible Chat Completions 接口生成回复，并可在远程服务失败时回退到 `SCRIPTED`。

本项目最初的想法来源于 Verity 模组，以及它在 Minecraft 中加入 AI 伴侣体验的方向。Echo Companion 是独立、原创实现，不复制或再分发 Verity 的代码、资产、对话、品牌标识或其他内容。

当前实现是客户端对话伴侣，并非可寻路的 NPC；它不能执行指令、接管玩家操作或修改服务器世界。

## 支持范围

| 项目 | 当前范围 |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 或更高版本 |
| Fabric | Fabric Loader 0.16.0 或更高版本；构建基线为 0.16.14 |
| NeoForge | NeoForge 21.1；构建基线为 21.1.216 |
| 运行位置 | 客户端 |
| 模组版本 | 0.1.0（早期开发） |

这里的多平台支持指 Fabric 与 NeoForge 双加载器。当前代码没有宣称兼容其他 Minecraft 版本。

## 使用方法

1. 安装与游戏相匹配的 Fabric 或 NeoForge 加载器。
2. 将对应加载器的 Echo Companion JAR 放入客户端实例的 `mods` 目录；不要同时安装两个加载器版本。
3. 在主菜单或游戏内打开“选项”，点击右上角的“Echo AI 设置”。
4. 选择 `SCRIPTED` 或 `REMOTE`，按需填写接口、模型和 API Key，然后保存。
5. 进入世界后打开暂停菜单，点击“与 Echo 对话”。对话界面也可以再次进入 AI 设置。

`REMOTE` 模式可选择“失败回退伪 AI”。启用后，远程服务不可用或返回无效响应时，会显示离线规则回复并标记为回退结果。

## OpenAI-compatible 接口

请在设置中填写完整的 Chat Completions endpoint，例如：

```text
https://api.openai.com/v1/chat/completions
```

服务端需要接受非流式 `POST` 请求：请求体包含 `model` 与 `messages`，API Key 非空时通过 `Authorization: Bearer <key>` 发送；响应需要包含字符串字段 `choices[0].message.content`。

当前不支持 OpenAI Responses API、流式 SSE、工具调用或提供商专有的请求结构。远程服务是否兼容、可用以及如何计费，由相应服务提供商决定。

出于安全考虑，公网 endpoint 必须使用 HTTPS；只有 `localhost`、`127.0.0.1` 和 IPv6 回环地址允许 HTTP。endpoint 不允许用户信息、查询参数或片段，请勿把 Key 放进 URL；客户端也不会跟随 HTTP 重定向。

## API Key 与隐私

- 默认不记住 API Key。保存设置后，Key 只在当前游戏进程内使用，不写入配置文件；重新启动后需要再次输入。
- 如果主动开启“在本机记住 Key”，Key 会以未加密明文写入当前游戏实例的 `config/echo_companion-client.json`。任何能读取该文件或客户端进程的软件、模组或账户都可能取得它。
- API Key 不会通过 Minecraft 网络包发送给游戏服务器，但会作为认证信息发送到玩家配置的远程 endpoint。只应使用自己信任的服务。
- `REMOTE` 会把系统提示与本次本地对话消息发送给该 endpoint；`SCRIPTED` 引擎不发起远程 AI 请求。
- 对话历史只由客户端会话持有。当前实现没有服务器指令通道，也不会根据模型回复控制服务器世界。

建议使用可撤销、有限额度的专用 Key。不要把真实 Key 写进源码、提交、Issue、日志或截图。更多信息见 [安全策略](SECURITY.md)。

## 从源码构建与测试

需要 JDK 21。仓库自带 Gradle Wrapper，无需另行安装 Gradle。

Windows PowerShell：

```powershell
.\gradlew.bat clean test build
```

Linux / macOS：

```bash
./gradlew clean test build
```

也可以分别执行：

```powershell
.\gradlew.bat :common:test
.\gradlew.bat :fabric:build
.\gradlew.bat :neoforge:build
```

构建产物位于 `fabric/build/libs/` 与 `neoforge/build/libs/`。发布时应选择不带 `-sources` 和 `-dev-shadow` 后缀的重映射 JAR。

在部分 Windows 环境中，从包含中文字符的绝对路径直接启动 Gradle test worker，可能会错误地报告已编译测试类 `ClassNotFoundException`。遇到该特征时，应从纯 ASCII 路径或临时 ASCII 盘符重新执行测试，不能把失败忽略为通过；安全的临时盘符示例见 [发布说明](docs/RELEASING.md#windows-中文路径说明)。

仓库中的 GitHub Actions 会先运行公共模块单元测试，再分别构建 Fabric 与 NeoForge。工作流配置不等同于一次已完成的验证；发布前还必须在两个加载器的隔离客户端实例中进行手工启动和界面检查。完整清单见 [发布说明](docs/RELEASING.md)。

## 项目结构

```text
common/    共享的对话、配置、界面与测试代码
fabric/    Fabric 入口与打包配置
neoforge/  NeoForge 入口与打包配置
docs/      架构边界与发布流程
```

设计与安全边界详见 [架构说明](docs/ARCHITECTURE.md)。

## 与 Verity / ARR 的关系

Echo Companion 是独立、原创实现，仅借鉴“游戏内可切换离线对话与联网模型”的通用产品思路，与 Verity 或 ARR 的作者及项目没有从属或授权关系。本仓库不复制、打包或再分发 Verity / ARR 的代码、名称、对话文本、模型、纹理、声音、UI、品牌标识或其他资产。

贡献内容也必须保持这一边界。请不要提交从 Verity / ARR 或其他第三方项目提取的受版权保护内容；第三方依赖或素材必须具有明确且兼容的授权。

## 参与贡献

提交 Issue 或 Pull Request 前请阅读 [贡献指南](.github/CONTRIBUTING.md)。Bug 报告中务必删除 API Key、Authorization 请求头和其他私人信息。

## 许可证

本项目以 [MIT License](LICENSE) 开源。该许可证仅覆盖本仓库中由项目贡献者提供的原创内容，不授予任何第三方游戏、模组、品牌或素材的权利。
