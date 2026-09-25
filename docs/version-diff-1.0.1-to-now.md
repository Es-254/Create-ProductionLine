# 与 1.0.1 的版本对比 / Every local build against 1.0.1

> [← 总体 / Overview](../README.md) · 相关： [更新日志](../CHANGELOG.md) · [发布流程](../RELEASING.md) · [构建与安装](build.md) · [无头自检](qa.md)
>
> 实机走查用的验收清单不在仓库里，而在工作区 `文档\1.0.3-实机验收清单.md`（本地文件，clone 不到）／ the
> in-game acceptance checklist is **not** in this repository: it lives in the author's workspace at
> `文档\1.0.3-实机验收清单.md`.

## 这份文件回答什么 / What this page is

**EN** — Everything that happened between the last stable release **`1.0.1`** and the working tree as it
stands now: the 13 jars sitting in `build/libs`, the git tag (if any) each one corresponds to, and what
changed in each one relative to `1.0.1`. `1.0.1` is the baseline because it is the last cut that is a
**finished release**; `1.0.2` was promoted to a release after it, and everything since then is either a
snapshot (`1.0.3-snapshot.*`) or a dev build (`0.0.0-dev.N`).

**中文** — 这里记录**上一个正式版 `1.0.1`** 到当前工作区之间的全部变化：`build/libs` 里那 13 个 jar 分别
对应哪个 git tag、以及每一个相对 `1.0.1` 改了什么。以 `1.0.1` 为基准，是因为它是**最后一个"已完成"的正式版**；
它之后的 `1.0.2` 是当天由 beta 晋升的正式版，再往后要么是快照（`1.0.3-snapshot.*`），要么是开发构建
（`0.0.0-dev.N`）。

> 三个词先分清 / three words first：**release** = 已完成的正式版；**snapshot** = 朝某个正式版推进中的
> 预览（`x.y.z-snapshot.0.0.N`）；**dev** = 本地开发构建（`0.0.0-dev.N`，由 `-PdevBuild` 从 `dev-build.txt` 取号）。

## 一、一句话结论 / The one-paragraph summary

**EN** — `1.0.1` is a 47-file / 6,908-line mod with a 13-check self test. Everything since adds the **OP
anvil flow** and the **target-output & repeat budget** (1.0.2), a **recipe-only refresh that no longer runs
`/reload`** (`1.0.3-snapshot.0.0.1`), the **redraw of all three machines plus their front-end logic**
(dev.5 … dev.8), a **GUI layout pass with the computer reporting into chat** (dev.9), a **dismantler decision
table — erase a scheme, make a doubling refund a true inverse, name skipped fluids** (dev.10), a
**component-keyed dismantle plus used-material marking** (dev.11), **retired recipes instead of deleted
ones** (dev.12), and **Ponder scenes** (dev.13/dev.14, in the working tree and not documented in the changelog
yet). The source is now 62 files / 10,231 lines with a 23-check self test, and `1.0.3` has still not been cut:
`mod_version` is `1.0.3-snapshot.0.0.2`. The two newest jars are both local; `dev.13` was tagged and published
retroactively, `dev.14` is the one build that is neither.

**中文** — `1.0.1` 是一个 47 个 Java 文件 / 6,908 行、自检 13 项的模组。此后依次加入：**OP 铁砧自定义方案**
与**目标产量/循环预算**（1.0.2）、**只刷新配方、不再跑 `/reload`**（`1.0.3-snapshot.0.0.1`）、
**三台机器整体重绘＋随之而来的前端逻辑**（dev.5…dev.8）、**GUI 版式对齐＋计算机结果私聊**（dev.9）、
**破拆机决策表：拆方案、倍增退款改真逆运算、流体告知**（dev.10）、**按组件识别中间产物＋"仅使用不消耗"标记**（dev.11）、
**退役配方而非删除**（dev.12）、以及 **Ponder 场景**（dev.13/dev.14，仍在工作区，CHANGELOG 尚未收录）。
源码现在是 62 个文件 / 10,231 行、自检 23 项。`1.0.3` **仍未收线**：`mod_version` 停在 `1.0.3-snapshot.0.0.2`；
最新两个包都只在本机——`dev.13` 已补打 tag 并补发，`dev.14` 是唯一既没 tag 也没发的那一版。

