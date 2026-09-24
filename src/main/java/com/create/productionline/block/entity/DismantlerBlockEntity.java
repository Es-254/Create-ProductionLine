package com.create.productionline.block.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import com.create.productionline.item.LineSchemeMirrorItem;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.registry.ModBlockEntities;
import com.create.productionline.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 破拆机 block entity.
 *
 * <p>Slot 0 holds the generic intermediate / finished item to dismantle; slot 1
 * holds the plan — only a genuine Line Scheme item is accepted there (mirrors /
 * paper / clipboards are rejected, P0-1). The dismantle action first validates
 * that slot 0's item matches the plan's output, then consumes one slot-0 item
 * and refunds the plan's raw materials, placing a read-only
 * {@link LineSchemeMirrorItem} into slot 0.
 */
public class DismantlerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {

    public static final int SLOT_ITEM = 0;
    public static final int SLOT_SCHEME = 1;

    /**
     * Outcome of a dismantle attempt plus what could not be given back. The GUI's
     * button claims "materials back + mirror", so a refusal has to say <em>why</em>
     * instead of doing nothing — every {@link RevertResult} except
     * {@link RevertResult#DONE}, {@link RevertResult#SCHEME_ERASED} and
     * {@link RevertResult#NOT_SERVER_SIDE} carries the translation key of the line
     * sent to the player who pressed the button.
     */
    public enum RevertResult {
        /** Consumed, materials refunded, mirror produced. */
        DONE("dismantler.create_productionline.result.done"),
        /** A written scheme was erased and a fresh blank one handed back. */
        SCHEME_ERASED("dismantler.create_productionline.result.scheme_erased"),
        /** A blank scheme holds nothing to erase. */
        SCHEME_ALREADY_BLANK("dismantler.create_productionline.result.scheme_already_blank"),
        /** A mirror is a read-only snapshot: nothing to take apart. */
        MIRROR_READ_ONLY("dismantler.create_productionline.result.mirror_read_only"),
        /** Slot 0 was empty. */
        NOTHING_HELD("dismantler.create_productionline.result.nothing_held"),
        /** Slot 0 held something this machine cannot dismantle (no provenance). */
        NO_PROVENANCE("dismantler.create_productionline.result.no_provenance"),
        /** The recipe behind the item is gone / unknown, so nothing can be refunded safely. */
        RECIPE_MISSING("dismantler.create_productionline.result.recipe_missing"),
        /** The item is not the product the plan describes. */
        OUTPUT_MISMATCH("dismantler.create_productionline.result.output_mismatch"),
        /** Fewer items than one inverse batch of the recipe. */
        NOT_ENOUGH("dismantler.create_productionline.result.not_enough"),
        /** Nothing in the plan could be turned back into an item. */
        NOT_REFUNDABLE("dismantler.create_productionline.result.not_refundable"),
        /** Client-side copy of the block entity: unreachable from the GUI payload path. */
        NOT_SERVER_SIDE(null);

        private final String langKey;

        RevertResult(String langKey) {
            this.langKey = langKey;
        }

        /** Player-facing message key, or {@code null} when there is nothing to say. */
        public String langKey() {
            return langKey;
        }

        public boolean ok() {
            return this == DONE || this == SCHEME_ERASED;
        }
    }

    /**
     * What a dismantle attempt did, and what it could not give back.
     *
     * @param result        the outcome, {@link RevertResult#ok()} when something happened
     * @param fluidsSkipped fluid-form ingredients the source recipe also used: a fluid
     *                      cannot exist as an item, so it is never part of the refund and
     *                      the player has to be told rather than left guessing
     */
    public record RevertOutcome(RevertResult result, int fluidsSkipped) {

        static RevertOutcome refusal(RevertResult result) {
            return new RevertOutcome(result, 0);
        }
    }

    private final ModContainer inventory = new ModContainer(this, 2, (s) -> setChanged());

