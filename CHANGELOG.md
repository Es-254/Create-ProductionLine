# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] — 2026-09-16

Fixes for the issues found by the 2026-09-16 project assessment, plus the earlier in-tree
hardening batch (A1–A7). Headless QA self test: **9 passed, 0 failed** on a real server
(`gradlew runServer -PselfTest`).

### Fixed

- **Tag ingredients in converted processing recipes (A1).** `CreateRecipePack.flat()` wrote every
  ingredient as `{"item": …}`, so a tag material (kept as `"#tag"` by the reader) produced an invalid
  ingredient: the file was written, the GUI reported success, and the recipe silently never loaded.
  Flat payloads now normalise ingredients through the same `asIngredient()` path as the assembly
  payloads, so `{"tag": …}` is emitted for `#tag` references.
- **Plan and embedded recipe now share one material list (A2).** The plan dropped materials equal to
  the product ("self-supplied") while the derived recipe still consumed them, so an affected plan was
  one station short and building it exactly as shown produced nothing. Both now use the same ordered,
  unfiltered material list; self-referencing materials appear as ordinary stations.
- **Scheme Loader contributions can no longer outlive their cabinet (A3).** A loader's contribution
  was keyed by its coordinates and only removed by `onRemove`, so a cabinet that vanished without that
  callback (relocation, `/clone`, rollback) left its recipes active in the world forever. Vanilla
  pistons cannot move block-entity blocks at all, so this was not a piston-specific bug; the fix is
  two-fold: the block entity now persists the key it registered under (and drops it when it comes
  back at a different position), and the server sweeps orphaned contribution files on start-up —
  but only for coordinates whose chunk is loaded and which no longer hold a Scheme Loader.
- **Union order is deterministic.** When two loaders contribute the same recipe file name, the winner
  no longer depends on the filesystem's directory enumeration order.
- **Datapack format corrected (B4).** `pack.mcmeta` used pack format 34 (the 1.21.1 *resource* pack
  format); the data pack format is 48. The pack loaded anyway only because the server tolerates
  older-format world datapacks — it is now written with the correct format.
- **Headless self test asserts recipes actually load (B1).** The datapack-install check only verified
  that files were written; a recipe the server refuses to parse still passed. It now asserts both test
  recipes are present in the live `RecipeManager` after the reload.
- **Dismantler refund count cross-checked against the live recipe (A4).** The refund scaled by the
  `result.count` text in the datapack JSON, which a modded recipe can understate; it now takes the
  larger of that text and the live `ItemStack.getCount()` of the recipe result
  (`ServerRecipeLookup.collectOutputs` now propagates it, `RecipeDescriptor.outputCount`), so a
  `count > 1` recipe can no longer be arbitraged by dismantling a single product.
- **The computer GUI now says why a computation failed (M7).** The specific reason (no recipe found /
  no usable output / no registry id) was only written to `lastError` and never shown; a second menu
  data slot now syncs it and the screen renders it above the generic message.
- **Output count propagates everywhere (B7).** The per-craft count used for plans and embedded recipes
  came from the JSON text only; both `RecipeDeriver` and the computer now take the larger of the JSON
  count and the live recipe result's stack size.
- **No more zero-station "successful" plans (A5).** A recipe with no usable materials could still write
  a scheme (`LineScheme.isEmpty() == true`) carrying an installable recipe and report
  `RESULT_GENERATED`; it is now refused with `RESULT_NOT_CONVERTIBLE` and the carriers are left alone.

### Changed

- **The headless self test no longer touches live state.** `qa/SelfTest` used
  `CreateRecipePack.install()/deactivate()`, whose first step deletes the whole `cpl_converted` pack —
  running the self test therefore wiped every scheme loader's `contributions/`. It now installs into an
  isolated `cpl_converted_selftest` pack and asserts that the live pack and its contributions survive.
