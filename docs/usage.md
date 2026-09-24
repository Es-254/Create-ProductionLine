# 使用 / Usage

> [← 总体 / Overview](../README.md) · 相关： [依赖](dependencies.md) · [构建与安装](build.md) · [配方转换](conversion.md) · [兼容性与边界](compatibility.md) · [安全](security.md) · [无头自检](qa.md)

## Modules / 模块

| Module / 模块 | Description (EN) | 说明（中文） |
| --- | --- | --- |
| Production Computer / 产线计算机 | **3 slots side by side** (`SLOT_TARGET=0` target, `SLOT_SCHEME=1` blank Line Scheme carrier, `SLOT_CLIPBOARD=2` optional paper — that constant name is legacy, clipboards are not accepted). On **Compute** the client scans resource packs and sends parsed recipe JSON; the server re-derives from the live `RecipeManager`, writes a **single-layer direct plan + embedded native Create recipe JSON** onto the carrier, and injects the `custom_data.LineBuildGuide` build guide. | **3 格并排**（`SLOT_TARGET=0` 目标物品 / `SLOT_SCHEME=1` 空白产线方案载体 / `SLOT_CLIPBOARD=2` 可选纸——这个常量名是历史遗留，剪贴板放不进去）。点【计算】时，客户端先扫资源包、把解析出的配方 JSON 交给服务端；服务端拿实时 `RecipeManager` 重新推导，然后把**单层直连方案 + 内嵌 Create 原生配方 JSON**写入载体，同时注入 `custom_data.LineBuildGuide` 施工指引。 |
| Line Scheme / 产线方案 | Carries `LineScheme` NBT (`Version/RecipeId/OutputItem/BaseMaterial/Steps[]` + embedded `CreateRecipes`); activatable by the loader, readable by the dismantler. | 携带 `LineScheme` NBT（`Version/RecipeId/OutputItem/BaseMaterial/Steps[]` + 内嵌 `CreateRecipes`），可被加载柜激活、被破拆机读取。 |
| Scheme Loader / 方案加载柜 | **16 slots (2×8)**, **accepts only genuine `LineSchemeItem`** (paper/clipboard/mirror/forged NBT rejected). On insert it re-derives this cabinet's contribution server-side from each scheme's `recipeId`, writes the `cpl_converted` union and installs it into the running server — the recipe-only refresh, no `/reload`; contributions from other cabinets and slots are **unioned, never overwritten**. The cabinet emits redstone while recipes are active. | **16 格（2×8）**，**只收真 `LineSchemeItem`**（纸/剪贴板/镜像/伪造 NBT 一律拒收）。放入即按方案 `recipeId` 在服务端**重推导**本柜贡献，写进 `cpl_converted` 并集并**只刷新配方**（不跑 `/reload`）；多柜、多格之间是**并集，互不覆盖**。有生效配方时输出红石信号。 |
| Dismantler / 破拆机 | 2 slots (item / optional scheme). An **unfinished intermediate** (Generic Intermediate + Create's `SEQUENCED_ASSEMBLY` component) is refunded by re-reading the sequence recipe it names: base + exactly the materials the finished steps consumed, plus a read-only mirror. A **finished product** refunds one full inverse batch (consume `count`, refund inputs), resolved server-side. Tags refund their first registered member; nothing is consumed when nothing can be refunded. | 2 格（物品 / 方案可选）。**未完成的中间产物**（通用中间产物 + Create `SEQUENCED_ASSEMBLY` 组件）会按它记录的序列配方**退还基底，外加已完成步骤真正吃掉的原料**，并给一份只读镜像；**成品**按"一次完整产出的逆运算"退款（消耗 count 个、退还输入），配方由服务端解析。tag 退回首个成员；退不出任何东西时不消耗物品。 |
| Generic Intermediate / 通用中间产物 | Sequenced-assembly transitional item (`extends SequencedAssemblyItem`, carries progress component). | 序列装配过渡物（`extends SequencedAssemblyItem`，带进度组件）。 |
| Line Scheme Mirror / 产线方案镜像 | Read-only display snapshot (`LineSchemeMirror` key); structurally contains no executable scheme, so it **cannot be activated or fed back to the dismantler**. | 只读展示快照（`LineSchemeMirror` 键）。结构上不含可执行方案，所以**不可激活、不可复喂破拆机**。 |

## Quick start / 快速使用

**EN**

1. **Compute** — the computer GUI has **3 slots side by side**: `target item / Line Scheme / paper` (left→right). Put the target in slot 1 and a **blank Line Scheme** in slot 2 — that is the carrier the plan is written to, and only a genuine Line Scheme can activate a loader later. Slot 3 is optional and takes **paper**: fill it as well and both carriers receive the same plan. Press **Compute**. The item tooltip then shows `Output: …`, `Recipe: …`, `Base: … (goes on the line first)`, `1. [material] -> Feed`, `2. [material] -> Deployer`… plus `Embedded Create recipes: N`.
2. **Activate** — put the written **Line Scheme** into any slot of a **Scheme Loader** (multi-slot/multi-cabinet). The server re-derives the recipes from `recipeId`, writes the datapack and installs them right away — a recipe-only refresh, no `/reload`. Only genuine Line Scheme items activate: paper/clipboard/mirror/forged NBT never do.
3. **Build** — for an assembly recipe the line is a **sequenced assembly**: feed the base first (arm/funnel/chute/drop-in all fine); **one Deployer per extra material** (USE mode, facing DOWN above the belt, holding that material); the product rolls out at the end. A "Generic Intermediate" at the end means the sequence is unfinished / a material is missing.
4. **Dismantle / mirror** — the dismantler refunds materials (dropped beside the machine) and produces a read-only mirror. If it refuses, the reason arrives in your own chat.

**中文**

1. **计算**：计算机 GUI 的 3 个槽位**并排**，自左至右是`目标物品 / 产线方案 / 纸`。目标物品放第 1 格，第 2 格放**空白产线方案**——方案就写在这上面，之后也只有真方案能激活加载柜。第 3 格可选放**纸**：也放上则两份载体写入同一份方案。全部放好后点【计算】。完成后物品 tooltip 显示 `目标产物：…`、`来源配方：…`、`基底：…（最先上线…）`、`1. [原料] -> 投料`、`2. [原料] -> 机械手`… 以及 `内嵌 Create 配方：N 条`。
2. **激活**：把写好的**产线方案**放入**方案加载柜**任意格（可多格/多柜）→ 服务端按 `recipeId` 重推导配方写入数据包并**即时生效**（只刷新配方，不跑 `/reload`）。加载柜只认真正的产线方案物品：纸/剪贴板/镜像/伪造 NBT 都不会激活。
3. **搭建**：装配类配方走**序列装配**——基底先上带（动力臂/漏斗/溜槽/直接放均可）；**每种追加原料一台机械手**（USE 模式、朝下置于传送带上方、手持对应原料）；跑完 roll 出成品；末端出现"通用中间产物"=序列未完/缺料。
4. **拆解/镜像**：破拆机还原原料（掉在机器旁）并生成只读镜像。拒绝拆解时，原因会发到你自己的聊天框。

## Data pack & refresh / 数据包与刷新

**EN** — The generated recipes are written into a real world data pack (`world/datapacks/cpl_converted`,
namespace `cpl`), so they survive a restart and can be read with any text editor. Activating a scheme does
**not** run `/reload`: the recipes this pack owns are parsed and swapped into the running recipe manager
(`RecipeManager.replaceRecipes`), then the clients get the server's normal post-reload sync. A payload the
mod cannot parse in place — a conditional recipe, or a file edited by hand — makes the activation fall back
to a full `/reload`, which is also the only path that discovers a data pack folder the server has not seen
yet.

- `/cpl reload recipes` — permission level 2. Re-reads the recipe JSON of every data pack the server knows
  and installs the result through the recipe reload listener, so NeoForge recipe conditions and vanilla's
  error handling behave exactly as during a reload. Tags, loot tables, advancements and functions are not
  touched. The reply goes to the sender only — the mod never broadcasts chat. Handy after editing a recipe
  file yourself.
- Anything else (tags, loot tables, advancements, functions, a newly added pack folder) still needs the
  vanilla `/reload`.

**中文** — 生成的配方写在真实的世界数据包里（`world/datapacks/cpl_converted`，命名空间 `cpl`），重启后依然
有效，也能用文本编辑器直接查看。激活方案**不跑 `/reload`**：本模组自己那几条配方会被解析并替换进正在运行的配方
管理器（`RecipeManager.replaceRecipes`），随后客户端走原版的重载后同步。若某条配方无法就地解析（带条件、或被手工
改坏），激活会回落到完整 `/reload`——那也是唯一能发现"服务器尚未见过的数据包文件夹"的路径。

- `/cpl reload recipes` —— 权限等级 2。按**配方重载监听器**重读服务器已知的所有数据包的配方 JSON，因此 NeoForge
  的配方条件与原版的报错行为都和 `/reload` 时一致；标签/战利品表/进度/函数一概不动。回复只发给执行者，本模组从不
  广播聊天栏。自己改过配方文件后可用。
- 其余内容（标签、战利品表、进度、函数、新加的数据包文件夹）仍需原版 `/reload`。

## OP anvil flow (hand-authored schemes) / OP 铁砧自定义流程（手写方案）

**EN** — An operator (permission level 2) can hand-author a Line Scheme in an anvil instead of computing one. A scheme that already carries a plan, with `minecraft:paper` on the right, gives a **cleared** copy: every material step and every cached recipe payload is dropped, the target output and its per-craft count stay. From there, hammering the cleared scheme together with one material appends that material to an ordered list, and the plan — with its embedded native recipe — is rebuilt from that list. `paper` again **locks** the scheme, and a locked scheme refuses every later anvil operation. Two or more materials produce a `create:sequenced_assembly` line, the first material as the base and every later material as one Deployer step; exactly one material cannot be expressed that way, so the scheme is locked with a `single_material_fallback` flag, derivation picks the semantic single machine instead, and the game says `单原料自定义方案需等待后续版本支持` / "Single-material custom schemes are not supported yet" in the tooltip, once more in the action bar when it locks.

**EN** — Input that cannot be honoured (a stacked scheme, a material before clearing, an empty right slot, a material equal to the product, anything on a locked scheme) is refused outright: nothing is consumed and the item stays as it was. In the end no experience is spent: the vanilla gate needs a positive level cost to let the result be taken at all, so one level is charged and refunded on pickup, which means the net cost is zero but the player still needs at least 1 level to take it. Each operation uses exactly one item from the right slot.

**中文** — OP（权限等级 2）可以不靠计算机，直接在铁砧里手写产线方案。左槽放一份已经带方案的产线方案、右槽放 `minecraft:paper`，得到的是**清空**后的副本：材料步骤和内嵌配方缓存全部丢弃，目标产物和它的单次产出数量保留。之后每放一种原料敲一次即**锤入**：该原料追加进有序列表，方案连同内嵌的原生配方按这份列表重建。再放一次 `paper` 就把方案**锁定**，锁定后任何后续铁砧操作都会被拒绝。两种以上原料生成 `create:sequenced_assembly` 产线，第一种原料上带当基底、之后每种原料一台机械手；只有一种原料时铁砧表达不了序列装配，方案会带 `single_material_fallback` 标记锁定，推导改走单原料语义机器，并在锁定时提示`单原料自定义方案需等待后续版本支持` / "Single-material custom schemes are not supported yet"（tooltip 里常驻一条，锁定时再走一条 action bar 消息）。

**中文** — 输入不合法时（方案叠放、没清空就放原料、右槽为空、原料就是产物本身、对已锁定的方案动手）一律直接拒绝：不消耗任何物品，物品原样保留。经验上净消耗为 0：原版取件门槛要求成本必须大于 0，所以这里收 1 级、取件时再退回，也就是说当时至少要有 1 级才能取走。每次操作只消耗右槽里的一个物品。

## Target output & repeat budget / 目标产量与循环次数

**EN** — The number of items stacked into the computer's **target slot** is the number you want out: the server reads that stack size (clamped to the item's max stack size) and records it as the scheme's target output. One pass through the line still yields **one craft**, because the installed Create recipe stays a single-craft payload — sequenced assembly cannot loop by itself, and baking N crafts' worth of materials into one payload would consume N times the input for one craft's output — so what the scheme records is how often the line has to run, and the loop itself is a belt you build by feeding the product back.

