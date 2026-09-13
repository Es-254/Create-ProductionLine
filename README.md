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
| Artifact / 产物 | `create_productionline/build/libs/create_productionline-1.0.0.jar` (**175,334 B ≈ 171 KiB**, built 2026-09-13 14:37) |
| Source size / 工程规模 | `src/main/java` **46 Java files / 4,907 lines** (2026-09-13) |
| Docs / 文档 | This file (**current implementation & usage** 当前实现与用法); `docs/开发文档.md` (**architecture evolution / verification / open issues** 架构演进·验证记录·遗留问题); `docs/需求规格说明书.docx` (legacy SRS, traceability only 旧架构仅作溯源); `CHANGELOG.md`; `RELEASING.md`. Icon / 图标: `create_productionline.ico` (16–256, source `贴图/ico.png`), platform icon `icon_512x512.png` |

---

## Modules / 模块

| Module / 模块 | Description (EN) | 说明（中文） |
| --- | --- | --- |
| Production Computer / 产线计算机 | **3 slots side by side** (`SLOT_TARGET=0` target, `SLOT_SCHEME=1` carrier, `SLOT_CLIPBOARD=2` clipboard). On **Compute** the client scans resource packs and sends parsed recipe JSON; the server re-derives from the live `RecipeManager`, writes a **single-layer direct plan + embedded native Create recipe JSON** onto the carrier, and injects the `custom_data.LineBuildGuide` build guide. | **3 格并排**（`SLOT_TARGET=0` 目标物品 / `SLOT_SCHEME=1` 载体 / `SLOT_CLIPBOARD=2` 剪贴板）。点【计算】时客户端先扫资源包解析配方 JSON 送服务端，服务端以实时 `RecipeManager` 重新推导 → 生成**单层直连方案 + 内嵌 Create 原生配方 JSON** → 写入载体物品并注入 `custom_data.LineBuildGuide` 施工指引。 |
| Line Scheme / 产线方案 | Carries `LineScheme` NBT (`Version/RecipeId/OutputItem/BaseMaterial/Steps[]` + embedded `CreateRecipes`); activatable by the loader, readable by the dismantler. | 携带 `LineScheme` NBT（`Version/RecipeId/OutputItem/BaseMaterial/Steps[]` + 内嵌 `CreateRecipes`），可被加载柜激活、被破拆机读取。 |
| Scheme Loader / 方案加载柜 | **16 slots (2×8)**, **accepts only genuine `LineSchemeItem`** (paper/clipboard/mirror/forged NBT rejected). On insert it re-derives this cabinet's contribution server-side from each scheme's `recipeId`, writes the `cpl_converted` union and runs `/reload`; **union across cabinets/slots**, never overwrites; emits redstone while recipes are active. | **16 格（2×8）**，**只收真 `LineSchemeItem`**（纸/剪贴板/镜像/伪造 NBT 一律拒收）。放入即按方案 `recipeId` 服务端**重推导**本柜贡献 → 写入 `cpl_converted` 并集并 `/reload`；**多柜/多格并集**，互不覆盖；有生效配方时输出红石信号。 |
| Dismantler / 破拆机 | 2 slots (product / scheme). Resolves the scheme's `recipeId` to the **real inputs** server-side, refunds materials and creates a read-only mirror; unresolvable id, output mismatch or any `#tag` input → **reject the whole operation, consume nothing, produce nothing**. | 2 格（产物 / 方案）。按方案 `recipeId` 在服务端解析**真实输入**后退还原料，并生成只读镜像；解析不到、产物不符、含 `#tag` 输入一律**整单拒绝，不消费不产出**。 |
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
- **Crafting / assembly** (`minecraft:crafting` etc.) → `create:sequenced_assembly`: `ingredient` = base, `transitional_item` = Generic Intermediate, one `create:deploying` step per extra material, `loops=1`, output count preserved.
- **Native Create recipes** (`create:mechanical_crafting`, e.g. CBC shells) → **additionally** generate a non-conflicting `cpl` sequence recipe (the original stays), decided by `isConvertibleAssembly`.
- **Tag fidelity** — source tags are written verbatim as `{"tag":…}` so any tag member matches; tooltips show the first member's localized name (display only).
- **Single ordering source** — plan and embedded recipe share ONE "JSON-ordered, tag-preserving" material list, eliminating base/step mismatches (root cause of the diesel-engine bug).
- Single-layer direct plans only, no upstream recursion; self-referencing materials (input == output) are treated as "bring your own" and omitted.
- Assembly mode: `config/create_productionline-mappings.json` → `"assemblyMode": "sequenced"|"mechanical"` (default `sequenced`).
- **24 built-in mappings**: vanilla 6 (crafting/smelting/smoking/blasting/campfire_cooking/stonecutting) + Create 18 (cutting/pressing/milling/crushing/mixing/compacting/deploying/item_application/sandpaper_polishing/mechanical_crafting/haunting/splashing/washing/fan_washing/fan_splashing/fan_haunting/fan_smoking/fan_blasting); overridable/extendable via the same JSON's `categories`, with tail-key fallback in `lookup()`.
- **Single derivation entry point** `recipegen/RecipeDeriver`: `derive()` returns "input order + output count + installable entries" in one shot, shared by the computer and the loader; the embedded JSON is only a cache — the server always trusts `recipeId` + the live `RecipeManager`.