## 二、13 个本地 jar ↔ 提交 / tag 对照 / The 13 local jars, mapped

`build/libs` 里现有 13 个 jar（数字均为本机实测 / all numbers measured on this machine）：

| # | jar（`build/libs/`） | 字节 / B | `sha256`（前 8 位） | 对应提交 / commit | tag | 发布 / published |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `create_productionline-1.0.2.jar` | 226,351 | `38fcbb9e…` | `55282f5`（`v1.0.2`） | `v1.0.2` | 已是正式版；**本机这份字节与线上包不同**（线上 215,411 B / `27e0a3c6…`），见 §六 |
| 2 | `create_productionline-1.0.3-snapshot.0.0.1.jar` | 248,895 | `d43a9380…` | `b4edfcf`（`v1.0.3-snapshot.0.0.1`） | `v1.0.3-snapshot.0.0.1` | 已发（2026-09-20，三平台）：GitHub pre-release 资产 / Modrinth `pLbPKG48`（Alpha）/ CurseForge `8947278`（alpha）；线上资产 226,361 B / `aaf8e9e2…`，与本机这份不同 |
| 3 | `create_productionline-0.0.0-dev.5.jar` | 228,185 | `14cb64ea…` | `315ab79`（`v0.0.0-dev.5`） | `v0.0.0-dev.5` | 已发（仅 GitHub）；与线上资产逐字节一致 |
| 4 | `create_productionline-0.0.0-dev.7.jar` | 248,889 | `fc006e28…` | `7e1f846`（`v0.0.0-dev.7`） | `v0.0.0-dev.7` | 已补发（2026-09-25，仅 GitHub，pre-release） |
| 5 | `create_productionline-0.0.0-dev.6.jar` | 248,890 | `65815583…` | `d65f4e8`（`v0.0.0-dev.6`） | `v0.0.0-dev.6` | 已发（仅 GitHub）；与线上资产逐字节一致 |
| 6 | `create_productionline-0.0.0-dev.8.jar` | 248,897 | `d29c1baa…` | `267394c`（`v0.0.0-dev.8`） | `v0.0.0-dev.8` | 已补发（2026-09-25，仅 GitHub） |
| 7 | `create_productionline-0.0.0-dev.9.jar` | 254,760 | `da484ab2…` | `92f2d58`（`v0.0.0-dev.9`） | `v0.0.0-dev.9` | 已补发（2026-09-25，仅 GitHub） |
| 8 | `create_productionline-0.0.0-dev.10.jar` | 260,279 | `610396ff…` | `becb2ae`（`v0.0.0-dev.10`） | `v0.0.0-dev.10` | 已补发（2026-09-25，仅 GitHub） |
| 9 | `create_productionline-0.0.0-dev.11.jar` | 265,079 | `c0f47310…` | `86270fc`（`v0.0.0-dev.11`） | `v0.0.0-dev.11` | 已补发（2026-09-25，仅 GitHub） |
| 10 | `create_productionline-0.0.0-dev.12.jar` | 266,636 | `b7417af9…` | `00be949`（`v0.0.0-dev.12`） | `v0.0.0-dev.12` | 已补发（2026-09-25，仅 GitHub） |
| 11 | `create_productionline-1.0.3-snapshot.0.0.2.jar` | 261,785 | `a1f9bfef…` | `318ac5e`（`v1.0.3-snapshot.0.0.2`） | `v1.0.3-snapshot.0.0.2` | 已发（2026-09-25）：GitHub pre-release + CurseForge `8969678`（alpha）；Modrinth 仍只有 `snapshot.0.0.1`。与线上资产逐字节一致 |
| 12 | `create_productionline-0.0.0-dev.13.jar` | 281,600 | `e806b770…` | `57997d1`（`v0.0.0-dev.13`） | `v0.0.0-dev.13` | 已补发（2026-09-25，仅 GitHub）；该版**缺 Ponder 结构文件**，场景会显示空世界 |
| 13 | `create_productionline-0.0.0-dev.14.jar` | 283,701 | `d81c4035…` | `0169c68`（`HEAD`） | **无** | **未发**：Ponder 结构文件＋tag 分组的修复版，也是作者还没实机看过的那一版 |

