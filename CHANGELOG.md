# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**Version policy / 版本规范** (see `RELEASING.md`):

- **release**: `1.0.x`, the value of `mod_version`; `1.0.2` is the current stable release (`1.0.1` is the
  one before it).
- **beta**: also a `1.0.x` value, published as a pre-release when `mod_version_type=beta`
  (Modrinth/CurseForge channel *Beta*, GitHub pre-release). The first such cut was `1.0.2` on
  2026-09-17, which was promoted to a release the same day.
- **snapshot (alpha)**: `x.y.z-snapshot.0.0.N` with `mod_version_type=alpha` — work in progress towards
  `x.y.z`. `1.0.3-snapshot.0.0.1` is the first.
- **dev (beta)**: `0.0.0-dev.N`, built with `gradlew build -PdevBuild` (N from `dev-build.txt`),
  always published as `beta`.

The four jars of the **old numbering** (`1.0.0` / `1.0.1` / `1.0.2` / `1.0.3`) predate this policy: they
were published with channel `release` back then, and they are recorded below as the development snapshots
`0.0.0-dev.1` … `0.0.0-dev.4`. No entry below is one of those four jars. The **first official release** is
`1.0.1` as cut under the current policy — it shares a number with one of those snapshots but is a
different artifact. The number `1.0.2` was reused for the anvil-flow cut, so the old snapshot of that
number no longer exists anywhere. The four `0.0.0-dev.N` sections below are that renumbered record,
and they keep the `(beta)` marker a dev build carries **under the current policy** — the channel they were
actually published with at the time was `release`.

## Unreleased

### Fixed

- **Intermediates could not be dismantled at all.** The intermediate branch keyed on *our* item
  (`Generic Intermediate`), so every Create-native transitional item — whose provenance is the
  `SEQUENCED_ASSEMBLY` component on the item itself — fell through to the finished-product path, where it
  could only ever answer "no recipe for it". The branch now keys on the **component**, which is what
  actually records provenance: anything carrying it is refunded from the sequence recipe it names (base +
  the steps that already ran), while our own item *without* the component is still refused as "no
  processing record". A refusal now also names the recipe it could not find.
  `RecipeJsonReader.sequenceParts` was hardened at the same time: an alternatives array resolves to its
  first usable entry, and when a recipe writes no `ingredient` at all the base is taken from the first
  sequence step's `ingredients[0]` — that slot *is* the item entering the line.
- **The compute result never reached the player's chat.** The run is queued for the next tick after the
  button press, so reading the status in the same tick always saw `RESULT_EMPTY` and sent the "how to use
  me" line instead of the outcome. The computer now remembers who asked (by UUID) and reports the real
  status — product, incremental marker, target output, material budget, plan size, embedded recipes —
  once the run has finished.
- **A dismantle's materials landed on the floor.** They now go into the inventory of the player who
  pressed **Dismantle** (anything that does not fit falls at their feet); only the no-player path — the
  self test, scripted use — still pops them at the block.
- **A scheme that cannot avoid referencing itself did not say so.** `1A + 1B = 2A` and friends are
  incremental lines: they run from a single seed item, which the numbers alone never explained. The
  scheme tooltip and the computer's chat/panel now state it —
  `该物品无法避免自引用，已转换为增量配方`.
- **A material the line merely *uses* read as one it consumes.** A Deployer holding an item applies it in
  USE mode, so a smithing recipe's `base` (the equipment being upgraded — the diamond sword on its way to
  netherite) stays on the line and is never consumed, yet the plan chain listed it like any other input.
  The computer now records those materials on the scheme (`SchemeRoles`, taken from the source recipe's
  own roles) and the chain marks them `（不消耗）`. The steps alone cannot express that difference, and
  nothing else knows the recipe's roles once the plan has been written.
- **The Scheme Loader's title was pressed by its own panel.** That background's well starts at y=13 (the
  other two start lower) and a glyph is 8 px tall: at y=6 the title's bottom row sat exactly on the
  well's top edge. The title moved up one row and the self test now asserts the clearance.
