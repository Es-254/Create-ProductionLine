# Create: Production Line / 机械动力：产业线

**EN** — A Create addon for **Minecraft 1.21.1 / NeoForge**. Point it at any recipe (vanilla,
Create, another mod), and it writes **native Create recipe JSON** at runtime, drops the files into
a world datapack and reloads that datapack. The target item can then be produced by a real Create
production line.

**中文** — 面向 **Minecraft 1.21.1 / NeoForge** 的 Create 附属模组。配方不管来自原版、Create
还是别的 mod，都能在游戏内实时转成 **Create 原生配方 JSON** 写进世界数据包，再 `/reload` 一次，
目标物品随后就能沿一条真实 Create 产线生产出来。

| Item / 项 | Value / 值 |
| --- | --- |
| Mod ID / 包名 | `create_productionline` / `com.create.productionline` |
| Platform / 平台 | NeoForge (FML 1.x) / MC `[1.21.1]` / JDK 21 |
| Prerequisites / 前置 | Create `6.0.10+` (**required** 缺失拒载); JEI `19.x` (**optional** 仅配方查看，不调用其 API) |
| Version / 版本 | **`1.0.2` (beta, in development)** / **`1.0.1` (release, current stable)**. `1.0.2` adds the OP anvil flow and ships as a **beta pre-release** (Modrinth channel *Beta*, CurseForge release type `beta`, GitHub pre-release); **`1.0.1` stays the current release**, the first official release, which supersedes every dev snapshot. Dev builds are **`0.0.0-dev.N`** (**beta**, built by `gradlew build -PdevBuild`, with N auto-incremented in `dev-build.txt`)。中文：`1.0.2` 是开发中的 **beta**（新增 OP 铁砧自定义流程），按 **beta 预发布**（Modrinth *Beta* 通道、CurseForge `beta`、GitHub pre-release）；**`1.0.1` 仍是当前正式版**，也是首个正式发布，已取代全部开发快照；开发构建为 `0.0.0-dev.N`（beta），由 `-PdevBuild` 产出并按 `dev-build.txt` 递增。 |
| Artifact / 产物 | `build/libs/create_productionline-1.0.2.jar` (**the 1.0.2 beta**), also downloadable from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/create-production-line), [Modrinth](https://modrinth.com/project/createproductionline) or the [Releases](https://github.com/Es-254/Create-ProductionLine/releases) page. CurseForge 页面已上线；Modrinth 项目仍在审核中，公开页面待通过后生效 / the CurseForge page is live, the Modrinth one goes live once the project passes review. 早期 1.0.0–1.0.3 构建包均为开发快照，已被 1.0.1 取代（旧编号里的 1.0.2 就是其中之一，和这次重新编号的 1.0.2 beta 不是同一个包）(earlier 1.0.0–1.0.3 jars, including the old-numbering 1.0.2, were dev snapshots superseded by 1.0.1; the 1.0.2 beta named here is a new artifact under the new numbering). |
| Source size / 工程规模 | `src/main/java` **51 Java files** / **~6,700 lines** (the line count is a snapshot, it moves with every commit; the file count is the stable part) |
| Docs / 文档 | This file (**current implementation & usage** 当前实现与用法); `CHANGELOG.md` (**release history** 更新日志); `RELEASING.md` (**how a release is cut** 发布流程); `CONTRIBUTORS.md` (**contributors & funding** 贡献与资助名单); `THIRD_PARTY_NOTICES.md` (**third-party inventory** 第三方清单). Icon / 图标: `create_productionline.ico` (16–256), platform icon `icon_512x512.png` |

---

## Modules / 模块

| Module / 模块 | Description (EN) | 说明（中文） |
| --- | --- | --- |
| Production Computer / 产线计算机 | **3 slots side by side** (`SLOT_TARGET=0` target, `SLOT_SCHEME=1` blank Line Scheme carrier, `SLOT_CLIPBOARD=2` optional paper — that constant name is legacy, clipboards are not accepted). On **Compute** the client scans resource packs and sends parsed recipe JSON; the server re-derives from the live `RecipeManager`, writes a **single-layer direct plan + embedded native Create recipe JSON** onto the carrier, and injects the `custom_data.LineBuildGuide` build guide. | **3 格并排**（`SLOT_TARGET=0` 目标物品 / `SLOT_SCHEME=1` 空白产线方案载体 / `SLOT_CLIPBOARD=2` 可选纸——这个常量名是历史遗留，剪贴板放不进去）。点【计算】时，客户端先扫资源包、把解析出的配方 JSON 交给服务端；服务端拿实时 `RecipeManager` 重新推导，然后把**单层直连方案 + 内嵌 Create 原生配方 JSON**写入载体，同时注入 `custom_data.LineBuildGuide` 施工指引。 |
| Line Scheme / 产线方案 | Carries `LineScheme` NBT (`Version/RecipeId/OutputItem/BaseMaterial/Steps[]` + embedded `CreateRecipes`); activatable by the loader, readable by the dismantler. | 携带 `LineScheme` NBT（`Version/RecipeId/OutputItem/BaseMaterial/Steps[]` + 内嵌 `CreateRecipes`），可被加载柜激活、被破拆机读取。 |
| Scheme Loader / 方案加载柜 | **16 slots (2×8)**, **accepts only genuine `LineSchemeItem`** (paper/clipboard/mirror/forged NBT rejected). On insert it re-derives this cabinet's contribution server-side from each scheme's `recipeId`, writes the `cpl_converted` union and runs `/reload`; contributions from other cabinets and slots are **unioned, never overwritten**. The cabinet emits redstone while recipes are active. | **16 格（2×8）**，**只收真 `LineSchemeItem`**（纸/剪贴板/镜像/伪造 NBT 一律拒收）。放入即按方案 `recipeId` 在服务端**重推导**本柜贡献，写进 `cpl_converted` 并集并 `/reload`；多柜、多格之间是**并集，互不覆盖**。有生效配方时输出红石信号。 |
| Dismantler / 破拆机 | 2 slots (item / optional scheme). An **unfinished intermediate** (Generic Intermediate + Create's `SEQUENCED_ASSEMBLY` component) is refunded by re-reading the sequence recipe it names: base + exactly the materials the finished steps consumed, plus a read-only mirror. A **finished product** refunds one full inverse batch (consume `count`, refund inputs), resolved server-side. Tags refund their first registered member; nothing is consumed when nothing can be refunded. | 2 格（物品 / 方案可选）。**未完成的中间产物**（通用中间产物 + Create `SEQUENCED_ASSEMBLY` 组件）会按它记录的序列配方**退还基底，外加已完成步骤真正吃掉的原料**，并给一份只读镜像；**成品**按"一次完整产出的逆运算"退款（消耗 count 个、退还输入），配方由服务端解析。tag 退回首个成员；退不出任何东西时不消耗物品。 |
| Generic Intermediate / 通用中间产物 | Sequenced-assembly transitional item (`extends SequencedAssemblyItem`, carries progress component). | 序列装配过渡物（`extends SequencedAssemblyItem`，带进度组件）。 |
| Line Scheme Mirror / 产线方案镜像 | Read-only display snapshot (`LineSchemeMirror` key); structurally contains no executable scheme, so it **cannot be activated or fed back to the dismantler**. | 只读展示快照（`LineSchemeMirror` 键）。结构上不含可执行方案，所以**不可激活、不可复喂破拆机**。 |

## Quick start / 快速使用

**EN**

1. **Compute** — the computer GUI has **3 slots side by side**: `target item / Line Scheme / paper` (left→right). Put the target in slot 1 and a **blank Line Scheme** in slot 2 — that is the carrier the plan is written to, and only a genuine Line Scheme can activate a loader later. Slot 3 is optional and takes **paper**: fill it as well and both carriers receive the same plan. Press **Compute**. The item tooltip then shows `Output: …`, `Recipe: …`, `Base: … (goes on the line first)`, `1. [material] -> Feed`, `2. [material] -> Deployer`… plus `Embedded Create recipes: N`.
2. **Activate** — put the written **Line Scheme** into any slot of a **Scheme Loader** (multi-slot/multi-cabinet). The server re-derives the recipes from `recipeId`, writes the datapack and reloads automatically. Only genuine Line Scheme items activate: paper/clipboard/mirror/forged NBT never do.
3. **Build** (assembly = sequenced assembly) — feed the base first (arm/funnel/chute/drop-in all fine); **one Deployer per extra material** (USE mode, facing DOWN above the belt, holding that material); the product rolls out at the end. A "Generic Intermediate" at the end means the sequence is unfinished / a material is missing.
4. **Dismantle / mirror** — the dismantler refunds materials and produces a read-only mirror.

**中文**

1. **计算**：计算机 GUI 的 3 个槽位**并排**，自左至右是`目标物品 / 产线方案 / 纸`。目标物品放第 1 格，第 2 格放**空白产线方案**——方案就写在这上面，之后也只有真方案能激活加载柜。第 3 格可选放**纸**：也放上则两份载体写入同一份方案。全部放好后点【计算】。完成后物品 tooltip 显示 `目标产物：…`、`来源配方：…`、`基底：…（最先上线…）`、`1. [原料] -> 投料`、`2. [原料] -> 机械手`… 以及 `内嵌 Create 配方：N 条`。
2. **激活**：把写好的**产线方案**放入**方案加载柜**任意格（可多格/多柜）→ 服务端按 `recipeId` 重推导配方写入数据包并自动 `/reload`。加载柜只认真正的产线方案物品：纸/剪贴板/镜像/伪造 NBT 都不会激活。
3. **搭建**（装配类=序列装配）：基底先上带（动力臂/漏斗/溜槽/直接放均可）；**每种追加原料一台机械手**（USE 模式、朝下置于传送带上方、手持对应原料）；跑完 roll 出成品；末端出现"通用中间产物"=序列未完/缺料。
4. **拆解/镜像**：破拆机还原原料并生成只读镜像。

## Recipe conversion rules / 配方转换规则

**EN**

- **Single-machine processes** turn into flat `create:<type>` recipes (crushing/milling/mixing/pressing/cutting…, via the category→facility dictionary).
- A **single-material recipe** of any category (sticks, planks, buttons, smelting …) still gets a real single-machine Create recipe, picked from the material's semantics (`RecipeAnalyzer.machineForMaterial`: wood-ish → saw/`create:cutting`, ore/`raw_*` → crushing wheel, organic → millstone, metal/gem → press, fallback press). Only targets already produced by a native Create process, or recipes with no usable materials, are refused.
- Multi-material recipes with no flat dictionary method (unknown categories, smithing, …) go to `create:mixing`, one mixer for all materials.
- Crafting and assembly (`minecraft:crafting` etc.) go to `create:sequenced_assembly`: `ingredient` = base, `transitional_item` = Generic Intermediate, one `create:deploying` step per extra material, `loops=1`, output count preserved.
- Native Create recipes (`create:mechanical_crafting`, e.g. CBC shells) **additionally** get a non-conflicting `cpl` sequence recipe, with the original left in place; `isConvertibleAssembly` decides whether that happens.
- Tag fidelity: source tags are written verbatim as `{"tag":…}` so any tag member matches. Tooltips show the first member's localized name, display only.
- Plan and embedded recipe share ONE "JSON-ordered, tag-preserving" material list, so base/step mismatches cannot happen (that mismatch was the root cause of the diesel-engine bug).
- The plan mirrors the derived recipe: step layout is generated FROM the derived Create recipe JSON's `type` (`MachineSelector.appendChainSteps`). Sequenced assembly → one Deployer per extra material; flat/mechanical → one station for the machine that really executes the recipe. A step's machine is always the machine that really processes that material; nothing is guessed from list indices.
- Multi-candidate selection: copy/repair/dye recipes (only ingredient == product) are skipped, our own
  `cpl:…` conversions are never re-converted, and the first candidate that actually derives an
  installable entry wins.
- Single-layer direct plans only, no upstream recursion. Self-referencing materials (input == output) appear as ordinary stations, since the recipe still consumes them.
- Assembly mode: `config/create_productionline-mappings.json` → `"assemblyMode": "sequenced"|"mechanical"` (default `sequenced`).
- **24 built-in mappings**: vanilla 6 (crafting/smelting/smoking/blasting/campfire_cooking/stonecutting) + Create 18 (cutting/pressing/milling/crushing/mixing/compacting/deploying/item_application/sandpaper_polishing/mechanical_crafting/haunting/splashing/washing/fan_washing/fan_splashing/fan_haunting/fan_smoking/fan_blasting). You can override or extend them through the same JSON's `categories`, and `lookup()` falls back to the tail key.
- Derivation has one entry point, `recipegen/RecipeDeriver`: `derive()` returns the input order, the output count and the installable entries in a single call, and both the computer and the loader use it. The embedded JSON is only a cache, because the server always trusts `recipeId` + the live `RecipeManager`.

**中文**

- **单机工艺类**转成扁平 `create:<type>` 配方（粉碎/研磨/混合/压片/切割等），机器按映射字典 category→facility 查。
- **单一材料配方**不分类别（木棍、木板、按钮、熔炼都算），一样会给一台真实的 Create 单机配方，机器按材料语义挑（`RecipeAnalyzer.machineForMaterial`：木类→锯/`create:cutting`、矿/`raw_*`→粉碎轮、有机物→石磨、金属/宝石→压片机，兜底压片机）。只有两种情况会拒：目标本身已由 Create 原生工艺产出，或配方里没有可用原料。
- 多种材料、词典里又没有扁平方法的配方（未知类别、锻造等），走 `create:mixing`，一台搅拌机处理全部材料。
- 合成与装配类（`minecraft:crafting` 等）走 `create:sequenced_assembly`：`ingredient`=基底，`transitional_item`=通用中间产物，每个追加原料一个 `create:deploying` 步，`loops=1`，产物 count 保留。
- **原生 Create 配方**（`create:mechanical_crafting`，如 CBC 炮弹）会**额外**生成一份不冲突的 cpl 序列配方，原配方保持不变，判定统一交给 `isConvertibleAssembly`。
- 标签保真：源配方的 tag 原样写成 `{"tag":…}`，同标签下任何成员都能匹配。tooltip 里显示的是标签首个成员的中文名，仅展示用。
- 方案与内嵌配方共用同一份"JSON 保序、保标签"材料列表，所以基底和步骤不会错位（柴油引擎那个 bug 的根因就是错位）。
- 方案是推导配方的镜像：站点布局由推导出的 Create 配方 JSON 的 `type` 生成（`MachineSelector.appendChainSteps`）。序列装配就每种追加原料一台机械手；扁平/机械合成就给执行该配方的机器一个工位。每一步的机器就是真实处理该材料的机器，不按下标猜。
- 多配方候选选择：只含"产物自身"的复制/修复/染色类配方跳过；本模组自己装出的 `cpl:…` 转换配方不再二次转换；优先选能真正推导出可安装条目的候选。
- 只做单层直连，无上游递归。自引用材料（原料==目标）作为普通工位保留，因为配方确实会消耗它。
- 装配模式：`config/create_productionline-mappings.json` → `"assemblyMode": "sequenced"|"mechanical"`（默认 sequenced）。
- **内建映射 24 条**：原版 6（crafting/smelting/smoking/blasting/campfire_cooking/stonecutting）+ Create 18（cutting/pressing/milling/crushing/mixing/compacting/deploying/item_application/sandpaper_polishing/mechanical_crafting/haunting/splashing/washing/fan_washing/fan_splashing/fan_haunting/fan_smoking/fan_blasting）。可用同一 JSON 的 `categories` 覆盖或扩展，`lookup()` 还有"尾键回退"。
- 推导只有一个入口 `recipegen/RecipeDeriver`：`derive()` 一次返回输入顺序、产物 count 和可安装条目，产线计算机与加载柜共用。方案内嵌的 JSON 只是缓存，服务端始终以 `recipeId` + 实时 `RecipeManager` 为准。

## OP anvil flow (hand-authored schemes) / OP 铁砧自定义流程（手写方案）

**EN** — An operator (permission level 2) can hand-author a Line Scheme in an anvil instead of computing one. A scheme that already carries a plan, with `minecraft:paper` on the right, gives a **cleared** copy: every material step and every cached recipe payload is dropped, the target output and its per-craft count stay. The cleared scheme plus one material per operation **hammers** it: that material is appended to an ordered list and the plan, together with its embedded native recipe, is rebuilt from that list. `paper` again **locks** the scheme, and a locked scheme refuses every later anvil operation. Two or more materials produce a `create:sequenced_assembly` line, the first material as the base and every later material as one Deployer step; exactly one material cannot be expressed that way, so the scheme is locked with a `single_material_fallback` flag, derivation picks the semantic single machine instead, and the game says `单原料自定义方案需等待后续版本支持` / "Single-material custom schemes are not supported yet" in the tooltip, once more in the action bar when it locks.

**EN** — Input that cannot be honoured (a stacked scheme, a material before clearing, an empty right slot, a material equal to the product, anything on a locked scheme) is refused outright: nothing is consumed and the item stays as it was. In the end no experience is spent: the vanilla gate needs a positive level cost to let the result be taken at all, so one level is charged and refunded on pickup, which means the net cost is zero but the player still needs at least 1 level to take it. Each operation uses exactly one item from the right slot.

**中文** — OP（权限等级 2）可以不靠计算机，直接在铁砧里手写产线方案。左槽放一份已经带方案的产线方案、右槽放 `minecraft:paper`，得到的是**清空**后的副本：材料步骤和内嵌配方缓存全部丢弃，目标产物和它的单次产出数量保留。之后每放一种原料敲一次即**锤入**：该原料追加进有序列表，方案连同内嵌的原生配方按这份列表重建。再放一次 `paper` 就把方案**锁定**，锁定后任何后续铁砧操作都会被拒绝。两种以上原料生成 `create:sequenced_assembly` 产线，第一种原料上带当基底、之后每种原料一台机械手；只有一种原料时铁砧表达不了序列装配，方案会带 `single_material_fallback` 标记锁定，推导改走单原料语义机器，并在锁定时提示`单原料自定义方案需等待后续版本支持` / "Single-material custom schemes are not supported yet"（tooltip 里常驻一条，锁定时再走一条 action bar 消息）。

**中文** — 输入不合法时（方案叠放、没清空就放原料、右槽为空、原料就是产物本身、对已锁定的方案动手）一律直接拒绝：不消耗任何物品，物品原样保留。经验上净消耗为 0：原版取件门槛要求成本必须大于 0，所以这里收 1 级、取件时再退回，也就是说当时至少要有 1 级才能取走。每次操作只消耗右槽里的一个物品。

## Target output & repeat budget / 目标产量与循环次数

**EN** — The number of items stacked into the computer's **target slot** is the number you want out: the server reads that stack size (clamped to the item's max stack size) and records it as the scheme's target output. One pass through the line still yields **one craft**, because the installed Create recipe stays a single-craft payload — sequenced assembly cannot loop by itself, and baking N crafts' worth of materials into one payload would consume N times the input for one craft's output — so what the scheme records is how often the line has to run, and the loop itself is a belt you build by feeding the product back.

**EN** — The arithmetic (`RepeatPlan`) has two shapes. An ordinary recipe (it does not consume its own product) repeats `ceil(target / per-craft output)` times. A doubling / recursive recipe (`A + B = 2A`, consuming `c` copies of the product and yielding `p > c`) bootstraps from the single unit that goes on the belt, so it repeats `ceil((target - 1) / (p - c))` times, with a net gain of `p - c` per pass. A recipe that cannot grow the stock (`p <= c`) is **unreachable**: the repeat count stays 1 and the game tells you to bring the product yourself, and there is no "loop until it works" mode anywhere. When the line has to run more than once, the plan's topology closes with an explicit instruction line such as `repeat 3x -> 4 Iron Ingot`, and the Scheme Loader reports the largest repeat count in its cabinet from a **server-derived data slot** (not the client's copy of the item), warning `该产线包含 N 次循环组装，请准备充足的基础材料` / "This line runs N times - prepare enough base materials" (a player who opens such a cabinet for the first time also gets that line once in their own action bar; nothing is ever broadcast to chat or to other players). The OP anvil flow inherits these numbers: clearing a scheme copies the compute-time target output and repeat count into the custom scheme, and hammering materials in never resets them.

