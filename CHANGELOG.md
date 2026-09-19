# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**Version policy / 版本规范** (see `RELEASING.md`):

- **release**: `1.0.x`, the value of `mod_version`; `1.0.1` is the current stable release.
- **beta**: also a `1.0.x` value, published as a pre-release when `mod_version_type=beta`
  (Modrinth/CurseForge channel *Beta*, GitHub pre-release). `1.0.2` is the first such cut.
- **dev (beta)**: `0.0.0-dev.N`, built with `gradlew build -PdevBuild` (N from `dev-build.txt`),
  always published as `beta`.

The jars of the old numbering (1.0.0 / 1.0.1 / 1.0.2 / 1.0.3) were development snapshots and are
recorded as `0.0.0-dev.1` … `0.0.0-dev.4`; `1.0.1` was the first official release. Note that the
version number `1.0.2` was reused for this beta — the old snapshot of the same number no longer
exists anywhere, and no entry below refers to it.

## [1.0.2] — 2026-09-17 (beta)

**Beta pre-release.** This jar is a beta: Modrinth channel *Beta*, CurseForge release type `beta`, and a
GitHub Release marked as a pre-release. **`1.0.1` stays the current stable release.** Existing schemes
keep working unchanged — a V1 scheme item loads with a target output and repeat count of 1 — and the only
change on the way in is that the computer now reads the target slot's stack size as the output the player
wants.

### Added

- **The OP anvil flow: hand-author a Line Scheme in an anvil.** An operator (permission level 2) builds a
  plan one operation at a time. A scheme that already carries a plan plus `minecraft:paper` in the right
  slot yields a cleared copy: every material step and every cached recipe payload is dropped, the target
  output and its per-craft count stay. The cleared scheme plus one material per operation appends that
  material to an ordered list and rebuilds the plan from it. The scheme plus paper again locks it:
  `locked` freezes the scheme, and every later anvil operation on it is refused. Two or more materials
  produce a `create:sequenced_assembly` payload, the first material as the base and every later material
  as one `Deployer` step (`create:deployer` by default).
- **A single material locks into a fallback instead of a fake sequence.** With exactly one material the
  anvil cannot express a sequenced assembly, so the scheme is locked with a `single_material_fallback`
  flag and derivation uses the single-material semantic machine (wood → saw/cutting, ore and `raw_*` →
  crushing wheel, organic → millstone, metal/gem → press, default press). The player is told
  `单原料自定义方案需等待后续版本支持` / "Single-material custom schemes are not supported yet", in the
  tooltip and once in the action bar at lock time.
- The hand-authored state lives in a registered **Data Component** (`cpl:custom_assembly`), persisted and
  network-synchronised rather than parked in ad-hoc NBT.
- Every operation is **server-authoritative**, gated on `player.hasPermissions(2)`. Illegal input (a
  stacked scheme, a material before clearing, an empty right slot, a material equal to the product,
  anything on a locked scheme) cancels it, so nothing is consumed and the item is untouched.
- The operation **costs no experience and consumes exactly one item**. The level cost is the vanilla
  minimum of 1 (`setCost(1)`; `AnvilMenu.mayPickup` requires `cost > 0`, so 0 would make the result
  impossible to take) and that level is refunded when the item is taken, which leaves a net cost of zero
  while the player still needs 1 level to pick it up. The repair penalty is cleared, and the right slot
  contributes one item (`setMaterialCost(1)`; `0` would eat the whole stack).
- The scheme loader **re-derives the entries from the component server-side** and ignores the embedded
  JSON, exactly like the existing `recipeId` path. `RecipeDeriver` itself is unchanged: a new adapter
  hands it a synthetic descriptor whose category decides between the assembly rule and the
  single-material rule.