- **The build-guide block is gone from the tooltip**, by request: its hand-written hint lines were generic
  boilerplate ("belt first, one Deployer per material…") that said nothing about the plan at hand. The
  `LineBuildGuide` payload is still written — it is what makes a plain item a carrier — it is simply not
  rendered, and its four now-unused language keys were removed.
- **Three machines never appeared in their own Ponder scenes.** The scenes place their machine with
  `setBlock` and then revealed it with `showSection`, but a section shows what the *structure schematic's
  backup* holds — a block the scene placed at runtime is not in it, so the base plate appeared and the
  machine did not. Create's own scenes reveal runtime-placed blocks with `showIndependentSection`; the
  computer, the Dismantler, and the Scheme Loader scene's machine, lamp, belt row and two Deployers now do
  the same (and the loader hides them again through the returned section links).
- **The Ponder panel was blank, and showed the half a scene has no use for.** Only the background texture
  was drawn — but the backgrounds are just a border, a well and a groove: the slot frames, the title and
  the button are drawn *in code* by the screens, so the overlay read as an empty grey box. It also blitted
  all 176x196 px, including the player-inventory half that a Ponder scene has no inventory for. The panel
  now stops at the groove and draws what the screens draw into that half: one Create-style cell frame per
  slot (at `slot - 1`, as the screens do it), the stacks, the panel title, and the button — same position,
  same size, same vanilla button sprite as the real widget.
- **A and D switched nothing in a Ponder scene.** Chapter keys *are* the arrow buttons' shortcuts
  (`Options.keyLeft` / `keyRight`, i.e. A and D by default), and those buttons only exist for a component
  with more than one scene — `PonderChapter` itself is a stub in this Ponder version (`getTitle()` returns
  an empty constant and `PonderUI`'s chapter field is only ever `null`). With one storyboard per machine
  there were three single-scene entries, hence no arrows and no chapter keys. The Production Computer and
  the Scheme Loader are now both registered for both items, so they are one entry with two chapters
  (writing the plan / loading it) that A and D page through; the Dismantler stays its own entry.
- **The machines were still missing, because the scenes were placing blocks Ponder discards in silence.**
  Scene code may only touch positions inside the block bounding box its structure schematic actually
  *places*: `setBlock` / `modifyBlock` run through `ReplaceBlocksInstruction`, whose first line is
  `if (level.getBounds().isInside(pos))`, and `SchematicLevel` computes that box from the blocks that were
  placed — not from the schematic's declared `size`. The schematics held the base plate alone (y=0), so the
  machines, the cabinet's lamp and the whole closing belt/Deployer picture, all at y≥1, were thrown away
  with no log line at all — indistinguishable from a scene that never drew them. They now live **in** the
  schematics and the scenes only show and hide them, which is how Create's own scenes are built (its
  ponder schematics carry the machines, the scene reveals sections of them). The three schematics are now
  generated by `tools/ponder-schematics.js`, which also writes down the two NBT traps in full, and the self
  test's new 24th check asserts the invariant from both sides: each scene's schematic holds the expected
  block at every position that scene shows, hides or modifies, and that position lies inside the box the
  schematic's blocks span.
- **The closing picture was a still life.** The belt-and-Deployers panel showed the shape of a line but
  nothing ran: no power source, no motion. Ponder does run Create's kinetics — `setKineticSpeed` writes the
  speed onto the kinetic block entities of a selection, which is how Create's own scenes start a belt
  (`DeployerScenes` does `setKineticSpeed(select().layer(1), -32F)`) — so the loader schematic now carries a
  **Creative Motor** beside the belt's first pulley (at (0,1,0) facing south, the axis a belt travelling
  along x is turned by), and the scene sets the speed on the motor and the belt, drops the base item onto
  the running belt and lets each Deployer reach down onto it and retract again, with a success flash. The
  line reads as a powered line now, not as a diagram of one.