**中文** — 计算机**目标槽**里叠了几个，就是要产出几个：服务端读取该堆叠数量（按物品最大堆叠数封顶），作为方案的目标产量记下来。一趟产线仍然只出**一次合成**，因为装进去的 Create 配方始终是单次合成载荷——序列装配自己不会循环，而把 N 次合成的材料塞进同一份载荷，等于吃掉 N 倍原料、只出一次产物——所以方案里记的是这条线要跑几趟，物理上的循环要你自己搭：把产物喂回产线。

**中文** — 算术在 `RepeatPlan` 里，分两种形状：普通配方（不消耗自己的产物）重复 `ceil(目标 / 单次产出)` 次；倍增/递归配方（`A + B = 2A`，消耗 `c` 个自身产物、产出 `p > c`）从上带的那 1 个起家，重复 `ceil((目标 - 1) / (p - c))` 次，每趟净增 `p - c`。产物"长不大"的配方（`p <= c`）判为**不可达**：重复次数保持 1，游戏会让你自己带产物来，哪里都没有"跑到成功为止"这条路。需要多趟时，方案拓扑末尾会多一行 `repeat 3x -> 4 Iron Ingot`；加载柜通过**服务端数据槽**（不读客户端手里物品的那份）上报柜内最大的循环次数，并提示`该产线包含 N 次循环组装，请准备充足的基础材料` / "This line runs N times - prepare enough base materials"。铁砧流程会继承这些数字：清空方案时把计算时的目标产量与循环次数抄进自定义方案，之后敲入原料不会重置它们。

