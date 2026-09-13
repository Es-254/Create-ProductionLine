package com.create.productionline.block.entity;

import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A tiny {@link Container} implementation for block-entity inventories. Kept
 * deliberately simple (no capabilities / item handlers) so the menus and the
 * block entities only depend on the vanilla {@link Container} contract.
 */
public class ModContainer implements Container {

    private final int size;
    private final NonNullList<ItemStack> items;
    private final BlockEntity owner;
    private final IntConsumer onChange;

    public ModContainer(BlockEntity owner, int size, IntConsumer onChange) {
        this.owner = owner;
        this.size = size;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        this.onChange = onChange;
    }

    @Override
    public int getContainerSize() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < 0 || index >= size) {
            return ItemStack.EMPTY;
        }
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        if (index < 0 || index >= size) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.removeItem(items, index, count);
        changed(index);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (index < 0 || index >= size) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = items.set(index, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= size) {
            return;
        }
        items.set(index, stack);
        if (stack.getCount() > stack.getMaxStackSize()) {
            stack.setCount(stack.getMaxStackSize());
        }
        changed(index);
    }

    @Override
    public void setChanged() {
        if (owner != null) {
            owner.setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (owner == null || owner.getLevel() == null) {
            return true;
        }
        return owner.getLevel().getBlockEntity(owner.getBlockPos()) == owner
                && player.distanceToSqr(owner.getBlockPos().getX() + 0.5D,
                        owner.getBlockPos().getY() + 0.5D,
                        owner.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
        changed(-1);
    }

    public List<ItemStack> snapshot() {
        return List.copyOf(items);
    }

    public void loadFrom(List<ItemStack> list) {
        items.clear();
        for (int i = 0; i < Math.min(size, list.size()); i++) {
            items.set(i, list.get(i) == null ? ItemStack.EMPTY : list.get(i).copy());
        }
    }

    private void changed(int slot) {
        setChanged();
        if (onChange != null) {
            onChange.accept(slot);
        }
    }
}