**中文**

- **单机工艺类** → 扁平 `create:<type>` 配方（粉碎/研磨/混合/压片/切割等，按映射字典 category→facility）。
- **合成/装配类**（`minecraft:crafting` 等）→ `create:sequenced_assembly`：`ingredient`=基底，`transitional_item`=通用中间产物，每个追加原料一个 `create:deploying` 步，`loops=1`，产物 count 保留。
- **原生 Create 配方**（`create:mechanical_crafting`，如 CBC 炮弹）→ **额外**生成一份不冲突的 cpl 序列配方（原配方保留），`isConvertibleAssembly` 统一判定。
- **标签保真**：源配方 tag 原样写成 `{"tag":…}`，任何同标签成员都能匹配；tooltip 里按标签首个成员显示中文名（仅展示层）。
- **单一顺序来源**：方案与内嵌配方共用同一份"JSON 保序、保标签"材料列表，杜绝基底/步骤错位（柴油引擎问题根因）。
- 单层直连、无上游递归；自引用材料（原料==目标）按"自备/现编"省略。
- 装配模式：`config/create_productionline-mappings.json` → `"assemblyMode": "sequenced"|"mechanical"`（默认 sequenced）。
- **内建映射 24 条**（原版 6 + Create 18，同上），可用同一 JSON 的 `categories` 覆盖或扩展，`lookup()` 另有"尾键回退"匹配。
- **唯一推导入口** `recipegen/RecipeDeriver`：`derive()` 一次性给出「输入顺序 + 产物 count + 可安装条目」，产线计算机与加载柜共用；方案内嵌 JSON 只是缓存，服务端始终以 `recipeId` + 实时 `RecipeManager` 为准。

## Technical notes (from decompiling Create 6.0.10) / 关键技术点（反编译 Create 6.0.10 结论）

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

**EN** — **2026-09-13 re-check**: `gradlew compileJava --offline` → `BUILD SUCCESSFUL` (`compileJava UP-TO-DATE`; sources unchanged since the 09-07 23:14 build, so jar and sources are the same revision). Since 2026-09-06 this machine has direct internet, the proxy lines in `gradle.properties` are commented out; if blocked again, start the `nettest/` proxy or use the "remote Thunder/curl → shared disk (I:)" channel (`nettest/thunder_probe.py`). Install: copy the jar into `.../mods/` (both the dev instance and `1.21.1-{Neoforge}` are already synced).