## Technical notes (from Create's public sources & observed behaviour) / 关键技术点（依据 Create 公开源码与行为分析）

**EN**

- Sequenced assembly is machine-driven. What decides matching is whether the item carries the `SEQUENCED_ASSEMBLY` DataComponent; each step runs on the machine matching its recipe type (deploy → Deployer etc.); `advance()` returns the transitional + component until the end, where `rollResult()` fires.
- Deployer requirements: USE mode, FACING=DOWN above the belt, rotational power, non-empty hand.
- All three GUI screens skin their slots by **runtime-referencing** Create's `AllGuiTextures` (`client/CreateGui`). No Create assets are bundled.

**中文**

- 序列装配由机器驱动。物品带不带 `SEQUENCED_ASSEMBLY` DataComponent 决定能不能匹配；每步由对应机器执行（deploy→机械手等）；`advance()` 未到终点就返回过渡物+组件，末步触发 `rollResult()`。
- 机械手的前置条件：USE 模式、FACING=DOWN 位于传送带上方、有旋转动力、手持非空。
- 三个界面的槽位皮肤都是**运行时引用** Create 的 `AllGuiTextures`（`client/CreateGui`），不打包 Create 资源。

## Compatibility (loader-declaration friendly) / 兼容性（加载器声明友好）