- **The Deployers were one cell too low to reach the belt, and the belt carried nothing.** A Deployer acts
  on the position **two** blocks in front of itself, so the cells at y=2 facing down work the plate at y=0
  and never touch the belt at y=1 — exactly the "leave one cell empty" the author described from a real
  sequenced assembly. They now hang two cells above the belt (schematic grown to 5x4x5), holding the
  material they add, and the belt carries the whole story: the base goes on under the first Deployer, which
  turns it into a **Generic Intermediate** (this mod's own unfinished-line item), and the second — waiting
  at the belt's last cell, where an item stops by itself, so the picture cannot drift out of step with the
  belt speed — finishes it into the product. Base, intermediate and product all travel the belt.
- **The narration boxes ran across the GUI panel.** Ponder anchors a speech box to the pointed position
  (`min(0.75 * width, projectedX + 50)`, `projectedY + 3`), and the scenes pointed at `topOf(machine)`,
  which projects to roughly 40% of the screen height — the panel's own band. They now point at the machine's
  **near-bottom** corner (the side facing the camera, just above the plate), which drops the boxes to about
  two thirds of the height, and the panel moved up to the top edge (`y=6`). Both boxes and panel are clear
  of each other in every scene, with the projection worked out from Ponder's own transform rather than by
  eye.
- **Nothing ever appeared on the belt.** A belt placed from a schematic does not know which of its segments
  is the controller — that link is per-instance state and is lost when the ponder level is restored from its
  backup — and `createItemOnBelt` then does nothing at all: no item, no error, no log line. Create fixes
  exactly this in `CreatePonderPlugin.onPonderLevelRestore` via
  `PonderWorldBlockEntityFix.fixControllerBlockEntities`; our plugin now implements the same hook. The item
  is also inserted the way Create's own scenes do it: at the belt's `start` cell, passing the side the item
  comes from (a belt facing east takes items from the west), and the first Deployer now waits above that very
  cell, so base and hand start in position instead of relying on a guessed travel time.
- **The redstone signal had no wire to travel along, and the wire it got could not stay powered.** The
  narration promises that an active cabinet emits a redstone signal, but the lamp simply appeared already
  lit. The schematic now carries redstone dust between the cabinet (2,1,2) and the lamp (4,1,2) — spelled
  with the two connection flags, so it draws as a straight run rather than a dot. Raising its `power` from
  the scene does **not** work and never would: a redstone wire recomputes its own strength on any neighbour
  change (`updatePowerStrength`), and the ponder world contains no redstone source, so the wire was put
  straight back to 0 in the same tick — the lamp lit up (its state did change, and it notified the lamp) and
  the wire stayed dark, which is exactly what was reported. Both states are now baked into the schematic, so
  the wire arrives already carrying the signal.
- **The belt could not hold an item, because our schematics carried no block-entity data.** Create's own
  Ponder schematics ship block-entity NBT on every kinetic block, and a belt's carries `Index`, `Length`,
  `IsController` and `Controller` — with the item `Inventory` on the chain head. Without them a segment has
  no place in its chain and no inventory to insert into, so `createItemOnBelt` inserted "successfully" and
  nothing ever appeared or moved. The generator now writes that NBT for every belt segment (schematic
  coordinates are world coordinates here, so the controller link is exact rather than stale like Create's
  authored-in-world values), and the belt also carries its speed, so the line is running the moment it
  appears.
- **The items ride the belt's own inventory, because runtime insertion never showed one.** Even with the
  controller hook and the full block-entity NBT in place, `createItemOnBelt` produced no visible item in
  game — the belt's inventory is the shape that works: `BeltInventory.write` /
  `TransportedItemStack.serializeNBT` (`Item`, `Pos` in belt cells, `Offset`, `InSegment`, `InDirection`,
  `Locked`), which the scene drives directly (see the next entry) and which the schematic can also carry,
  `Locked` being exactly the state Create uses for an item a Deployer is working on.
- **The closing line is now the whole process, and it runs the right way round.** Three corrections the
  author gave from the game, all of them right: an east-facing belt carries items east only at a
  **negative** speed (`getDirectionAwareBeltMovementSpeed` negates the movement on the x axis, which is why
  Create's own `compacting.nbt` ships `facing:east` with `Speed:-32`), the picture and the sample scheme
  take **raw iron** rather than ore (`minecraft:iron_ingot_from_blasting_raw_iron`), and the two Deployers
  belong on the **second and fourth cells** of the five-cell belt. The scene drives the belt's own
  `Inventory` NBT (`BlockEntityDataInstruction` saves, applies and reloads the block entity, so a rewrite
  takes effect at once): raw iron is fed in and held, released for a cell, held under the first Deployer —
  which turns it into a Generic Intermediate — carried two cells to the second, turned into the product,
  and taken off the end with a dropped item beside it. Positions are written explicitly rather than
  estimated from belt speed, and `Locked` holds an item under a hand.
- **The wire would not light up, because a redstone wire recomputes itself.** `RedStoneWireBlock`
  recalculates its strength in `onPlace` as well as on neighbour changes, and the ponder world has no
  redstone source: baking `power=15` into the schematic did not survive placement, and setting it at
  runtime was undone by the lamp's own state change notifying the wire back. The step now lights the
  **lamp first** — that update reaches a wire still sitting at 0, which does not care — and raises the
  wire's strength afterwards, with no neighbour left to change it, so the wire keeps its 15 and re-bakes
  bright.

### Added

- **Generated recipes are retired, not deleted, when they leave the union.** An item crafted on a line
  outlives the line: the Dismantler refunds an unfinished intermediate from the recipe that gave it its
  provenance, so deleting that recipe the moment its scheme left the loader turned every leftover
  intermediate into scrap — reported in game as "找不到它来源的序列配方 cpl:…". The pack now moves retired
  files to `<world>/cpl_retired/` (outside `datapacks/`, so nothing loads them again) and the reader falls
  back to that folder when the live pack no longer has the recipe. Intermediates orphaned *before* this
  change stay unrecoverable unless their scheme is put back once, which regenerates the file.
- **Ponder tutorials for the three machines** (the ponder key — `W` by default — on a machine). The
  Production Computer and the Scheme Loader are the two chapters of one entry, writing a plan and then
  loading it, and the Dismantler is an entry of its own; all three sit in the `machines` tag in the ponder
  index. Each scene carries its own structure schematic at `assets/create_productionline/ponder/<id>.nbt`,
  which is what draws the checkered base plate **and holds every prop the scene shows** — the machines, the
  cabinet's lamp (lit, so the narration's "emits a redstone signal" is what you see) and the closing
  belt-with-two-Deployers picture. Those files are generated by `tools/ponder-schematics.js`, added here so
  the binaries have a readable source; they are gzip-compressed vanilla structure NBT, and `size` plus every
  block's `pos` have to be a `TAG_List` of `TAG_Int` — written as `TAG_Int_Array` they load as an empty
  structure **with no error at all**, which is what left both earlier attempts blank. The narration is the
  author's own wording, one `showText` per row of his table, in
  `create_productionline.ponder.<sceneId>.header` / `.text_<n>` — numbered by the order the scene shows
  them. A panel in the corner shows the machine half of the matching GUI with the items the scene places
  in it.

## 1.0.3-snapshot.0.0.2 — 2026-09-25 (alpha)

**The second `1.0.3` snapshot: the whole line so far, for an in-game pass before the release cut.**
`1.0.3-snapshot.0.0.1` carried the recipe-refresh work alone; this one adds everything the line has
produced since — the three redrawn machines (Production Computer in `0.0.0-dev.5`, Scheme Loader with its
fill bar and Dismantler in `0.0.0-dev.6`), the normalised texture names, the GUI layout pass, the
dismantler's decision table and the computer's private chat output. See the `0.0.0-dev.N` sections below
for the per-build detail. `1.0.2` remains the current stable release; a snapshot is a work in progress
towards `1.0.3`, not a finished line.

### Fixed

- **A carrier's build guide was written but never shown.** The Production Computer writes the plan
  <em>and</em> the `LineBuildGuide` onto the same carrier (paper takes both), but the tooltip returned
  right after the plan block, so the guide could never be reached on exactly the items it exists for —
  and the three `guide.*` hint lines were dead text because of it. The tooltip now renders the guide
  block after the plan and uses those localised build instructions (belt first, one Deployer per material
  facing down, where the product comes out, and the return belt a repeating line needs), gated so a
  single-machine plan is not given assembly-line advice it cannot use. The stored `LineBuildGuide` payload
  is unchanged — it stays the machine-readable record carriers are recognised by.
- **The Scheme Loader's bar grew from the wrong end.** The six strips had been ordered left→right as
  `bar_1 … bar_6`, which inverts the author's layout: in the Blockbench export the rightmost strip (x=13)
  already carried `bar_1` and the leftmost (x=3) carried `bar_6` (with one mis-dragged placeholder in
  between). The bar now fills from the **right**, and each stage keeps the rightmost N strips
  (`scheme_loader_bar_1` draws x=13, `bar_2` adds x=11, and so on). The fill arithmetic is unchanged:
  `segments = ceil(count * 6 / 16)`. Spotted in game — the direction is the one thing no headless check
  could have caught.
- **All three GUIs had their contents laid out against the wrong reference.** The backgrounds are
  hand-drawn and each has a recessed well near the top; the code had been placing cells and text by hand,
  so: the **Scheme Loader**'s 2×8 grid sat flush left inside its well (an 18 px hole on the right, and the
  grid 2 px high vertically) with its first status line printed **on** the well's bottom edge; the
  **Dismantler**'s two slots hugged the ends of their well (no margin at all, 1 px from the top) and its
  hint text was drawn at y=40, straight **across** the well and both slots; the **Production Computer**'s
  three slots were 2 px high and its status list could grow to a fifth row **on** the player-inventory
  groove and the "Inventory" label. Every cell grid is now centred in its well, every text row starts
  below the well and stops above the groove or the button. The numbers live in one place
  (`menu/GuiLayout`) that both the menus and the screens read, and the self test asserts the invariants —
  a layout drift is invisible to every resource check we have and only shows up in game.