> **EN** — Rows 12 and 13 are an inference from the jars' own contents and the tag message, not from a build
> log (no build log ties a dev counter to a commit): the `dev.13` jar lacks the three ponder `.nbt` assets and
> `dev.14` has them, and the `v0.0.0-dev.13` tag is annotated *"Ponder scenes, first cut (known defect:
> schematics missing)"*. For rows 3–12 the sha256 in the table matches the digest `RELEASING.md` records for
> the published asset, i.e. those jars are the published bytes.
> **中文** — 第 12、13 行是**从 jar 内容与 tag 说明推断**的，不是构建日志（没有日志把 dev 编号绑到提交上）：
> `dev.13` 没有那三个 ponder `.nbt`，`dev.14` 有；`v0.0.0-dev.13` 的 tag 说明写着"Ponder 第一版（已知缺陷：
> 缺 schematic）"。第 3–12 行的 sha256 与 `RELEASING.md` 记录的线上资产摘要一致，即这些包就是线上那份字节。

> **EN** — The dev jars are numbered from `dev-build.txt`, not from `mod_version`: that is why jars 3–10
> still declare `mod_version=1.0.3-snapshot.0.0.1` in `gradle.properties` while the jar itself reports
> `0.0.0-dev.N`. `build.gradle` reads the number at build time (`-PdevBuild`) and advances the file
> afterwards. ／ **中文** — dev 包的编号来自 `dev-build.txt`，不是 `mod_version`：所以 3–10 号 jar 构建时
> `gradle.properties` 里写的还是 `1.0.3-snapshot.0.0.1`，而包内版本号是 `0.0.0-dev.N`——`build.gradle`
> 构建时读取该文件取号并在出包后自增。

## 三、相对 1.0.1 的分版差异 / Version-by-version, from 1.0.1

### 3.1 `1.0.2` — OP 铁砧流程＋目标产量/循环预算

**EN** — 17 commits, 31 files, +2,044 / −372; 7 new classes, 1 new script. The big one is a second way to
author a scheme: an operator hand-builds one in an anvil (clear → hammer one material per operation → lock),
with a pure state machine (`SchemeAnvilMachine`) and a component (`cpl:custom_assembly`) instead of ad-hoc NBT.
The other half is arithmetic: the target slot's **stack size** becomes the target output, `RepeatPlan` computes
how many passes that needs (`ceil(target / per-craft)` normally; `ceil((target − 1) / (p − c))` for a doubling
recipe; a recipe that cannot grow its stock is reported unreachable), and both the scheme tooltip and the loader
panel show the same numbers.

**中文** — 17 个提交、31 个文件、+2,044 / −372，新增 7 个类与 1 个发布脚本。大头是**第二条写方案的路径**：
OP 在铁砧里手写（清空 → 每次一击砸入一种原料 → 锁定），状态机抽成纯函数 `SchemeAnvilMachine`，手写状态
存进注册过的数据组件 `cpl:custom_assembly` 而不是临时 NBT。另一半是算术：目标槽的**堆叠数量**即目标产量，
`RepeatPlan` 算出要跑几趟（普通配方 `ceil(目标 / 单次产出)`；倍增配方 `ceil((目标 − 1) / (p − c))`；
无法增长库存的配方直接报"不可达"），方案 tooltip 与加载柜面板显示同一组数字。

- 新增 / added：`event/AnvilSchemeCustomizer.java`、`line/scheme/CustomAssembly.java`、
  `line/scheme/CustomAssemblyPlanner.java`、`line/scheme/RepeatPlan.java`、`line/scheme/SchemeAnvilMachine.java`、
  `registry/ModDataComponents.java`、`scripts/publish-curseforge.ps1`
- 关键改动 / key changes：`LineScheme` 增加 `TargetOutputCount` / `RepeatCount`（方案格式 **V2**，V1 旧物按 1/1 读）；
  加载柜用**服务端推导的数据槽**报告最大循环次数，首次打开时给该玩家一行 action bar 提示（全程不广播）；
  单原料手写方案走 `single_material_fallback`（语义选机：木→锯/切、矿与 `raw_*`→粉碎轮、有机→磨石、金属宝石→压床）；
  铁砧操作零经验净消耗、每次只吃右槽 1 个物品（`setCost(1)` / `setMaterialCost(1)`）
