# Create: Production Line / 机械动力：产业线

**EN** — A Create addon for **Minecraft 1.21.1 / NeoForge** that converts recipes from any source
(vanilla, Create, other mods) into **native Create recipe JSON** at runtime, installs them into a
world datapack and reloads it, so the target item can be produced by a real Create production line.

**中文** — 面向 **Minecraft 1.21.1 / NeoForge** 的 Create 附属模组：把"任意来源配方"（原版 / Create /
其它 mod）在游戏内实时转成 **Create 原生配方 JSON**，写入世界数据包并 `/reload`，
让目标物品能沿一条真实 Create 产线被生产出来。

| Item / 项 | Value / 值 |
| --- | --- |
| Mod ID / 包名 | `create_productionline` / `com.create.productionline` |
| Platform / 平台 | NeoForge (FML 1.x) / MC `[1.21.1]` / JDK 21 |
| Prerequisites / 前置 | Create `6.0.10+` (**required** 缺失拒载); JEI `19.x` (**optional** 仅配方查看，不调用其 API) |
| Version / 版本 | **`1.0.1` (release)** — the first official release; supersedes every dev snapshot. Dev builds are **`0.0.0-dev.N`** (**beta**, `gradlew build -PdevBuild`, N auto-incremented in `dev-build.txt`) — 首个正式发布；开发构建为 `0.0.0-dev.N`（beta），由 `-PdevBuild` 产出并按 `dev-build.txt` 递增。 |
| Artifact / 产物 | `build/libs/create_productionline-1.0.1.jar` — also downloadable from [Modrinth](https://modrinth.com/project/createproductionline) / the [Releases](https://github.com/Es-254/Create-ProductionLine/releases) page (Modrinth 项目仍在审核中，公开页面待通过后生效 / the Modrinth page goes live once the project passes review). 早期 1.0.0–1.0.3 构建包均为开发快照，已被 1.0.1 取代 (earlier 1.0.0–1.0.3 jars were dev snapshots and are superseded). |
| Source size / 工程规模 | `src/main/java` **46 Java files** / **~6,500 lines** (line count is a snapshot — it moves with every commit; the file count is the stable part) |
| Docs / 文档 | This file (**current implementation & usage** 当前实现与用法); `CHANGELOG.md` (**release history** 更新日志); `RELEASING.md` (**how a release is cut** 发布流程); `CONTRIBUTORS.md` (**contributors & funding** 贡献与资助名单); `THIRD_PARTY_NOTICES.md` (**third-party inventory** 第三方清单). Icon / 图标: `create_productionline.ico` (16–256), platform icon `icon_512x512.png` |

---

## Modules / 模块

| Module / 模块 | Description (EN) | 说明（中文） |
| --- | --- | --- |
| Production Computer / 产线计算机 | **3 slots side by side** (`SLOT_TARGET=0` target, `SLOT_SCHEME=1` carrier, `SLOT_CLIPBOARD=2` clipboard). On **Compute** the client scans resource packs and sends parsed recipe JSON; the server re-derives from the live `RecipeManager`, writes a **single-layer direct plan + embedded native Create recipe JSON** onto the carrier, and injects the `custom_data.LineBuildGuide` build guide. | **3 格并排**（`SLOT_TARGET=0` 目标物品 / `SLOT_SCHEME=1` 载体 / `SLOT_CLIPBOARD=2` 剪贴板）。点【计算】时客户端先扫资源包解析配方 JSON 送服务端，服务端以实时 `RecipeManager` 重新推导 → 生成**单层直连方案 + 内嵌 Create 原生配方 JSON** → 写入载体物品并注入 `custom_data.LineBuildGuide` 施工指引。 |
| Line Scheme / 产线方案 | Carries `LineScheme` NBT (`Version/RecipeId/OutputItem/BaseMaterial/Steps[]` + embedded `CreateRecipes`); activatable by the loader, readable by the dismantler. | 携带 `LineScheme` NBT（`Version/RecipeId/OutputItem/BaseMaterial/Steps[]` + 内嵌 `CreateRecipes`），可被加载柜激活、被破拆机读取。 |
| Scheme Loader / 方案加载柜 | **16 slots (2×8)**, **accepts only genuine `LineSchemeItem`** (paper/clipboard/mirror/forged NBT rejected). On insert it re-derives this cabinet's contribution server-side from each scheme's `recipeId`, writes the `cpl_converted` union and runs `/reload`; **union across cabinets/slots**, never overwrites; emits redstone while recipes are active. | **16 格（2×8）**，**只收真 `LineSchemeItem`**（纸/剪贴板/镜像/伪造 NBT 一律拒收）。放入即按方案 `recipeId` 服务端**重推导**本柜贡献 → 写入 `cpl_converted` 并集并 `/reload`；**多柜/多格并集**，互不覆盖；有生效配方时输出红石信号。 |
| Dismantler / 破拆机 | 2 slots (item / optional scheme). An **unfinished intermediate** (Generic Intermediate + Create's `SEQUENCED_ASSEMBLY` component) is refunded by re-reading the sequence recipe it names: base + exactly the materials the finished steps consumed, plus a read-only mirror. A **finished product** refunds one full inverse batch (consume `count`, refund inputs), resolved server-side. Tags refund their first registered member; nothing is consumed when nothing can be refunded. | 2 格（物品 / 方案可选）。**未完成的中间产物**（通用中间产物 + Create `SEQUENCED_ASSEMBLY` 组件）会按它记录的序列配方**退还基底 + 已完成步骤真正吃掉的原料**，并给一份只读镜像；**成品**按"一次完整产出的逆运算"退款（消耗 count 个、退还输入），配方由服务端解析。tag 退回首个成员；退不出任何东西时不消耗物品。 |
| Generic Intermediate / 通用中间产物 | Sequenced-assembly transitional item (`extends SequencedAssemblyItem`, carries progress component). | 序列装配过渡物（`extends SequencedAssemblyItem`，带进度组件）。 |
| Line Scheme Mirror / 产线方案镜像 | Read-only display snapshot (`LineSchemeMirror` key); structurally contains no executable scheme — **cannot be activated or fed back to the dismantler**. | 只读展示快照（`LineSchemeMirror` 键），结构上不含可执行方案，**不可激活、不可复喂破拆机**。 |

## Quick start / 快速使用

**EN**

1. **Compute** — the computer GUI has **3 slots side by side**: `target item / carrier / clipboard` (left→right). Put the target in slot 1; put paper, a clipboard or a blank Line Scheme in slots 2–3 (if both are filled, both receive the plan). Press **Compute**. The item tooltip then shows `Output: …`, `Recipe: …`, `Base: … (goes on the line first)`, `1. [material] -> Feed`, `2. [material] -> Deployer`… plus `Embedded Create recipes: N`.
2. **Activate** — put the written **Line Scheme** into any slot of a **Scheme Loader** (multi-slot/multi-cabinet). The server re-derives the recipes from `recipeId`, writes the datapack and reloads automatically. Only genuine Line Scheme items activate: paper/clipboard/mirror/forged NBT never do.
3. **Build** (assembly = sequenced assembly) — feed the base first (arm/funnel/chute/drop-in all fine); **one Deployer per extra material** (USE mode, facing DOWN above the belt, holding that material); the product rolls out at the end. A "Generic Intermediate" at the end means the sequence is unfinished / a material is missing.
4. **Dismantle / mirror** — the dismantler refunds materials and produces a read-only mirror.

**中文**

1. **计算**：计算机 GUI 的 3 个槽位**并排**，自左至右是`目标物品 / 载体 / 剪贴板`。目标物品放第 1 格；第 2、3 格放纸、剪贴板或空白产线方案（两者都放则都写入）。全部放好后点【计算】。完成后物品 tooltip 显示 `目标产物：…`、`来源配方：…`、`基底：…（最先上线…）`、`1. [原料] -> 投料`、`2. [原料] -> 机械手`… 以及 `内嵌 Create 配方：N 条`。
2. **激活**：把写好的**产线方案**放入**方案加载柜**任意格（可多格/多柜）→ 服务端按 `recipeId` 重推导配方写入数据包并自动 `/reload`。加载柜只认真正的产线方案物品：纸/剪贴板/镜像/伪造 NBT 都不会激活。
3. **搭建**（装配类=序列装配）：基底先上带（动力臂/漏斗/溜槽/直接放均可）；**每种追加原料一台机械手**（USE 模式、朝下置于传送带上方、手持对应原料）；跑完 roll 出成品；末端出现"通用中间产物"=序列未完/缺料。
4. **拆解/镜像**：破拆机还原原料并生成只读镜像。

## Recipe conversion rules / 配方转换规则

**EN**

- **Single-machine processes** → flat `create:<type>` recipes (crushing/milling/mixing/pressing/cutting…, via the category→facility dictionary).
- **Single-material recipes** (any category — sticks, planks, buttons, smelting …) → a real single-machine Create recipe chosen from the material's semantics (`RecipeAnalyzer.machineForMaterial`: wood-ish → saw/`create:cutting`, ore/`raw_*` → crushing wheel, organic → millstone, metal/gem → press, fallback press). Only targets already produced by a native Create process, or recipes with no usable materials, are refused.
- **Multi-material recipes without a flat dictionary method** (unknown categories, smithing, …) → `create:mixing` (one mixer for all materials).
- **Crafting / assembly** (`minecraft:crafting` etc.) → `create:sequenced_assembly`: `ingredient` = base, `transitional_item` = Generic Intermediate, one `create:deploying` step per extra material, `loops=1`, output count preserved.
- **Native Create recipes** (`create:mechanical_crafting`, e.g. CBC shells) → **additionally** generate a non-conflicting `cpl` sequence recipe (the original stays), decided by `isConvertibleAssembly`.
- **Tag fidelity** — source tags are written verbatim as `{"tag":…}` so any tag member matches; tooltips show the first member's localized name (display only).
- **Single ordering source** — plan and embedded recipe share ONE "JSON-ordered, tag-preserving" material list, eliminating base/step mismatches (root cause of the diesel-engine bug).
- **The plan is the mirror of the derived recipe** — step layout is generated FROM the derived Create recipe JSON's `type` (`MachineSelector.appendChainSteps`): sequenced assembly → one Deployer per extra material; flat/mechanical → one station for the machine that really executes the recipe. A step's machine is always the machine that really processes that material — no index-based guessing.
- Multi-candidate selection: copy/repair/dye recipes (only ingredient == product) are skipped, our own
  `cpl:…` conversions are never re-converted, and the first candidate that actually derives an
  installable entry wins.
- Single-layer direct plans only, no upstream recursion; self-referencing materials (input == output) appear as ordinary stations (the recipe still consumes them).
- Assembly mode: `config/create_productionline-mappings.json` → `"assemblyMode": "sequenced"|"mechanical"` (default `sequenced`).
- **24 built-in mappings**: vanilla 6 (crafting/smelting/smoking/blasting/campfire_cooking/stonecutting) + Create 18 (cutting/pressing/milling/crushing/mixing/compacting/deploying/item_application/sandpaper_polishing/mechanical_crafting/haunting/splashing/washing/fan_washing/fan_splashing/fan_haunting/fan_smoking/fan_blasting); overridable/extendable via the same JSON's `categories`, with tail-key fallback in `lookup()`.
- **Single derivation entry point** `recipegen/RecipeDeriver`: `derive()` returns "input order + output count + installable entries" in one shot, shared by the computer and the loader; the embedded JSON is only a cache — the server always trusts `recipeId` + the live `RecipeManager`.

**中文**

- **单机工艺类** → 扁平 `create:<type>` 配方（粉碎/研磨/混合/压片/切割等，按映射字典 category→facility）。
- **单一材料配方**（任何类别——木棍、木板、按钮、熔炼等）→ 按材料语义选一台真实 Create 机器（`RecipeAnalyzer.machineForMaterial`：木类→锯/`create:cutting`、矿/`raw_*`→粉碎轮、有机物→石磨、金属/宝石→压片机，兜底压片机）。仅"目标已由 Create 原生工艺产出"或"配方没有可用原料"才拒绝。
- **无词典扁平方法的多种材料配方**（未知类别、锻造等）→ `create:mixing`（一台搅拌机处理全部材料）。
- **合成/装配类**（`minecraft:crafting` 等）→ `create:sequenced_assembly`：`ingredient`=基底，`transitional_item`=通用中间产物，每个追加原料一个 `create:deploying` 步，`loops=1`，产物 count 保留。
- **原生 Create 配方**（`create:mechanical_crafting`，如 CBC 炮弹）→ **额外**生成一份不冲突的 cpl 序列配方（原配方保留），`isConvertibleAssembly` 统一判定。
- **标签保真**：源配方 tag 原样写成 `{"tag":…}`，任何同标签成员都能匹配；tooltip 里按标签首个成员显示中文名（仅展示层）。
- **单一顺序来源**：方案与内嵌配方共用同一份"JSON 保序、保标签"材料列表，杜绝基底/步骤错位（柴油引擎问题根因）。
- **计划 = 推导配方的镜像**：站点布局由推导出的 Create 配方 JSON 的 `type` 生成（`MachineSelector.appendChainSteps`）——序列装配=每种追加原料一台机械手；扁平/机械合成=执行该配方的机器一个工位。每一步的机器就是真实处理该材料的机器，不再有按下标猜测的配对。
- 多配方候选选择：只含"产物自身"的复制/修复/染色类配方被跳过；本模组自己装出的 `cpl:…` 转换配方不会被再次转换；优先选"能真正推导出可安装条目"的候选。
- 单层直连、无上游递归；自引用材料（原料==目标）作为普通工位保留（配方确实会消耗它）。
- 装配模式：`config/create_productionline-mappings.json` → `"assemblyMode": "sequenced"|"mechanical"`（默认 sequenced）。
- **内建映射 24 条**（原版 6 + Create 18，同上），可用同一 JSON 的 `categories` 覆盖或扩展，`lookup()` 另有"尾键回退"匹配。
- **唯一推导入口** `recipegen/RecipeDeriver`：`derive()` 一次性给出「输入顺序 + 产物 count + 可安装条目」，产线计算机与加载柜共用；方案内嵌 JSON 只是缓存，服务端始终以 `recipeId` + 实时 `RecipeManager` 为准。

## Technical notes (from Create's public sources & observed behaviour) / 关键技术点（依据 Create 公开源码与行为分析）

**EN**

- Sequenced assembly is machine-driven: whether the item carries the `SEQUENCED_ASSEMBLY` DataComponent decides matching; each step runs on the machine matching its recipe type (deploy → Deployer etc.); `advance()` returns the transitional + component until the end, where `rollResult()` fires.
- Deployer requirements: USE mode, FACING=DOWN above the belt, rotational power, non-empty hand.
- GUI: all three screens skin their slots by **runtime-referencing** Create's `AllGuiTextures` (`client/CreateGui`); no Create assets are bundled.

**中文**

- 序列装配由机器驱动：物品带/不带 `SEQUENCED_ASSEMBLY` DataComponent 决定匹配；每步由对应机器执行（deploy→机械手等），`advance()` 未到终点返回过渡物+组件，末步 `rollResult()`。
- 机械手执行前置：USE 模式、FACING=DOWN 位于传送带上方、有旋转动力、手持非空。
- GUI：三个界面槽位皮肤**运行时引用** Create `AllGuiTextures`（`client/CreateGui`，不打包 Create 资源）。

## Compatibility (loader-declaration friendly) / 兼容性（加载器声明友好）

**EN**

- NeoForge `[21.1.249,)`, MC `[1.21.1]`, Create `[6.0.10,7.0.0)`, JEI optional; `displayTest=IGNORE_SERVER_VERSION`; changing the NeoForge patch version only needs `neo_version` in `gradle.properties`.
- Forge/Fabric and cross-MC versions are **rewrite-scale work** (multi-loader project such as Architectury) — not maintained.
- Tested environments: `1.21.1-create_productionline`, `1.21.1-{Neoforge}` (~100 mods incl. CBC/CDG/superbwarfare).

**中文**

- NeoForge `[21.1.249,)`、MC `[1.21.1]`、Create `[6.0.10,7.0.0)`、JEI optional；`displayTest=IGNORE_SERVER_VERSION`；换 NeoForge 小版本只改 `gradle.properties` 的 `neo_version`。
- Forge/Fabric 与跨 MC 版本为**重写级工程量**（Architectury 等多加载器工程），暂不维护。
- 实测环境：`1.21.1-create_productionline`、`1.21.1-{Neoforge}`（~100 mod，含 CBC/CDG/superbwarfare）。

## Build & install / 构建与安装

```
gradlew build -x neoFormJoined1.21.1-20240808.144430DownloadAssets   # skip asset download offline / 离线跳过资产下载
```

**EN** — Requires **JDK 21**. `gradlew compileJava --offline` is enough to confirm the sources compile; `gradlew build` produces `build/libs/create_productionline-<version>.jar`. Install by dropping that jar into your instance's `mods/` folder. If your network cannot reach a repository, set a proxy in your **user-level** `~/.gradle/gradle.properties` rather than in this repo (see the commented example there).

**Version policy / 版本规范** — `gradlew build` cuts the **release** (`mod_version`, `1.0.x`); `gradlew build -PdevBuild` cuts a **dev beta** (`0.0.0-dev.N`, N from `dev-build.txt`, auto-incremented after the jar is written). Only a release version is published as `release`: a dev version defaults to `beta` and is rejected only if you force `-PreleaseType=release`. `./gradlew build -PdevBuildNumber=5` reproduces a numbered dev artifact without touching the counter (this is what CI does for a `v0.0.0-dev.N` tag).

**中文** — 需要 **JDK 21**。只想确认能编译，`gradlew compileJava --offline` 即可；`gradlew build` 产出 `build/libs/create_productionline-<版本>.jar`。安装就是把 jar 放进实例的 `mods/` 目录。若你的网络访问不了仓库，请把代理写在**用户级** `~/.gradle/gradle.properties` 里，不要写进本仓库（本仓库 `gradle.properties` 有注释示例）。

**版本规范** — `gradlew build` 出**正式版**（取 `mod_version`，形如 `1.0.x`）；`gradlew build -PdevBuild` 出**开发版 beta**（`0.0.0-dev.N`，N 取自 `dev-build.txt`，出包后自动 +1）。只有正式版能以 `release` 类型发布；开发版默认即 `beta`，只有强行 `-PreleaseType=release` 才会被发布任务拒绝。`gradlew build -PdevBuildNumber=5` 可复现指定编号的开发包（CI 对 `v0.0.0-dev.N` tag 就是这么构建的）。

## Source layout (highlights) / 源码布局（要点）

```
com/create/productionline/
├── ProductionLineMod              @Mod entry (registration + dual-side event buses) / @Mod 主类（注册 + 双端事件总线挂载）
├── registry/                      ModBlocks/Items/BlockEntities/MenuTypes/CreativeTabs
├── block(+block/entity)           production_computer / scheme_loader / dismantler + ModContainer
├── menu/ + client/screen/         three GUIs (CreateGui runtime-references Create slot skin) / 三个 GUI（CreateGui 运行时引用 Create 槽位皮肤）
├── item/                          LineSchemeItem / GenericIntermediateItem / LineSchemeMirrorItem
├── line/scheme                    LineScheme + LineSchemeSerializer (NBT incl. embedded recipe entries)
├── line/mapper                    RecipeDescriptor / ServerRecipeLookup / Mappers / MappingDictionary
│                                  / RecipeMapper (build-guide text only)
├── line/analyzer                  RecipeAnalyzer (features) + MachineSelector (machine choice / step layout)
├── recipegen                      CreateRecipePack (flat/mechanical/sequenceEntry + multi-cabinet union rebuild)
│                                  RecipeDeriver (server-side single derivation entry) / 服务端唯一推导入口
├── compat/                        ClipboardCompat (carrier checks / whitelist / guide injection)
├── client/                        ClientSetup / CreateGui / ClientRecipeResolver
├── mixin/                         only two Smithing @Accessors (mixin config lists exactly those) / 仅 Smithing 两个 @Accessor
├── util/                          Names (#tag localization) / RecipeJsonReader (order- & tag-preserving)
└── qa/ event/ network/            SelfTest (13 headless checks) / events / payloads
```

## Security (multiplayer anti-injection, landed 2026-09-07) / 安全（多人服防注入，2026-09-07 落地）

**EN** — NBT-carrying items (schemes, mirrors) can be forged by modified clients on survival servers. The mod is now built on "the server is the only authority" (all findings reference current sources; `file:line` in parentheses):

- **Carrier whitelist** — `ClipboardCompat.isLoaderCarrier` (`compat/ClipboardCompat.java:83`) accepts only genuine `LineSchemeItem`; the loader slot (`menu/SchemeLoaderMenu.java:52`) and the BE collector (`block/entity/SchemeLoaderBlockEntity.java:100`) both use it, so paper/clipboard/mirror/forged NBT are rejected.
- **Server-side re-derivation in the loader** — activation never trusts the scheme's embedded JSON/Steps: `SchemeLoaderBlockEntity.currentEntriesMap` (:93) resolves `RecipeId` via `ServerRecipeLookup.findById` (`line/mapper/ServerRecipeLookup.java:53`) to the **live recipe**, then `RecipeDeriver.derive` (`recipegen/RecipeDeriver.java:48`) re-derives the entries to install — a forged scheme can at most activate "a recipe that really exists on the server"; missing/stale `recipeId` is skipped.
- **Authoritative dismantler refund** — `DismantlerBlockEntity.revert()` (`block/entity/DismantlerBlockEntity.java:58`) validates in order: slot 1 must be a genuine `LineSchemeItem` (:64-68) → slot 0 item id must equal the scheme's `OutputItem` (:79-82) → the `recipeId` must resolve server-side with a matching output (:86-98) → any `#tag` in the refund set rejects the whole operation (:100-108); it consumes one first, then refunds, then places the mirror (:112-138). Unresolvable/mismatched/tagged → **consume nothing, produce nothing**, killing "forge a scheme to print valuable materials".
- **Network entry validation** — `ModPayloads.handleDismantle` (`network/ModPayloads.java:87`) calls `menu.stillValid(player)` before touching any slot.
- **Mirror is text-only** — `LineSchemeMirrorItem` (`item/LineSchemeMirrorItem.java:29,42`) writes only a `LineSchemeMirror` display snapshot (OutputItem/BaseMaterial/Steps text) with no executable `LineScheme`/embedded recipes, and `ClipboardCompat.isCarrier` returns `false` for it (:64) — it can neither be activated nor re-fed.
- The computer itself generates server-side (validation is inherent), sharing the same algorithm/entry point as the loader.

**中文** — 方案/镜像等"存 NBT 的物品"在多人生存服可能被改包客户端伪造。现已按"服务端为唯一权威"重构（下列结论均对应当前源码，括号内为文件:行）：

- **容器白名单**：`ClipboardCompat.isLoaderCarrier`（`compat/ClipboardCompat.java:83`）只认真 `LineSchemeItem`；加载柜槽位（`menu/SchemeLoaderMenu.java:52`）与 BE 收集（`block/entity/SchemeLoaderBlockEntity.java:100`）都走它，纸/剪贴板/镜像/任意伪造 NBT 载体一律拒收。
- **加载柜服务端推导**：激活时不信任方案内嵌 JSON/Steps。`SchemeLoaderBlockEntity.currentEntriesMap`（:93）按 `RecipeId` 调 `ServerRecipeLookup.findById`（`line/mapper/ServerRecipeLookup.java:53`）取**实时配方**，再交给 `RecipeDeriver.derive`（`recipegen/RecipeDeriver.java:48`）重推导安装条目——伪造方案最多只能激活"服务器上真实存在的配方"；方案里没有/失效的 `recipeId` 直接跳过。
- **破拆机权威退款**：`DismantlerBlockEntity.revert()`（`block/entity/DismantlerBlockEntity.java:58`）依次校验槽 1 必须是真 `LineSchemeItem`（:64-68）→ 槽 0 物品 id 必须等于方案 `OutputItem`（:79-82）→ 方案 `recipeId` 能在服务端解析且产物一致（:86-98）→ 退还集合里出现 `#tag` 一律整单拒绝（:100-108）；先消费 1 份再退还，最后放镜像（:112-138）。解析不到/产物不符/含 tag 全部**不消费不产出**，杜绝"伪方案刷贵重原料"。
- **网络入口校验**：`ModPayloads.handleDismantle`（`network/ModPayloads.java:87`）先做 `menu.stillValid(player)` 再触碰任何槽位，失效 GUI 不会被执行。
- **镜像纯文本化**：`LineSchemeMirrorItem`（`item/LineSchemeMirrorItem.java:29,42`）只写 `LineSchemeMirror` 展示快照（OutputItem/BaseMaterial/Steps 文本），结构上不携带可执行 `LineScheme` 与内嵌配方，`ClipboardCompat.isCarrier` 对其直接返回 `false`（:64）——无法被激活或复喂。
- 产线计算机本身服务端生成（校验天然存在），与加载柜同源同算法。

> **EN** — The same "server is the only authority" rule covers the compute entry point as well: the payload carries the real `recipeId`, and the server re-resolves it against its live `RecipeManager`, accepting it **only** when that recipe really produces the item sitting in the target slot. A client hint that cannot be verified yields no plan at all.
> **中文** — 同一条"服务端为唯一权威"的规则同样覆盖计算入口：计算包携带真 `recipeId`，服务端在实时 `RecipeManager` 上重新解析，**只有**当该配方确实产出目标槽内的物品时才采纳；无法验证的客户端数据不会写出任何方案。

## Known limits / 已知边界

**EN**

- Sequence lines consume 1 unit of each material per step (cheaper than the source grid when a material repeats) but still produce output.
- Results carry item id + count only: recipes with NBT/enchantments/state yield the "plain" variant.
- Multi-level intermediates are not recursed into a single scheme by default: compute each stage, activate them together as a union in the 16-slot loader for an end-to-end line.
- Single-material recipes are converted through a semantic single-machine choice (wood → saw, ore → crushing wheel, organic → millstone, metal/gem → press); the machine is a heuristic, not a faithful simulation of the original recipe. Only targets already produced by a native Create process, or recipes with no usable materials, are refused with "cannot convert".
- The dismantler refunds what it can materialize (`#tag` → first member) and refuses without consuming when nothing can be refunded.
- Recipe types that cannot specify a duration (pressing / splashing / haunting / mixing) get no `processing_time` — Create rejects such files outright, so only milling/crushing/cutting carry it.
- Create `assets/` is All Rights Reserved: this mod only "runtime-references" its GUI/textures and never bundles copies.
- Every texture and icon in this project is original artwork drawn by the author; the mod bundles no third-party assets.

**中文**

- 序列产线每种材料每步耗 1 个（源网格多量词时"省料"），仍可产出。
- 结果仅 item id+count：带 NBT/附魔/状态产物为"素体"。
- 多级中间物默认不递归进单份方案：分多方案经 16 格柜并集激活组成端到端线。
- 破拆机对能还原的材料尽力退还（`#tag` → 首个成员），完全退不出时不消耗物品。
- 不能指定时长的配方类型（pressing / splashing / haunting / mixing）不写 `processing_time`——Create 会直接拒绝这类文件；只有 milling/crushing/cutting 携带该字段。
- Create `assets/` 为 All Rights Reserved：本 mod 只"运行时引用"其 GUI/贴图，不打包复制。
- 本项目的全部贴图与图标均为作者原创手绘；模组不打包任何第三方素材。

## Contributors & funding / 贡献与资助名单

**EN** — This project is kept alive by people who give time **and** money. The full list lives in
[`CONTRIBUTORS.md`](CONTRIBUTORS.md): **funding supporters first** (the main list), then **technical
support** (tooling / AI agents that did code analysis, algorithm fixes, hardening, builds & docs),
then code/art/testing contributors, then upstream projects. Amounts are optional and anonymous entries
are welcome; every entry can be corrected or removed on request.

**中文** — 本项目靠投入时间与**资金**的人维持。完整名单见 [`CONTRIBUTORS.md`](CONTRIBUTORS.md)：
**资金支持者排在最前（主要名单）**，其后依次是**技术支持**（承担代码分析、算法修复、加固、构建与文档
的工具/AI 代理）、代码/美术/测试贡献者、上游项目。金额可留空、可匿名，任何条目都可按要求更正或删除。

**Funding supporters / 资金支持**

| Supporter / 支持者 | Amount / 金额 | Date / 日期 | Note / 备注 |
| --- | --- | --- | --- |
| 那狐不开提那狐 [LUOZY] | ¥50 CNY | 2026-09-16 | 公开 / public |

**Technical support / 技术支持**

| Contributor / 贡献者 | Contribution / 贡献内容 | Period / 时间 |
| --- | --- | --- |
| **DeepSeek Harness (dsh) · deepseek-v4-flash** (AI coding agent / AI 编码代理) | 反编译取证、配方转换算法修复、多人服防注入加固、构建/网络工具链、双语文档与 QA 用例 / decompilation evidence, recipe-conversion fixes, anti-injection hardening, build & network tooling, bilingual docs and QA cases | 2026-09 |
| **deepseek-v4-pro** (AI model / AI 模型) | 架构与实现评审、加固与发布方案评估 / architecture & implementation review, hardening and release assessment | 2026-09 |
| **deepseek-v4-vision-exp** (AI vision model / AI 视觉模型) | 截图判读、GUI/资源核对 / screenshot reading and GUI/asset verification | 2026-09 |

> To be added / 登记方式：把「署名（或匿名）/ 金额或区间 / 日期 / 是否公开」发给维护者即可。
> Third-party code & license inventory / 第三方代码与许可清单：见 `THIRD_PARTY_NOTICES.md`。

## Headless self-test (QA) / 无头自检（QA）

```
# start the server / dev runtime with the self-test enabled / 启动服务器或开发运行时开启自检
gradlew runServer -PselfTest
```

> **EN** — `-PselfTest` forwards `create_productionline.selfTest=true` to the GAME JVM (a bare `-D` on the Gradle command line does not reach it). The property is read by `qa/SelfTest.isEnabled()`; after server start the 13 checks run against a **real server** (real registries/NBT/components/`RecipeManager`).
> **中文** — `-PselfTest` 会把 `create_productionline.selfTest=true` 传给**游戏 JVM**（在 Gradle 命令行上直接写 `-D` 传不到游戏进程）。该属性由 `qa/SelfTest.isEnabled()` 读取；服务器启动后跑完 **13 项**检查。
> The server halts itself afterwards, but the game process may not exit cleanly — if `:runServer` hangs, kill the game JVM; the task then reports `FAILED` even though the checks passed, so judge by the lines below.
> 自检后服务器会自行 `halt`，但游戏进程有时不会干净退出：若 `:runServer` 卡住，手动结束游戏进程即可；此时任务会显示 `FAILED`，但检查本身已通过，看下面的输出为准。

```
[PASS] TC-05 scheme NBT round-trip
[PASS] TC-02 clipboard guide injection
[PASS] TC-01 recipe derivation (positive, live recipes)
[PASS] TC-01 recipe derivation (negative, not convertible)
[PASS] Create recipe JSON schema + datapack install
[PASS] Tag ingredients kept in flat recipes
[PASS] Duration only on duration-capable types
[PASS] Loader accepts written schemes only
[PASS] Self-referential recipes are skipped
[PASS] Deriver refuses native/unmappable recipes
[PASS] Single-material recipes map to a semantic machine
[PASS] Scheme embeds generated recipes (round trip)
[PASS] Plan topology (chain: base -> machine+material -> product)
CPL SELF-TEST RESULT: 13 passed, 0 failed
```

> **EN** — **Do not hard-code the count when judging a build.** `qa/SelfTest.java` prints
> `CPL SELF-TEST RESULT: <passed> passed, <failed> failed` (`SelfTest.java:103`), so the pass criterion is
> *the last line matches `\d+ passed, 0 failed`* — never a literal number. The number below is only a
> convenience snapshot and is the count of `check("…")` calls in `qa/SelfTest.java`.
> **中文** — **验收时不要把项数写死。** `qa/SelfTest.java` 打印的是
> `CPL SELF-TEST RESULT: <passed> passed, <failed> failed`（`SelfTest.java:103`），所以判据是
> *最后一行匹配 `\d+ passed, 0 failed`*，而不是某个字面数字。下面的数字只是便于阅读的快照，其值等于
> `qa/SelfTest.java` 里 `check("…")` 的调用数。
>
> **EN** — Current snapshot: **13** checks. `Plan topology (chain: base -> machine+material -> product)`
> arrived with dev snapshot `0.0.0-dev.3`; `Tag ingredients kept in flat recipes`, `Duration only on
> duration-capable types`, `Loader accepts written schemes only`, `Self-referential recipes are skipped`,
> `Deriver refuses native/unmappable recipes` and `Single-material recipes map to a semantic machine`
> landed by release 1.0.1. Adding or removing a `check(…)`
> changes this number, and nothing else needs editing except the snapshot mentions in this README,
> in `CHANGELOG.md` and in `RELEASING.md`.
> **中文** — 当前快照：**13 项**。`Plan topology (chain: base -> machine+material -> product)` 随开发快照
> `0.0.0-dev.3` 引入；`Tag ingredients kept in flat recipes`、`Duration only on duration-capable types`、
> `Loader accepts written schemes only`、`Self-referential recipes are skipped`、
> `Deriver refuses native/unmappable recipes`、`Single-material recipes map to a semantic machine`
> 六项随正式版 1.0.1 落地。
> 增删一个 `check(…)` 只会改变这个数字；除本 README、`CHANGELOG.md`、`RELEASING.md` 中标注为"快照"的处所外，
> 其他地方无需改动。
>
> **EN** — Historical docs mentioning "5 passed" / "6 passed" / "7 passed" / "9 passed" / "10 passed" refer to earlier
> rounds (the `DataPacket action whitelist` case was removed with the old architecture); current code has **13** checks
> and no DataPacket whitelist case.
> **中文** — 历史文档里的 "5 passed" / "6 passed" / "7 passed" / "9 passed" / "10 passed" 对应更早的轮次（`DataPacket
> action whitelist` 一项随旧架构删除）；当前代码为 **13 项**，且不再有 DataPacket 白名单用例。

## Doc↔code consistency baseline (2026-09-13) / 文档—代码一致性核对基线（2026-09-13）

**EN** — Records the "code is the source of truth" line-by-line review, for later reference.

**Corrected in this README**: computer slots changed from "top/bottom" to **3 side by side** (`ProductionComputerMenu.java:38-50`: target 44,20 / carrier 80,20 / clipboard 116,20); loader slot changed to **genuine-scheme-only**; built-in mappings corrected 17 → **24**; added `recipegen/RecipeDeriver`, `compat/ClipboardCompat`, `util/RecipeJsonReader` entries; jar size and source size now measured values. Also corrected later: the old "leftover `debug/` directory" and "`src/generated/` unused" notes (neither directory exists) and every "jar still ships `.bak` / `*_particle.png`" claim (the current jar was inspected and is clean).

**中文** — 本节记录"以现有代码为准"逐条核对后的结论，供后续改动对照。

**已按代码改正的本 README 条目**：计算机槽位由"上/下格"改为**3 格并排**（`ProductionComputerMenu.java:38-50`：目标 44,20 / 载体 80,20 / 剪贴板 116,20）；加载柜槽位由"载体槽"改为**只收真方案**；内建映射由 17 条更正为 **24 条**；新增 `recipegen/RecipeDeriver`、`compat/ClipboardCompat`、`util/RecipeJsonReader` 等模块条目；jar 体积/工程规模改为实测值。后续又改正：删除"残留空目录 `debug/`"与"`src/generated/` 未启用"两处说明（两个目录都已不存在），以及所有"jar 里仍有 `.bak` / `*_particle.png`"的断言（已实测当前 jar，内容干净）。

**Code vs text mismatches still open (to fix; not README errors) / 代码与文案仍不一致的未修项（非本 README 描述错误）**

Stale entries were **deleted, not softened**: any row that no longer matched the code as of this revision
was removed rather than reworded. Verified-clean as of this revision (so no longer listed): the language
files now describe the 3-slot side-by-side layout, `ClipboardCompat`'s javadoc no longer mentions the
removed "controller", `SchemeLoaderMenu` / `SchemeLoaderBlockEntity` javadoc now state the genuine-scheme-only
rule, the built jar ships no `.bak` / `*_particle.png` / `debug/` entries, and neither the empty `debug/`
directory nor `src/generated/` exists any more. Also fixed since: the dismantler got its own GUI background
(no longer the 16-slot loader sheet), and the loader screen's "embedded recipes" count now comes from the
server-derived active count instead of the item's cached JSON.

已过时条目一律**删除而非弱化措辞**：与当前代码不符的行直接删掉。本版已核实为干净（故不再列出）：
语言文件已改为描述 3 格并排布局；`ClipboardCompat` javadoc 已不再提已删除的 "controller"；
`SchemeLoaderMenu` / `SchemeLoaderBlockEntity` javadoc 已写明"只认真方案"；产物 jar 不含
`.bak` / `*_particle.png` / `debug/` 条目；空目录 `debug/` 与 `src/generated/` 均已不存在。
此后又修复：破拆机改用专属 GUI 底图（不再复用 16 格加载柜底图）；加载柜界面的"内嵌配方数"改显示
服务端推导的真实生效数量，不再读物品缓存 JSON。

| Location / 位置 | State / 现状 | Note / 说明 |
| --- | --- | --- |
| `lang` `loader.create_productionline.slots_filled` | hard-codes `%s/16` / 写死 `%s/16` | consistent with `SchemeLoaderBlockEntity.SLOT_COUNT=16` / 与 `SchemeLoaderBlockEntity.SLOT_COUNT=16` 一致，但改容量会漏改 |
