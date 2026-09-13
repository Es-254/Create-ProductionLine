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
        java.util.Map<String, String> own = currentEntriesMap();
        int activeCount = CreateRecipePack.reconcileContributions(serverLevel.getServer(), loaderKey(),
                own.isEmpty() ? null : own);
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
            active = false;
        }
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
        pendingReconcile = true; // re-install after restart while schemes are still inside
    }
}
