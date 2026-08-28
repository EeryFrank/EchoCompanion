# Third-party notices / 第三方声明

Echo Companion does not relicense third-party software, game content, services, names, or marks. This inventory distinguishes material redistributed in the repository from dependencies obtained separately for building, testing, or running the mod.

Echo Companion 不会重新授权第三方软件、游戏内容、服务、名称或标识。下列清单区分仓库实际再分发的内容，以及构建、测试或运行时另行取得的依赖。

## Redistributed with the source repository / 随源码仓库再分发

| Component | Version or scope | License | Included paths | Upstream |
| --- | --- | --- | --- | --- |
| Gradle Wrapper / Gradle | 8.14.1 | Apache-2.0 | `gradlew`, `gradlew.bat`, `gradle/wrapper/**` | <https://github.com/gradle/gradle> |

The Apache-2.0 text used for the redistributed Gradle Wrapper is stored at [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt). The generated wrapper files retain their upstream headers and are excluded from the project's LGPL source-header requirement.

随仓库再分发的 Gradle Wrapper 所适用的 Apache-2.0 正文见 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)。生成的 Wrapper 文件保留上游文件头，不纳入项目 LGPL 源码文件头检查。

The MIT text at [LICENSES/MIT.txt](LICENSES/MIT.txt) preserves Echo Companion's own historical grants and the terms previously applied to the two baseline logo files. It is not a third-party dependency license.

[LICENSES/MIT.txt](LICENSES/MIT.txt) 中的 MIT 正文用于保留 Echo Companion 自身的历史授权，以及迁移基线中两个 Logo 文件此前适用的条款；它不是第三方依赖许可证。

## Build, test, and runtime dependencies not bundled in release JARs / 不打入发布 JAR 的构建、测试与运行依赖

| Component | Pinned version | License or terms | Upstream |
| --- | --- | --- | --- |
| Minecraft Java Edition and official mappings | 1.21.1 | Mojang/Microsoft terms; not open-sourced by this project | <https://www.minecraft.net/usage-guidelines> |
| Gson | 2.10.1 (resolved compile classpath) | Apache-2.0 | <https://github.com/google/gson> |
| Fabric Loader | 0.16.14 | Apache-2.0 | <https://github.com/FabricMC/fabric-loader> |
| NeoForge | 21.1.216 | LGPL-2.1 | <https://github.com/neoforged/NeoForge> |
| Architectury Loom | 1.11.456 | MIT | <https://github.com/architectury/architectury-loom> |
| Architectury Plugin | 3.4-20260412.215235-1 | MIT | <https://github.com/architectury/architectury-plugin> |
| Shadow Gradle Plugin | 8.1.1 | Apache-2.0 | <https://github.com/GradleUp/shadow> |
| JUnit | 5.11.4 | EPL-2.0 | <https://github.com/junit-team/junit5> |

The release build shades only Echo Companion's `common` project output through `shadowBundle`. Minecraft, Gson supplied by the game environment, loaders, build plugins, and test libraries are not embedded in the Fabric or NeoForge release JAR. The artifact licensing verifier checks the exact packaged legal files and loader metadata.

发布构建仅通过 `shadowBundle` 打入 Echo Companion 自有的 `common` 项目输出。Minecraft、游戏环境提供的 Gson、加载器、构建插件和测试库均不会嵌入 Fabric 或 NeoForge 发布 JAR。产物许可证检查会核对随包法律文件与加载器元数据。

## CI actions and external publishing services / CI Action 与外部发布服务

The workflows invoke `actions/checkout`, `actions/setup-java`, `actions/upload-artifact`, and `gradle/actions`. These actions execute on GitHub-hosted infrastructure and are not copied into the mod JAR. The three `actions/*` repositories are MIT-licensed at the pinned major versions. `gradle/actions` contains components under multiple upstream terms; its current `LICENSE`, `NOTICE`, and `DISTRIBUTION.md` control. Modrinth, CurseForge, GitHub, configured AI endpoints, and their APIs remain external services governed by their own terms.

工作流会调用 `actions/checkout`、`actions/setup-java`、`actions/upload-artifact` 与 `gradle/actions`。这些 Action 在 GitHub 托管环境执行，不会复制进模组 JAR。三个 `actions/*` 仓库在当前固定主版本下采用 MIT；`gradle/actions` 含有适用不同上游条款的组件，应以其当前 `LICENSE`、`NOTICE` 与 `DISTRIBUTION.md` 为准。Modrinth、CurseForge、GitHub、玩家配置的 AI endpoint 及其 API 均为外部服务，适用各自条款。

## Independent implementation boundary / 独立实现边界

Echo Companion's gameplay is inspired by Verity, but Verity and ARR are not code, data, text, model, texture, sound, UI, name, or branding sources for this repository. No Verity or ARR material was identified in the transition inventory. Inspiration does not grant permission to copy third-party material.

Echo Companion 的玩法受 Verity 启发，但 Verity 与 ARR 均不是本仓库代码、数据、文本、模型、纹理、声音、UI、名称或品牌内容的来源。迁移盘点未发现 Verity 或 ARR 内容；玩法灵感不等于取得复制第三方内容的许可。
