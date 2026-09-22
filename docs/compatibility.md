# 兼容性与边界 / Compatibility & limits

> [← 总体 / Overview](../README.md) · 相关： [使用](usage.md) · [依赖](dependencies.md) · [配方转换](conversion.md) · [安全](security.md) · [无头自检](qa.md)

## Compatibility (loader-declaration friendly) / 兼容性（加载器声明友好）

**EN**

- NeoForge `[21.1.249,)`, MC `[1.21.1]`, Create `[6.0.10,7.0.0)`, JEI optional; `displayTest=IGNORE_SERVER_VERSION`. Bumping the NeoForge patch version only means editing `neo_version` in `gradle.properties`.
- Forge/Fabric and cross-MC versions are **rewrite-scale work** (multi-loader project such as Architectury), and we do not maintain them.
- Tested environments: `1.21.1-create_productionline`, `1.21.1-{Neoforge}` (~100 mods incl. CBC/CDG/superbwarfare).

**中文**

- NeoForge `[21.1.249,)`、MC `[1.21.1]`、Create `[6.0.10,7.0.0)`、JEI optional；`displayTest=IGNORE_SERVER_VERSION`。换 NeoForge 小版本时，只要改 `gradle.properties` 里的 `neo_version`。
- Forge/Fabric 和跨 MC 版本是**重写级工程量**（得做成 Architectury 那样的多加载器工程），我们暂不维护。
- 实测环境：`1.21.1-create_productionline`、`1.21.1-{Neoforge}`（~100 mod，含 CBC/CDG/superbwarfare）。

## Known limits / 已知边界

### Trust model & beta limits / 信任模型与 beta 已知边界

**EN** — A hand-authored scheme is **as powerful as a datapack**: the operator chooses the materials and the per-pass output, so an anvil-built line can declare a yield no real recipe has. That is intended (it is an authoring tool), and it is exactly why every anvil operation is server-authoritative and gated on permission level 2 — hand out OP with that in mind. Also true for this beta: the target output is read from the **target slot's stack size** (there is no numeric widget), there is **no unlock** (a locked scheme is frozen, build a new one), each operation consumes **one** item from the right slot (one material per strike; a stack is not hammered in at once), the anvil only accepts **concrete items** (so a `#tag` cannot be written that way), and a line that has to repeat needs **your own return belt**, because Create's sequenced assembly cannot loop by itself.

**中文** — 手写方案**与数据包同级强大**：OP 自己决定原料与单趟产出，因此铁砧产线可以声明一个真实配方并不存在的产量。这是有意的（它就是给作者用的工具），也正是每一次铁砧操作都必须服务端权威、且卡在权限等级 2 的原因——发 OP 时请按这个信任模型考虑。这个 beta 还要记住几点：目标产量取自**目标槽的堆叠数量**（没有数字输入框）；**没有解锁**（锁定即冻结，要改就重做一份）；每次操作只消耗右槽**一个**物品（一次一击一种原料，整叠不会一次砸入）；铁砧只认**具体物品**（`#tag` 写不进去）；需要多趟的产线要**你自己搭回环传送带**——Create 的序列装配自己不会循环。

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

> 发布流程里也记着同一份 OP 信任模型 / the same OP trust model is recorded in [`../RELEASING.md`](../RELEASING.md).

> 返回 [总体 / Overview](../README.md)
