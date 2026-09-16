package com.create.productionline.menu;

import com.create.productionline.block.entity.SchemeLoaderBlockEntity;
import com.create.productionline.compat.ClipboardCompat;
import com.create.productionline.registry.ModMenuTypes;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Scheme Loader GUI: 16 carrier slots (2 rows of 8) plus the player inventory.
 * All schemes placed inside are active at once (their embedded recipes form a
 * union). Data slot 0 = any pipeline recipe active flag; slot 1 = active
 * recipe count (server-side).
 */
public class SchemeLoaderMenu extends AbstractContainerMenu {

    public static final int DATA_ACTIVE = 0;
    public static final int DATA_COUNT = 2;
    /** Index of the first loader slot; loader slots occupy [0, 16). */
    public static final int LOADER_SLOT_COUNT = SchemeLoaderBlockEntity.SLOT_COUNT;

    private final Container loaderContainer;
    private final ContainerData data;

    public SchemeLoaderMenu(int id, Inventory playerInventory, Container loaderContainer, ContainerData data) {
        super(ModMenuTypes.SCHEME_LOADER.get(), id);
        this.loaderContainer = loaderContainer;
        this.data = data;

        addLoaderSlots();
        addPlayerSlots(playerInventory, 112);
        addDataSlots(data);
    }

    private void addLoaderSlots() {
        // 2 rows of 8 (16 slots); only genuine Line Scheme items may be inserted
        // (see ClipboardCompat.isLoaderCarrier — anti-injection).
        for (int index = 0; index < LOADER_SLOT_COUNT; index++) {
            int col = index % 8;
            int row = index / 8;
            int x = 8 + col * 18;
            int y = 17 + row * 18;
            addSlot(new Slot(loaderContainer, index, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ClipboardCompat.isLoaderCarrier(stack);
                }
            });
        }
    }

    public static SchemeLoaderMenu fromServer(int id, Inventory playerInventory, SchemeLoaderBlockEntity be) {
        return new SchemeLoaderMenu(id, playerInventory, be.getInventory(), new Data(be));
    }

    public static SchemeLoaderMenu createClient(int id, Inventory playerInventory) {
        return new SchemeLoaderMenu(id, playerInventory, new SimpleContainer(LOADER_SLOT_COUNT),
                new SimpleContainerData(2));
    }

    private static final class Data implements ContainerData {
        private final SchemeLoaderBlockEntity be;

        Data(SchemeLoaderBlockEntity be) {
            this.be = be;
        }

        @Override
        public int get(int index) {
            if (index == DATA_ACTIVE) {
                return be.isActive() ? 1 : 0;
            }
            if (index == 1) {
                return be.getActiveCount();
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
            // read-only on the server side
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }

    public boolean isActive() {
        return data.get(DATA_ACTIVE) != 0;
    }

    public int getActiveCount() {
        return data.get(1);
    }

    public Container getLoaderContainer() {
        return loaderContainer;
    }

    private void addPlayerSlots(Inventory playerInventory, int yOrigin) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, yOrigin + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, yOrigin + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < LOADER_SLOT_COUNT) {
                // From a loader slot into the player inventory.
                if (!this.moveItemStackTo(stack, LOADER_SLOT_COUNT, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From the player inventory into an empty loader slot.
                if (!this.moveItemStackTo(stack, 0, LOADER_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == moved.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return loaderContainer.stillValid(player);
    }
}
