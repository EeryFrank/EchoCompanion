# Echo Companion License Policy / Echo Companion 许可证政策

This policy explains which license applies to each part of the repository. If this summary conflicts with an applicable license text, the license text controls.

本文说明仓库中不同内容分别适用哪一种许可证。若本文摘要与适用许可证正文冲突，以许可证正文为准。

## 1. Project-authored code and functional material / 项目原创代码与功能性材料

Unless a file states otherwise, project-authored source code, tests, build and CI configuration, functional data, translations, and documentation in revisions after the license transition are licensed under `LGPL-3.0-or-later`. The complete license text is in [LICENSE](LICENSE).

除非文件另有明确声明，许可证迁移后的项目原创源码、测试、构建与 CI 配置、功能性数据、翻译和文档均以 `LGPL-3.0-or-later` 授权。完整许可证正文见 [LICENSE](LICENSE)。

## 2. Original non-brand media / 原创非品牌美术与音频

Original textures, models, animations, illustrations, fonts, sound effects, and music added outside the branding scope are licensed under `CC-BY-SA-4.0`, unless a file or adjacent notice says otherwise. The complete license text is in [LICENSES/CC-BY-SA-4.0.txt](LICENSES/CC-BY-SA-4.0.txt).

在品牌范围之外新增的原创纹理、模型、动画、插画、字体、音效和音乐，除非文件或相邻声明另有说明，均以 `CC-BY-SA-4.0` 授权。完整许可证正文见 [LICENSES/CC-BY-SA-4.0.txt](LICENSES/CC-BY-SA-4.0.txt)。

Suggested attribution: `Echo Companion contributors, CC BY-SA 4.0, source: https://github.com/EeryFrank/EchoCompanion`.

建议署名：`Echo Companion contributors, CC BY-SA 4.0, source: https://github.com/EeryFrank/EchoCompanion`。

At the transition baseline, the repository contains no non-brand media assigned to this CC scope. The CC license text is included so future qualifying media has an explicit, stable default from its first commit.

在迁移基线中，仓库尚无归入该 CC 范围的非品牌媒体。仓库预先收录 CC 许可证正文，使未来符合条件的媒体从首次提交起就有明确、稳定的默认授权。

## 3. Branding, logo, and project identity / 品牌、Logo 与项目身份

`assets/branding/**`, any Echo Companion logo or icon, and other files explicitly identified as branding are not automatically licensed under `CC-BY-SA-4.0`. No code or media license grants trademark rights, permission to imply endorsement, or permission to present a fork as the official Echo Companion project. Accurate nominative references to the project remain allowed.

`assets/branding/**`、任何 Echo Companion Logo 或图标，以及被明确标记为品牌内容的文件，不会自动采用 `CC-BY-SA-4.0`。任何代码或媒体许可证都不授予商标权，不允许暗示获得官方认可，也不允许把分支项目冒充为 Echo Companion 官方项目；准确、必要的指称性引用不受影响。

For branding first added or materially changed after the transition, permission is granted to redistribute the branding unmodified only as part of an unmodified official Echo Companion source archive, release package, or exact mirror. Standalone reuse, modified branding, or use as a fork's identity requires separate permission. This limited permission ensures official packages can be redistributed intact without granting broader branding rights.

对于迁移后首次加入或发生实质修改的品牌内容，仅允许在未经修改的 Echo Companion 官方源码归档、发布包或精确镜像中原样再分发。独立复用、修改品牌内容或将其作为分支项目身份，需另行取得许可。该有限许可用于保证官方包可以完整再分发，并不扩大品牌授权。

The two logo PNG files present at the transition baseline were previously distributed under MIT. Rights already granted for those historical copies remain in force, including copyright permissions granted by MIT; trademark and false-endorsement rules remain separate. Their fixed hashes and available generation provenance are recorded in [ASSET_LICENSES.md](ASSET_LICENSES.md).

迁移基线中已有的两个 Logo PNG 曾随 MIT 版本发布。对这些历史副本已经授予的 MIT 权利继续有效，包括 MIT 已授予的著作权许可；商标与虚假认可问题仍与著作权许可相互独立。其固定哈希与现有生成来源证据记录在 [ASSET_LICENSES.md](ASSET_LICENSES.md) 中。

## 4. Historical MIT releases / 历史 MIT 版本

Commit `569b02de7c2dfc6e3ea583855485c07098541f72` and earlier Echo Companion revisions were published with the repository's historical MIT License, preserved locally at [LICENSES/MIT.txt](LICENSES/MIT.txt). Existing tags, releases, source archives, and binaries derived solely from those revisions remain available under the MIT terms shipped with them. Those grants are not revoked.

提交 `569b02de7c2dfc6e3ea583855485c07098541f72` 以及更早的 Echo Companion 修订曾以仓库当时的历史 MIT License 发布，正文已在 [LICENSES/MIT.txt](LICENSES/MIT.txt) 中保留。仅由这些修订构建的既有标签、发布、源码归档和二进制包，继续适用其随附的 MIT 条款；已经授予的权利不会撤回。

The first public alpha, `v0.1.0-alpha.1`, is therefore a legacy MIT release. The license transition applies to new changes in the first migration commit and later revisions; it does not rewrite history or relicense third-party material.

首个公开测试版 `v0.1.0-alpha.1` 因此属于历史 MIT 版本。许可证迁移适用于首个迁移提交及其后修订中的新增改动，不会重写历史，也不会重新授权第三方内容。

## 5. Third-party material / 第三方内容

Third-party material keeps its own copyright and license. It is not relicensed by this policy. Sources, versions, distribution status, and license references are recorded in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Minecraft, loader, API, service, and third-party project names remain the property of their respective owners.

第三方内容保留其自身著作权与许可证，不会被本文重新授权。来源、版本、是否随包分发及许可证链接记录在 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。Minecraft、加载器、API、服务及第三方项目名称仍归各自权利人所有。

## 6. Contributions / 贡献

By contributing, a contributor confirms that they have the necessary rights and agrees that accepted material is licensed according to its repository scope: `LGPL-3.0-or-later` for code and functional material, and `CC-BY-SA-4.0` for original non-brand media. Branding contributions require an explicit, separate agreement before acceptance.

提交贡献即表示贡献者确认自己拥有必要权利，并同意已接受内容按仓库范围授权：代码与功能性材料采用 `LGPL-3.0-or-later`，原创非品牌媒体采用 `CC-BY-SA-4.0`。品牌贡献必须在接受前另行明确约定。