- 自检 / self test：**13 → 18 项**（铁砧状态表 12 行、目标产量与循环预算、装配载荷形状、单原料回退）

### 3.2 `1.0.3-snapshot.0.0.1` — 只刷新配方（性能）

**EN** — 8 commits, 15 files, +662 / −107. Activating a scheme used to run a full `/reload` — every data
pack, tag, loot table, advancement and function — for a change that only touches recipes. Now the pack's own
recipe files are parsed and swapped straight into the live `RecipeManager` (`RecipeHotSwap.applyOwned`, public
`RecipeManager.replaceRecipes`), followed by the server's own post-reload sync; a payload that cannot be parsed
in place refuses the swap and falls back to the full reload. A new `/cpl reload recipes` (permission 2, reply
only to the caller) re-reads just the recipes of every known data pack.

**中文** — 8 个提交、15 个文件、+662 / −107。原先加载柜激活方案会跑一次完整 `/reload`（所有数据包、标签、
战利品表、进度、函数），而改动其实只涉及配方。现在本包自己的配方文件被就地解析并直接换进活的
`RecipeManager`（`RecipeHotSwap.applyOwned`，走公开 API `RecipeManager.replaceRecipes`），随后复用服务端
自身的重载后同步；无法就地解析的载荷（条件配方、手改文件）会拒绝换入并回落整包 `/reload`。新增
`/cpl reload recipes`（权限 2，只回执执行者）只重读所有已知数据包的配方。

- 新增 / added：`recipegen/RecipeHotSwap.java`、`event/CommandEvents.java`
- 实测 / measured：测试服 2,819 条配方仅配方重载 **~250 ms**
- 自检 / self test：**18 → 20 项**（新文件被拾取、本包载荷在数据包 id 下往返、条件载荷被拒）

### 3.3 `0.0.0-dev.5` → `0.0.0-dev.8` — 三台机器重绘＋加载柜进度条

四台包共享同一件美术工作：三台方块从 16×16 四面立方换成 Blockbench 模型，并顺带补齐导出件的三处通病。

| 包 / build | 提交 / commits | 内容 / what |
| --- | --- | --- |
| `dev.5` | 16 | **产线计算机重绘**：16 元素（底板/顶板/四角立柱/前面板含屏幕与键盘）+ 4 贴图；导出件三处修复（贴图路径补命名空间、7 处 `#missing` 改指 shell、补回 `minecraft:block/block` 父模型）；方块补 `.noOcclusion()`（敞口机箱非实心立方）；删 5 张旧面贴图。同批还有**文档重构**：README 拆成总体＋`docs/` 七份专题。实测验收：2026-09-24 两个实例实装表现正常 |
| `dev.6` | 6 | **方案加载柜重绘**（14 元素）+ **进度条联动**：`SchemeLoaderBlock.FILL`（0–16）镜像已加载方案数，7 个阶段模型按 `ceil(count × 6 / 16)` 点亮，`setBlock(…, 2)` 只同步客户端、无广播、无自定义渲染器；计数用与槽位校验同一白名单（纸/镜像/伪造 NBT 点不亮）。共用贴图改名 `production_computer_shell` → `production_block_shell` |
| `dev.7` | 2（`f5f086b` 破拆机重绘 + `7e1f846` 计数器） | **破拆机重绘**：18 元素 + 12 张部件贴图 + 共用 shell；补命名空间与父模型、补 `.noOcclusion()`；**新增逐面像素采样校验**（108 个面全部不透明、颜色与部件相符）。这一版**尚未**做同表末行的贴图拼写规范化——`dev.7` 的 tag 就落在计数器提交上，所以它能被 `-PdevBuildNumber=7` 逐字节重建 |
| `dev.8` | 4 | **进度条方向修正**：导出件里 `bar_1` 在 x=13（最右）、`bar_6` 在 x=3，即条本就该**从右向左**增长；上一轮按左→右规整，方向正好相反。现改为段序右→左、第 N 阶段保留最右 N 段，fill 公式不变。**方向正反是无头校验唯一抓不到的东西**，实机才发现 |
| `dev.6`（收尾）/ `dev.7`（被并） | 1 | **贴图拼写规范化**：`loader_sceen` → `loader_screen`、`hoder{1..4,b}` → `holder{1..4,b}`、`basebord` → `baseboard`；8 个模型引用同步；旧 4 张面贴图退役 |