- **Self test grew from 7 to 9 checks**: `Tag ingredients kept in flat recipes` (A1 regression) and
  `Deriver refuses unconvertible recipe` (single-material crafting must be refused, not faked). The
  TC-01 positive/negative checks now run against the production derivation path (`RecipeDeriver`)
  instead of the removed legacy mapper.
- **The client caches recipe scans (M11).** The compute button re-enumerated and parsed every recipe
  JSON on the client for every click; the resolver now keeps a small session cache keyed by item id.
- **The union datapack is only rewritten when it actually changes (B6).** Placing or touching a loader
  used to delete and rewrite every recipe file and run `/reload` unconditionally; the rebuild is now
  skipped when the merged content is byte-identical to the last write.
- **Dead code removed.** The legacy whole-pack `install()/deactivate()` (a footgun that wiped every
  loader's contributions), `installEntries`, `tryConvertOne`, `keyMap`, `patternFromRowMajor`, the
  planning-only `RecipeMapper.map`/`Mappers.get()`/`MappingResult` engine, `RESULT_NO_CLIPBOARD`,
  `computeNow()`, and several unused helpers are gone.
- **In-game mod metadata now links the issue tracker** (`issueTrackerURL` expanded from
  `gradle.properties`, which no longer claims the repository does not exist).

## [1.0.2] — 2026-09-13

### Changed

- **Plans now read as one linear chain**: `[base] → [machine 1 + material 1] → [machine 2 + material 2] → … → [product]`.
  Step 1 puts the base onto the line; every following station is exactly one machine paired with the single
  material it applies; the carried item is chained through the stations (base → Generic Intermediate → …) and
  only the last station yields the product. Previously extra materials were all listed as separate feed steps
  *before* the machines, so which machine applied which material was not visible. If a recipe selects more
  machines than there are extra materials, the surplus machines still get their own station, so no facility
  the player must place is hidden.

### Fixed

- **No more plans for recipes that cannot be converted.** A scheme with no installable Create recipe was a
  promise the mod could not keep (single-material recipes such as iron blocks, or targets already produced by
  a native Create process) and it consumed the player's paper / clipboard / blank Line Scheme for nothing.
  The computer now refuses instead, says why, and leaves the carriers untouched.

## [1.0.1] — 2026-09-13

### Fixed

- **Dismantler item duplication.** The refund ignored how many items a recipe yields per craft:
  it consumed a single output item and refunded one of every input. Any recipe with a yield
  greater than 1 therefore minted items — vanilla `minecraft:iron_ingot_from_iron_block`
  (1 iron block → **9** iron ingots) turned a single iron ingot into a whole block, a 9× gain
  per operation, repeatable forever. Every block ↔ ingot/nugget pair in vanilla has this shape,
  as do modded multi-output recipes. The dismantler now requires — and consumes — a full batch of
  `count` items, where `count` is the recipe's own yield, making the refund the exact inverse of
  the recipe. Holding fewer items than the batch size is refused outright (nothing consumed,
  nothing produced), and the in-game hint says so.

## [1.0.0] — 2026-09-13

First public release. Requires **Minecraft 1.21.1**, **NeoForge 21.1.249+** and **Create 6.0.10+**.
JEI is optional (recipe viewer only — this mod does not call its API).

### Added

- **Production Computer** — 3 slots (target / carrier / clipboard). Press *Compute* to turn any
  recipe into an ordered production-line plan, write it onto a carrier (paper, clipboard or
  Line Scheme) and inject a `custom_data.LineBuildGuide` build guide.
- **Line Scheme** — item carrying the plan (`Version / RecipeId / OutputItem / BaseMaterial /
  Steps[]` plus cached embedded Create recipes).
- **Scheme Loader** — 16-slot cabinet (2×8). Every scheme inserted is activated, and several
  loaders/schemes combine (union) so multi-stage lines work. Emits a redstone signal while
  recipes are active.
- **Dismantler** — reverts a Generic Intermediate or finished product into its raw materials and
  leaves a read-only Line Scheme Mirror.
- **Generic Intermediate** — the opaque transitional item used by generated
  `create:sequenced_assembly` recipes (extends Create's `SequencedAssemblyItem`, so assembly
  progress is tracked correctly).
- **Line Scheme Mirror** — text-only, read-only snapshot of a plan. Structurally incapable of
  being activated or fed back into the Dismantler.
- Recipe conversion: machine processes become flat `create:<type>` recipes (crushing, milling,
  mixing, pressing, cutting, …); crafting/assembly becomes `create:sequenced_assembly`
  (base material + one Deployer station per extra material, `loops=1`, output count preserved);
  native `create:mechanical_crafting` recipes additionally get a non-conflicting sequenced-assembly
  variant.
- Tag fidelity: source recipe tags are written back as `{"tag": …}` so any tag member matches —
  this is what makes e.g. 48-round rifle ammo craftable.
- Extensible mapping dictionary (24 built-in categories) via
  `config/create_productionline-mappings.json`, which also selects `assemblyMode`
  (`sequenced` default, `mechanical` optional).
- Headless QA self-test (9 checks) on a real server:
  `gradlew runServer -PselfTest` (9 checks, then the server halts).
  Pass criterion: the last log line matches `\d+ passed, 0 failed` — the count is a snapshot
  (number of `check("…")` calls in `qa/SelfTest.java`), never a hard-coded acceptance value.

### Security

- **Server is the only authority.** Nothing stored on an item and nothing sent by a client is
  trusted:
  - the Scheme Loader accepts **only** genuine Line Scheme items and re-derives every recipe from
    the scheme's `recipeId` against the live server `RecipeManager`;
  - the Dismantler refuses to consume unless the scheme is genuine, the item matches the scheme's
    output, the recipe resolves server-side and no refund input is a `#tag`;
  - the compute entry point carries a real `recipeId` that the server re-resolves and verifies
    against the target slot; unverifiable client data can produce a plan but **never** an
    installable recipe;
  - payload handlers validate `menu.stillValid(player)` before touching any slot.
- Mirrors are text-only and can never be activated or re-fed.

### Known limits

- Sequence lines consume 1 unit of each material per step (cheaper than the source grid when a
  material repeats) but still produce output.
- Results carry item id + count only — recipes with NBT/enchantments/state yield the plain variant.
- Multi-level intermediates are not recursed into a single scheme: compute each stage and activate
  them together as a union in the 16-slot loader.
- The Dismantler refuses `#tag` inputs or recipes it cannot resolve server-side (refuses rather
  than swallowing items).
- Create's `assets/` are all rights reserved: this mod only runtime-references its GUI/textures and
  never bundles copies. The binding statement of this constraint is in
  [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) → "Create assets are All Rights Reserved —
  do not copy them".
- Changing a loader's schemes triggers a datapack rewrite + `/reload`; the rewrite is skipped when the
  merged content is unchanged, but on very large packs a real change can still cause a brief hitch.
- On a dedicated server the client cannot see world datapacks, which is why the server always
  re-resolves recipes itself.

### Notes

- Licensing: **MIT**.
- Build: `gradlew build` (JDK 21). See `RELEASING.md` for the publication flow.

<!-- 1.0.0 / 1.0.1 were never tagged on GitHub (only v1.0.2 exists), so those two entries point at the
     Modrinth version list instead of a compare URL that would 404. Switch them to
     https://github.com/Es-254/Create-ProductionLine/compare/v1.0.1...v1.0.2 style links once the
     older tags are pushed. -->
[1.0.3]: https://github.com/Es-254/Create-ProductionLine/releases/tag/v1.0.3
[1.0.2]: https://github.com/Es-254/Create-ProductionLine/releases/tag/v1.0.2
[1.0.1]: https://modrinth.com/project/createproductionline/versions
[1.0.0]: https://modrinth.com/project/createproductionline/versions