**EN**

- NeoForge `[21.1.249,)`, MC `[1.21.1]`, Create `[6.0.10,7.0.0)`, JEI optional; `displayTest=IGNORE_SERVER_VERSION`. Bumping the NeoForge patch version only means editing `neo_version` in `gradle.properties`.
- Forge/Fabric and cross-MC versions are **rewrite-scale work** (multi-loader project such as Architectury), and we do not maintain them.
- Tested environments: `1.21.1-create_productionline`, `1.21.1-{Neoforge}` (~100 mods incl. CBC/CDG/superbwarfare).

**中文**

- NeoForge `[21.1.249,)`、MC `[1.21.1]`、Create `[6.0.10,7.0.0)`、JEI optional；`displayTest=IGNORE_SERVER_VERSION`。换 NeoForge 小版本时，只要改 `gradle.properties` 里的 `neo_version`。
- Forge/Fabric 和跨 MC 版本是**重写级工程量**（得做成 Architectury 那样的多加载器工程），我们暂不维护。
- 实测环境：`1.21.1-create_productionline`、`1.21.1-{Neoforge}`（~100 mod，含 CBC/CDG/superbwarfare）。

## Build & install / 构建与安装

```
gradlew build -x neoFormJoined1.21.1-20240808.144430DownloadAssets   # skip asset download offline / 离线跳过资产下载
```

