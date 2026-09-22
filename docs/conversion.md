# 配方转换 / Recipe conversion

> [← 总体 / Overview](../README.md) · 相关： [使用](usage.md) · [依赖](dependencies.md) · [兼容性与边界](compatibility.md) · [无头自检](qa.md)

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

## Technical notes (from Create's public sources & observed behaviour) / 关键技术点（依据 Create 公开源码与行为分析）

**EN**

- Sequenced assembly is machine-driven. What decides matching is whether the item carries the `SEQUENCED_ASSEMBLY` DataComponent; each step runs on the machine matching its recipe type (deploy → Deployer etc.); `advance()` returns the transitional + component until the end, where `rollResult()` fires.
- Deployer requirements: USE mode, FACING=DOWN above the belt, rotational power, non-empty hand.
- All three GUI screens skin their slots by **runtime-referencing** Create's `AllGuiTextures` (`client/CreateGui`). No Create assets are bundled.

**中文**

- 序列装配由机器驱动。物品带不带 `SEQUENCED_ASSEMBLY` DataComponent 决定能不能匹配；每步由对应机器执行（deploy→机械手等）；`advance()` 未到终点就返回过渡物+组件，末步触发 `rollResult()`。
- 机械手的前置条件：USE 模式、FACING=DOWN 位于传送带上方、有旋转动力、手持非空。
- 三个界面的槽位皮肤都是**运行时引用** Create 的 `AllGuiTextures`（`client/CreateGui`），不打包 Create 资源。

> 返回 [总体 / Overview](../README.md)
