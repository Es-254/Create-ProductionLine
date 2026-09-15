package com.create.productionline.block.entity;

import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.recipegen.CreateRecipePack;
import com.create.productionline.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity of the Scheme Loader cabinet.
 *
 * <p>Holds up to {@value #SLOT_COUNT} slots which accept ONLY genuine Line Scheme
 * items ({@code ClipboardCompat.isLoaderCarrier}); paper, clipboards, mirrors and
 * any forged-NBT carrier are rejected. The recipes installed by this loader are
 * <b>re-derived server-side</b> from each scheme's {@code RecipeId} against the
 * live {@code RecipeManager} (see {@code RecipeDeriver}) — the scheme's stored
 * Steps/JSON are never trusted. All loaders' contributions are merged into one
 * world datapack (union), so several schemes — e.g. an intermediate-product
 * scheme and the final-product scheme of one line — can be active at the same
 * time. Taking a scheme out only removes the recipes that came from it. A
 * redstone signal is emitted while at least one pipeline recipe is active.
 */
public class SchemeLoaderBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {

    public static final int SLOT_COUNT = 16;

    private final ModContainer inventory = new ModContainer(this, SLOT_COUNT, this::onSlotChanged);
    private boolean active = false;
    private boolean pendingReconcile = true;
    /**
     * Key (dimension + position) this cabinet's contribution is currently
     * registered under, or {@code null} when nothing is registered.
     *
     * <p>Persisted in NBT ({@code "RegisteredKey"}) on purpose: the BE is destroyed
     * and re-created whenever the block is moved by a Create contraption, so an
     * in-memory-only key would reset to {@code null} on the destination block and
     * the contribution left at the OLD coordinates could never be identified again.
     * Reading it back after restart/BE re-creation is what lets {@link #reconcile()}
     * drop the stale contribution under the old key.
     */
    private String registeredKey = null;

    public SchemeLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCHEME_LOADER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SchemeLoaderBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (be.pendingReconcile) {
            be.pendingReconcile = false;
            be.reconcile();
        }
    }

    private void onSlotChanged(int slot) {
        if (level != null && !level.isClientSide) {
            pendingReconcile = true;
            setChanged();
        }
    }

    /** (Re)installs the union of all filled scheme slots as this loader's contribution. */
    private void reconcile() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        String key = loaderKey();
        if (registeredKey != null && !registeredKey.equals(key)) {
            // The cabinet's contribution is registered under DIFFERENT coordinates
            // than it now occupies — it was relocated (Create contraption move,
            // /clone, …) or restored from NBT at a new place. Without this, the
            // contribution registered under the OLD coordinates would stay in the
            // union forever: a cabinet that no longer exists keeping recipes active.
            // The old key survives restarts because it is persisted (see below).
            CreateRecipePack.dropContribution(serverLevel.getServer(), registeredKey);
        }
        java.util.Map<String, String> own = currentEntriesMap();
        int activeCount = CreateRecipePack.reconcileContributions(serverLevel.getServer(), key,
                own.isEmpty() ? null : own);
        registeredKey = key;
        active = !own.isEmpty() && activeCount > 0;
        syncState();
    }

    /** Stable identity of this loader for additive contribution tracking. */
    private String loaderKey() {
        if (level == null) {
            return "unknown";
        }
        return level.dimension().location() + "/" + getBlockPos();
    }

    /** Called when the block is destroyed: drop this loader's contribution only. */
    public void onRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            CreateRecipePack.reconcileContributions(serverLevel.getServer(), loaderKey(), null);
            registeredKey = null;
            active = false;
        }
    }

    /**
     * Defensive hook for a piston move: drops the contribution registered under the
     * OLD coordinates so it is re-registered at the new position.
     *
     * <p><b>Vanilla pistons cannot push this cabinet at all:</b>
     * {@code PistonBaseBlock.isPushable(…)} ends in {@code !state.hasBlockEntity()},
     * and Create's contraption movers remove the block entity before moving the
     * block. So this method is pure defence — the real guarantees are the
     * <b>persisted</b> {@code registeredKey} (so the next {@link #reconcile()} at the
     * destination still knows the old key) plus
     * {@code CreateRecipePack.sweepOrphanContributions} on server start.
     *
     * <p>The contents are never dropped — they travel with the block (dropping them
     * would duplicate schemes).
     */
    public void onMovedByPiston() {
        if (level instanceof ServerLevel serverLevel && registeredKey != null) {
            // Rebuild right away (not just delete the file): the union written to the
            // datapack must stop serving this cabinet's recipes at once, instead of
            // waiting for the next unrelated reconcile.
            CreateRecipePack.reconcileContributions(serverLevel.getServer(), registeredKey, null);
        }
        registeredKey = null;
        active = false;
        pendingReconcile = true; // re-register under the new coordinates on the next tick
        setChanged();
    }

    /**
     * Requests a re-registration on the next tick. Called on block placement, which
     * also covers a block entity re-created at the destination of a Create
     * contraption move (vanilla pistons never push a BE-holding block), so a
     * relocated cabinet always re-reads its slots and registers under its current
     * key — while the persisted old key triggers the stale-contribution drop.
     */
    public void markRelocated() {
        pendingReconcile = true;
        setChanged();
    }

    /**
     * Authoritative contribution of every filled slot. Anti-injection: only
     * genuine Line Scheme items are accepted, and the installable recipes are
     * RE-DERIVED server-side from each scheme's {@code recipeId} against the
     * live RecipeManager — the scheme's stored Steps/JSON are never trusted.
     */
    private java.util.Map<String, String> currentEntriesMap() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (!(level instanceof ServerLevel serverLevel)) {
            return map;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(stack)) {
                continue;
            }
            LineScheme scheme = LineSchemeSerializer.fromStack(stack);
            String recipeId = scheme.getRecipeId();
            if (recipeId == null || recipeId.isBlank()) {
                continue; // nothing authoritative to derive from
            }
            var descriptor = com.create.productionline.line.mapper.ServerRecipeLookup.findById(serverLevel, recipeId);
            if (descriptor == null || descriptor.outputs().isEmpty()) {
                continue; // not a live server recipe -> cannot verify, skip
            }
            var derived = com.create.productionline.recipegen.RecipeDeriver.derive(serverLevel, descriptor);
            for (LineScheme.CreateRecipeEntry entry : derived.entries()) {
                if (entry.isValid()) {
                    map.put(entry.getFileName(), entry.getJson());
                }
            }
        }
        return map;
    }

    public boolean isActive() {
        return active;
    }

    /** Number of slots holding a usable scheme. */
    public int filledSlots() {
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && com.create.productionline.compat.ClipboardCompat.isCarrier(stack)) {
                LineScheme scheme = LineSchemeSerializer.fromStack(stack);
                if (!scheme.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    private void syncState() {
        if (level != null) {
            level.updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
            BlockState state = level.getBlockState(getBlockPos());
            level.sendBlockUpdated(getBlockPos(), state, state, 3);
        }
        setChanged();
    }

    public ModContainer getInventory() {
        return inventory;
    }

    public SimpleMenuProvider menuProvider() {
        return new SimpleMenuProvider((id, inv, player) ->
                com.create.productionline.menu.SchemeLoaderMenu.fromServer(id, inv, this),
                Component.translatable("container.create_productionline.scheme_loader"));
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
        tag.putBoolean("Active", active);
        // Persisted so a relocated cabinet can still identify (and drop) the
        // contribution it left behind at its previous coordinates.
        tag.putString("RegisteredKey", registeredKey == null ? "" : registeredKey);
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
        active = tag.getBoolean("Active");
        if (tag.contains("RegisteredKey", Tag.TAG_STRING)) {
            String storedKey = tag.getString("RegisteredKey");
            if (!storedKey.isBlank()) {
                // Restores the relocate detection across restart / BE re-creation:
                // reconcile() compares this against the current position and drops
                // the contribution registered under the old one.
                registeredKey = storedKey;
            }
        }
        pendingReconcile = true; // re-install after restart while schemes are still inside
    }
}