**EN** — Requires **JDK 21**. To check that the sources compile, `gradlew compileJava --offline` is enough; a full `gradlew build` leaves `build/libs/create_productionline-<version>.jar`. Install by dropping that jar into your instance's `mods/` folder. If your network cannot reach a repository, put the proxy in your **user-level** `~/.gradle/gradle.properties`, not in this repo (there is a commented example in that file).

**Version policy / 版本规范** — `gradlew build` cuts the **release** (`mod_version`, `1.0.x`); `gradlew build -PdevBuild` cuts a **dev beta** (`0.0.0-dev.N`, N from `dev-build.txt`, auto-incremented after the jar is written). Only a release version is published as `release`: a dev version defaults to `beta`, and it is rejected only if you force `-PreleaseType=release`. `./gradlew build -PdevBuildNumber=5` reproduces a numbered dev artifact without touching the counter, which is what CI does for a `v0.0.0-dev.N` tag.

**中文** — 需要 **JDK 21**。只想确认能不能编译，跑 `gradlew compileJava --offline` 就够了；完整的 `gradlew build` 会产出 `build/libs/create_productionline-<版本>.jar`。安装就是把 jar 放进实例的 `mods/` 目录。如果网络访问不了仓库，代理请写在**用户级** `~/.gradle/gradle.properties` 里，别写进本仓库（本仓库 `gradle.properties` 有注释示例）。