**中文** — **2026-09-13 复核**：`gradlew compileJava --offline` → `BUILD SUCCESSFUL`（`compileJava UP-TO-DATE`，源码自 09-07 23:14 构建后未再改动，故 jar 与源码同版本）。2026-09-06 起本机可直连外网，`gradle.properties` 代理行已注释；再遇封锁可启用 `nettest/` 代理或走"远端迅雷/curl → 共享盘(I:)取回"通道（`nettest/thunder_probe.py`）。安装：jar 覆盖到 `.../mods/`（开发实例与 `1.21.1-{Neoforge}` 实机均已同步）。

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
│                                  / RecipeMapper / MappingResult
├── line/analyzer                  RecipeAnalyzer (features) + MachineSelector (machine choice / step layout)
├── recipegen                      CreateRecipePack (flat/mechanical/sequenceEntry + multi-cabinet union rebuild)
│                                  RecipeDeriver (server-side single derivation entry) / 服务端唯一推导入口
├── compat/                        ClipboardCompat (carrier checks / whitelist / guide injection)
├── client/                        ClientSetup / CreateGui / ClientRecipeResolver
├── mixin/                         only two Smithing @Accessors (mixin config lists exactly those) / 仅 Smithing 两个 @Accessor
├── util/                          Names (#tag localization) / RecipeJsonReader (order- & tag-preserving)
└── qa/ event/ network/            SelfTest (6 headless checks) / events / payloads
```

> **EN** — Leftover empty `debug/` directory (0 files); `src/generated/` unused (no datagen output).
> **中文** — 残留空目录 `debug/`（无文件）；`src/generated/` 未启用（无 datagen 产物）。

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

> **EN** — The same "server is the only authority" rule now covers the compute entry point too (see `docs/开发文档.md` §6.2 **M1**): the payload carries the real `recipeId`, and the server re-resolves it against its live `RecipeManager`, accepting it **only** when that recipe really produces the item sitting in the target slot. A client hint that cannot be verified yields a plan but **never** an installable recipe.
> **中文** — 同一条"服务端为唯一权威"的规则现已覆盖计算入口（详见 `docs/开发文档.md` §6.2 **M1**）：计算包携带真 `recipeId`，服务端在实时 `RecipeManager` 上重新解析，**只有**当该配方确实产出目标槽内的物品时才采纳；无法验证的客户端数据只能生成方案，**绝不**产出可安装配方。

## Known limits / 已知边界

**EN**

- Sequence lines consume 1 unit of each material per step (cheaper than the source grid when a material repeats) but still produce output.
- Results carry item id + count only: recipes with NBT/enchantments/state yield the "plain" variant.
- Multi-level intermediates are not recursed into a single scheme by default: compute each stage, activate them together as a union in the 16-slot loader for an end-to-end line.
- The dismantler refuses items with `#tag` inputs or recipes it cannot resolve server-side (refuse rather than swallow).
- Create `assets/` is All Rights Reserved: this mod only "runtime-references" its GUI/textures and never bundles copies.
- Icon sources live in `贴图/` (since 2026-09-07 the redrawn Generic Intermediate / Mirror 16×16 and the 512×512 `ico.png`); wired into `src/main/resources/assets/create_productionline/textures/item/` and `src/main/resources/create_productionline_icon.png`; Windows icon: `create_productionline/create_productionline.ico`.

**中文**

- 序列产线每种材料每步耗 1 个（源网格多量词时"省料"），仍可产出。
- 结果仅 item id+count：带 NBT/附魔/状态产物为"素体"。
- 多级中间物默认不递归进单份方案：分多方案经 16 格柜并集激活组成端到端线。
- 破拆机不支持含 `#tag` 输入/无法服务端解析的产物（宁拒不吞）。
- Create `assets/` 为 All Rights Reserved：本 mod 只"运行时引用"其 GUI/贴图，不打包复制。
- 图标源文件位于 `贴图/`（2026-09-07 起含重绘的 通用中间产物/镜像 16×16 与 512×512 `ico.png`），已接入 `src/main/resources/assets/create_productionline/textures/item/` 与 `src/main/resources/create_productionline_icon.png`；Windows 图标见 `create_productionline/create_productionline.ico`。

## Headless self-test (QA) / 无头自检（QA）

```
# start the server / dev runtime with this JVM property / 启动服务器或开发运行时并传入该属性
gradlew runServer -Dcreate_productionline.selfTest=true
```

> **EN** — The property is read by `qa/SelfTest.isEnabled()` (`Boolean.getBoolean`); after server start the checks run and the process `halt`s. 6 checks run against a **real server** (real registries/NBT/components/`RecipeManager`) — `qa/SelfTest.java:57-62`.
> **中文** — 该属性由 `qa/SelfTest.isEnabled()`（`Boolean.getBoolean`）读取，服务器启动完成后自动跑完并 `halt`。`qa/SelfTest` 在**真实服务器**（真实注册表/NBT/组件/`RecipeManager`）上跑 **6 项**检查（`qa/SelfTest.java:57-62`）。

```
[PASS] TC-05 scheme NBT round-trip
[PASS] TC-02 clipboard guide injection
[PASS] TC-01 mapping (positive, live recipes)
[PASS] TC-01 mapping (negative, unmappable)
[PASS] Create recipe JSON schema + datapack install
[PASS] Scheme embeds generated recipes (round trip)
CPL SELF-TEST RESULT: 6 passed, 0 failed
```

> **EN** — Historical docs mentioning "5 passed" refer to the 2nd-round build (which included the `DataPacket action whitelist` case, removed with the old architecture); current code has **6** checks and no DataPacket whitelist case.
> **中文** — 历史文档里的 "5 passed" 对应第 2 轮版本（含 `DataPacket action whitelist`，该项随旧架构删除）；当前代码为 **6 项**，且不再有 DataPacket 白名单用例。

## Doc↔code consistency baseline (2026-09-13) / 文档—代码一致性核对基线（2026-09-13）

**EN** — Records the "code is the source of truth" line-by-line review, for later reference.

**Corrected in this README**: computer slots changed from "top/bottom" to **3 side by side** (`ProductionComputerMenu.java:38-50`: target 44,20 / carrier 80,20 / clipboard 116,20); loader slot changed to **genuine-scheme-only**; built-in mappings corrected 17 → **24**; added `recipegen/RecipeDeriver`, `compat/ClipboardCompat`, `util/RecipeJsonReader` entries; jar size and source size now measured values.

**中文** — 本节记录"以现有代码为准"逐条核对后的结论，供后续改动对照。

**已按代码改正的本 README 条目**：计算机槽位由"上/下格"改为**3 格并排**（`ProductionComputerMenu.java:38-50`：目标 44,20 / 载体 80,20 / 剪贴板 116,20）；加载柜槽位由"载体槽"改为**只收真方案**；内建映射由 17 条更正为 **24 条**；新增 `recipegen/RecipeDeriver`、`compat/ClipboardCompat`、`util/RecipeJsonReader` 等模块条目；jar 体积/工程规模改为实测值。

**Code vs text mismatches (to fix; not README errors) / 代码与文案仍不一致（属待修项，非本 README 描述错误）**

| Location / 位置 | State / 现状 | Note / 说明 |
| --- | --- | --- |
| `lang/zh_cn.json`·`en_us.json` `screen…computer.empty/no_scheme/no_target` | text says "top slot / bottom two slots" / 文案写"上面格/下面两格" | conflicts with the 3-slot side-by-side layout / 与代码的 3 格并排不符（`ProductionComputerMenu.java:38-50`） |
| `lang` `loader.create_productionline.slots_filled` | hard-codes `%s/16` / 写死 `%s/16` | consistent with `SLOT_COUNT=16` but easy to miss on resize / 与 `SchemeLoaderBlockEntity.SLOT_COUNT=16` 一致，但改容量会漏改 |
| `SchemeLoaderScreen.java:77` | counts "embedded recipes N" via `scheme.getCreateRecipes().size()` / 用该字段统计"内嵌配方 N 条" | that field is now **cache only**; real entries come from `RecipeDeriver` server-side / 该字段现在只是缓存；真实生效条目由 `RecipeDeriver` 服务端推导，二者可能不等 |
| `SchemeLoaderMenu.java:43` / `SchemeLoaderBlockEntity.java:22-28` javadoc | still says "a slot accepts any plan carrier / paper / clipboard / Line Scheme" | implementation is now `isLoaderCarrier` (genuine scheme only) / 实现已收口为 `isLoaderCarrier`（只认真方案） |
| `ClipboardCompat.java:54-58` javadoc | still mentions "the controller", "its two item slots" | controller module removed; there are 3 slots now / 控制器模块已删除、槽位现有 3 个 |
| `DismantlerScreen.java:19-20` | dismantler GUI reuses `textures/gui/scheme_loader.png` | that background is drawn with 16-slot decoration while the dismantler has 2 slots / 加载柜底图画了 16 格装饰，破拆机只有 2 槽 |
| jar contents / jar 内容 | still ships `lang/*.json.bak`×2 and unreferenced `*_particle.png`×3 | `build.gradle` does not exclude them (release hygiene) / `build.gradle` 未排除，属发布卫生问题 |
| `debug/` directory / 目录 | empty shell (0 files) / 空壳（0 文件） | old diagnostics deleted, directory left behind / 旧诊断类已删，目录残留 |