- **A refused dismantle was silent.** The button promised "materials back + mirror" but pressing it with
  an unmatched item, a plain intermediate, a missing recipe or too few items for one batch did nothing at
  all, with no clue why. `DismantlerBlockEntity.revert()` now returns a typed result
  (`RevertResult.DONE` / `NOTHING_HELD` / `NO_PROVENANCE` / `RECIPE_MISSING` / `OUTPUT_MISMATCH` /
  `NOT_ENOUGH` / `NOT_REFUNDABLE`) and the server answers the player who pressed it, privately, with the
  matching reason.
- **The dismantler's hint overstated the scheme slot.** It read "right slot = scheme" while the scheme is
  optional (a mirror snapshot is synthesised from the recipe when the slot is empty) and did not say where
  the refunds go — they are dropped beside the machine. Both languages now say the slot is optional, and
  that a scheme in the item slot is erased rather than refunded.
- **A refund was not the inverse it claimed to be for recipes that consume their own product.** The
  refund list dropped any input equal to the product, so `1 A + 1 B = 2 A` consumed `2 A` and returned
  only `B` — eating an `A`. The product now stays in the refund list, which makes the batch a true
  inverse (`2 A -> 1 A + 1 B`) and, as before, never lets a `count > 1` recipe be farmed one item at a
  time. The self test dismantles a real doubling recipe and counts the dropped items. The refund is still
  a set of **unique materials, one each** rather than one per recipe slot — `2 planks -> 4 sticks` hands
  back a single plank. That is deliberate and now documented in `docs/usage.md` and `docs/security.md`:
  what prevents farming is the batch rule (`consume >= count`), not the size of the refund, while an
  unfinished intermediate keeps counting per deploy step so nothing it absorbed is lost.