**EN** — The arithmetic (`RepeatPlan`) has two shapes. An ordinary recipe (it does not consume its own product) repeats `ceil(target / per-craft output)` times. A doubling / recursive recipe (`A + B = 2A`, consuming `c` copies of the product and yielding `p > c`) bootstraps from the single unit that goes on the belt, so it repeats `ceil((target - 1) / (p - c))` times, with a net gain of `p - c` per pass. A recipe that cannot grow the stock (`p <= c`) is **unreachable**: the repeat count stays 1 and the game tells you to bring the product yourself, and there is no "loop until it works" mode anywhere. When the line has to run more than once, the plan's topology closes with an explicit instruction line such as `repeat 3x -> 4 Iron Ingot`, and the Scheme Loader reports the largest repeat count in its cabinet from a **server-derived data slot** (not the client's copy of the item), warning `该产线需循环 N 次，请备足材料` / "Loops N times, stock up" on its panel (a player who opens such a cabinet for the first time also gets that line once in their own action bar; nothing is ever broadcast to chat or to other players). The OP anvil flow inherits these numbers: clearing a scheme copies the compute-time target output and repeat count into the custom scheme, and hammering materials in never resets them. The Production Computer's own result — product, target output / repeat budget, material budget, plan size, embedded recipe count — is written both into its panel and, privately, into the chat of whoever pressed **Compute**, so a long plan is readable after the GUI is closed.

