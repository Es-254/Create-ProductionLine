# 安全（多人服防注入）/ Security

> [← 总体 / Overview](../README.md) · 相关： [使用](usage.md) · [配方转换](conversion.md) · [兼容性与边界](compatibility.md) · [无头自检](qa.md)

## Security (multiplayer anti-injection, landed 2026-09-07) / 安全（多人服防注入，2026-09-07 落地）

**EN** — NBT-carrying items (schemes, mirrors) can be forged by modified clients on survival servers. So we rebuilt the mod around one rule: the server is the only authority. All findings below reference current sources, with `file:line` in parentheses.

- **Carrier whitelist.** `ClipboardCompat.isLoaderCarrier` (`compat/ClipboardCompat.java:83`) accepts only genuine `LineSchemeItem`, and both the loader slot (`menu/SchemeLoaderMenu.java:54-55`) and the loader's own collection path (`block/entity/SchemeLoaderBlockEntity.java:220`, inside `currentEntriesMap` — which `reconcile()` calls at `:118` when it registers this cabinet's contribution) go through it; `maxRepeatAmongSlots()` applies the same check at `:137` while scanning the cabinet for the panel's repeat notice. Paper, clipboards, mirrors and forged NBT are all rejected.
- **Server-side re-derivation in the loader.** Activation never trusts the scheme's embedded JSON/Steps. `SchemeLoaderBlockEntity.currentEntriesMap` (`block/entity/SchemeLoaderBlockEntity.java:213-255`) resolves `RecipeId` via `ServerRecipeLookup.findById` (`line/mapper/ServerRecipeLookup.java:43`) to the **live recipe**, then `RecipeDeriver.derive` (`recipegen/RecipeDeriver.java:49`) re-derives the entries to install. A forged scheme can at most activate "a recipe that really exists on the server"; a missing or stale `recipeId` is skipped.
- **Authoritative dismantler refund.** `DismantlerBlockEntity.revert()` (`block/entity/DismantlerBlockEntity.java:66`) validates in order: slot 1 must be a genuine `LineSchemeItem` (:77-79) → slot 0 item id must equal the scheme's `OutputItem` (:124-128) → the `recipeId` must resolve server-side with a matching output (:118-123). A `#tag` in the refund set is materialized as the tag's first registered member, best effort (`materialize`, :194-221); if *nothing* in the set can be materialized, nothing is consumed at all (:162-164). It consumes first — one unit for an unfinished intermediate (:88), a full inverse batch of `count` for a finished product (:144-147) — then refunds, then places the mirror (:169-188). Unresolvable or mismatched → **consume nothing, produce nothing**, which kills the "forge a scheme to print valuable materials" trick.
- **Network entry validation.** `ModPayloads.handleDismantle` (`network/ModPayloads.java:90`) calls `menu.stillValid(player)` before touching any slot.
- **Mirror is text-only.** `LineSchemeMirrorItem` (`item/LineSchemeMirrorItem.java:29,42`) writes only a `LineSchemeMirror` display snapshot (OutputItem/BaseMaterial/Steps text) with no executable `LineScheme`/embedded recipes, and `ClipboardCompat.isCarrier` returns `false` for it (`compat/ClipboardCompat.java:64-65`), so it can neither be activated nor re-fed.
- The computer generates server-side too, so validation is inherent, and it shares the same algorithm/entry point as the loader.

**中文** — 方案、镜像这类"存 NBT 的物品"，在多人服上会被改包客户端伪造。为此我们按"服务端为唯一权威"重构了一遍（下面每条结论都对应现在的源码，括号里是文件:行）：

- **容器白名单**：`ClipboardCompat.isLoaderCarrier`（`compat/ClipboardCompat.java:83`）只认真 `LineSchemeItem`，加载柜槽位（`menu/SchemeLoaderMenu.java:54-55`）与加载柜自己的收集路径（`block/entity/SchemeLoaderBlockEntity.java:220`，位于 `currentEntriesMap` 内——`reconcile()` 在 `:118` 调它来登记本柜贡献）都走它；`maxRepeatAmongSlots()` 在 `:137` 用同一判定扫描柜内循环次数（供面板提示）。纸、剪贴板、镜像、随便伪造的 NBT 载体，一律拒收。
- **加载柜服务端推导**：激活时不信任方案内嵌的 JSON/Steps。`SchemeLoaderBlockEntity.currentEntriesMap`（`block/entity/SchemeLoaderBlockEntity.java:213-255`）按 `RecipeId` 调 `ServerRecipeLookup.findById`（`line/mapper/ServerRecipeLookup.java:43`）取到**实时配方**，再交给 `RecipeDeriver.derive`（`recipegen/RecipeDeriver.java:49`）重新推导该装哪些条目。伪造方案最多只能激活"服务器上真实存在的配方"；方案里没有的、失效的 `recipeId` 直接跳过。
- **破拆机权威退款**：`DismantlerBlockEntity.revert()`（`block/entity/DismantlerBlockEntity.java:66`）按顺序校验：槽 1 必须是真 `LineSchemeItem`（:77-79）→ 槽 0 物品 id 必须等于方案 `OutputItem`（:124-128）→ 方案 `recipeId` 能在服务端解析且产物一致（:118-123）。退还集合里的 `#tag` 会尽力折算成该标签的首个成员（`materialize`，:194-221）；只有当整个集合**一个都折算不出来**时才什么都不消耗（:162-164）。顺序是先消耗——未完成的中间产物消耗 1 个（:88），成品按 `count` 整批消耗（:144-147）——再退还，最后放镜像（:169-188）。解析不到或产物不符 → **不消费不产出**，"伪方案刷贵重原料"这条路就被堵死了。
- **网络入口校验**：`ModPayloads.handleDismantle`（`network/ModPayloads.java:90`）先做 `menu.stillValid(player)`，再触碰任何槽位，所以失效的 GUI 不会被执行。
- **镜像纯文本化**：`LineSchemeMirrorItem`（`item/LineSchemeMirrorItem.java:29,42`）只写 `LineSchemeMirror` 展示快照（OutputItem/BaseMaterial/Steps 文本），结构上不携带可执行 `LineScheme` 和内嵌配方，`ClipboardCompat.isCarrier` 对它直接返回 `false`（`compat/ClipboardCompat.java:64-65`），因此既不能被激活，也不能复喂。
- 产线计算机本身也是服务端生成，校验天然存在，和加载柜同源同算法。

> **EN** — The same rule covers the compute entry point. The payload carries the real `recipeId`, and the server re-resolves it against its live `RecipeManager`, accepting it **only** when that recipe really produces the item sitting in the target slot. A client hint that cannot be verified yields no plan at all.
> **中文** — 计算入口也守同一条规则。计算包携带真 `recipeId`，服务端在实时 `RecipeManager` 上重新解析，**只有**当该配方确实产出目标槽内的物品时才采纳；验证不通过的客户端数据写不出任何方案。

> 返回 [总体 / Overview](../README.md)