- **Fluid-form ingredients vanished without a word.** A fluid cannot exist as an item, so it can never be
  part of a refund — but the player used to get no hint at all that part of the recipe was not coming
  back. The dismantler now counts the fluid ingredients of the source recipe and says so
  (`另有 N 项流体原料无法退还` / "Another N fluid ingredient(s) could not be refunded"). A pure-fluid
  recipe still refuses outright (`NOT_REFUNDABLE`) without consuming anything.

### Added

- **The Dismantler erases a written Line Scheme back to a blank one.** A plan costs nothing to
  author — the Production Computer only writes onto the carrier, it consumes no materials — so a
  scheme that is no longer wanted should not be a dead item. Put one into the item slot and press
  **Dismantle**: the plan is erased and a **fresh blank scheme** takes its place. A blank scheme has
  nothing to erase and a mirror is a read-only snapshot; both are refused with their own message
  instead of being mistaken for a product that does not match the plan.
- **The Production Computer reports its result in the player's chat.** The panel can only show four rows
  between its button and the inventory groove, so the same status list (product, target output / repeat
  budget, material budget, plan size, embedded recipe count) is now also sent — privately, to whoever
  pressed **Compute**, never as a broadcast — as chat lines. The list is built once
  (`menu/ComputerStatus`) and used by both the screen and the server, so panel and chat can never drift
  apart. A long plan is no longer truncated away.
