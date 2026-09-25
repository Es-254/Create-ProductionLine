package com.create.productionline.client.ponder;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.menu.GuiLayout;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Draws one of this mod's container GUIs over a Ponder scene, in the top-right corner, with stacks
 * in their real slots.
 *
 * <p>The scenes are about a workflow that happens <em>in a GUI</em> — which slot takes what, which
 * button to press — so showing the panel beats pointing at a block. Slot coordinates come from
 * {@link GuiLayout}, the same numbers the screens use, so the panel can never show an item where
 * the real one would not.
 *
 * <p>The contents are a plain display list the scene fills in as its story advances: the scene shows
 * items appearing in the grid, it does not simulate the machine.
 */
public class MachineGuiElement implements PonderOverlayElement {

    private final ResourceLocation texture;
    private final List<ItemStack> stacks = new ArrayList<>();
    private final int[] slotX;
    private final int[] slotY;
    private boolean visible;

    /**
     * @param texture the panel background (a 256x256 sheet, as the screens blit it)
     * @param slotX   x of each drawn slot inside the panel, in the menu's own coordinates
     * @param slotY   y of each drawn slot inside the panel
     * @param visible whether it starts visible (scenes usually fade it in after the block appears)
     */
    public MachineGuiElement(ResourceLocation texture, int[] slotX, int[] slotY, boolean visible) {
        this.texture = texture;
        this.slotX = slotX.clone();
        this.slotY = slotY.clone();
        for (int i = 0; i < slotX.length; i++) {
            stacks.add(ItemStack.EMPTY);
        }
        this.visible = visible;
    }

    /** Puts a stack into one of the drawn slots (or clears it with {@link ItemStack#EMPTY}). */
    public void setStack(int index, ItemStack stack) {
        if (index >= 0 && index < stacks.size()) {
            stacks.set(index, stack == null ? ItemStack.EMPTY : stack);
        }
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void render(PonderScene scene, PonderUI ui, GuiGraphics graphics, float partialTicks) {
        if (!visible) {
            return;
        }
        int x = ui.width - GuiLayout.PANEL_WIDTH - 14;
        int y = 26;
        graphics.blit(texture, x, y, 0, 0, GuiLayout.PANEL_WIDTH, GuiLayout.PANEL_HEIGHT, 256, 256);
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            graphics.renderItem(stack, x + slotX[i], y + slotY[i]);
        }
    }
}
