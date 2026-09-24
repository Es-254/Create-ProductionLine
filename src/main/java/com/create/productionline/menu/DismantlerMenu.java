package com.create.productionline.menu;

import com.create.productionline.block.entity.DismantlerBlockEntity;
import com.create.productionline.registry.ModMenuTypes;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Dismantler GUI: slot 0 = the intermediate/finished item, slot 1 = the Line
 * Scheme (mirror / paper / clipboard are refused there). The "拆解" button calls
 * {@link #revert()} on the server.
 */
public class DismantlerMenu extends AbstractContainerMenu {

    private static final int SLOTS = 2;

    private final Container container;
    private final DismantlerBlockEntity be; // null on the client

    public DismantlerMenu(int id, Inventory playerInventory, Container container, DismantlerBlockEntity be) {
        super(ModMenuTypes.DISMANTLER.get(), id);
        this.container = container;
        this.be = be;
        addSlot(new Slot(container, DismantlerBlockEntity.SLOT_ITEM,
                GuiLayout.dismantlerItemX(), GuiLayout.dismantlerSlotY()));
        // 槽 1 只接受真正的产线方案(LineSchemeItem)——镜像/纸/剪贴板不允许放入,
        // 与 BlockEntity 的校验保持一致(双保险;权威校验仍在服务端 revert())。
        addSlot(new Slot(container, DismantlerBlockEntity.SLOT_SCHEME,
                GuiLayout.dismantlerSchemeX(), GuiLayout.dismantlerSlotY()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !stack.isEmpty() && stack.getItem() instanceof com.create.productionline.item.LineSchemeItem;
            }
        });
        addPlayerSlots(playerInventory, 112);
    }

    public static DismantlerMenu fromServer(int id, Inventory playerInventory, DismantlerBlockEntity be) {
        return new DismantlerMenu(id, playerInventory, be.getInventory(), be);
    }

    public static DismantlerMenu createClient(int id, Inventory playerInventory) {
        return new DismantlerMenu(id, playerInventory, new SimpleContainer(SLOTS), null);
    }

    /** Runs the authoritative dismantle on the server and reports what happened. */
    public DismantlerBlockEntity.RevertOutcome revert() {
        return be != null ? be.revert() : new DismantlerBlockEntity.RevertOutcome(
                DismantlerBlockEntity.RevertResult.NOT_SERVER_SIDE, 0);
    }

    public ItemStack getItem() {
        return container.getItem(DismantlerBlockEntity.SLOT_ITEM);
    }

    public ItemStack getScheme() {
        return container.getItem(DismantlerBlockEntity.SLOT_SCHEME);
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
            if (index < SLOTS) {
                if (!this.moveItemStackTo(stack, SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, SLOTS, false)) {
                return ItemStack.EMPTY;
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
        return container.stillValid(player);
    }
}
