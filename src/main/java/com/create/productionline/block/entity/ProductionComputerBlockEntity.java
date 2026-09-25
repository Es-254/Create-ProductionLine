package com.create.productionline.block.entity;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.compat.ClipboardCompat;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * Block entity of the Production Computer.
 *
 * <p>Slots: 0 target item to produce, 1 line scheme (write target), 2 clipboard
 * (receives the build guide). Whenever the target item changes, the computer
 * resolves the recipe and writes the plan into the scheme item and the guide
 * into the clipboard item (TC-02).
 */
public class ProductionComputerBlockEntity extends BlockEntity {

    public static final int SLOT_TARGET = 0;
    public static final int SLOT_SCHEME = 1;
    public static final int SLOT_CLIPBOARD = 2;

    /** Result codes surfaced in the GUI. */
    public static final int RESULT_EMPTY = 0;   // nothing to compute yet
    public static final int RESULT_GENERATED = 6; // native Create recipe JSON written onto the carrier
    public static final int RESULT_NOT_CONVERTIBLE = 7; // no Create recipe could be generated -> nothing written
    public static final int RESULT_NO_SCHEME = 2;
    public static final int RESULT_NO_RECIPE = 4; // cannot map / no recipe found (TC-01)
    public static final int RESULT_NO_TARGET = 5;
    /**
     * No usable recipe, but the requester may author one by hand: a PLACEHOLDER scheme
     * (target only, empty recipe id, zero steps) was written onto the carriers. Only ever
     * reported for a requester at the authoring permission level (2, the same gate the anvil
     * flow uses); everybody else still gets {@link #RESULT_NO_RECIPE} and an untouched carrier.
     */
    public static final int RESULT_PLACEHOLDER = 8;

    /**
     * Machine-readable reason for the last failed run (M7). {@code resultCode}
     * only says WHICH stage failed ("no recipe" covers four different causes),
     * so the GUI cannot tell the player what to actually fix without this:
     * <ul>
     *   <li>{@code 0} — no specific reason (success, or a stage that needs none)</li>
     *   <li>{@code 1} — the server found no recipe producing the target item</li>
     *   <li>{@code 2} — a recipe exists, but it has no usable output</li>
     *   <li>{@code 3} — the target item has no registry id (unmappable)</li>
     *   <li>{@code 4} — a live recipe exists, but it cannot become a Create line</li>
     * </ul>
     */
    public static final int ERROR_NONE = 0;
    public static final int ERROR_NO_RECIPE_PRODUCING = 1;
    public static final int ERROR_NO_USABLE_OUTPUT = 2;
    public static final int ERROR_NO_REGISTRY_ID = 3;
    public static final int ERROR_NOT_CONVERTIBLE = 4;

    private final ModContainer inventory = new ModContainer(this, 3, this::onSlotChanged);
    private int resultCode = RESULT_EMPTY;
    private int lastErrorCode = 0;
    private String lastError = "";
    private boolean computeQueued = false;

    /**
     * The permission level that may turn a failed compute into a placeholder scheme. It is
     * the same gate the anvil custom flow uses ({@code AnvilSchemeCustomizer}), because it
     * hands out the same thing: the right to author a line by hand. The gate protects the
     * <em>surface</em>, not the world — a placeholder installs nothing on its own (its recipe
     * id is empty), so the worst a wrongly granted placeholder can do is put an inert item
     * into the requester's own carrier slot.
     */
    private static final int AUTHORING_PERMISSION_LEVEL = 2;

    /**
     * Whether the run queued by the last {@link #computeProvided} request may write a
     * placeholder scheme. Decided where the requester is known (the server-side entry point
     * holding the {@link net.minecraft.server.level.ServerPlayer}) and carried to the queued
     * run, which has no player any more. Transient on purpose and never sent by the client:
     * the compute payload has no such field, so a client cannot ask for it — a forged
     * request simply does not set it (fail closed).
     */
    private boolean placeholderPermitted = false;

