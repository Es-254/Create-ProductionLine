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
     */
    public void computeProvided(String targetId, String recipeId, String categoryId,
            java.util.List<String> inputs, String outputId) {
        if (level != null && !level.isClientSide) {
            ProductionLineMod.LOGGER.info("CPL compute hint from client: recipe={} target={} cat={} inputs={} out={}",
                    recipeId, targetId, categoryId, inputs, outputId);
            this.hintRecipeId = recipeId == null ? "" : recipeId.trim();
            this.hintFallback = new com.create.productionline.line.mapper.RecipeDescriptor(
                    targetId != null && !targetId.isBlank() ? targetId : outputId,
                    categoryId == null ? "" : categoryId,
                    inputs == null ? java.util.List.of() : inputs,
                    outputId == null || outputId.isBlank() ? java.util.List.of() : java.util.List.of(outputId));
            computeQueued = true;
            setChanged();
        }
    }

    /** Consumes the pending hint exactly once (one hint per compute request). */
    private void clearHint() {
        this.hintRecipeId = "";
        this.hintFallback = null;
    }

    public void runCompute() {
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
            setResult(RESULT_NO_RECIPE, onlySelfRecipes
                    ? "only self-referential (copy/repair) recipes exist for " + targetId
                    : "no recipe producing " + targetId + " was found");
            return;
        }

        processSource(source, carrier, secondary, authoritative);
    }

    /** Builds the plan, embeds a native Create recipe when possible, writes carriers. */
    private void processSource(com.create.productionline.line.mapper.RecipeDescriptor source,
            ItemStack carrier, ItemStack secondary, boolean authoritative) {
        if (source == null || source.outputs().isEmpty()) {
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
        com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry entry = derived.get(0);
        com.create.productionline.line.analyzer.MachineSelector.appendChainSteps(
                scheme, orderedInputs, entry, output);

        for (com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry e : derived) {
            scheme.addCreateRecipe(e.getFileName(), e.getJson());
        }

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