- **Self test grew to 23 checks** (was 20): `GUI layout fits the drawn wells` asserts that every slot
  grid is centred inside its well and that text starts below the well and stays clear of the button and
  of the player-inventory groove; `Dismantler decision table, doubling refund, fluid notice` places a
  real dismantler, runs a real `1 A + 1 B = 2 A` recipe through it and counts the items that actually
  dropped; and `Computer writes plan + guide onto both carriers` drives a real Production Computer and
  asserts both carriers end up holding the plan <em>and</em> the build guide — the data side of the
  tooltip fix above.

## 0.0.0-dev.6 — 2026-09-25 (beta)

A development snapshot of `main`, published **to GitHub only** — no Modrinth and no CurseForge upload. It
carries **all three redrawn machines** (the Production Computer from `0.0.0-dev.5`, plus the Scheme Loader
with its new fill bar and the Dismantler) and normalises three texture names the exports misspelled.

### Added

- **The Scheme Loader's front bar shows how many schemes are loaded.** The redrawn cabinet carries six bar
  strips on its front; `SchemeLoaderBlock.FILL` (0 … 16) mirrors the loaded-scheme count into the block
  state, and `scheme_loader_empty` / `scheme_loader_bar_1…5` / `scheme_loader` draw one stage each, so the
  client paints `ceil(count * 6 / 16)` strips as schemes go in and out. The count comes from the same
  whitelist the slot check uses (`filledSlots()`), so paper, a mirror or a forged stack never lights the
  bar. Nothing is broadcast and there is no custom renderer: the property rides the ordinary block-state
  update (`setBlock(…, 2)`), and the bar follows the inventory the moment a slot changes.

### Changed

- **The Scheme Loader has been redrawn** like the computer: a 14-element Blockbench model — base plate, top
  plate, four corner pillars, a 256×256 baseboard, a front screen and the six bar strips — replaces the old
  16×16 four-face cube, and the block declares `noOcclusion` for the same reason (open-sided chassis). The
  author renamed the shared shell texture to `production_block_shell`, so the computer's model follows that
  rename, and the four old loader face textures are gone. One note for the next export: the six strips had
  placeholder textures (`bar_1`, `bar_3`, `bar_3`, `bar_4`, `bar_5`, `bar_6` — `bar_2` unused), so the
  mapping is now left→right = `bar_1 … bar_6`.
