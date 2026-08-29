<!-- SPDX-License-Identifier: GPL-3.0-only -->
<!-- Asset-Default-License: LicenseRef-EeryFrank-Assets-Permission-Required -->
<!-- Historical-Grants: MIT-and-LGPL-not-revoked -->

# Echo Companion License Policy / Echo Companion 许可证政策

This policy defines the license boundary for the current repository. A more specific file or asset-inventory entry takes precedence. It does not relicense third-party material or revoke any license already granted for an earlier revision.

本文规定当前仓库的许可证边界。更具体的文件级声明或资产清单条目优先。本政策不会重新授权第三方内容，也不会撤回任何早期修订已经授予的许可。

## 1. Current project-authored source and functional material / 当前项目原创源码与功能材料

Beginning with the commit that introduces this policy, project-authored source code, tests, build and CI configuration, functional data, translations, documentation, and other software-oriented material are licensed under `GPL-3.0-only`, unless a file says otherwise. The unmodified official GPLv3 text is in [LICENSE](LICENSE).

自引入本政策的提交起，除非文件另有声明，项目原创源码、测试、构建与 CI 配置、功能性数据、翻译、文档及其他软件功能材料均按 `GPL-3.0-only` 授权。未经修改的官方 GPLv3 正文见 [LICENSE](LICENSE)。

## 2. Historical MIT and LGPL grants / 历史 MIT 与 LGPL 授权

Commit `569b02de7c2dfc6e3ea583855485c07098541f72` and earlier revisions, including `v0.1.0-alpha.1`, were distributed under MIT. The exact text remains at [LICENSES/MIT.txt](LICENSES/MIT.txt).

Revisions distributed after the MIT-to-LGPL transition and through the public baseline `d713ba3144270d3daa8932c7a25c934251b89312` were offered under `LGPL-3.0-or-later`. The preserved text is at [LICENSES/LGPL-3.0-or-later.txt](LICENSES/LGPL-3.0-or-later.txt).

Those grants remain valid for the copies and material to which they applied. This GPL transition is prospective: it does not withdraw MIT or LGPL permissions already received, rewrite tags or releases, or relicense third-party material.

提交 `569b02de7c2dfc6e3ea583855485c07098541f72` 及更早修订（包括 `v0.1.0-alpha.1`）曾按 MIT 发布，原文保存在 [LICENSES/MIT.txt](LICENSES/MIT.txt)。

从 MIT→LGPL 迁移后至公开基线 `d713ba3144270d3daa8932c7a25c934251b89312` 的修订曾按 `LGPL-3.0-or-later` 提供，原文保存在 [LICENSES/LGPL-3.0-or-later.txt](LICENSES/LGPL-3.0-or-later.txt)。

这些授权对其原本适用的副本和材料继续有效。本次 GPL 迁移只面向当前及未来修订，不撤回既有 MIT/LGPL 权利，不重写标签或 Release，也不重新授权第三方材料。

## 3. Existing branding assets / 既有品牌资产

The two PNG files under `assets/branding/**` that are listed in [ASSET_LICENSES.md](ASSET_LICENSES.md) retain their historical MIT copyright grants. Their fixed hashes, C2PA provenance record, and trademark/false-endorsement boundary remain authoritative. They are not relicensed under GPL or the new asset LicenseRef.

[ASSET_LICENSES.md](ASSET_LICENSES.md) 所列的两张 `assets/branding/**` PNG 继续保留历史 MIT 著作权授权；固定哈希、C2PA 来源记录和商标/虚假背书边界继续有效。它们不会改为 GPL，也不会改用新的资产 LicenseRef。

## 4. New visual, audio, and branding assets / 新增视觉、音频与品牌资产

No path automatically licenses an asset. A new project-owned texture, model, animation, illustration, font, sound, music, logo, icon, or branding asset must first pass a provenance and rights review and be explicitly recorded in `ASSET_LICENSES.md`.

Unless that inventory grants another expressly authorized license, the asset uses `LicenseRef-EeryFrank-Assets-Permission-Required`, whose complete terms are in [LICENSES/LicenseRef-EeryFrank-Assets-Permission-Required.txt](LICENSES/LicenseRef-EeryFrank-Assets-Permission-Required.txt). It permits the unmodified asset to travel only inside an unmodified official release package. Standalone extraction, reuse, modification, redistribution, commercial use, or branding use requires prior written permission from EeryFrank.

任何目录都不会自动为资产授予许可。新增的项目自有纹理、模型、动画、插画、字体、声音、音乐、Logo、图标或品牌资产，必须先完成来源与权利核验，并在 `ASSET_LICENSES.md` 中明确登记。

除非资产清单另有经授权的明确许可，该资产默认使用 `LicenseRef-EeryFrank-Assets-Permission-Required`，完整条款见 [LICENSES/LicenseRef-EeryFrank-Assets-Permission-Required.txt](LICENSES/LicenseRef-EeryFrank-Assets-Permission-Required.txt)。它仅允许未修改资产随未经修改的官方发布包完整分发；单独提取、复用、修改、再分发、商业使用或品牌使用均须事先取得 EeryFrank 的书面授权。

The retained [CC-BY-SA-4.0 text](LICENSES/CC-BY-SA-4.0.txt) documents the previous policy. No current Echo Companion asset is assigned to that scope, and it is not a default for new assets.

保留的 [CC-BY-SA-4.0 正文](LICENSES/CC-BY-SA-4.0.txt) 用于记录上一版政策；当前没有 Echo Companion 资产处于该范围，它也不再是新增资产的默认许可。

## 5. Third-party material and trademarks / 第三方材料与商标

Third-party code, generated third-party files, game content, services, dependencies, names, and assets retain their original terms and are not relicensed here. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Minecraft and official mappings remain subject to Mojang/Microsoft terms. The Gradle Wrapper remains Apache-2.0.

No code or asset license grants trademark rights or permission to imply endorsement. Accurate nominative references remain allowed.

第三方代码、第三方生成文件、游戏内容、服务、依赖、名称和资产保留原始条款，不受本政策重新授权。详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。Minecraft 与官方映射继续受 Mojang/Microsoft 条款约束；Gradle Wrapper 继续采用 Apache-2.0。

任何代码或资产许可均不授予商标权，也不允许暗示背书；准确、必要的指称性引用不受影响。

## 6. Contributions / 贡献

A contributor must own or be authorized to license the submitted material. Accepted source and functional material is contributed under `GPL-3.0-only`. Visual, audio, or branding material is accepted only after an explicit provenance, rights, and written-authorization review; it receives no automatic open-content license.

贡献者必须拥有提交内容，或已获权按相应条款授权。接受的源码与功能材料按 `GPL-3.0-only` 提交。视觉、音频或品牌材料只有在完成明确的来源、权利及书面授权审查后才会接受，不会自动获得开放内容许可。