**版本规范** — `gradlew build` 出**正式版**（取 `mod_version`，形如 `1.0.x`）；`gradlew build -PdevBuild` 出**开发版 beta**（`0.0.0-dev.N`，N 取自 `dev-build.txt`，出包后自动 +1）。只有正式版能以 `release` 类型发布；开发版默认就是 `beta`，只有强行 `-PreleaseType=release` 才会被发布任务拒绝。`gradlew build -PdevBuildNumber=5` 用来复现指定编号的开发包、不动计数器，CI 对 `v0.0.0-dev.N` tag 就是这么构建的。

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
└── qa/ event/ network/            SelfTest (18 headless checks) / events / payloads
```

## Security (multiplayer anti-injection, landed 2026-09-07) / 安全（多人服防注入，2026-09-07 落地）

**EN** — NBT-carrying items (schemes, mirrors) can be forged by modified clients on survival servers. So we rebuilt the mod around one rule: the server is the only authority. All findings below reference current sources, with `file:line` in parentheses.

- **Carrier whitelist.** `ClipboardCompat.isLoaderCarrier` (`compat/ClipboardCompat.java:83`) accepts only genuine `LineSchemeItem`, and both the loader slot (`menu/SchemeLoaderMenu.java:54`) and the BE collector (`block/entity/SchemeLoaderBlockEntity.java:190`) go through it. Paper, clipboards, mirrors and forged NBT are all rejected.
- **Server-side re-derivation in the loader.** Activation never trusts the scheme's embedded JSON/Steps. `SchemeLoaderBlockEntity.currentEntriesMap` (:183) resolves `RecipeId` via `ServerRecipeLookup.findById` (`line/mapper/ServerRecipeLookup.java:43`) to the **live recipe**, then `RecipeDeriver.derive` (`recipegen/RecipeDeriver.java:49`) re-derives the entries to install. A forged scheme can at most activate "a recipe that really exists on the server"; a missing or stale `recipeId` is skipped.
- **Authoritative dismantler refund.** `DismantlerBlockEntity.revert()` (`block/entity/DismantlerBlockEntity.java:66`) validates in order: slot 1 must be a genuine `LineSchemeItem` (:77-79) → slot 0 item id must equal the scheme's `OutputItem` (:124-128) → the `recipeId` must resolve server-side with a matching output (:118-123). A `#tag` in the refund set is materialized as the tag's first registered member, best effort (`materialize`, :194-221); if *nothing* in the set can be materialized, nothing is consumed at all (:162-164). It consumes first — one unit for an unfinished intermediate (:88), a full inverse batch of `count` for a finished product (:144-147) — then refunds, then places the mirror (:169-188). Unresolvable or mismatched → **consume nothing, produce nothing**, which kills the "forge a scheme to print valuable materials" trick.
- **Network entry validation.** `ModPayloads.handleDismantle` (`network/ModPayloads.java:90`) calls `menu.stillValid(player)` before touching any slot.
- **Mirror is text-only.** `LineSchemeMirrorItem` (`item/LineSchemeMirrorItem.java:29,42`) writes only a `LineSchemeMirror` display snapshot (OutputItem/BaseMaterial/Steps text) with no executable `LineScheme`/embedded recipes, and `ClipboardCompat.isCarrier` returns `false` for it (:64), so it can neither be activated nor re-fed.
- The computer generates server-side too, so validation is inherent, and it shares the same algorithm/entry point as the loader.

**中文** — 方案、镜像这类"存 NBT 的物品"，在多人服上会被改包客户端伪造。为此我们按"服务端为唯一权威"重构了一遍（下面每条结论都对应现在的源码，括号里是文件:行）：

- **容器白名单**：`ClipboardCompat.isLoaderCarrier`（`compat/ClipboardCompat.java:83`）只认真 `LineSchemeItem`，加载柜槽位（`menu/SchemeLoaderMenu.java:54`）和 BE 收集（`block/entity/SchemeLoaderBlockEntity.java:190`）都走它。纸、剪贴板、镜像、随便伪造的 NBT 载体，一律拒收。
- **加载柜服务端推导**：激活时不信任方案内嵌的 JSON/Steps。`SchemeLoaderBlockEntity.currentEntriesMap`（:183）按 `RecipeId` 调 `ServerRecipeLookup.findById`（`line/mapper/ServerRecipeLookup.java:43`）取到**实时配方**，再交给 `RecipeDeriver.derive`（`recipegen/RecipeDeriver.java:49`）重新推导该装哪些条目。伪造方案最多只能激活"服务器上真实存在的配方"；方案里没有的、失效的 `recipeId` 直接跳过。
- **破拆机权威退款**：`DismantlerBlockEntity.revert()`（`block/entity/DismantlerBlockEntity.java:66`）按顺序校验：槽 1 必须是真 `LineSchemeItem`（:77-79）→ 槽 0 物品 id 必须等于方案 `OutputItem`（:124-128）→ 方案 `recipeId` 能在服务端解析且产物一致（:118-123）。退还集合里的 `#tag` 会尽力折算成该标签的首个成员（`materialize`，:194-221）；只有当整个集合**一个都折算不出来**时才什么都不消耗（:162-164）。顺序是先消耗——未完成的中间产物消耗 1 个（:88），成品按 `count` 整批消耗（:144-147）——再退还，最后放镜像（:169-188）。解析不到或产物不符 → **不消费不产出**，"伪方案刷贵重原料"这条路就被堵死了。
- **网络入口校验**：`ModPayloads.handleDismantle`（`network/ModPayloads.java:90`）先做 `menu.stillValid(player)`，再触碰任何槽位，所以失效的 GUI 不会被执行。
- **镜像纯文本化**：`LineSchemeMirrorItem`（`item/LineSchemeMirrorItem.java:29,42`）只写 `LineSchemeMirror` 展示快照（OutputItem/BaseMaterial/Steps 文本），结构上不携带可执行 `LineScheme` 和内嵌配方，`ClipboardCompat.isCarrier` 对它直接返回 `false`（:64），因此既不能被激活，也不能复喂。
- 产线计算机本身也是服务端生成，校验天然存在，和加载柜同源同算法。

> **EN** — The same rule covers the compute entry point. The payload carries the real `recipeId`, and the server re-resolves it against its live `RecipeManager`, accepting it **only** when that recipe really produces the item sitting in the target slot. A client hint that cannot be verified yields no plan at all.
> **中文** — 计算入口也守同一条规则。计算包携带真 `recipeId`，服务端在实时 `RecipeManager` 上重新解析，**只有**当该配方确实产出目标槽内的物品时才采纳；验证不通过的客户端数据写不出任何方案。

## Known limits / 已知边界

**EN**