- **The Dismantler has been redrawn** — the third and last machine: 18 elements (base and top plates, four
  corner pillars, an inner baseboard, three stepped bars, four holders, a button and a rod) over twelve part
  textures (mostly 16×16, the baseboard 64×64) plus the shared shell. Same repairs, plus one this export
  added: it left the `block/` prefix off **entirely** (`production_block_shell`, not
  `block/production_block_shell`), which resolves to `minecraft:` just the same. `minecraft:block/block` is
  back as the parent, the block takes `noOcclusion` like its two siblings, and the four old face textures
  are gone. Because its parts map a whole face onto a 1 px strip of a 16×16 texture, the install samples
  every face's pixels: 108 faces, none transparent, colours matching the parts (holders 120, stepped bars
  180/85, button pure red, shell pale yellow).
- **Three misspelled texture names are normalised**: `loader_sceen` → `loader_screen`,
  `production_dismantler_hoder1…4` / `hoderb` → `holder1…4` / `holderb`, and
  `production_dismantler_basebord` → `baseboard`. Every model reference follows; the source folder carries
  the same names, and the unused byte-identical duplicate `Dismantler_basebord.png` is gone. Renaming them
  inside the Blockbench project too keeps the next export from bringing the old spelling back.

## 0.0.0-dev.5 — 2026-09-24 (beta)

A development snapshot of `main`, published **to GitHub only** — no Modrinth and no CurseForge upload for
this one. It carries the redrawn Production Computer below plus everything already shipped in
`1.0.3-snapshot.0.0.1` (the recipe-only refresh).

### Changed

- **The Production Computer has been redrawn.** Its single-cube, five-face 16×16 art is replaced by a
  16-element Blockbench model — base plate, top plate, four corner pillars and a front bezel carrying a
  screen and a keyboard — with four textures (shell 32×32, baseboard 64×64, board 64×64, keyboard
  128×128). The block now declares `noOcclusion`: an open-sided chassis is not a full cube, so without it
  the neighbouring blocks cull their faces against it and its own boundary faces vanish into them. The
  five old face textures are gone. Three things in the export had to be repaired before it could render:
  its bare texture paths (`block/…` resolves to `minecraft:`, i.e. the purple/black missing texture) are
  namespaced, its seven occluded `#missing` pillar faces point at the shell texture, and
  `minecraft:block/block` is back as the parent so the item keeps the standard GUI transforms.

  Scope of the `1.0.3` art work: **blocks only**. The item icons and the GUI sheets were drawn for `1.0.1`
  and stay as they are; of the three machines, the Scheme Loader and the Dismantler are still to be drawn
  (one block at a time, each replacing the old 16×16 faces the same way).

## 1.0.3-snapshot.0.0.1 — 2026-09-20 (alpha)

**A performance optimization — and only part of the `1.0.3` line.** The `1.0.3` plan also covers redrawn
machine textures, the safety-mechanism pass, GUI work and a unified art style; none of that is in this jar.
What it does carry is the first piece of that line: the recipe-refresh rework, which is the path a scheme
activation pays for on every insert, so it is the one worth measuring first.

Published as version `1.0.3-snapshot.0.0.1`, channel *Alpha* on Modrinth and CurseForge, and a GitHub
pre-release. The `1.0.3` line is not finished: treat this jar as a preview of the performance work, not as a
release.

### Added

- **Recipe refresh without `/reload`.** A Scheme Loader used to run a full `/reload` whenever its recipes
  changed, which re-reads every data pack, tag, loot table, advancement and function — a visible tick spike
  on a modded world, for a change that only ever touches recipes. The recipes this pack owns are now parsed
  and swapped straight into the live `RecipeManager` (`RecipeHotSwap.applyOwned`, public API:
  `RecipeManager.replaceRecipes`), followed by the server's own post-reload sync, so nothing else is re-read
  and no pack folder has to be re-discovered. The data pack files are still written to
  `world/datapacks/cpl_converted` and stay the source of truth across restarts. A payload that cannot be
  parsed in place — a conditional recipe, or a file edited by hand — makes the swap refuse and the activation
  falls back to the full `/reload`, so the slow path is still there where it is the correct one.
