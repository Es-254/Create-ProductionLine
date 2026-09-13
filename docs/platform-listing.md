# Create: Production Line / 机械动力：产业线

**EN** — A Create addon for **Minecraft 1.21.1 / NeoForge** that converts recipes from *any* source
(vanilla, Create, other mods) into **native Create recipe JSON** at runtime, installs them into a
world datapack and reloads it — so the target item can actually be produced by a real Create
production line you build in the world.

**中文** — 面向 **Minecraft 1.21.1 / NeoForge** 的 Create 附属模组：把"任意来源配方"
（原版 / Create / 其它 mod）在游戏内实时转成 **Create 原生配方 JSON**，写入世界数据包并 `/reload`，
让目标物品能沿一条你自己搭出来的真实 Create 产线被生产出来。

## Why / 它解决什么问题

**EN** — Create's own processing covers only part of the vanilla item set. Every other mod adds its
own machines and its own recipes, so a modpack's items often sit behind a slow, manual machine
step. This mod closes that gap: point it at any item, and it tells you how to build a Create line
that produces it — and installs the recipes that line needs.

**中文** — Create 原有的加工只覆盖原版物品的一部分，而其它 mod 各有一套机器与配方，
整合包里很多东西只能靠又慢又手动的一台机器产出。本模组补上这一环：指定任意物品，
它给出用 Create 设施生产它的产线方案，并把这条产线需要的配方装上。

## Features / 功能

**EN**

- **Production Computer** — 3 slots (target / carrier / clipboard). Press **Compute**: the recipe is
  resolved and turned into an ordered machine plan, written onto a carrier (paper, clipboard or
  Line Scheme) together with a generated native Create recipe and a build guide tooltip.
- **Scheme Loader** — a 16-slot cabinet. Insert written Line Schemes and their recipes are installed
  into the world datapack and reloaded. Multiple cabinets and multiple schemes **combine (union)**,
  so multi-stage lines work. Emits a redstone signal while recipes are active.
- **Dismantler** — reverts a Generic Intermediate or a finished product back into its raw materials
  and leaves a read-only Line Scheme Mirror.
- **Line Scheme / Line Scheme Mirror / Generic Intermediate** — the plan item, a text-only
  read-only copy, and the transitional item used by generated Sequenced Assembly recipes.
- **Genuine recipe conversion, not a fake grid** — machine processes become flat `create:<type>`
  recipes (crushing, milling, mixing, pressing, cutting, …); crafting/assembly becomes
  `create:sequenced_assembly` with one Deployer station per extra material; native
  `create:mechanical_crafting` recipes additionally get a non-conflicting sequenced-assembly
  variant.
- **Tag fidelity** — source recipe tags are written back as `{"tag": …}`, so *any* member of the tag
  matches. This is what makes tag-based recipes (e.g. 48-round rifle ammo) actually craftable.
- **Extensible mapping** — 24 built-in category mappings plus your own entries in
  `config/create_productionline-mappings.json`, which also selects the assembly mode.
- **Honest failure** — a recipe that cannot be mapped reports "cannot map" instead of inventing a
  plan.

**中文**

- **产线计算机** — 3 格（目标 / 载体 / 剪贴板）。点【计算】：解析配方并排成有序的机器方案，
  连同生成好的 Create 原生配方与施工指引一并写到载体（纸 / 剪贴板 / 产线方案）上。
- **方案加载柜** — 16 格柜子。放入写好的产线方案，配方即写入世界数据包并 reload。
  **多柜、多方案并集**，因此多段产线可以同时生效；配方生效时输出红石信号。
- **破拆机** — 把通用中间产物或成品还原成原料，并留下一面只读的产线方案镜像。
- **产线方案 / 镜像 / 通用中间产物** — 方案物品、纯文本只读副本，以及序列装配用的过渡物。
- **真配方转换，不是假网格** — 单机工艺转成扁平 `create:<type>`（粉碎 / 研磨 / 混合 / 压片 / 切割…）；
  合成装配转成 `create:sequenced_assembly`，每个追加原料一台机械手；原生
  `create:mechanical_crafting` 额外生成一份不冲突的序列配方。
- **标签保真** — 源配方的 tag 原样写回 `{"tag": …}`，标签下**任何**成员都能匹配；
  这是"按标签写的配方"（如 48 发步枪弹药）能真正合成的关键。