- 资源实测 / measured：Java 文件 55 → 55（美术批次几乎不改 Java），方块资源 32 → 50，模型新增 6 个进度条阶段
- 发布 / published：`dev.5`、`dev.6` 先后发到 GitHub（仅 GitHub，无 Modrinth / CurseForge）；`dev.7`、`dev.8`
  当时只在本机，2026-09-25 与 `dev.9…dev.13` 一起**补打 tag 补发**（每个 tag 都放在能用
  `-PdevBuildNumber=N` 逐字节重建出该 jar 的提交上，发完再核对线上资产摘要），Release 正文是手写的——
  这些提交早于 CHANGELOG 对应段落，属 `RELEASING.md` 记录在案的例外

### 3.4 `0.0.0-dev.9` — GUI 版式对齐＋计算机结果私聊（自检 21）

**EN** — Every panel had been laid out by hand against hand-drawn backgrounds, and all three were wrong:
the loader's 2×8 grid sat flush left inside its well (18 px hole on the right) with its first status line
printed *on* the well's bottom edge; the dismantler's two slots hugged the ends of their well and its hint
text ran straight across the well and both slots at y=40; the computer's slots were 2 px high and its status
list could grow a fifth row onto the player-inventory groove. All geometry moved into `menu/GuiLayout`, read by
both the menus and the screens, and the self test now asserts the invariants. In the same batch: the
dismantler's `revert()` returns a typed result so a refusal answers the player privately, and the computer's
four status rows are also sent to chat (built once in `menu/ComputerStatus`, used by panel and server).

**中文** — 三块手绘底图上的内容此前都是凭手摆的，三台全错：加载柜 2×8 栅格贴死在凹槽左侧（右侧空 18 px），
第一行状态文字**压在**凹槽底边上；破拆机两个槽贴死凹槽两端、提示文字 y=40 横穿凹槽与两槽；计算机槽位只有
2 px 高，状态列表能长到第 5 行压上玩家背包分隔线。几何全部收进 `menu/GuiLayout`，菜单与界面共用，自检新增
不变量断言。同批：破拆机 `revert()` 改为返回类型化结果，被拒时私聊原因；计算机的四行状态同时以聊天行发给
按【计算】的玩家（`menu/ComputerStatus` 一处构建，面板与服务器共用）。

- 新增 / added：`menu/GuiLayout.java`、`menu/ComputerStatus.java`；破拆机 `RevertResult` 7 态
- 自检 / self test：**20 → 21 项**（`GUI layout fits the drawn wells`）

### 3.5 `0.0.0-dev.10` — 破拆机功能补齐（自检 22）

**EN** — A scheme costs nothing to author (the computer only writes onto a carrier), so a scheme that is no
longer wanted should not be a dead item: put it in the item slot and press **Dismantle** → the plan is erased
and a **fresh blank scheme** takes its place. A doubling recipe's refund became a true inverse — `1 A + 1 B = 2 A`
used to consume `2 A` and return only `B`, eating an `A`; the product now stays in the refund list. Fluid
ingredients can never be refunded (a fluid is not an item) and are now **named** instead of vanishing silently
(`另有 N 项流体原料无法退还`), while a pure-fluid recipe still refuses outright. The refund is deliberately a
**set of unique materials, one each** (`2 planks -> 4 sticks` hands back a single plank); what prevents farming
is the batch rule (`consume >= count`), now documented in [usage](usage.md) and [security](security.md).

**中文** — 写方案不消耗原料（计算机只是往载体上写字），所以不再想要的方案不该变成死物：放进物品槽按
**【拆解】**，方案被清空并返还一份**全新空白方案**。倍增配方的退款改成真逆运算——`1 A + 1 B = 2 A` 原先
消耗 `2 A` 只退 `B`（净吞一个 A），现在产物那份留在退款集合里。流体原料永远退不回来（流体不是物品），
现在会**明说**（`另有 N 项流体原料无法退还`），纯流体配方仍旧直接拒绝。退款刻意是**去重集合、每种一个**
（`2 木板 → 4 木棍` 只退 1 块木板）；防刷靠整批消耗规则（`consume >= count`），已在
[usage](usage.md) 与 [security](security.md) 写明。