- **`/cpl reload recipes`** (permission level 2) re-reads the recipe JSON of every data pack the server knows
  and installs the result through the recipe reload listener itself, so NeoForge recipe conditions and
  vanilla's error handling behave exactly as during a reload. Tags, loot tables, advancements and functions
  are not touched, and the reply goes only to whoever ran the command. A data pack folder that appeared
  *after* the last reload still needs a full `/reload` to be discovered — the documented boundary of the
  command.

### Changed

- **Performance: activating a scheme no longer reloads the world's data packs.** The old activation path ran
  a full `/reload` for every insert or removal, so a single cabinet cost a server-wide re-read of all data
  packs and every listener that hangs off them. Now the cost is a parse of this pack's own files, and the
  only server-wide path left is the explicit command: re-reading the recipes of every data pack took ~250 ms
  for 2,819 recipes on the test server, where a full reload also rebuilds tags, loot tables, advancements and
  functions. Nothing about the produced line changes — the same recipes are installed, just through a
  narrower door.
- **Self test grew to 20 checks** (was 18): a recipe file that appears after its data pack was discovered
  reaches the live `RecipeManager` through the recipe-only refresh, and this pack's payloads round-trip
  through the server's recipe codec under their data pack id while a conditional payload is refused.

## [1.0.2] — 2026-09-17 (release)

**Promoted from beta** on 2026-09-17: the same version number, now published as a **release** on all
three platforms (GitHub Release, CurseForge release type `release`, Modrinth channel *Release*). The jar
is the one built after the promotion pass described under *Changed* below — state machine extraction,
material budget line, documented trust model — so it supersedes the earlier 1.0.2 beta asset.

**CurseForge content replaced** on 2026-09-20: promotion first flipped the existing CurseForge file
(208,860 B beta jar) to release type `release`, which left the wrong bytes under a release label — the
upload API cannot swap a file's content. The author archived that file and the jar was uploaded again as
file `8924265`, so all three platforms now carry the same 215,411 B / `sha256:27e0a3c6…`.

*How this version first went out, kept for the record:* it shipped as a beta — Modrinth channel *Beta*,
CurseForge release type `beta`, GitHub Release marked as a pre-release, with **`1.0.1` the current stable
release** — and was promoted the same day. Existing schemes keep working unchanged — a V1 scheme item
loads with a target output and repeat count of 1 — and the only change on the way in is that the computer
now reads the target slot's stack size as the output the player wants.

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

- **The anvil state machine is now a pure function** (`line/scheme/SchemeAnvilMachine`): it takes the two
  slots plus the permission gate and returns the decision, and `AnvilSchemeCustomizer` only performs the
  side/permission gate, writes the output stack and takes over the anvil's numbers. All twelve rows of the
  table (not-our-item, non-OP, stacked scheme, clear, material-before-clearing, empty right slot, hammer,
  lock-without-material, single-material lock plus its notice, lone-product lock, and both operations on a
  locked scheme) are asserted by the self test — the first two in-play bugs of this feature lived in exactly
  that seam and no check covered it.
- **The plan reports its material budget.** `LineScheme.materialsPerPass()` and `materialBudget()` (units per
  pass times the repeat count) drive a new computer-panel line, and the loop hint says that its repeat count
  is measured from one base unit.
- **Headless self test grew to 18 checks** (was 13; 15 after the anvil flow above, 16 for the target-output
  / repeat budget, 18 after the promotion pass). The new assertions cover the assembly payload shape, the
  single-material fallback, the repeat budget (a doubling recipe with target output 4 must yield 3 passes, a
  material budget of 6 for two materials, a plan whose topology says `repeat 3x`, and an unreachable recipe
  (`p <= c`) that must not be looped) and all twelve rows of the anvil state table.

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

First public dev snapshot (published with channel `release` at the time; recorded as `beta` under the
current policy, superseded by the release `1.0.1`). Requires **Minecraft 1.21.1**,
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
