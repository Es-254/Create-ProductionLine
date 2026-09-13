package com.create.productionline.block.entity;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.compat.ClipboardCompat;
import com.create.productionline.line.mapper.Mappers;
import com.create.productionline.line.mapper.RecipeDescriptor;
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
    public static final int RESULT_NO_CLIPBOARD = 3;
    public static final int RESULT_NO_RECIPE = 4; // cannot map / no recipe found (TC-01)
    public static final int RESULT_NO_TARGET = 5;

    private final ModContainer inventory = new ModContainer(this, 3, this::onSlotChanged);
    private int resultCode = RESULT_EMPTY;
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
        // three slots are set (explicit trigger). We only track changes here.
        setChanged();
    }

    /** Requests one computation run (from the GUI compute button, server side). */
    public void computeNow() {
        if (level != null && !level.isClientSide) {
            computeQueued = true;
            setChanged();
        }
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
            if (verified != null && verified.outputs().contains(targetId.toString())) {
                source = verified; // server-derived inputs, not the client's
                ProductionLineMod.LOGGER.info("CPL compute: client recipe hint '{}' verified server-side", hintId);
            } else {
                ProductionLineMod.LOGGER.warn(
                        "CPL compute: rejected recipe hint '{}' for target {} (not a live server recipe for this item)",
                        hintId, targetId);
            }
        }

        // 2) Server-side lookup — always available, never trusts the client.
        boolean authoritative = source != null;
        if (source == null) {
            List<com.create.productionline.line.mapper.ServerRecipeLookup.Found> found =
                    com.create.productionline.line.mapper.ServerRecipeLookup.findDetailed(serverLevel, targetId);
            for (com.create.productionline.line.mapper.ServerRecipeLookup.Found f : found) {
                if (!f.descriptor().categoryId().startsWith("create:")) {
                    source = f.descriptor();
                    break;
                }
            }
            if (source == null && !found.isEmpty()) {
                source = found.get(0).descriptor();
            }
            authoritative = source != null;
        }

        // 3) Last resort: the server knows no recipe, but the client saw one (e.g. a
        //    recipe type the server scan cannot enumerate). Honour it for the PLAN
        //    only — no installable recipe is derived from unverified data.
        if (source == null && fallback != null && !fallback.inputs().isEmpty()
                && !fallback.categoryId().isBlank() && !fallback.outputs().isEmpty()
                && fallback.outputs().get(0).equals(targetId.toString())) {
            source = fallback;
            ProductionLineMod.LOGGER.info(
                    "CPL compute: server found no recipe for {}; using the client-reported recipe for the plan only "
                            + "(no installable recipe)",
                    targetId);
        }

        if (source == null) {
            setResult(RESULT_NO_RECIPE, "no recipe producing " + targetId + " was found");
            return;
        }

        processSource(source, carrier, secondary, authoritative);
    }

    /** Builds the plan, embeds a native Create recipe when possible, writes carriers. */
    private void processSource(com.create.productionline.line.mapper.RecipeDescriptor source,
            ItemStack carrier, ItemStack secondary, boolean authoritative) {
        if (source == null || source.outputs().isEmpty()) {
            setResult(RESULT_NO_RECIPE, "recipe of this item has no usable output");
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
        int count = 1;
        java.util.List<String> orderedInputs = source.uniqueInputs();
        if (level instanceof net.minecraft.server.level.ServerLevel slo) {
            ResourceLocation rid2 = ResourceLocation.tryParse(source.recipeId());
            if (rid2 != null) {
                count = com.create.productionline.util.RecipeJsonReader.resultCount(
                        slo.getServer().getResourceManager(), rid2);
                java.util.List<String> ordered =
                        com.create.productionline.util.RecipeJsonReader.shapedMaterialOrder(
                                slo.getServer().getResourceManager(), rid2, source.uniqueInputs());
                if (!ordered.isEmpty()) {
                    orderedInputs = ordered;
                }
            }
        }

        // Direct (single-layer) plan: the recipe's own materials + its machine.
        // No upstream recursion (was rejected: it explodes a simple item into
        // dozens of steps). Materials equal to the output are treated as
        // 自备/现编 and omitted.
        java.util.List<String> unique = new java.util.ArrayList<>();
        for (String in : orderedInputs) {
            if (in.equals(output)) {
                continue; // self-reference -> 自备/现编
            }
            unique.add(in);
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
        // (e.g. single-material crafting such as iron_block, or a target that is
        // already a native Create recipe), and it would burn the player's paper /
        // clipboard / blank Line Scheme for nothing. Refuse instead and leave the
        // carriers untouched.
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
                    output, source.recipeId(), source.categoryId(), unique, authoritative);
            setResult(RESULT_NOT_CONVERTIBLE, output);
            return; // carriers are left untouched on purpose
        }

        // Plan topology: ONE linear chain, left to right —
        //   [基底] -> [器械1+原料1] -> [器械2+原料2] -> … -> [产物]
        // Step 1 feeds the base; every following station is one machine paired
        // with the one material it applies; the carried item is chained through
        // the stations and only the last one yields the product.
        boolean assembly =
                com.create.productionline.recipegen.RecipeDeriver.isConvertibleAssembly(source.categoryId());
        java.util.List<String> machines;
        if (assembly) {
            machines = java.util.List.of(com.create.productionline.line.analyzer.MachineSelector.DEPLOYER);
        } else {
            var analysis = com.create.productionline.line.analyzer.RecipeAnalyzer.analyze(source);
            java.util.List<String> selected =
                    com.create.productionline.line.analyzer.MachineSelector.selectForSource(
                            source.categoryId(), analysis);
            int scale = com.create.productionline.line.analyzer.MachineSelector.scaleOf(analysis);
            java.util.List<String> expanded = new java.util.ArrayList<>();
            for (int s = 0; s < scale; s++) {
                expanded.addAll(selected);
            }
            machines = expanded;
        }
        scheme.setBaseMaterial(unique.isEmpty() ? output : unique.get(0));
        com.create.productionline.line.analyzer.MachineSelector.appendChainSteps(
                scheme, unique, machines, output);

        com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry entry = derived.get(0);
        for (com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry e : derived) {
            scheme.addCreateRecipe(e.getFileName(), e.getJson());
        }

        LineSchemeSerializer.saveToStack(carrier, scheme);
        ClipboardCompat.writeGuide(carrier, scheme);
        if (!secondary.isEmpty() && secondary != carrier) {
            LineSchemeSerializer.saveToStack(secondary, scheme);
            ClipboardCompat.writeGuide(secondary, scheme);
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

    private void setResult(int code, String error) {
        this.resultCode = code;
        this.lastError = error == null ? "" : error;
        setChanged();
    }

    public int getResultCode() {
        return resultCode;
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
        lastError = tag.getString("LastError");
        computeQueued = false;
    }
}
