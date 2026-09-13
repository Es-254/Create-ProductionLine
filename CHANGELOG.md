# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- Headless QA self-test (6 checks) on a real server:
  `gradlew runServer -Dcreate_productionline.selfTest=true`.

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
  never bundles copies.
- Replacing a scheme triggers a full datapack rebuild + `/reload` per change; on very large packs
  this can cause a brief hitch.
- On a dedicated server the client cannot see world datapacks, which is why the server always
  re-resolves recipes itself.

### Notes

- Licensing: **MIT**.
- Build: `gradlew build` (JDK 21). See `RELEASING.md` for the publication flow.

[1.0.0]: https://modrinth.com/project/createproductionline/versions