- **映射可扩展** — 24 条内建类别映射，并可在 `config/create_productionline-mappings.json` 中自行覆盖，
  该文件同时决定装配模式。
- **如实报错** — 无法映射的配方明确提示"无法映射"，绝不硬凑一个方案。

## How to use / 使用步骤

**EN**

1. Craft the Production Computer, put the item you want in the **left** slot and paper / a clipboard
   / a blank Line Scheme in the **middle** and **right** slots. Press **Compute**.
2. Put the written **Line Scheme** into a **Scheme Loader**. Its recipes are installed and reloaded
   automatically (place several schemes for multi-stage lines).
3. Build the line exactly as the tooltip / plan describes.
   For assembly recipes this is Create's **Sequenced Assembly**: the base material goes onto a belt
   first, then **one Deployer per extra material** above the belt (USE mode, facing down, powered,
   holding the material).
4. Pull the finished product off the end. If a **Generic Intermediate** comes out instead, the
   sequence is not finished or a material is missing.
5. Use the **Dismantler** to turn an intermediate or product back into materials.

**中文**

1. 合成产线计算机，把要生产的物品放**左**格，纸 / 剪贴板 / 空白产线方案放**中**、**右**格，点【计算】。
2. 把写好的**产线方案**放进**方案加载柜**，配方会自动写入并 reload（多段产线就多放几份方案）。
3. 按提示 / 方案把产线搭出来。装配类配方即 Create 的**序列装配**：基底先上带，
   然后**每种追加原料一台机械手**置于传送带上方（USE 模式、朝下、有动力、手持该原料）。
4. 从末端取出成品。若出来的是**通用中间产物**，说明序列没跑完或缺料。
5. 用**破拆机**把中间产物或成品还原成原料。

## Requirements / 前置

- **Minecraft 1.21.1**
- **NeoForge** 21.1.249 or newer
- **Create** 6.0.10 or newer — **required**
- JEI 19.x — *optional*, only as a recipe viewer; this mod does not call JEI's API

## Multiplayer & safety / 多人服与安全

**EN** — The server is the only authority. Nothing stored on an item and nothing sent by a client is
trusted: the Scheme Loader accepts only genuine Line Schemes and re-derives every recipe from the
live server recipe manager; the Dismantler refuses to consume unless it can verify the scheme, the
item and the recipe server-side; and the compute request carries a real recipe id that the server
re-resolves and checks against the target slot. Forged items or forged network data can therefore
never inject recipes into the world datapack.

**中文** — 服务端是唯一权威。存在物品上的 NBT 与客户端发来的数据都不被信任：
加载柜只认真产线方案，并按方案里的 recipeId 在服务端实时配方表上重新推导；
破拆机在能于服务端核实方案、物品与配方之前绝不消耗；计算请求携带真 recipeId，
服务端会重新解析并核对目标槽。伪造物品或伪造网络数据都无法向世界数据包注入配方。

## Known limits / 已知边界

**EN**

- Sequence lines consume 1 unit of each material per step, but still produce output.
- Results carry item id + count only — recipes with NBT/enchantments/state yield the plain variant.
- Multi-level intermediates are not recursed into one scheme: compute each stage and activate them
  together as a union.
- The Dismantler refuses `#tag` inputs or recipes it cannot resolve server-side (refuses rather than
  swallowing items).
- Create's assets are all rights reserved — this mod only runtime-references its GUI/textures and
  never bundles copies.

**中文**

- 序列产线每种材料每步消耗 1 个，但仍能产出。
- 结果只有物品 id + 数量，带 NBT / 附魔 / 状态的产物为素体。
- 多级中间物不递归进单份方案：分段计算、并用加载柜并集一起激活。
- 破拆机拒绝含 `#tag` 输入或服务端无法解析的配方（宁拒不吞物）。
- Create 的素材为保留所有权利：本模组仅在运行时引用其 GUI / 贴图，不打包任何副本。

## Links / 链接

- Modrinth: https://modrinth.com/project/createproductionline
- Changelog / 更新日志: see `CHANGELOG.md` in the downloaded jar's project page (Modrinth → Versions)
- Source / 源码: not published yet — coming later