- Sequence lines consume 1 unit of each material per step. That is cheaper than the source grid when a material repeats, and the line still produces output.
- Results carry item id + count only, so recipes with NBT/enchantments/state yield the "plain" variant.
- Multi-level intermediates are not recursed into a single scheme by default. Compute each stage, then activate them together as a union in the 16-slot loader for an end-to-end line.
- Single-material recipes are converted through a semantic single-machine choice (wood → saw, ore → crushing wheel, organic → millstone, metal/gem → press). The machine is a heuristic, not a faithful simulation of the original recipe. The only refusals with "cannot convert" are targets already produced by a native Create process, and recipes with no usable materials.
- The dismantler refunds what it can materialize (`#tag` → first member), and refuses without consuming when nothing can be refunded.
- Recipe types that cannot specify a duration (pressing / splashing / haunting / mixing) get no `processing_time`. Create rejects such files outright, so only milling/crushing/cutting carry it.
- Create `assets/` is All Rights Reserved: this mod only "runtime-references" its GUI/textures and never bundles copies.
- Every texture and icon in this project is original artwork drawn by the author; the mod bundles no third-party assets.

**中文**

- 序列产线每种材料每步耗 1 个。源网格里同一种材料要多个时，这么算反而省料，产出照旧。
- 结果只记 item id+count，所以带 NBT/附魔/状态的产物一律是"素体"。
- 多级中间物默认不递归进单份方案。分几个方案算，再一起放进 16 格柜做并集激活，就能拼出端到端线。
- 单一材料配方走的是"按材料语义挑一台机器"（木→锯、矿→粉碎轮、有机物→石磨、金属/宝石→压片机）。机器是启发式选的，不是原配方的忠实模拟。报"无法转换"的只有两种：目标已由 Create 原生工艺产出，或配方没有可用材料。
- 破拆机只退还它真能具现出来的材料（`#tag` → 首个成员）；完全退不出时不消耗物品。
- 不能指定时长的配方类型（pressing / splashing / haunting / mixing）不写 `processing_time`。Create 会直接拒绝这类文件，所以只有 milling/crushing/cutting 携带该字段。
- Create `assets/` 为 All Rights Reserved：本 mod 只"运行时引用"其 GUI/贴图，不打包复制。
- 本项目的全部贴图与图标都是作者原创手绘，模组不打包任何第三方素材。

## Contributors & funding / 贡献与资助名单

**EN** — This project stays alive because people give both time **and** money. The full list is in
[`CONTRIBUTORS.md`](CONTRIBUTORS.md): **funding supporters first** (the main list), then **technical
support** (the tooling / AI agents that did code analysis, algorithm fixes, hardening, builds and docs),
then code/art/testing contributors, and upstream projects last. Amounts are optional, anonymous entries
are welcome, and any entry can be corrected or removed on request.

**中文** — 本项目靠投入时间和**资金**的人维持。完整名单见 [`CONTRIBUTORS.md`](CONTRIBUTORS.md)：
**资金支持者排在最前（主要名单）**，然后是**技术支持**（承担代码分析、算法修复、加固、构建与文档
的工具/AI 代理），接着是代码/美术/测试贡献者，最后是上游项目。金额可以留空，也可以匿名；
名单里任何一条都能按你的要求更正或删除。

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