    // --- client hint (NEVER authoritative) -----------------------------------
    // The client can only see its own resource packs, so it sends the registry id
    // of the recipe it matched plus the data it read. The server re-resolves that
    // id against its live RecipeManager and only accepts it when the recipe really
    // produces the item sitting in the target slot. The rest of the hint is a
    // display-only fallback used when the server finds no recipe at all — and a
    // fallback descriptor NEVER yields an installable recipe (anti-injection).
    private String hintRecipeId = "";
    private com.create.productionline.line.mapper.RecipeDescriptor hintFallback = null;

    /**
     * Who pressed [Compute], by UUID, so the outcome can be reported back once the
     * queued run has finished (see {@link #runCompute()}). Transient by design: it is
     * never saved, and it is cleared whether or not the player could be resolved.
     */
    private java.util.UUID notifyPlayer = null;

    /**
     * True while the computer itself writes the computed plan onto the carrier slots.
     * Those writes also fire {@link #onSlotChanged(int)}; without this guard the
     * fresh "generated" status would be wiped immediately.
     */
    private boolean writingCarriers = false;

    public ProductionComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRODUCTION_COMPUTER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
            ProductionComputerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (be.computeQueued) {
            be.computeQueued = false;
            be.runCompute();
        }
    }

    private void onSlotChanged(int slot) {
        // No auto computation: the player presses the "Compute" button once all
        // three slots are set (explicit trigger). Any change made BY THE PLAYER
        // invalidates the previous run, so the GUI falls back to its initial text
        // instead of still showing the last computed item.
        if (!writingCarriers) {
            resultCode = RESULT_EMPTY;
            lastErrorCode = ERROR_NONE;
            lastError = "";
            hintRecipeId = "";
            hintFallback = null;
            computeQueued = false;
        }
        setChanged();
    }

    /**
     * Records a client-resolved recipe HINT (nothing here is trusted).
     *
     * <p>{@code recipeId} is verified server-side against the live
     * {@code RecipeManager}; {@code categoryId}/{@code inputs}/{@code outputId} are
     * kept only as a display fallback and can never produce an installable recipe.
     *
     * <p>The compute itself is queued for the next tick, so the requester is stored
     * (by UUID, resolved when the run finishes) and told the outcome then: reading the
     * result code right after this call would always see {@code RESULT_EMPTY}.
     *
     * <p>This is also where the authoring permission is read, because it is the only place
     * that sees the player: {@code requester.hasPermissions(2)} is evaluated server-side on
     * the real player, and the outcome is stored for the queued run. A request that arrives
     * without a resolvable player (the API allows {@code null}) sets it to {@code false}, so
     * the gate fails closed.
     */
    public void computeProvided(net.minecraft.server.level.ServerPlayer requester, String targetId, String recipeId,
            String categoryId, java.util.List<String> inputs, String outputId) {
        if (level != null && !level.isClientSide) {
            ProductionLineMod.LOGGER.info("CPL compute hint from client: recipe={} target={} cat={} inputs={} out={}",
                    recipeId, targetId, categoryId, inputs, outputId);
            this.hintRecipeId = recipeId == null ? "" : recipeId.trim();
            this.hintFallback = new com.create.productionline.line.mapper.RecipeDescriptor(
                    targetId != null && !targetId.isBlank() ? targetId : outputId,
                    categoryId == null ? "" : categoryId,
                    inputs == null ? java.util.List.of() : inputs,
                    outputId == null || outputId.isBlank() ? java.util.List.of() : java.util.List.of(outputId));
            this.notifyPlayer = requester == null ? null : requester.getUUID();
            this.placeholderPermitted = requester != null
                    && requester.hasPermissions(AUTHORING_PERMISSION_LEVEL);
            computeQueued = true;
            setChanged();
        }
    }

    /** Consumes the pending hint exactly once (one hint per compute request). */
    private void clearHint() {
        this.hintRecipeId = "";
        this.hintFallback = null;
    }

    /**
     * Runs the queued compute. The authoring permission comes from the request that queued
     * it (see {@link #computeProvided}) and is consumed here exactly once, like the recipe
     * hint, so an operator's grant can never be inherited by a later run.
     */
    public void runCompute() {
        boolean permitted = this.placeholderPermitted;
        this.placeholderPermitted = false;
        runCompute(permitted);
    }

    /**
     * Runs one compute with the authoring permission already resolved by the caller.
     *
     * <p>Split out the same way {@code SchemeAnvilMachine.decide} takes its {@code allowed}
     * flag: the decision needs to know whether the acting player may hand-author a line, and
     * the caller is the only party that can know it. The QA self test drives both sides of
     * the gate through this parameter, because a headless server has no connected player to
     * press [Compute] — and a test that could not tell the two sides apart would not prove
     * the gate exists. The value must therefore come from a server-side permission check on
     * a real player, never from a payload.
     */
    public void runCompute(boolean requesterHasPermission) {
        runComputeInternal(requesterHasPermission);
        // Only now is the result code final: the run is queued for the next tick after
        // the button press, so this is the first moment the requester can be told what
        // actually happened (before, the chat could only ever show "how to use me").
        if (level instanceof ServerLevel serverLevel) {
            java.util.UUID waiting = this.notifyPlayer;
            this.notifyPlayer = null;
            if (waiting != null) {
                net.minecraft.server.level.ServerPlayer player =
                        serverLevel.getServer().getPlayerList().getPlayer(waiting);
                if (player != null) {
                    for (net.minecraft.network.chat.Component line : com.create.productionline.menu.ComputerStatus
                            .lines(resultCode, lastErrorCode, inventory.getItem(SLOT_SCHEME),
                                    inventory.getItem(SLOT_CLIPBOARD))) {
                        player.displayClientMessage(line, false);
                    }
                }
            }
        }
    }

    private void runComputeInternal(boolean requesterHasPermission) {
        if (level == null || level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Consume the client hint up front so it can never leak into a later run.
        final String hintId = hintRecipeId;
        final com.create.productionline.line.mapper.RecipeDescriptor fallback = hintFallback;
        clearHint();

        ItemStack target = inventory.getItem(SLOT_TARGET);
        if (target.isEmpty()) {
            setResult(RESULT_NO_TARGET, "");
            return;
        }
        ItemStack primary = findCarrier(SLOT_SCHEME);
        ItemStack secondary = findCarrier(SLOT_CLIPBOARD);
        if (primary.isEmpty() && secondary.isEmpty()) {
            setResult(RESULT_NO_SCHEME, "");
            return;
        }
        ItemStack carrier = primary.isEmpty() ? secondary : primary;

        ResourceLocation targetId = BuiltInRegistries.ITEM.getKey(target.getItem());
        if (targetId == null) {
            // Deliberately NOT a placeholder case: a placeholder exists to NAME the item an
            // operator is about to hand-author, and an item without a registry id has no name
            // to write. The old refusal stands.
            setResult(RESULT_NO_RECIPE, "target has no registry id");
            return;
        }

        // 1) Verify the client's recipe id on the SERVER: it must exist in the live
        //    RecipeManager AND really produce the item in the target slot. A forged
        //    id/target either fails this check or resolves to a genuine server
        //    recipe — so the worst case is "activate a recipe that already exists".
        com.create.productionline.line.mapper.RecipeDescriptor source = null;
        if (!hintId.isBlank()) {
            com.create.productionline.line.mapper.RecipeDescriptor verified =
                    com.create.productionline.line.mapper.ServerRecipeLookup.findById(serverLevel, hintId);
            if (verified != null && verified.outputs().contains(targetId.toString())
                    && verified.hasUsableMaterials() && !isOwnRecipe(verified.recipeId())) {
                source = verified; // server-derived inputs, not the client's
                ProductionLineMod.LOGGER.info("CPL compute: client recipe hint '{}' verified server-side", hintId);
            } else {
                ProductionLineMod.LOGGER.warn(
                        "CPL compute: rejected recipe hint '{}' for target {} "
                                + "(not a live recipe, wrong output, self-referential only, or our own conversion)",
                        hintId, targetId);
            }
        }

        // 2) Server-side lookup — always available, never trusts the client.
        boolean authoritative = source != null;
        java.util.List<com.create.productionline.line.mapper.ServerRecipeLookup.Found> found =
                java.util.List.of();
        if (source == null) {
            found = com.create.productionline.line.mapper.ServerRecipeLookup.findDetailed(serverLevel, targetId);
            source = chooseSource(serverLevel, found);
            authoritative = source != null;
        }

        // 3) Last resort: the server knows no recipe, but the client saw one (e.g. a
        //    recipe type the server scan cannot enumerate). Honour it for the PLAN
        //    only — no installable recipe is derived from unverified data.
        if (source == null && fallback != null && !fallback.inputs().isEmpty()
                && !fallback.categoryId().isBlank() && !fallback.outputs().isEmpty()
                && fallback.outputs().get(0).equals(targetId.toString())
                && fallback.hasUsableMaterials()) {
            source = fallback;
            ProductionLineMod.LOGGER.info(
                    "CPL compute: server found no recipe for {}; using the client-reported recipe for the plan only "
                            + "(no installable recipe)",
                    targetId);
        }

        if (source == null) {
            boolean onlySelfRecipes = !found.isEmpty()
                    && found.stream().noneMatch(f -> f.descriptor().hasUsableMaterials());
            String reason = onlySelfRecipes
                    ? "only self-referential (copy/repair) recipes exist for " + targetId
                    : "no recipe producing " + targetId + " was found";
            // No usable recipe for the target: the operator's one route to a scheme naming
            // this item (refine a computed scheme in an anvil) is blocked, because a computed
            // scheme needs a recipe. Write the placeholder instead — for the caller that
            // passed the authoring gate, and only then.
            if (requesterHasPermission) {
                writePlaceholder(targetId, carrier, secondary, reason);
                return;
            }
            setResult(RESULT_NO_RECIPE, reason);
            return;
        }

        processSource(source, targetId, carrier, secondary, authoritative, requesterHasPermission);
    }

    /**
     * Writes the placeholder scheme onto the carrier(s): the target, an empty recipe id, an
     * empty base material and zero steps (see
     * {@link com.create.productionline.line.scheme.LineScheme#placeholder(String)}).
     *
     * <p>Both carriers are treated exactly like a generated plan, so the player is never left
     * with one written carrier and one blank one. The empty recipe id is what makes this safe
     * rather than a lie: the Scheme Loader re-derives every installed recipe from
     * {@code RecipeId} against the server's live {@code RecipeManager}, and an empty id has
     * nothing to look up, so the cabinet installs nothing and its bar stays dark (see
     * {@code SchemeLoaderBlockEntity#filledSlots}).
     */
    private void writePlaceholder(ResourceLocation targetId, ItemStack carrier, ItemStack secondary,
            String reason) {
        com.create.productionline.line.scheme.LineScheme placeholder =
                com.create.productionline.line.scheme.LineScheme.placeholder(targetId.toString());
        writingCarriers = true;
        try {
            LineSchemeSerializer.saveToStack(carrier, placeholder);
            ClipboardCompat.writeGuide(carrier, placeholder);
            if (!secondary.isEmpty() && secondary != carrier) {
                LineSchemeSerializer.saveToStack(secondary, placeholder);
                ClipboardCompat.writeGuide(secondary, placeholder);
            }
        } finally {
            writingCarriers = false;
        }
        // The reason travels as the diagnostic, so the log still says WHY no plan exists even
        // though the run now ends in a written scheme instead of a refusal.
        setResult(RESULT_PLACEHOLDER, reason);
        ProductionLineMod.LOGGER.info(
                "CPL compute: no usable plan for {} ({}); placeholder scheme written for the operator",
                targetId, reason);
    }

    /** Builds the plan, embeds a native Create recipe when possible, writes carriers. */
    private void processSource(com.create.productionline.line.mapper.RecipeDescriptor source,
            ResourceLocation targetId, ItemStack carrier, ItemStack secondary, boolean authoritative,
            boolean requesterHasPermission) {
        if (source == null || source.outputs().isEmpty()) {
            // A recipe exists but has no usable output: the same dead end for the player as
            // "no recipe at all" — and the target id they picked is right here, so the
            // placeholder can still name it.
            if (requesterHasPermission) {
                writePlaceholder(targetId, carrier, secondary,
                        "no usable output: recipe of this item has no usable output");
                return;
            }
            setResult(RESULT_NO_RECIPE, "no usable output: recipe of this item has no usable output");
            return;
        }
        ProductionLineMod.LOGGER.info("CPL plan: recipe={} cat={} inputs={} out={}",
                source.recipeId(), source.categoryId(), source.uniqueInputs(), source.outputs().get(0));
        com.create.productionline.line.scheme.LineScheme scheme = new com.create.productionline.line.scheme.LineScheme();
        scheme.setRecipeId(source.recipeId());
        scheme.setOutputItem(source.outputs().get(0));

        // ONE source of material order: the recipe's own datapack JSON in
        // row-major grid order (tags keep their "#tag" identity). The PLAN and
        // the embedded sequenced-assembly recipe MUST share this order — a
        // mismatch made e.g. the diesel engine line never match (the plan said
        // base=engine_piston while the recipe expected base=flint_and_steel).
        String output = source.outputs().get(0);
        // B7: the LIVE recipe decides the yield. A mod may produce more at runtime
        // than its datapack JSON claims, so source.outputCount()
        // (getResultItem().getCount()) wins whenever it is larger; the JSON count
        // is only the fallback (and RecipeJsonReader.resultCount already defaults
        // to 1 when the file is missing/unreadable). Never below 1.
        int count = Math.max(1, source.outputCount());
        java.util.List<String> orderedInputs = source.uniqueInputs();
        if (level instanceof net.minecraft.server.level.ServerLevel slo) {
            ResourceLocation rid2 = ResourceLocation.tryParse(source.recipeId());
            if (rid2 != null) {
                count = Math.max(1, Math.max(source.outputCount(),
                        com.create.productionline.util.RecipeJsonReader.resultCount(
                                slo.getServer().getResourceManager(), rid2)));
                java.util.List<String> ordered =
                        com.create.productionline.util.RecipeJsonReader.shapedMaterialOrder(
                                slo.getServer().getResourceManager(), rid2, source.uniqueInputs());
                if (!ordered.isEmpty()) {
                    orderedInputs = ordered;
                }
            }
        }

        // ONE material list drives BOTH the plan and the embedded recipe. The old
        // "self-reference is 自备/现编, omit it" shortcut made the plan one station
        // shorter than the recipe that actually gets installed (players built the
        // plan and still got no product), and a recipe derived from a filtered list
        // would be weaker than the recipe it converts. Self-referencing materials
        // are therefore kept as ordinary stations.
        if (orderedInputs.isEmpty()) {
            ProductionLineMod.LOGGER.info(
                    "CPL compute: recipe {} has no usable materials - no plan written", source.recipeId());
            setResult(RESULT_NOT_CONVERTIBLE, output);
            return; // carriers are left untouched on purpose
        }

        // Embed a genuine native-Create recipe payload — derived through the
        // SAME server-side code path the loader uses (see RecipeDeriver), so the
        // scheme's stored JSON is only a cache and never the source of truth.
        //
        // SECURITY: only an AUTHORITATIVE descriptor (resolved from the live
        // server RecipeManager and verified to produce the target item) may yield
        // installable recipes. A client-only fallback produces no recipe, and so
        // is refused below rather than written.
        //
        // This is also the gate for whether a plan is written AT ALL: a scheme
        // with no installable recipe would be a promise the mod cannot keep
        // (a target that is already made by a native Create process, or a recipe
        // with no usable material), and it would burn the player's paper /
        // clipboard / blank Line Scheme for nothing. Refuse instead and leave the
        // carriers untouched. A single-material recipe is NOT refused any more:
        // its material semantics pick a real Create machine (B3).
        java.util.List<com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry> derived =
                new java.util.ArrayList<>();
        if (authoritative && level instanceof net.minecraft.server.level.ServerLevel srv) {
            derived = com.create.productionline.recipegen.RecipeDeriver.entriesFor(
                    srv, source, orderedInputs, count);
        }
        if (derived.isEmpty()) {
            // Deliberately NOT a placeholder case, for either permission level: a usable recipe
            // for the target DOES exist here, the mod just cannot turn it into a Create line.
            // The player gets a reason they can act on (the mapping config) instead of a scheme
            // they would have to author from nothing.
            ProductionLineMod.LOGGER.info(
                    "CPL compute: no convertible Create recipe for {} (recipe={} cat={} materials={}, authoritative={})"
                            + " - no plan written",
                    output, source.recipeId(), source.categoryId(), orderedInputs, authoritative);
            setResult(RESULT_NOT_CONVERTIBLE, output);
            return; // carriers are left untouched on purpose
        }

        // Plan topology: the Steps are a pure MIRROR of the derived recipe JSON —
        // the SAME derivation that produced `entry` decides the stations, so a
        // machine is only ever paired with the material it really processes. No
        // machine is picked from recipe "features" any more (that index-by-index
        // pairing was fake semantics: a plausible-looking plan that matched
        // nothing). See MachineSelector.appendChainSteps.
        scheme.setBaseMaterial(orderedInputs.get(0));
        // Target output: the number of items the player stacked into the target slot is
        // the number they want out. The installed recipe stays a ONE-CRAFT payload (see
        // RepeatPlan), and the plan records how often the line has to run for it — a
        // doubling recipe such as "A + B = 2A" bootstraps from the unit on the belt, so
        // its repeat count comes from the net gain per pass, never from an endless loop.
        ItemStack targetStack = inventory.getItem(SLOT_TARGET);
        int targetCount = Math.max(1, Math.min(targetStack.getCount(), targetStack.getMaxStackSize()));
        int consumedPerCraft = 0;
        for (String material : orderedInputs) {
            if (output.equals(material)) {
                consumedPerCraft++;
            }
        }
        com.create.productionline.line.scheme.RepeatPlan repeat =
                com.create.productionline.line.scheme.RepeatPlan.of(targetCount, count, consumedPerCraft);
        scheme.setRepeatPlan(repeat);
        com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry entry = derived.get(0);
        com.create.productionline.line.analyzer.MachineSelector.appendChainSteps(
                scheme, orderedInputs, entry, output);

        for (com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry e : derived) {
            scheme.addCreateRecipe(e.getFileName(), e.getJson());
        }

        // Record the roles the source recipe gives its own materials before anything is written:
        // a smithing recipe's base is equipment that a Deployer uses rather than consumes, and a
        // plan's steps cannot express that difference on their own.
        com.create.productionline.line.mapper.SchemeRoles.markToolMaterials(
                level == null ? null : level.getServer(), scheme);

        writingCarriers = true;
        try {
            LineSchemeSerializer.saveToStack(carrier, scheme);
            ClipboardCompat.writeGuide(carrier, scheme);
            if (!secondary.isEmpty() && secondary != carrier) {
                LineSchemeSerializer.saveToStack(secondary, scheme);
                ClipboardCompat.writeGuide(secondary, scheme);
            }
        } finally {
            writingCarriers = false;
        }

        setResult(RESULT_GENERATED, "embedded create recipe: " + entry.getFileName());
        ProductionLineMod.LOGGER.info("Embedded converted Create recipe {} for {}", entry.getFileName(),
                scheme.getOutputItem());
    }

    private ItemStack findCarrier(int slot) {
        ItemStack stack = inventory.getItem(slot);
        return stack.isEmpty() || !com.create.productionline.compat.ClipboardCompat.isCarrier(stack)
                ? ItemStack.EMPTY
                : stack;
    }

    /**
     * Records the outcome of one run AND its machine-readable cause (M7), so the
     * GUI can show a specific reason instead of one generic "cannot map" line.
     * The classification is derived from the (server-only, never translated)
     * diagnostic string plus the stage:
     *
     * <ul>
     *   <li>"no recipe producing …" → 1 (no recipe at all)</li>
     *   <li>"no usable output…" → 2 (recipe exists, output unusable)</li>
     *   <li>"target has no registry id" → 3 (item not registered)</li>
     *   <li>otherwise {@link #RESULT_NOT_CONVERTIBLE} → 4 (live recipe, not convertible)</li>
     *   <li>anything else → 0</li>
     * </ul>
     */
    /**
     * Recipe selection for the server-side scan. Prefers a recipe that
     * (a) is not a native Create type, (b) has at least one material that is not the product
     * itself, and (c) actually yields an installable entry. Copy/repair/dye recipes whose only
     * ingredient is the target are skipped entirely — converting one produced the nonsense
     * plan {@code [target] -> press -> target}.
     */
    private static com.create.productionline.line.mapper.RecipeDescriptor chooseSource(
            ServerLevel level,
            java.util.List<com.create.productionline.line.mapper.ServerRecipeLookup.Found> found) {
        com.create.productionline.line.mapper.RecipeDescriptor firstUsable = null;
        for (com.create.productionline.line.mapper.ServerRecipeLookup.Found f : found) {
            com.create.productionline.line.mapper.RecipeDescriptor d = f.descriptor();
            if (d.categoryId().startsWith("create:") || !d.hasUsableMaterials() || isOwnRecipe(d.recipeId())) {
                continue;
            }
            if (com.create.productionline.recipegen.RecipeDeriver.derive(level, d).hasEntries()) {
                return d; // convertible with real materials: the best possible choice
            }
            if (firstUsable == null) {
                firstUsable = d; // usable materials but not convertible -> plan-only fallback
            }
        }
        for (com.create.productionline.line.mapper.ServerRecipeLookup.Found f : found) {
            com.create.productionline.line.mapper.RecipeDescriptor d = f.descriptor();
            if (!d.hasUsableMaterials() || isOwnRecipe(d.recipeId())) {
                continue;
            }
            if (com.create.productionline.recipegen.RecipeDeriver.derive(level, d).hasEntries()) {
                return d;
            }
            if (firstUsable == null) {
                firstUsable = d;
            }
        }
        return firstUsable;
    }

    /** True for recipes this mod itself installed ({@code cpl:…}) — never re-convert those. */
    private static boolean isOwnRecipe(String recipeId) {
        return recipeId != null && recipeId.startsWith(
                com.create.productionline.recipegen.CreateRecipePack.PACK_NAMESPACE + ":");
    }

    private void setResult(int code, String error) {
        this.resultCode = code;
        this.lastError = error == null ? "" : error;
        String diagnostic = this.lastError;
        if (diagnostic.startsWith("no recipe producing")) {
            this.lastErrorCode = ERROR_NO_RECIPE_PRODUCING;
        } else if (diagnostic.startsWith("no usable output")) {
            this.lastErrorCode = ERROR_NO_USABLE_OUTPUT;
        } else if (diagnostic.startsWith("target has no registry id")) {
            this.lastErrorCode = ERROR_NO_REGISTRY_ID;
        } else if (code == RESULT_NOT_CONVERTIBLE) {
            this.lastErrorCode = ERROR_NOT_CONVERTIBLE;
        } else {
            this.lastErrorCode = ERROR_NONE;
        }
        setChanged();
    }

    public int getResultCode() {
        return resultCode;
    }

    /** Machine-readable reason of the last failed run (M7); 0 when there is none. */
    public int getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastError() {
        return lastError;
    }

    public Container getInventory() {
        return inventory;
    }

    public SimpleMenuProvider menuProvider() {
        return new SimpleMenuProvider((id, inv, player) ->
                com.create.productionline.menu.ProductionComputerMenu.fromServer(id, inv, this),
                Component.translatable("container.create_productionline.production_computer"));
    }

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : inventory.snapshot()) {
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, stack);
            }
        }
    }

    // --- persistence ----------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag items = new ListTag();
        for (ItemStack stack : inventory.snapshot()) {
            items.add(stack.saveOptional(provider));
        }
        tag.put("Items", items);
        tag.putInt("ResultCode", resultCode);
        tag.putInt("LastErrorCode", lastErrorCode);
        tag.putString("LastError", lastError);
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("Items", Tag.TAG_LIST)) {
            ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
            java.util.ArrayList<ItemStack> list = new java.util.ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                list.add(ItemStack.parseOptional(provider, items.getCompound(i)));
            }
            inventory.loadFrom(list);
        }
        resultCode = tag.getInt("ResultCode");
        lastErrorCode = tag.getInt("LastErrorCode");
        lastError = tag.getString("LastError");
        computeQueued = false;
    }
}
