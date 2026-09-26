# Create: Production Line / 机械动力：产业线

**EN** — A Create addon for **Minecraft 1.21.1 / NeoForge**. Point it at any recipe (vanilla,
Create, another mod), and it writes **native Create recipe JSON** at runtime into a world datapack and
installs it immediately — no `/reload`, because only the recipes are refreshed and nothing else in the
world is re-read. The target item can then be produced by a real Create production line.

**中文** — 面向 **Minecraft 1.21.1 / NeoForge** 的 Create 附属模组。配方不管来自原版、Create
还是别的 mod，都能在游戏内实时转成 **Create 原生配方 JSON** 写进世界数据包并**即时生效**（不跑
`/reload`，只刷新配方），目标物品随后就能沿一条真实 Create 产线生产出来。

## 状态 / Status

| Item / 项 | Value / 值 |
| --- | --- |
| Mod ID / 包名 | `create_productionline` / `com.create.productionline` |
| Platform / 平台 | NeoForge (FML 1.x) / MC `[1.21.1]` / JDK 21 |
| Prerequisites / 前置 | Create `6.0.10+` (**required** 缺失拒载); JEI `19.x` (**optional** 仅配方查看，不调用其 API) |
| Version / 版本 | **`1.0.3` (release, current stable)** / **`1.0.2` (release, previous)** / **`1.0.1` (release, the first under this policy)**; the `1.0.4` work rides the **dev** line (`0.0.0-dev.N`) for now. It adds the **OP-only placeholder scheme**: when a target item has no usable recipe at all, an operator (permission level 2+) gets a scheme carrying just that item; when a recipe exists but cannot become a Create line, the operator is **asked first in chat** (two clickable options) before anything is written. Either way a placeholder installs nothing on its own, and players without permission see the old refusal. `1.0.3` collects the recipe-refresh work (`1.0.3-snapshot.0.0.1`), the three redrawn machines with the Scheme Loader's fill bar, the GUI layout pass, the dismantler's decision table, the compute reply in chat, the build-guide removal, the **Ponder tutorials for all three machines** and everything else under `## [1.0.3]` in the changelog — cut as a **release** on 2026-09-26 after the author walked the in-game acceptance list. Dev builds are **`0.0.0-dev.N`** (**beta**, built by `gradlew build -PdevBuild`, N auto-incremented in `dev-build.txt`, published up to `dev.27`) ／ 中文：**`1.0.4-snapshot.0.0.1`** 新增**仅 OP 可用的占位方案**：目标物品**一条配方都没有**时，权限等级 ≥2 的玩家会拿到一张只写着该物品的方案，可在铁砧上手工补全产线；普通玩家看到的拒绝与以前完全一致，占位方案本身**什么都装不上**。**`1.0.3` 是当前正式版**（含三台方块重绘与加载柜进度条、GUI 版式、破拆机决策表、计算机私聊、施工指引移除与三台机器的思索教程），`1.0.2` 为上一正式版（OP 铁砧自定义流程），`1.0.1` 为现行策略下第一个正式版；开发构建 `0.0.0-dev.N`（beta）已发到 `dev.27`。 |
| Artifact / 产物 | `build/libs/create_productionline-1.0.3.jar` (**this release**, 290,613 B, `sha256:0eaa6a0b…`); the one before it remains `build/libs/create_productionline-1.0.2.jar` (215,411 B, `sha256:27e0a3c6…`). CurseForge 页面已上线；Modrinth 项目仍在审核中，公开页面待通过后生效 / both are downloadable from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-production-line), [Modrinth](https://modrinth.com/project/createproductionline) (live once the project passes review) or the [Releases](https://github.com/Es-254/Create-ProductionLine/releases) page. 早期 1.0.0–1.0.3 那四个包**早于现行版本策略**：当时以 `release` 频道发布过，按现行策略记作开发快照 `0.0.0-dev.1…4`，现已被上面的正式版取代（旧编号里的 1.0.2 与这里的 1.0.2 不是同一个包）(the four old-numbering jars, 1.0.0–1.0.3, **predate the current version policy**: they were published with channel `release` at the time and are recorded as the dev snapshots `0.0.0-dev.1…4`. They are superseded by the releases above, and the old-numbering 1.0.2 is not the 1.0.2 named here). |
| Source size / 工程规模 | `src/main/java` **57 Java files** / **~9,200 lines** (8,377 non-blank). The line count is a snapshot that moves with every commit; the file count is the stable part ／ **57 个 Java 文件 / 约 9,200 行**（非空 8,377 行）。行数每次提交都会变，文件数才是稳定值 |

## 文档 / Docs

| File / 文件 | Topic / 主题 | What it covers / 内容 |
| --- | --- | --- |
| [`docs/usage.md`](docs/usage.md) | 使用 / Usage | Current implementation & usage: modules, quick start, data pack & refresh (`/cpl reload recipes`), the OP anvil flow, target output & repeat count ／ 当前实现与用法：模块详解、快速使用、数据包与刷新（含 `/cpl reload recipes`）、OP 铁砧自定义流程、目标产量与循环次数 |
| [`docs/conversion.md`](docs/conversion.md) | 配方转换 / Conversion | Recipe conversion rules plus the technical notes taken from Create's public sources ／ 配方转换规则 + 关键技术点（依据 Create 公开源码与行为分析） |
| [`docs/dependencies.md`](docs/dependencies.md) | 依赖 / Dependencies | Runtime prerequisites, how the dependencies are declared, development-time versions, build network & proxy ／ 运行前置、依赖声明方式、开发期依赖版本、构建网络与代理 |
| [`docs/compatibility.md`](docs/compatibility.md) | 兼容性与边界 / Compatibility & limits | Loader-declaration friendliness, known limits, the trust model and the beta boundaries ／ loader 声明友好性、已知边界、信任模型与 beta 边界 |
| [`docs/security.md`](docs/security.md) | 安全 / Security | The multiplayer anti-injection work (landed 2026-09-07), with `file:line` evidence ／ 多人服防注入（2026-09-07 落地，含 `file:line` 依据） |
| [`docs/build.md`](docs/build.md) | 构建与安装 / Build & install | Building, installing, the version policy and the source layout ／ 构建、安装、版本规范、源码布局 |
| [`docs/qa.md`](docs/qa.md) | 无头自检 / QA | How to run the self test, the 24-check snapshot and the doc↔code consistency baseline ／ 自检运行方式、24 项快照、文档—代码一致性核对基线 |
| [`docs/platform-listing.md`](docs/platform-listing.md) | 平台文案 / Platform listing | The project body text uploaded to Modrinth / CurseForge ／ 上传到 Modrinth / CurseForge 的项目描述文案 |
| [`CHANGELOG.md`](CHANGELOG.md) | 更新日志 / Release history | What changed in every version ／ 每个版本的变更记录 |
| [`docs/version-diff-1.0.1-to-now.md`](docs/version-diff-1.0.1-to-now.md) | 版本对比 / Version diff | Every local jar against the last stable release `1.0.1`: which commit/tag each one is, what changed and what is still unreleased ／ 与上一个正式版 `1.0.1` 的逐包对比：每个本地 jar 对应的提交/tag、改了什么、还有什么没发布 |
| [`RELEASING.md`](RELEASING.md) | 发布流程 / Releasing | How a release is cut: version policy, tags and CI, the three-platform paths ／ 版本规范、tag 与 CI、三平台发布路径 |
| [`CONTRIBUTORS.md`](CONTRIBUTORS.md) | 贡献与资助名单 / Contributors | The full funding-support and technical-support lists ／ 资金支持与技术支持完整名单 |
| [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) | 第三方清单 / Third-party inventory | Third-party code and licences ／ 第三方代码与许可清单 |

> Icon / 图标: `create_productionline.ico` (16–256), platform icon `icon_512x512.png`

> **Language contract / 语言契约** — the reader-facing documents are bilingual: `README.md` and every
> `docs/*.md` page pair an English block with its Chinese counterpart (tables carry both in the cells).
> `CONTRIBUTORS.md` follows the same rule for its prose. `CHANGELOG.md`, `RELEASING.md` and
> `THIRD_PARTY_NOTICES.md` are **maintainer / release / legal records and English-only** — a few notes
> carry Chinese, most headings do not, and `THIRD_PARTY_NOTICES.md` has no Chinese at all. 读者文档
> （`README.md` 与 `docs/*.md`）为**双语**：每段英文都有对应中文（表格在单元格内并列），`CONTRIBUTORS.md`
> 的正文同样双语；而 `CHANGELOG.md`、`RELEASING.md`、`THIRD_PARTY_NOTICES.md` 属**维护/发布/法律记录，
> 仅英文**——只有少数说明带中文，标题大多为英文，`THIRD_PARTY_NOTICES.md` 则完全没有中文。

## 模块一览 / Modules at a glance

| Module / 模块 | Role / 作用 |
| --- | --- |
| Production Computer / 产线计算机 | computes a plan onto a carrier ／ 把方案算到载体上 |
| Line Scheme / 产线方案 | carries the plan; activatable, dismantlable ／ 携带方案，可激活、可拆解 |
| Scheme Loader / 方案加载柜 | activates up to 16 schemes as a union ／ 最多 16 格并集激活 |
| Dismantler / 破拆机 | refunds a product or an intermediate ／ 拆解成品/中间产物并退料 |
| Generic Intermediate / 通用中间产物 | sequenced-assembly transitional item ／ 序列装配过渡物 |
| Line Scheme Mirror / 产线方案镜像 | read-only display snapshot ／ 只读展示快照 |

> 完整模块说明见 [`docs/usage.md`](docs/usage.md) / full module details: [`docs/usage.md`](docs/usage.md).

## 安装 / Install

```powershell
gradlew build      # -> build/libs/create_productionline-<version>.jar
```

把该 jar 放进实例的 `mods/` 目录即可 / Drop that jar into your instance's `mods/` folder.
构建细节 / build details: [`docs/build.md`](docs/build.md) · 依赖与代理 / dependencies & proxy: [`docs/dependencies.md`](docs/dependencies.md)

## 许可与贡献 / License & contributions

**MIT.** 资金支持者（主要名单）、技术支持与代码/美术/测试贡献者的完整名单见
[`CONTRIBUTORS.md`](CONTRIBUTORS.md)；第三方代码与许可清单见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
Funding supporters (the main list), technical support and code/art/testing contributors are in
[`CONTRIBUTORS.md`](CONTRIBUTORS.md); third-party inventory is in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