- 返回类型 / types：`RevertOutcome(result, fluidsSkipped)`，`RevertResult` 增至 11 态（含 `SCHEME_ERASED` /
  `SCHEME_ALREADY_BLANK` / `MIRROR_READ_ONLY`）
- 自检 / self test：**21 → 22 项**（`Dismantler decision table, doubling refund, fluid notice`，真放机器、
  真跑 `1 A + 1 B = 2 A`、数掉落物）

### 3.6 `0.0.0-dev.11` — 中间产物按组件识别＋"仅使用不消耗"（自检 23）

**EN** — Intermediates could not be dismantled at all: the branch keyed on *our* item (`Generic Intermediate`),
so every Create-native transitional item — whose provenance is the `SEQUENCED_ASSEMBLY` component on the item
itself — fell through to the finished-product path and could only answer "no recipe for it". It now keys on the
**component**: anything carrying it is refunded from the sequence recipe it names (base + the steps that already
ran), while our own item *without* the component is still refused as "no processing record". Also in this batch:
the compute result actually reaches the player's chat (the run is queued for the next tick, so reading the
status in the same tick always saw `RESULT_EMPTY`); a dismantle's materials go into the presser's inventory;
a material the line merely *uses* (a smithing recipe's `base`, applied in `USE` mode by a Deployer) is marked
`（不消耗）` via `SchemeRoles`; a scheme that cannot avoid referencing itself says so
(`该物品无法避免自引用，已转换为增量配方`); the Scheme Loader's title no longer sits on its own panel edge; and
the build-guide block is gone from the tooltip by request (the `LineBuildGuide` payload is still written — it is
what makes a plain item a carrier — it is simply not rendered).

**中文** — 中间产物原先**完全拆不了**：分支按**我们的**物品（`Generic Intermediate`）判断，于是所有 Create 原生
过渡物（来源记录在物品自身的 `SEQUENCED_ASSEMBLY` 组件上）都掉进"成品"分支，只能答"没有它的配方"。现在改按
**组件**判断：带组件的一律按它指名的序列配方退还（基底＋已跑完的步骤），我们自己那个**不带组件**的物品仍旧
按"没有加工记录"拒绝。同批还有：计算结果真的进聊天（运行被排到下一 tick，同 tick 读状态永远是 `RESULT_EMPTY`）；
拆解退料进按按钮那个玩家的背包；产线**仅使用**的材料（锻造配方的 `base`，机械手以 `USE` 模式施加）经
`SchemeRoles` 标记 `（不消耗）`；无法避免自引用的方案会说明已转成增量配方；加载柜标题不再压在自己面板边上；
按作者要求，tooltip 里的搭建指引块移除（`LineBuildGuide` 载荷照旧写入——它是普通物品成为载体的依据——只是不渲染）。

- 新增 / added：`line/mapper/SchemeRoles.java`
- 自检 / self test：**22 → 23 项**（`Computer writes plan + guide onto both carriers`）

### 3.7 `0.0.0-dev.12` — 退役配方而非删除

**EN** — An item crafted on a line outlives the line: the Dismantler refunds an unfinished intermediate from
the recipe that gave it its provenance, so deleting that recipe the moment its scheme left the loader turned
every leftover intermediate into scrap ("找不到它来源的序列配方 cpl:…"). The pack now moves retired files to
`<world>/cpl_retired/` (outside `datapacks/`, so nothing loads them again) and the reader falls back there when
the live pack no longer has the recipe.

**中文** — 产线上做出来的物品比产线活得久：破拆机要按"给它来源的那条配方"退还未完成中间产物，而方案一离开
加载柜就删掉那条配方，等于把所有遗留中间产物变成废品（"找不到它来源的序列配方 cpl:…"）。现在退役文件被移到
`<world>/cpl_retired/`（在 `datapacks/` 之外，不会被再次加载），活数据包里找不到配方时读取端回落到那里。

### 3.8 `0.0.0-dev.13` / `0.0.0-dev.14` — Ponder 场景（工作区最新，CHANGELOG 未收录）

**EN** — Two commits (`57997d1` then `0169c68`), +627 / −10, adding a `client/ponder/` package of four classes
and three structure files. Three scenes, one tag: the loader's and the computer's stories are the two chapters
of one line story, the dismantler's is separate because it runs the other way. The author asked for chapters via
Ponder's built-in mechanism, and the plugin's own javadoc records why that cannot work in this version —
`PonderChapter.getTitle()` returns an empty string and `PonderUI`'s chapter field is only ever assigned `null`
— so a `PonderTag` is the grouping that actually renders in the index. `MachineGuiElement` draws one of this
mod's container GUIs over the scene as its overlay element, and the second commit added the three structure
files the first one was missing — `dev.13` was published that way and its tag says so (*"known defect:
schematics missing"*), so that build's scenes show an empty world; `dev.14` is the fix and is still local.

**中文** — 两个提交（`57997d1`、`0169c68`）、+627 / −10，新增 `client/ponder/` 四个类与三个结构文件。**三个场景、
一个 tag**：加载柜与计算机是"一条产线故事"的两章，破拆机因为方向相反单独成场。作者要求用 Ponder 自带机制
做章节，插件的 javadoc 记下了这一版为何做不到——`PonderChapter.getTitle()` 返回空串、`PonderUI` 的章节字段
只被赋 `null`——所以真正能在索引里分组显示的是 `PonderTag`。`MachineGuiElement` 把本模组的容器 GUI 画成场景
浮层；第二个提交补上第一个缺的三个结构文件。`dev.13` 就是**带着这个缺陷补发**的那一版（tag 说明：已知缺陷：
缺 schematic），它的场景会显示空世界；`dev.14` 是修复版，且仍只在本机。

> **待复核 / to double-check** — 三个 `.nbt` **逐字节相同**（各 228 B / `62cfd125…`）：gzip 解开后是同一份
> `DataVersion=2230`、`size=[5,3,5]`、palette `[minecraft:white_concrete, minecraft:snow_block]`、25 个方块的
> 5×5 平台，即 Create 场景的**演示底板**，而不是三台机器各自的结构。场景本身在运行时用
> `scene.world().setBlock(…)` 摆机器，所以不是一个缺陷，但**实机走查时值得确认三场底板表现是否如预期**。
> **EN** — the three `.nbt` files are byte-identical (228 B, `62cfd125…`): the same 5×5 two-material demo
> plate, not three machine-specific structures. The scenes place their machines at runtime with
> `scene.world().setBlock(…)`, so it is not a defect — but it is worth a look during the in-game pass.

> **语言键改动 / language keys** — `ponder.production_line.*`（12 键，第一版的"章节"写法）在第二个提交被换成
> 按机器的键：`ponder.production_computer.header` + `text_1..5`、`ponder.scheme_loader.header` + `text_1..5`、
> `ponder.dismantler.header` + `text_1..4`，加 tag 的标题与描述；两个语言文件各 82 → 84 键。

## 四、把差异摊平看：1.0.1 → 现在 / The same difference, by topic

| 维度 / dimension | `1.0.1` | 现在 / now（`HEAD`） | 变化 / delta |
| --- | --- | --- | --- |
| Java 文件 / files | 47 | **62** | +15（无删除） |
| Java 行数 / lines | 6,908 | **10,231** | +3,323（+48%） |
| 自检项 / self-test checks | 13 | **23** | +10 |
| 资源文件 / resource files | 43 | **63** | +20（方块贴图/模型为主） |
| 提交 / commits | — | — | 58 个提交、111 个文件、+13,005 / −973 |
| 文档 / docs | README 单文件 | README ＋ **8 份 `docs/`** | 12 个 md 文件、+1,214 / −480 |
| 版本号 / version | `1.0.1` release | `1.0.3-snapshot.0.0.2` alpha | 中间经过 1.0.2 release 与 10 个 dev 包 |

**按主题归并的 10 类更新 / ten threads of change**

1. **写方案的第二条路**（1.0.2）：OP 铁砧手写 + `cpl:custom_assembly` 数据组件 + 纯函数状态机
2. **产量算术**（1.0.2）：目标产量＝目标槽堆叠数、`RepeatPlan` 循环预算、材料预算行、"不可达"判定
3. **性能**（`snapshot.0.0.1`）：激活方案只换配方、不跑 `/reload`；`/cpl reload recipes`
4. **方块美术**（dev.5–dev.8）：三台机器全部重绘、加载柜进度条、贴图拼写规范化、方向修正
5. **GUI 版式**（dev.9）：`menu/GuiLayout` 统一几何、三块面板对齐手绘凹槽、自检钉不变量
6. **反馈闭环**（dev.9–dev.11）：计算机结果私聊、破拆机拒绝原因 11 态、流体原料告知、自引用告知
7. **破拆机语义**（dev.10–dev.12）：拆方案返还空白方案、倍增退款真逆运算、按组件识别中间产物、
   退役配方到 `<world>/cpl_retired/`、"仅使用不消耗"标记
8. **工具提示与文案**（dev.9–dev.11）：指引块可读（后又按作者要求移除渲染）、`（不消耗）`、长度受控
9. **Ponder 教程**（dev.13/dev.14）：三个场景一个 tag、GUI 浮层、演示底板结构文件
10. **文档体系**（dev.5 起）：README 拆分为总体＋7 份专题（本页是第 8 份），双语与事实口径经过四轮复核

## 五、与 1.0.1 对比的实测口径 / How the numbers above were measured

**EN** — Java file counts and line counts come from `git ls-tree` / `git show` at each tag (line counts include
every line of every `.java` file under `src/main/java`); self-test counts are the number of `check("…")` calls in
`qa/SelfTest.java` at that tag; jar sizes and hashes are `Get-FileHash -Algorithm SHA256` on the jars in
`build/libs`; the per-build "what changed" lines are the commit subjects and the on-disk diff between the tags.
The comparison writes no acceptance number into any check — a build passes when the last self-test line matches
`\d+ passed, 0 failed`, never because it printed a particular count.

**中文** — Java 文件数与行数取自各 tag 的 `git ls-tree` / `git show`（行数含 `src/main/java` 下每个 `.java` 的全部行）；
自检项数＝该 tag 下 `qa/SelfTest.java` 里 `check("…")` 的调用数；jar 字节与哈希来自对 `build/libs` 内文件的
`Get-FileHash -Algorithm SHA256`；分版"改了什么"取自提交标题与 tag 之间的实际 diff。对比不把任何验收数字写死进
检查——判一个构建过没过，看自检最后一行是否匹配 `\d+ passed, 0 failed`，而不是看它打印了几项。

## 六、需要注意的三件事 / Three things to be careful about

1. **同一版本号，不同字节 / same version, different bytes.** 本机的 `1.0.2` jar 是 226,351 B / `38fcbb9e…`，
   而三平台上的 `1.0.2` 是 215,411 B / `27e0a3c6…`；本机的 `1.0.3-snapshot.0.0.1` 是 248,895 B / `d43a9380…`，
   线上资产是 226,361 B / `aaf8e9e2…`。要核对线上资产请直接下载对应文件，别拿本机包当参照。
   （本机与线上摘要一致的共 10 个：`dev.5` 与 `dev.6` 是"与本地 `-PdevBuild` 构建逐字节相同"，
   `snapshot.0.0.2` 是"与 GitHub 资产逐字节一致"，`dev.7…dev.13` 是补发后核对资产摘要相符。）
2. **Ponder 只进了 dev 包，没进快照 / Ponder exists only in dev builds, not in a snapshot.** Ponder 两个提交
   （`dev.13`、`dev.14`）晚于最新公开快照 `1.0.3-snapshot.0.0.2`，`CHANGELOG.md` 的 *Unreleased* 段记的是
   dev.11/dev.12 的内容（组件识别、结果私聊、退款去向、退役配方等），**没有 Ponder**。收线写 `[1.0.3]` 时要补上。
   **EN** — the Ponder work is on `main` and in `dev.13`/`dev.14` only; no public snapshot carries it, and the
   changelog's *Unreleased* section (dev.11/dev.12 content) does not mention it yet.
3. **收线还差什么 / what is left before `1.0.3`.** ① 实机走查（见 `文档/1.0.3-实机验收清单.md`）；
   ② `mod_version=1.0.3` + `mod_version_type=release` + CHANGELOG 的 `[1.0.3]` 段与其链接定义；
   ③ tag `v1.0.3`；④ 三平台上传并同步正文；⑤ 平台侧待办（CurseForge 简介粘贴、文件页 changelog、
   Modrinth 过审后清理重复的 `1.0.2`）。