- **The target slot's stack size is the target output, and the line gets a repeat budget.** The number of
  items stacked into the target slot is the number the player wants out: the computer reads that stack size
  server-side (clamped to the item's max stack size) and records it as the target output. The installed
  Create recipe stays a **single-craft** payload, because Create's sequenced assembly cannot loop on its own
  and writing N crafts' worth of materials into one payload would consume N times the input for one craft's
  output. What the scheme records instead is how often the line has to run to reach the target, which the
  player realises physically by feeding the product back (their own belt loop).
- **A new `RepeatPlan` does the arithmetic.** An ordinary recipe (it does not consume its own product)
  repeats `ceil(target / per-craft output)` times. A doubling / recursive recipe (`A + B = 2A`, consuming
  `c` copies of the product and yielding `p > c`) bootstraps from the single unit that goes on the belt, so
  it repeats `ceil((target - 1) / (p - c))` times, with a net gain of `p - c` per pass. A recipe that cannot
  grow the stock (`p <= c`) is reported as **unreachable**: the repeat count stays 1 and the player is told
  to bring the product themselves. There is deliberately **no** "loop until it works" path anywhere.
- **A hand-built scheme is not "empty" while it is being authored.** "No Steps" used to mean "empty scheme": the
  cleared state (and a single-material line, which really is one machine) therefore showed up as an empty scheme
  and, worse, was refused by the Scheme Loader's carrier whitelist. A genuine Line Scheme item now counts as a
  carrier when it carries a **locked** custom component even with no Steps, its tooltip keeps showing the target
  product plus the custom state, and a truly blank scheme is still refused.
- **Where the numbers live.** `LineScheme` gained `TargetOutputCount` and `RepeatCount` (scheme format
  **V2**; a V1 item loads with 1/1 instead of failing), the plan's topology closes with an explicit
  instruction line such as `repeat 3x -> 4 Iron Ingot` when the line has to run more than once, and both the
  Line Scheme tooltip (`重复执行 N 次（目标产量 M）` / "Repeat N times (target output M)") and the
  computer's panel show the same numbers.
- **The Scheme Loader says how many passes its cabinet asks for.** It reports the largest repeat count among
  the schemes in the cabinet through a **server-derived menu data slot** (not the client's copy of the item),
  and its panel warns `该产线包含 N 次循环组装，请准备充足的基础材料` / "This line runs N times - prepare
  enough base materials". The first time a player opens such a cabinet they also get that single line in
  their own action bar; the mod never posts to chat and never messages anyone else.
- **The anvil flow inherits those numbers.** Clearing a scheme copies the compute-time target output and
  repeat count into the custom-scheme component, and hammering materials in never resets them;
  `CustomAssemblyPlanner` puts the target output into the descriptor it hands to `RecipeDeriver`, so the
  custom line's stated output matches what the player computed.

### Changed

- **Headless self test grew to 16 checks** (was 13; 15 after the anvil flow above). The new assertions cover
  the assembly payload shape, the single-material fallback, and the repeat budget: a doubling recipe with
  target output 4 must yield 3 passes, a material budget of 6 for two materials, a plan whose topology says
  `repeat 3x`, and an unreachable recipe (`p <= c`) that must not be looped.

## [1.0.1] — 2026-09-17 (release)

**First official release.** It supersedes every dev snapshot (`0.0.0-dev.1` … `0.0.0-dev.4`).
Everything the snapshots built up is in this jar: the assessment hardening batch (A1–A7, B1–B7,
M7, M11), the second-pass fixes (recipe selection, `processing_time`, dismantler rework,
count-prefixed ingredients, entity-result recipes, tooltip overflow), and the interface/feature
work (plan mirrors the derived recipe, semantic machine choice, all three screens reworked).
Headless QA self test: **13 passed, 0 failed** on a real server (`gradlew runServer -PselfTest`).

Any earlier jar, whatever its file name, is obsolete. Use `create_productionline-1.0.1.jar`.

### Fixed

- **Recipe selection when several recipes produce the target.** A mod may ship a "copy / repair /
  dye" recipe whose only ingredient *is* the product, and the computer would happily pick that one,
  emitting the nonsense plan `[遥控器] -> 动力压床 -> 遥控器`. Selection now does three things:
  (a) skip every recipe without a real material (`RecipeDescriptor.hasUsableMaterials`), (b) never pick
  a recipe this mod itself installed (`cpl:…`, which would re-convert a conversion), and (c) prefer the
  first candidate that actually derives an installable entry. All of it server-side, mirrored by the
  client resolver so the hint matches.
- **`processing_time` on types that reject it (machines did nothing).** Create validates that field:
  `ProcessingRecipe.canSpecifyDuration()` defaults to `false`, and a recipe carrying a duration anyway
  fails to load with *"Recipe specified a duration. Durations have no impact on this type of recipe."*
  We were emitting it for pressing / splashing / haunting / mixing, so those files never registered.
  The GUI said "generated" while the machine stayed dead, and the log showed
  `Parsing error loading recipe cpl:cpl_…_pressing`. Only milling / crushing / cutting may carry it now
  (`CreateRecipePack.DURATION_TYPES`), which is what Create's own datapack files do, and the self test
  asserts the rule.
- **Dismantler rework (was effectively dead).** Slot 1 (Line Scheme) is no longer mandatory, and the
  machine no longer refuses every input whose material is a tag:
  - an **unfinished intermediate** (Generic Intermediate carrying Create's `SEQUENCED_ASSEMBLY`
    component) is refunded by re-reading the sequence recipe it names: base + exactly the materials the
    completed steps consumed (multiplicity preserved), then a mirror;
  - a **finished product** still refunds one full inverse batch (consume `count`, refund the inputs),
    resolved server-side from its recipe;
  - tags refund their first registered member (best effort), fluids are impossible in deploy steps;
  - the mirror shows the plan from the scheme when one is present, otherwise a snapshot synthesized
    from the parsed recipe; nothing is consumed when nothing can be refunded.
- **Count-prefixed string ingredients.** Some mods write ingredients as strings with a leading amount,
  e.g. `"8 #c:storage_blocks/steel"`, `"24 superbwarfare:cemented_carbide_block"`, `"2 superbwarfare:track"`
  (`superbwarfare:vehicle_assembling`). The reader passed those through verbatim, so tooltips came out
  unreadable (no `#tag` resolution), the payloads were **invalid** (`{"item": "8 #…"}` was written and
  reported as success, then never loaded) and text overflowed. `RecipeJsonReader.normalizeMaterial()`
  now strips the amount prefix everywhere materials are read (ingredient objects, arrays, plain strings,
  shaped keys), and `CreateRecipePack.asIngredient()` normalises defensively before emitting JSON.
- **Entity-result recipes are refused.** A recipe whose result is an entity
  (`"result": {"entity": …}`, vehicles and turrets among them) is assembled by its own mod's machine,
  so converting it into Create processing was wrong. Such recipes now yield `RESULT_NOT_CONVERTIBLE`
  and nothing is written.
- **Unknown machine categories are refused instead of forced into mixing.** The unconditional
  "2+ materials → `create:mixing`" fallback is gone. An unmapped, non-assembly category with several
  materials is now reported as not convertible instead of being silently turned into a mixing line.
- **Tooltips can no longer overflow the screen.** Long unbroken runs (CJK sentences, long registry
  ids, tag paths) are now wrapped by estimated pixel width (`util/TextWrap`, ASCII ≈ 6 px /
  CJK ≈ 9 px), with indented continuation lines. This covers the Line Scheme, mirror and
  clipboard-guide tooltips.

- **Tag ingredients in converted processing recipes (A1).** `CreateRecipePack.flat()` wrote every
  ingredient as `{"item": …}`, so a tag material (which the reader keeps as `"#tag"`) came out as an
  invalid ingredient. The file was written, the GUI reported success, and the recipe silently never
  loaded. Flat payloads now normalise ingredients through the same `asIngredient()` path as the assembly
  payloads, so `{"tag": …}` is emitted for `#tag` references.
- **Plan and embedded recipe now share one material list (A2).** The plan dropped materials equal to
  the product ("self-supplied") while the derived recipe still consumed them, so an affected plan was
  one station short: building it exactly as shown produced nothing. Both sides now use the same ordered,
  unfiltered material list, and self-referencing materials appear as ordinary stations.
- **Scheme Loader contributions can no longer outlive their cabinet (A3).** A loader's contribution
  was keyed by its coordinates and only removed by `onRemove`, so a cabinet that vanished without that
  callback (relocation, `/clone`, rollback) left its recipes active in the world forever. To be clear,
  this was never piston-specific: vanilla pistons cannot move block-entity blocks at all. The fix has
  two parts. The block entity now persists the key it registered under and drops it when it comes back
  at a different position, and the server sweeps orphaned contribution files on start-up, but only for
  coordinates whose chunk is loaded and which no longer hold a Scheme Loader.
- **Union order is deterministic.** When two loaders contribute the same recipe file name, the winner
  used to depend on the filesystem's directory enumeration order. Not any more.
- **Datapack format corrected (B4).** `pack.mcmeta` used pack format 34, which is the 1.21.1 *resource*
  pack format; the data pack format is 48. The pack loaded anyway only because the server tolerates
  older-format world datapacks, and it is now written with the correct format.
- **Headless self test asserts recipes actually load (B1).** The datapack-install check only verified
  that files were written, so a recipe the server refuses to parse still passed. It now asserts both test
  recipes are present in the live `RecipeManager` after the reload.
- **Dismantler refund count cross-checked against the live recipe (A4).** The refund scaled by the
  `result.count` text in the datapack JSON, which a modded recipe can understate. It now takes the
  larger of that text and the live `ItemStack.getCount()` of the recipe result
  (`ServerRecipeLookup.collectOutputs` now propagates it, `RecipeDescriptor.outputCount`), so a
  `count > 1` recipe can no longer be arbitraged by dismantling a single product.
- **The computer GUI now says why a computation failed (M7).** The specific reason (no recipe found /
  no usable output / no registry id) was only written to `lastError` and never shown to the player.
  A second menu data slot now syncs it, and the screen renders it above the generic message.
- **Output count propagates everywhere (B7).** The per-craft count used for plans and embedded recipes
  came from the JSON text only; both `RecipeDeriver` and the computer now take the larger of the JSON
  count and the live recipe result's stack size.
- **No more zero-station "successful" plans (A5).** A recipe with no usable materials could still write
  a scheme (`LineScheme.isEmpty() == true`) that carried an installable recipe and reported
  `RESULT_GENERATED`. It is now refused with `RESULT_NOT_CONVERTIBLE`, and the carriers are left alone.

### Changed

- **The headless self test no longer touches live state.** `qa/SelfTest` used
  `CreateRecipePack.install()/deactivate()`, whose first step deletes the whole `cpl_converted` pack.
  Running the self test therefore wiped every scheme loader's `contributions/`. It now installs into an
  isolated `cpl_converted_selftest` pack, and asserts that the live pack and its contributions survive.
- **Self test grew to 13 checks**: `Tag ingredients kept in flat recipes` (the A1 regression),
  `Deriver refuses native/unmappable recipes` (a native `create:` process or a material-less recipe must
  yield no payload), `Single-material recipes map to a semantic machine`, `Duration only on
  duration-capable types`, `Loader accepts written schemes only` and `Self-referential recipes are
  skipped`. The TC-01 positive/negative checks now run against the production derivation path
  (`RecipeDeriver`), not the removed legacy mapper.
- **The plan is now the mirror of the derived recipe (A6).** Steps used to pair machines with materials
  by list index, so a plan could show a machine that the actual recipe never used. Step layout is now
  generated FROM the derived Create recipe JSON's `type` (`MachineSelector.appendChainSteps`): sequenced
  assembly → one Deployer per extra material; flat/mechanical → one station for the machine that really
  executes the recipe. A step's machine is always the machine that really processes that material.
- **Single-material recipes are now convertible (B3).** They used to be refused outright (sticks,
  planks, buttons, smelting …), because no faithful single-machine mapping existed. The deriver now
  chooses a real Create machine from the material's semantics (`RecipeAnalyzer.machineForMaterial`:
  wood → saw/cutting, ore/`raw_*` → crushing wheel, organic → millstone, metal/gem → press, fallback
  press), and multi-material recipes without a flat dictionary method fall back to `create:mixing`.
  Only targets already produced by a native Create process, or recipes with no usable materials, are
  still refused.
- **The client caches recipe scans (M11).** Every click on the compute button re-enumerated and parsed
  every recipe JSON on the client. The resolver now keeps a small session cache keyed by item id.
- **The union datapack is only rewritten when it actually changes (B6).** Placing or touching a loader
  used to delete and rewrite every recipe file and run `/reload` unconditionally. The rebuild is now
  skipped when the merged content is byte-identical to the last write.
- **Dead code removed.** The legacy whole-pack `install()/deactivate()` (a footgun that wiped every
  loader's contributions), `installEntries`, `tryConvertOne`, `keyMap`, `patternFromRowMajor`, the
  planning-only `RecipeMapper.map`/`Mappers.get()`/`MappingResult` engine, `RESULT_NO_CLIPBOARD`,
  `computeNow()`, and several unused helpers are gone.
- **GUI reworked.** The computer and loader panels grew to 176×196 with roomier status areas, so the specific
  failure reason and the success summary now fit without clipping. The computer additionally shows the
  embedded recipe count, and the loader now displays the **server-derived** active recipe count (a new menu
  data slot) instead of counting the item's cached JSON. The dismantler got its own background instead of
  the 16-slot loader sheet. All three backgrounds are proper 256×256 sheets; the previous 176×166 files
  were sampled as if 256×256, which rendered the panel as a stretched top-left crop. Text wrapping moved into
  one shared helper (`CreateGui.wrap`).
- **In-game mod metadata now links the issue tracker** (`issueTrackerURL` expanded from
  `gradle.properties`, which no longer claims the repository does not exist).

## 0.0.0-dev.4 — 2026-09-16 (beta)

Superseded dev snapshot. This was the first 1.0.3-era cut, i.e. the in-tree hardening batch (A1–A7):
tag ingredients in flat recipes, one shared material list for plan + embedded recipe, loader
contributions that cannot outlive their cabinet, deterministic union order, data pack format 48,
a self test that asserts recipes really load, the dismantler refund cross-checked against the live
recipe, and the computer GUI naming the failure reason. Everything in it shipped in **1.0.1**;
this entry is kept for provenance only.

## 0.0.0-dev.3 — 2026-09-13 (beta)

### Changed

- **Plans now read as one linear chain**: `[base] → [machine 1 + material 1] → [machine 2 + material 2] → … → [product]`.
  Step 1 puts the base onto the line; every following station is exactly one machine paired with the single
  material it applies; the carried item is chained through the stations (base → Generic Intermediate → …) and
  only the last station yields the product. Before this, extra materials were all listed as separate feed steps
  *before* the machines, so you could not see which machine applied which material. If a recipe selects more
  machines than there are extra materials, the surplus machines still get their own station, so no facility
  the player must place is hidden.

### Fixed

- **No more plans for recipes that cannot be converted.** A scheme with no installable Create recipe was a
  promise the mod could not keep (single-material recipes such as iron blocks, or targets already produced by
  a native Create process), and it consumed the player's paper / clipboard / blank Line Scheme for nothing.
  The computer now refuses instead, says why, and leaves the carriers untouched.

## 0.0.0-dev.2 — 2026-09-13 (beta)

### Fixed

- **Dismantler item duplication.** The refund ignored how many items a recipe yields per craft:
  it consumed a single output item and refunded one of every input. Any recipe with a yield
  greater than 1 therefore minted items, and the vanilla example is `minecraft:iron_ingot_from_iron_block`
  (1 iron block → **9** iron ingots), where a single iron ingot turned into a whole block: a 9× gain
  per operation, repeatable forever. Every block ↔ ingot/nugget pair in vanilla has this shape,
  as do modded multi-output recipes. The dismantler now requires, and consumes, a full batch of
  `count` items, where `count` is the recipe's own yield, which makes the refund the exact inverse of
  the recipe. Holding fewer items than the batch size is refused outright (nothing consumed,
  nothing produced), and the in-game hint says so.

## 0.0.0-dev.1 — 2026-09-13 (beta)

First public dev snapshot (beta; superseded by 1.0.1). Requires **Minecraft 1.21.1**,
**NeoForge 21.1.249+** and **Create 6.0.10+**.
JEI is optional, and only for viewing recipes: this mod does not call its API.

### Added

- **Production Computer**: 3 slots (target / carrier / clipboard). Press *Compute* to turn any
  recipe into an ordered production-line plan, write it onto a carrier (paper, clipboard or
  Line Scheme) and inject a `custom_data.LineBuildGuide` build guide.
- **Line Scheme**: item carrying the plan (`Version / RecipeId / OutputItem / BaseMaterial /
  Steps[]` plus cached embedded Create recipes).
- **Scheme Loader**: 16-slot cabinet (2×8). Every scheme inserted is activated, and several
  loaders/schemes combine (union) so multi-stage lines work. Emits a redstone signal while
  recipes are active.
- **Dismantler**: reverts a Generic Intermediate or finished product into its raw materials and
  leaves a read-only Line Scheme Mirror.
- **Generic Intermediate**: the opaque transitional item used by generated
  `create:sequenced_assembly` recipes (extends Create's `SequencedAssemblyItem`, so assembly
  progress is tracked correctly).
- **Line Scheme Mirror**: text-only, read-only snapshot of a plan. Structurally incapable of
  being activated or fed back into the Dismantler.
- Recipe conversion: machine processes become flat `create:<type>` recipes (crushing, milling,
  mixing, pressing, cutting, …); crafting/assembly becomes `create:sequenced_assembly`
  (base material + one Deployer station per extra material, `loops=1`, output count preserved);
  native `create:mechanical_crafting` recipes additionally get a non-conflicting sequenced-assembly
  variant.
- Tag fidelity: source recipe tags are written back as `{"tag": …}` so any tag member matches.
  This is what makes e.g. 48-round rifle ammo craftable.
- Extensible mapping dictionary (24 built-in categories) via
  `config/create_productionline-mappings.json`, which also selects `assemblyMode`
  (`sequenced` default, `mechanical` optional).
- Headless QA self-test on a real server: `gradlew runServer -PselfTest` (10 checks at the time,
  13 in 1.0.1; the server halts afterwards).
  Pass criterion: the last log line matches `\d+ passed, 0 failed`. The count is a snapshot
  (number of `check("…")` calls in `qa/SelfTest.java`), never a hard-coded acceptance value.

### Security

- **Server is the only authority.** Nothing stored on an item and nothing sent by a client is
  trusted:
  - the Scheme Loader accepts **only** genuine Line Scheme items and re-derives every recipe from
    the scheme's `recipeId` against the live server `RecipeManager`;
  - the Dismantler refuses to consume unless the scheme is genuine, the item matches the scheme's
    output, the recipe resolves server-side and no refund input is a `#tag`;
  - the compute entry point carries a real `recipeId` that the server re-resolves and verifies
    against the target slot; unverifiable client data can suggest a plan but **never** produce an
    installable recipe;
  - payload handlers validate `menu.stillValid(player)` before touching any slot.
- Mirrors are text-only and can never be activated or re-fed.

### Known limits

- Sequence lines consume 1 unit of each material per step. That is cheaper than the source grid when a
  material repeats, and the line still produces output.
- Results carry item id + count only, so recipes with NBT/enchantments/state yield the plain variant.
- Multi-level intermediates are not recursed into a single scheme. Compute each stage and activate
  them together as a union in the 16-slot loader.
- The Dismantler refuses `#tag` inputs, and recipes it cannot resolve server-side, instead of
  swallowing items.
- Create's `assets/` are all rights reserved: this mod only runtime-references its GUI/textures and
  never bundles copies. The binding statement of this constraint is in
  [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) → "Create assets are All Rights Reserved —
  do not copy them".
- Changing a loader's schemes triggers a datapack rewrite + `/reload`. The rewrite is skipped when the
  merged content is unchanged, but on very large packs a real change can still cause a brief hitch.
- On a dedicated server the client cannot see world datapacks, which is why the server always
  re-resolves recipes itself.

### Notes

- Licensing: **MIT**.
- Build: `gradlew build` (JDK 21). See `RELEASING.md` for the publication flow.

<!-- Dev snapshots (0.0.0-dev.N) are intentionally not tagged on GitHub and have no release
     page; only release versions (v1.0.x) get a tag and a GitHub Release. -->

[1.0.2]: https://github.com/Es-254/Create-ProductionLine/releases/tag/v1.0.2
[1.0.1]: https://github.com/Es-254/Create-ProductionLine/releases/tag/v1.0.1
