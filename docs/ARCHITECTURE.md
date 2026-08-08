# 架构与边界

## 模块

- `common`：共享 AI 抽象、对话历史、配置编解码、客户端界面、HUD 与 Mixin。
- `fabric`：Fabric 客户端入口和最终打包。
- `neoforge`：NeoForge 客户端入口和最终打包。

两种加载器共享同一套业务代码，目标 Minecraft 版本均为 1.21.1，编译目标为 Java 21。

## 对话路径

```text
暂停菜单 → 本地对话界面 → ClientDialogueController
                              ├─ SCRIPTED → ScriptedDialogueEngine
                              └─ REMOTE   → OpenAiCompatibleDialogueEngine
                                                └─ 玩家配置的 HTTPS endpoint
```

`ClientDialogueController` 在客户端保存当前会话历史。`SCRIPTED` 根据本地规则生成回复；`REMOTE` 通过 Java `HttpClient` 异步发送 Chat Completions 请求。启用回退时，REMOTE 失败会转到 SCRIPTED，并在结果中保留实际模式和回退标记。

## OpenAI-compatible 协议子集

请求为非流式 JSON `POST`：

```json
{
  "model": "provider-model-name",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ]
}
```

当 API Key 非空时，请求还会包含 `Authorization: Bearer <key>`。实现读取以下响应字段：

```json
{
  "choices": [
    {"message": {"content": "reply text"}}
  ]
}
```

实现不支持 Responses API、SSE 流式响应、工具调用或提供商专有字段。完整 endpoint 应在游戏设置中由玩家明确填写。

## 客户端与服务器边界

Echo Companion 当前没有自定义客户端到服务器的数据包，也没有服务器侧入口。API Key、配置和对话历史不通过 Minecraft 协议发送给服务器。

客户端会读取本地玩家可见的信息以构造对话上下文，但当前远程请求实现只序列化系统提示、历史消息与本次用户消息。无论模型回复什么，都只会显示为客户端文本；没有命令执行、方块修改、实体控制或其他世界写入桥接。

这条边界若在未来版本中变化，必须在实现前明确设计权限、服务器授权、审计记录与用户确认，并同步更新 README 和 SECURITY。

## 配置生命周期

配置文件位于当前游戏目录的 `config/echo_companion-client.json`。

- `rememberKey=false`（默认）：编码配置时不写入 `apiKey`；重新加载时 Key 为空。
- `rememberKey=true`：非空 Key 作为普通 JSON 字符串写入本机文件，不做加密。
- 配置读取失败或内容无效时，存储层回到安全默认值。
- endpoint 允许 HTTPS，或指向精确回环主机的 HTTP；用户信息、查询参数与片段被拒绝，请求客户端不跟随重定向。
- 远程响应以流式订阅器限制为 64 KiB，最终回复限制为 8,000 个 Java 字符，避免异常 endpoint 造成无界内存与会话增长。

## 原创与第三方内容

本仓库的实现、文本和界面应保持原创。模组玩法受 Verity 启发，但 Verity / ARR 均不作为代码、文本或资产来源；不得复制、打包或再分发其代码、资源、对话、模型、纹理、声音、UI、名称或品牌元素。任何新增第三方依赖或素材都应记录来源、许可证与再分发条件。