> **EN** — `-PselfTest` forwards `create_productionline.selfTest=true` to the GAME JVM. A bare `-D` on the Gradle command line does not reach it. The property is read by `qa/SelfTest.isEnabled()`, and once the server is up the 18 checks run against a **real server** (real registries/NBT/components/`RecipeManager`).
> **中文** — `-PselfTest` 会把 `create_productionline.selfTest=true` 传给**游戏 JVM**；在 Gradle 命令行上直接写 `-D` 传不到游戏进程。该属性由 `qa/SelfTest.isEnabled()` 读取，服务器启动后就对着**真实服务器**跑这 **18 项**检查（真实注册表/NBT/组件/`RecipeManager`）。
> The server halts itself afterwards, but the game process may not exit cleanly. If `:runServer` hangs, kill the game JVM; the task then reports `FAILED` even though the checks passed, so judge by the lines below.
> 自检后服务器会自行 `halt`，但游戏进程有时不会干净退出。若 `:runServer` 卡住，手动结束游戏进程即可；这时任务会显示 `FAILED`，而检查本身已经通过了，以下面的输出为准。

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
[PASS] Custom assembly builds a deployer sequence
[PASS] Single-material custom scheme falls back to one machine
[PASS] Doubling recipe repeats to reach the target output
[PASS] Scheme anvil state machine table
[PASS] Plan reports the material budget
CPL SELF-TEST RESULT: 18 passed, 0 failed
```

> **EN** — **Do not hard-code the count when judging a build.** `qa/SelfTest.java` prints
> `CPL SELF-TEST RESULT: <passed> passed, <failed> failed` (`SelfTest.java:110`), so the pass criterion is
> *the last line matches `\d+ passed, 0 failed`*, never a literal number. The number below is only a
> convenience snapshot, and its value is the count of `check("…")` calls in `qa/SelfTest.java`.
> **中文** — **判一个构建过没过，别把项数写死。** `qa/SelfTest.java` 打印的是
> `CPL SELF-TEST RESULT: <passed> passed, <failed> failed`（`SelfTest.java:110`），判据因此是
> *最后一行匹配 `\d+ passed, 0 failed`*，而不是某个字面数字。下面的数字纯粹是方便阅读的快照，
> 它的值等于 `qa/SelfTest.java` 里 `check("…")` 的调用数。
>
> **EN** — Current snapshot: **18** checks. `Plan topology (chain: base -> machine+material -> product)`
> arrived with dev snapshot `0.0.0-dev.3`; `Tag ingredients kept in flat recipes`, `Duration only on
> duration-capable types`, `Loader accepts written schemes only`, `Self-referential recipes are skipped`,
> `Deriver refuses native/unmappable recipes` and `Single-material recipes map to a semantic machine`
> landed by release 1.0.1; `Custom assembly builds a deployer sequence` and `Single-material custom
> scheme falls back to one machine` came with the 1.0.2 beta anvil flow, and `Doubling recipe repeats to
> reach the target output` came with the same 1.0.2 beta, for the target-output / repeat budget. Adding or
> removing a `check(…)` changes this number and nothing else, apart from the snapshot mentions in this
> README, in `CHANGELOG.md` and in `RELEASING.md`.
> **中文** — 当前快照 **18 项**。其中 `Plan topology (chain: base -> machine+material -> product)` 是随开发快照
> `0.0.0-dev.3` 进来的；`Tag ingredients kept in flat recipes`、`Duration only on duration-capable types`、
> `Loader accepts written schemes only`、`Self-referential recipes are skipped`、
> `Deriver refuses native/unmappable recipes`、`Single-material recipes map to a semantic machine`
> 这六项随正式版 1.0.1 落地；`Custom assembly builds a deployer sequence` 与
> `Single-material custom scheme falls back to one machine` 随 1.0.2 beta 的铁砧流程加入，
> `Doubling recipe repeats to reach the target output` 同样随 1.0.2 beta 加入，对应目标产量/循环次数这部分功能。
> 增删一个 `check(…)` 只会改变这个数字，别的地方不用动，
> 只需要改本 README、`CHANGELOG.md`、`RELEASING.md` 里标注为"快照"的那几处。
>
> **EN** — Historical docs mentioning "5 passed" / "6 passed" / "7 passed" / "9 passed" / "10 passed" describe earlier
> rounds; the `DataPacket action whitelist` case went away with the old architecture. Current code has **18** checks
> and no DataPacket whitelist case.
> **中文** — 历史文档里的 "5 passed" / "6 passed" / "7 passed" / "9 passed" / "10 passed" 是更早几轮的数字；
> `DataPacket action whitelist` 一项随旧架构一起删掉了。当前代码为 **18 项**，也不再有任何 DataPacket 白名单用例。

## Doc↔code consistency baseline (2026-09-13) / 文档—代码一致性核对基线（2026-09-13）

**EN** — Records the "code is the source of truth" line-by-line review, for later reference.

**Corrected in this README**: computer slots changed from "top/bottom" to **3 side by side** (`ProductionComputerMenu.java:44-51`: target 44,20 / carrier 80,20 / clipboard 116,20); loader slot changed to **genuine-scheme-only**; built-in mappings corrected 17 → **24**; added `recipegen/RecipeDeriver`, `compat/ClipboardCompat`, `util/RecipeJsonReader` entries; jar size and source size now measured values. Also corrected later: the old "leftover `debug/` directory" and "`src/generated/` unused" notes (neither directory exists) and every "jar still ships `.bak` / `*_particle.png`" claim (the current jar was inspected and is clean).

**中文** — 这一节记录"以现有代码为准"逐条核对后的结论，方便后续改动拿来对照。

**已按代码改正的本 README 条目**：计算机槽位由"上/下格"改为**3 格并排**（`ProductionComputerMenu.java:44-51`：目标 44,20 / 载体 80,20 / 剪贴板 116,20）；加载柜槽位由"载体槽"改为**只收真方案**；内建映射由 17 条更正为 **24 条**；补上 `recipegen/RecipeDeriver`、`compat/ClipboardCompat`、`util/RecipeJsonReader` 等模块条目；jar 体积和工程规模换成了实测值。后来又改正了两处：删掉"残留空目录 `debug/`"和"`src/generated/` 未启用"（这两个目录都不存在），以及所有"jar 里仍有 `.bak` / `*_particle.png`"的断言（实测过当前 jar，内容干净）。

**Code vs text mismatches still open (to fix; not README errors) / 代码与文案仍不一致的未修项（非本 README 描述错误）**

Stale entries were **deleted, not softened**: any row that no longer matched the code as of this revision was removed rather than reworded. Verified-clean as of this revision, so no longer listed: the language files now describe the 3-slot side-by-side layout, `ClipboardCompat`'s javadoc no longer mentions the removed "controller", `SchemeLoaderMenu` / `SchemeLoaderBlockEntity` javadoc now state the genuine-scheme-only rule, the built jar ships no `.bak` / `*_particle.png` / `debug/` entries, and neither the empty `debug/` directory nor `src/generated/` exists any more. Also fixed since: the dismantler got its own GUI background (no longer the 16-slot loader sheet), and the loader screen's "embedded recipes" count now comes from the server-derived active count instead of the item's cached JSON.

已过时条目一律**删除而非弱化措辞**，凡是与当前代码不符的行就直接删掉。本版核实为干净、因此不再列出的有：语言文件已改成描述 3 格并排布局；`ClipboardCompat` javadoc 不再提已删除的 "controller"；`SchemeLoaderMenu` / `SchemeLoaderBlockEntity` javadoc 已写明"只认真方案"；产物 jar 不含 `.bak` / `*_particle.png` / `debug/` 条目；空目录 `debug/` 与 `src/generated/` 均已不存在。此后又修了两处：破拆机改用专属 GUI 底图，不再复用 16 格加载柜底图；加载柜界面的"内嵌配方数"显示服务端推导出的真实生效数量，不再读物品缓存 JSON。

| Location / 位置 | State / 现状 | Note / 说明 |
| --- | --- | --- |
| `lang` `loader.create_productionline.slots_filled` | hard-codes `%s/16` / 写死 `%s/16` | consistent with `SchemeLoaderBlockEntity.SLOT_COUNT=16` / 与 `SchemeLoaderBlockEntity.SLOT_COUNT=16` 一致，但改容量会漏改 |
