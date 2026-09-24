package com.create.productionline.client.screen;

import com.create.productionline.menu.DismantlerMenu;
import com.create.productionline.network.ModPayloads;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Dismantler screen: two slots (intermediate + scheme) and a "拆解" button that
 * reverts the item back into raw materials and leaves a Line Scheme Mirror.
 */
public class DismantlerScreen extends AbstractContainerScreen<DismantlerMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("create_productionline", "textures/gui/dismantler.png");

    public DismantlerScreen(DismantlerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = com.create.productionline.menu.GuiLayout.PANEL_WIDTH;
        this.imageHeight = com.create.productionline.menu.GuiLayout.PANEL_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = com.create.productionline.menu.GuiLayout.INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.create_productionline.dismantle"),
                b -> ModPayloads.sendDismantleRequest())
                .bounds(left + (this.imageWidth - com.create.productionline.menu.GuiLayout.DISMANTLER_BUTTON_WIDTH) / 2,
                        top + com.create.productionline.menu.GuiLayout.DISMANTLER_BUTTON_Y,
                        com.create.productionline.menu.GuiLayout.DISMANTLER_BUTTON_WIDTH,
                        com.create.productionline.menu.GuiLayout.DISMANTLER_BUTTON_HEIGHT)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            drawSlotBg(guiGraphics, slot);
        }
    }

    private void drawSlotBg(GuiGraphics guiGraphics, net.minecraft.world.inventory.Slot slot) {
        int sx = this.leftPos + slot.x - 1;
        int sy = this.topPos + slot.y - 1;
        com.create.productionline.client.CreateGui.slot(guiGraphics, sx, sy, 18, 18);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, Component.translatable("container.inventory"),
                this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
        // Starts under the drawn well and stops above the button (GuiLayout): the
        // hint used to be printed at y=40, straight across the well and the slots.
        int y = com.create.productionline.menu.GuiLayout.DISMANTLER_TEXT_Y;
        int maxY = com.create.productionline.menu.GuiLayout.DISMANTLER_TEXT_MAX_Y;
        for (String wrapped : com.create.productionline.client.CreateGui.wrap(this.font,
                Component.translatable("dismantler.create_productionline.hint").getString(), 160)) {
            if (y > maxY) {
                break;
            }
            guiGraphics.drawString(this.font, wrapped, 8, y, 0x3C3C3C, false);
            y += 9;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
