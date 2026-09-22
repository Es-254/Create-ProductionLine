# 无头自检 / QA

> [← 总体 / Overview](../README.md) · 相关： [构建与安装](build.md) · [依赖](dependencies.md) · [配方转换](conversion.md) · [兼容性与边界](compatibility.md)

## Headless self-test (QA) / 无头自检（QA）

```
# start the server / dev runtime with the self-test enabled / 启动服务器或开发运行时开启自检
gradlew runServer -PselfTest
```

> **EN** — `-PselfTest` forwards `create_productionline.selfTest=true` to the GAME JVM. A bare `-D` on the Gradle command line does not reach it. The property is read by `qa/SelfTest.isEnabled()`, and once the server is up the 20 checks run against a **real server** (real registries/NBT/components/`RecipeManager`).
> **中文** — `-PselfTest` 会把 `create_productionline.selfTest=true` 传给**游戏 JVM**；在 Gradle 命令行上直接写 `-D` 传不到游戏进程。该属性由 `qa/SelfTest.isEnabled()` 读取，服务器启动后就对着**真实服务器**跑这 **20 项**检查（真实注册表/NBT/组件/`RecipeManager`）。
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
[PASS] Recipe-only reload registers new recipes
[PASS] Owned recipes parse for injection
CPL SELF-TEST RESULT: 20 passed, 0 failed
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
> **EN** — Current snapshot: **20** checks. `Plan topology (chain: base -> machine+material -> product)`
> arrived with dev snapshot `0.0.0-dev.3`; `Tag ingredients kept in flat recipes`, `Duration only on
> duration-capable types`, `Loader accepts written schemes only`, `Self-referential recipes are skipped`,
> `Deriver refuses native/unmappable recipes` and `Single-material recipes map to a semantic machine`
> landed by release 1.0.1; `Custom assembly builds a deployer sequence` and `Single-material custom
> scheme falls back to one machine` came with the 1.0.2 anvil flow, and `Doubling recipe repeats to
> reach the target output`, `Scheme anvil state machine table` and `Plan reports the material budget`
> came with the same 1.0.2 release; `Recipe-only reload registers new recipes` and `Owned recipes parse
> for injection` came with the recipe-refresh work on `main` after 1.0.2. Adding or
> removing a `check(…)` changes this number and nothing else, apart from the snapshot mentions in this
> file (`docs/qa.md`), in `../CHANGELOG.md` and in `../RELEASING.md`.
> **中文** — 当前快照 **20 项**。其中 `Plan topology (chain: base -> machine+material -> product)` 是随开发快照
> `0.0.0-dev.3` 进来的；`Tag ingredients kept in flat recipes`、`Duration only on duration-capable types`、
> `Loader accepts written schemes only`、`Self-referential recipes are skipped`、
> `Deriver refuses native/unmappable recipes`、`Single-material recipes map to a semantic machine`
> 这六项随正式版 1.0.1 落地；`Custom assembly builds a deployer sequence` 与
> `Single-material custom scheme falls back to one machine` 随 1.0.2 的铁砧流程加入，
> `Doubling recipe repeats to reach the target output`、`Scheme anvil state machine table`、
> `Plan reports the material budget` 同样随 1.0.2 加入；`Recipe-only reload registers new recipes` 与
> `Owned recipes parse for injection` 随 1.0.2 之后在 `main` 上做的配方刷新改造加入。
> 增删一个 `check(…)` 只会改变这个数字，别的地方不用动，
> 只需要改本文件（`docs/qa.md`）、`../CHANGELOG.md`、`../RELEASING.md` 里标注为"快照"的那几处。
>
> **EN** — Historical docs mentioning "5 passed" / "6 passed" / "7 passed" / "9 passed" / "10 passed" describe earlier
> rounds; the `DataPacket action whitelist` case went away with the old architecture. Current code has **20** checks
> and no DataPacket whitelist case.
> **中文** — 历史文档里的 "5 passed" / "6 passed" / "7 passed" / "9 passed" / "10 passed" 是更早几轮的数字；
> `DataPacket action whitelist` 一项随旧架构一起删掉了。当前代码为 **20 项**，也不再有任何 DataPacket 白名单用例。

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

> 返回 [总体 / Overview](../README.md)