    public DismantlerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISMANTLER.get(), pos, state);
    }

    /**
     * Consume-then-refund dismantle, authoritative on the server side.
     *
     * <p>Slot 0 may hold either of two things:
     * <ol>
     *   <li><b>an unfinished intermediate</b> (Generic Intermediate carrying Create's
     *       {@code SEQUENCED_ASSEMBLY} component). Its component names the sequence recipe and
     *       how many deploy steps already ran, so the server re-reads that recipe JSON and
     *       refunds the base plus exactly the materials those steps consumed — i.e. what is
     *       really "inside" the item. Fluids/tags that cannot exist as items are skipped
     *       (tags refund their first registered member as a best effort).</li>
     *   <li><b>a finished product</b>; then the recipe is resolved server-side (scheme
     *       {@code recipeId}, verified against the live {@code RecipeManager}) and the refund is
     *       one batch of its inputs — <em>including</em> the product itself when the recipe
     *       consumes it ({@code A + B = 2A} must give back {@code A + B}, not just {@code B}) —
     *       consuming {@code count} products so that a {@code count > 1} recipe is never
     *       farmed one product at a time.</li>
     *   <li><b>a written Line Scheme</b>; it holds no materials at all (authoring a plan costs
     *       one blank carrier and nothing else, see the Production Computer), so dismantling
     *       one simply erases the plan and hands back a <b>fresh blank scheme</b>. A blank
     *       scheme has nothing to erase and a mirror is a read-only snapshot: both are
     *       refused with their own message.</li>
     * </ol>
     * The Line Scheme in slot 1 is <b>optional</b>: when present it supplies the mirror's
     * plan text, otherwise a mirror snapshot is synthesized from the recipe that was parsed.
     *
     * <p>Fluids cannot exist as items, so fluid-form ingredients are never refunded; the
     * count of them is reported in {@link RevertOutcome#fluidsSkipped()} so the player is
     * told instead of being left to wonder where the water went.
     *
     * @return the outcome: {@link RevertResult#DONE} / {@link RevertResult#SCHEME_ERASED}
     *         when something happened, otherwise the refusal and nothing was consumed.
     */
    public RevertOutcome revert() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return RevertOutcome.refusal(RevertResult.NOT_SERVER_SIDE);
        }
        ItemStack slotZero = inventory.getItem(SLOT_ITEM);
        if (slotZero.isEmpty()) {
            return RevertOutcome.refusal(RevertResult.NOTHING_HELD);
        }
        // A scheme holds no materials: the plan on it was computed for free (the computer
        // only writes onto the carrier), so "dismantling" one is erasing it. One item in,
        // one blank item back, in the same slot.
        if (slotZero.getItem() == ModItems.LINE_SCHEME.get()) {
            if (!schemeCarriesContent(slotZero)) {
                return RevertOutcome.refusal(RevertResult.SCHEME_ALREADY_BLANK);
            }
            inventory.setItem(SLOT_ITEM, new ItemStack(ModItems.LINE_SCHEME.get()));
            setChanged();
            return new RevertOutcome(RevertResult.SCHEME_ERASED, 0);
        }
        if (slotZero.getItem() == ModItems.LINE_SCHEME_MIRROR.get()) {
            return RevertOutcome.refusal(RevertResult.MIRROR_READ_ONLY);
        }
        // Slot 1 (Line Scheme) is optional: genuine schemes supply the mirror text, mirrors /
        // paper / clipboard carriers are ignored (they must not act as authoritative input).
        LineScheme scheme = null;
        ItemStack schemeStack = inventory.getItem(SLOT_SCHEME);
        if (!schemeStack.isEmpty()
                && schemeStack.getItem() instanceof com.create.productionline.item.LineSchemeItem) {
            LineScheme parsed = LineSchemeSerializer.fromStack(schemeStack);
            if (!parsed.isEmpty()) {
                scheme = parsed;
            }
        }

        var manager = serverLevel.getServer().getResourceManager();
        java.util.List<String> toRestore = new java.util.ArrayList<>();
        int consume = 1;
        int fluidsSkipped = 0;
        LineScheme mirrorScheme = scheme;

        if (slotZero.getItem() == ModItems.GENERIC_INTERMEDIATE.get()) {
            // --- unfinished intermediate: refund what it already absorbed -------------
            var assembly = slotZero.get(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY);
            if (assembly == null) {
                return RevertOutcome.refusal(RevertResult.NO_PROVENANCE); // no provenance
            }
            com.create.productionline.util.RecipeJsonReader.SequenceParts parts =
                    com.create.productionline.util.RecipeJsonReader.sequenceParts(
                            manager, assembly.id());
            if (parts == null) {
                return RevertOutcome.refusal(RevertResult.RECIPE_MISSING);
            }
            fluidsSkipped = com.create.productionline.util.RecipeJsonReader.countFluidIngredients(
                    manager, assembly.id());
            if (parts.base() != null) {
                toRestore.add(parts.base());
            }
            int applied = Math.min(assembly.step(), parts.stepMaterials().size());
            for (int i = 0; i < applied; i++) {
                toRestore.add(parts.stepMaterials().get(i)); // multiplicity is intentional
            }
            if (toRestore.isEmpty()) {
                return RevertOutcome.refusal(RevertResult.NOT_REFUNDABLE);
            }
            if (mirrorScheme == null) {
                mirrorScheme = synthesizeSequenceMirror(parts);
            }
        } else {
            // --- finished product: inverse of one full craft -------------------------
            String recipeId = scheme != null ? scheme.getRecipeId() : null;
            com.create.productionline.line.mapper.RecipeDescriptor desc =
                    com.create.productionline.line.mapper.ServerRecipeLookup.findById(serverLevel, recipeId);
            if (desc == null || desc.outputs().isEmpty()) {
                return RevertOutcome.refusal(RevertResult.RECIPE_MISSING);
            }
            String authoritativeOutput = desc.outputs().get(0);
            ResourceLocation slotKey = BuiltInRegistries.ITEM.getKey(slotZero.getItem());
            if (slotKey == null || !slotKey.toString().equals(authoritativeOutput)) {
                return RevertOutcome.refusal(RevertResult.OUTPUT_MISMATCH);
            }
            for (String in : desc.uniqueInputs()) {
                // The product itself stays in the refund list on purpose: for a recipe that
                // consumes what it makes (A + B = 2A) the inverse of one craft is
                // 2A -> 1A + 1B, and dropping the self-reference would eat an A instead.
                toRestore.add(in);
            }
            if (toRestore.isEmpty()) {
                return RevertOutcome.refusal(RevertResult.NOT_REFUNDABLE);
            }
            // The refund is the inverse of the recipe, so a recipe yielding `count` items is
            // only dismantled in batches of `count`; the live recipe wins over the JSON text.
            int jsonCount = 1;
            ResourceLocation rid = ResourceLocation.tryParse(desc.recipeId());
            if (rid != null) {
                jsonCount = com.create.productionline.util.RecipeJsonReader.resultCount(manager, rid);
                fluidsSkipped = com.create.productionline.util.RecipeJsonReader.countFluidIngredients(
                        manager, rid);
            }
            consume = Math.max(1, Math.max(jsonCount, desc.outputCount()));
            if (slotZero.getCount() < consume) {
                return RevertOutcome.refusal(RevertResult.NOT_ENOUGH); // needs a full batch
            }
            if (mirrorScheme == null) {
                mirrorScheme = synthesizeDescriptorMirror(desc);
            }
        }

        // Resolve everything BEFORE consuming: if nothing can be materialized we must not eat
        // the item (refuse instead of swallowing).
        java.util.List<net.minecraft.world.item.Item> refunds = new java.util.ArrayList<>();
        for (String id : toRestore) {
            net.minecraft.world.item.Item item = materialize(id);
            if (item != null) {
                refunds.add(item);
            }
        }
        if (refunds.isEmpty()) {
            return RevertOutcome.refusal(RevertResult.NOT_REFUNDABLE);
        }

        BlockPos pos = getBlockPos();
        // Order matters: consume first, then refund, then the mirror — a repeated/concurrent
        // call can never refund twice for the same item.
        if (slotZero.getCount() > consume) {
            slotZero.shrink(consume);
            inventory.setChanged();
        } else {
            inventory.setItem(SLOT_ITEM, ItemStack.EMPTY);
        }
        for (net.minecraft.world.item.Item item : refunds) {
            net.minecraft.world.level.block.Block.popResource(serverLevel, pos, new ItemStack(item));
        }
        ItemStack mirror = new ItemStack(ModItems.LINE_SCHEME_MIRROR.get());
        if (mirrorScheme != null) {
            com.create.productionline.item.LineSchemeMirrorItem.write(mirror, mirrorScheme);
        }
        // If slot 0 is now empty the mirror goes back into it; otherwise (a stack remained)
        // pop it so the mirror never silently overwrites leftover items.
        if (inventory.getItem(SLOT_ITEM).isEmpty()) {
            inventory.setItem(SLOT_ITEM, mirror);
        } else {
            net.minecraft.world.level.block.Block.popResource(serverLevel, pos, mirror);
        }
        setChanged();
        return new RevertOutcome(RevertResult.DONE, fluidsSkipped);
    }

    /**
     * True when a Line Scheme stack carries anything at all: a computed plan, or a
     * hand-authored one that is still being built on the anvil (which has a
     * {@code CUSTOM_ASSEMBLY} component but may not have Steps yet — the same reason
     * {@code LineSchemeItem}'s tooltip does not treat "no steps" as "empty").
     */
    private static boolean schemeCarriesContent(ItemStack stack) {
        return !LineSchemeSerializer.fromStack(stack).isEmpty()
                || stack.has(com.create.productionline.registry.ModDataComponents.CUSTOM_ASSEMBLY.get());
    }

    /**
     * Turns a material token into an item, best effort: {@code "#tag"} resolves to the tag's
     * first registered member (it cannot be materialized otherwise), plain ids go through the
     * item registry. Returns {@code null} for anything unmaterializable.
     */
    private static net.minecraft.world.item.Item materialize(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(
                token.startsWith("#") ? token.substring(1) : token);
        if (key == null) {
            return null;
        }
        if (token.startsWith("#")) {
            var holders = BuiltInRegistries.ITEM.getTag(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, key));
            if (holders.isPresent()) {
                for (var holder : holders.get()) {
                    if (holder.value() != null && holder.value() != net.minecraft.world.item.Items.AIR) {
                        return holder.value();
                    }
                }
            }
            return null;
        }
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(key);
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    /** Mirror snapshot for a dismantled intermediate, built from the parsed recipe. */
    private static LineScheme synthesizeSequenceMirror(
            com.create.productionline.util.RecipeJsonReader.SequenceParts parts) {
        LineScheme out = new LineScheme();
        out.setOutputItem(parts.resultItem() == null ? "" : parts.resultItem());
        out.setBaseMaterial(parts.base() == null ? "" : parts.base());
        for (String material : parts.stepMaterials()) {
            LineScheme.Step step = out.addStep("create:deploying", 1);
            step.addInput(material);
        }
        return out;
    }

    /** Mirror snapshot for a dismantled finished product, built from its descriptor. */
    private static LineScheme synthesizeDescriptorMirror(
            com.create.productionline.line.mapper.RecipeDescriptor desc) {
        LineScheme out = new LineScheme();
        String output = desc.outputs().isEmpty() ? "" : desc.outputs().get(0);
        out.setOutputItem(output);
        String base = desc.uniqueInputs().isEmpty() ? "" : desc.uniqueInputs().get(0);
        out.setBaseMaterial(base);
        for (String input : desc.uniqueInputs()) {
            if (!input.equals(base)) {
                LineScheme.Step step = out.addStep("create:deploying", 1);
                step.addInput(input);
            }
        }
        return out;
    }

    public ModContainer getInventory() {
        return inventory;
    }

    public net.minecraft.world.SimpleMenuProvider menuProvider() {
        return new net.minecraft.world.SimpleMenuProvider((id, inv, player) ->
                com.create.productionline.menu.DismantlerMenu.fromServer(id, inv, this),
                net.minecraft.network.chat.Component.translatable(
                        "container.create_productionline.dismantler"));
    }

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : inventory.snapshot()) {
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, stack);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag items = new ListTag();
        for (ItemStack stack : inventory.snapshot()) {
            items.add(stack.saveOptional(provider));
        }
        tag.put("Items", items);
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
    }
}
