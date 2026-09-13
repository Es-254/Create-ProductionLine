package com.create.productionline.menu;

import com.create.productionline.block.entity.ProductionComputerBlockEntity;
import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.registry.ModMenuTypes;

import net.minecraft.network.FriendlyByteBuf;
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
 * GUI of the Production Computer. Three slots: target item, line scheme
 * (write), clipboard (guide). Data slot 0 = last computation result code.
 */
public class ProductionComputerMenu extends AbstractContainerMenu {

    private static final int COMPUTER_SLOTS = 3;

    private final Container computerContainer;
    private final ContainerData data;
    private final ProductionComputerBlockEntity computer; // server-side only; null on the client

    public ProductionComputerMenu(int id, Inventory playerInventory, Container computerContainer,
            ContainerData data, ProductionComputerBlockEntity computer) {
        super(ModMenuTypes.PRODUCTION_COMPUTER.get(), id);
        this.computerContainer = computerContainer;
        this.data = data;
        this.computer = computer;

        // Computer slots
        addSlot(new Slot(computerContainer, ProductionComputerBlockEntity.SLOT_TARGET, 44, 20));
        addSlot(new Slot(computerContainer, ProductionComputerBlockEntity.SLOT_SCHEME, 80, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return com.create.productionline.compat.ClipboardCompat.isCarrier(stack);
            }
        });
        addSlot(new Slot(computerContainer, ProductionComputerBlockEntity.SLOT_CLIPBOARD, 116, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return com.create.productionline.compat.ClipboardCompat.isClipboardLike(stack);
            }
        });

        addPlayerSlots(playerInventory, 84);
        addDataSlots(data);
    }

    /** Server factory. */
    public static ProductionComputerMenu fromServer(int id, Inventory playerInventory,
            ProductionComputerBlockEntity be) {
        return new ProductionComputerMenu(id, playerInventory, be.getInventory(),
                new BeData(be), be);
    }

    /** Client factory: all live state arrives via container data + slot sync. */
    public static ProductionComputerMenu createClient(int id, Inventory playerInventory) {
        return new ProductionComputerMenu(id, playerInventory, new SimpleContainer(COMPUTER_SLOTS),
                new SimpleContainerData(1), null);
    }

    /** Requests a computation on the server-side block entity (button click). */
    public void computeNow() {
        if (computer != null) {
            computer.computeNow();
        }
    }

    /**
     * Applies a client-resolved recipe hint. Nothing here is trusted: the block
     * entity re-resolves {@code recipeId} against the server's live
     * {@code RecipeManager} and verifies it produces the item in the target slot.
     */
    public void computeProvided(String targetId, String recipeId, String categoryId,
            java.util.List<String> inputs, String outputId) {
        if (computer != null) {
            computer.computeProvided(targetId, recipeId, categoryId, inputs, outputId);
        }
    }

    private static final class BeData implements ContainerData {
        private final ProductionComputerBlockEntity be;

        BeData(ProductionComputerBlockEntity be) {
            this.be = be;
        }

        @Override
        public int get(int index) {
            return index == 0 ? be.getResultCode() : 0;
        }

        @Override
        public void set(int index, int value) {
            // read-only from the server side
        }

        @Override
        public int getCount() {
            return 1;
        }
    }

    public int getResultCode() {
        return data.get(0);
    }

    public ItemStack getTargetItem() {
        return computerContainer.getItem(ProductionComputerBlockEntity.SLOT_TARGET);
    }

    public ItemStack getSchemeItem() {
        return computerContainer.getItem(ProductionComputerBlockEntity.SLOT_SCHEME);
    }

    public ItemStack getClipboardItem() {
        return computerContainer.getItem(ProductionComputerBlockEntity.SLOT_CLIPBOARD);
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
            if (index < COMPUTER_SLOTS) {
                if (!this.moveItemStackTo(stack, COMPUTER_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Move into the first container slot that accepts this item.
                boolean movedAny = false;
                for (int i = 0; i < COMPUTER_SLOTS; i++) {
                    if (this.slots.get(i).mayPlace(stack) && this.slots.get(i).getItem().isEmpty()) {
                        if (this.moveItemStackTo(stack, i, i + 1, false)) {
                            movedAny = true;
                            break;
                        }
                    }
                }
                if (!movedAny) {
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
        return computerContainer.stillValid(player);
    }
}
