# 许可证迁移记录

## 2026-08-29 GPL 源码与受限资产政策

- 当前及未来的项目原创源码、测试、构建/CI 配置、功能性数据、翻译与文档改为 `GPL-3.0-only`；根目录 `LICENSE` 为未经修改的 SPDX License List Data `v3.28.0` 官方 GPLv3 正文。
- 新增项目自有视觉、音频或品牌资产不会因目录或提交而自动获得开放内容授权；完成来源、权利和书面授权核验并在 `ASSET_LICENSES.md` 明确登记后，若没有其他经授权许可，则使用 `LicenseRef-EeryFrank-Assets-Permission-Required`。
- 该 LicenseRef 只允许未修改资产随未经修改的官方发布包完整分发；单独提取、复用、修改、再分发、商业或品牌使用须事先取得 EeryFrank 的书面授权。
- 现有两张 Logo 继续适用历史 MIT 条款及单独的品牌/商标边界；当前没有 LicenseRef 或 CC BY-SA 资产。第三方、Gradle Wrapper、Minecraft 与官方映射继续适用各自原始条款。
- 提交 `569b02de7c2dfc6e3ea583855485c07098541f72` 及更早版本的 MIT 授权，以及后续公开修订至 `d713ba3144270d3daa8932c7a25c934251b89312` 的 LGPL 授权，对其原适用副本继续有效；本次变更不重写标签、Release 或历史许可。
- 当前门禁核对 GPL、历史 LGPL、MIT、旧 CC、Apache 和资产 LicenseRef 的固定正文，要求所有项目源码/脚本使用 GPL SPDX，并检查最终 JAR 的 GPL、LicenseRef、政策与第三方声明条目。

本节的实际验证结果应以执行本次政策分支上的 `verifyLicensing`、`clean test build verifyModArtifacts --rerun-tasks` 和独立 JAR 审计输出为准；下节保留 2026-08-28 迁移的历史证据，不代表当前 GPL 候选包的验证结果。

## 2026-08-28 历史 MIT→LGPL 迁移范围与基线

- 迁移前基线：`569b02de7c2dfc6e3ea583855485c07098541f72`
- 基线核对：本地 `main`、GitHub `main` 与 GitLab `main` 指向同一提交，迁移从该提交建立独立分支。
- 迁移前许可证：项目原创内容整体使用 MIT License；原正文已原样保存在 `LICENSES/MIT.txt`。
- 迁移后默认：项目原创代码与功能性材料使用 `LGPL-3.0-or-later`；原创非品牌美术与音频使用 `CC-BY-SA-4.0`；品牌内容采用单独边界。

迁移不会撤回历史 MIT 版本已授予的权利。`v0.1.0-alpha.1` 及只由迁移前提交产生的历史包继续按其随附 MIT 条款使用。

### 内容盘点

- 项目原创 Java 源码、测试、构建脚本、CI、翻译与文档纳入 LGPL 范围。
- 迁移时仓库只有 `assets/branding/echo-companion-logo-source.png` 与 `assets/branding/echo-companion-logo-512.png` 两个媒体文件，均为品牌 Logo，不归入默认 CC 范围；固定哈希与嵌入式 C2PA 来源证据见 `ASSET_LICENSES.md`。
- 迁移时没有发现可归入 CC 范围的非品牌纹理、模型、动画、插画、字体、音效或音乐。
- Gradle Wrapper 保留 Apache-2.0；外部构建、测试与运行依赖不打入发布 JAR，并记录在 `THIRD_PARTY_NOTICES.md`。
- 未发现从 Verity、ARR 或其他第三方项目复制进仓库的代码或媒体。

### 自动化门禁

迁移加入以下可重复检查：

1. 核对四份标准或历史许可证正文、两个基线 Logo 的固定 SHA-256，并检查源 Logo 的嵌入式生成来源标记。
2. 核对所有项目 Java 源码和项目自有脚本的 SPDX 标识。
3. 核对 Fabric、NeoForge 模板与构建后元数据均声明 `LGPL-3.0-or-later`。
4. 核对两个最终发布 JAR 各包含且仅包含一份项目 LGPL、CC、许可证政策和第三方声明，并与仓库文件逐字节一致。
5. 核对发布 JAR 的 Manifest 许可证标识，并确认没有旧的模糊 `META-INF/LICENSE-echo-companion` 路径。

### 验证结果

验证日期：2026-08-28；环境为 Windows 11、Oracle JDK `21.0.8`、Gradle Wrapper `8.14.1`。

- `verifyLicensing`：`LICENSE_VERIFY=PASS java_files=38 legal_files=4`。
- `clean test build verifyModArtifacts --rerun-tasks`：退出码 0，`BUILD SUCCESSFUL`，33 个任务全部实际执行。
- 单元测试：9 个测试套件、56 个测试，0 failure、0 error、0 skipped。
- Fabric 产物：`echo-companion-fabric-1.21.1-0.1.0.jar`，98,198 bytes，SHA-256 `C219EFAEE5BD396684D8357439868167831E052E0B51B41507176136BC8422FB`。
- NeoForge 产物：`echo-companion-neoforge-1.21.1-0.1.0.jar`，99,101 bytes，SHA-256 `1FE01BE59FB7EB13F4C058F73D7227CB43C941B77756F23BA7232C7641F995B4`。
- Gradle 内置审计：Fabric `ARTIFACT_LICENSE_VERIFY=PASS`（60 entries）；NeoForge `ARTIFACT_LICENSE_VERIFY=PASS`（61 entries）。
- 独立 ZIP/JAR 审计：两个最终 JAR 的四份法律文件数量、逐字节哈希、Manifest、loader metadata 和未嵌入依赖检查均通过。
- 标准正文来源：SPDX License List Data `v3.28.0`；LGPL、CC BY-SA 与 Apache 2.0 三个本地文件均与对应上游原始字节完全一致。MIT 文件逐字节保留迁移前仓库正文。
- 静态发布前检查：未发现高风险凭据格式、带凭据远端、5 MiB 以上候选文件或 Office/密钥/压缩归档候选。

`--warning-mode all` 的项目自有 Groovy 旧语法和执行期 `Task.project` 警告已清理。剩余 14 条 Gradle 问题均来自 Shadow Plugin 8.1.1 调用已弃用的 `FileTreeElement.getMode()`；构建仍成功，但升级 Gradle 9 前需要单独升级并回归 Shadow 插件。

当前本机没有 Bash 或可用 WSL 发行版，因此没有重新执行 `bash -n .github/scripts/publish-platforms.sh`。本轮对该脚本只增加 SPDX 与历史 MIT 边界注释，未修改 shell 语法结构；合并前仍标记 `NEEDS_CI_VALIDATION`，由 GitHub Actions 的既有 Bash 语法步骤验证。

`NEEDS_PLATFORM_METADATA_UPDATE`：按本轮“仅本地、不推送”的范围，没有修改 GitHub、GitLab、Modrinth 或 CurseForge 的远端说明与项目级许可证。首次发布迁移后版本前必须按 `docs/RELEASING.md` 完成平台回读。

`NEEDS_MANUAL_VALIDATION`：本轮是许可证与打包元数据变更，没有启动 Fabric/NeoForge 真人客户端，也没有重新验证设置界面、SCRIPTED/REMOTE 交互或远程 endpoint。自动化检查不能替代这些已有的发布前手工验收项。