**中文** — 计算机**目标槽**里叠了几个，就是要产出几个：服务端读取该堆叠数量（按物品最大堆叠数封顶），作为方案的目标产量记下来。一趟产线仍然只出**一次合成**，因为装进去的 Create 配方始终是单次合成载荷——序列装配自己不会循环，而把 N 次合成的材料塞进同一份载荷，等于吃掉 N 倍原料、只出一次产物——所以方案里记的是这条线要跑几趟，物理上的循环要你自己搭：把产物喂回产线。

**中文** — 算术在 `RepeatPlan` 里，分两种形状：普通配方（不消耗自己的产物）重复 `ceil(目标 / 单次产出)` 次；倍增/递归配方（`A + B = 2A`，消耗 `c` 个自身产物、产出 `p > c`）从上带的那 1 个起家，重复 `ceil((目标 - 1) / (p - c))` 次，每趟净增 `p - c`。产物"长不大"的配方（`p <= c`）判为**不可达**：重复次数保持 1，游戏会让你自己带产物来，哪里都没有"跑到成功为止"这条路。需要多趟时，方案拓扑末尾会多一行 `repeat 3x -> 4 Iron Ingot`；加载柜通过**服务端数据槽**（不读客户端手里物品的那份）上报柜内最大的循环次数，并在面板上提示 `该产线需循环 N 次，请备足材料` / "Loops N times, stock up"（首次打开这种柜子的玩家还会在自己的动作栏看到一次；任何内容都不会广播到聊天或别的玩家）。铁砧流程会继承这些数字：清空方案时把计算时的目标产量与循环次数抄进自定义方案，之后敲入原料不会重置它们。产线计算机自己的计算输出——目标产物、目标产量/循环次数、材料预算、方案步数、内嵌配方数——既写进面板，也**私聊**发给按下【计算】的那个人，面板关了也还能回看。

> 返回 [总体 / Overview](../README.md)
