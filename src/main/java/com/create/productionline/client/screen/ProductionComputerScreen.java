package com.create.productionline.client.screen;

import java.util.List;

import com.create.productionline.menu.ProductionComputerMenu;
import com.create.productionline.network.ModPayloads;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Production Computer screen.
 *
 * <p>Slots are rendered by the screen at their exact coordinates, so the dark
 * cells always match the real slots. Computation only runs when the player
 * presses the "Compute" button (explicit trigger), and the status area reports
 * the target item / step totals instead of raw facility spam.
 *
 * <p>Positions come from {@link com.create.productionline.menu.GuiLayout}, and
 * the very same status list is also sent to the player's chat by the server (see
 * {@code ModPayloads#handleCompute}) — the panel is short, chat is not.
 */
public class ProductionComputerScreen extends AbstractContainerScreen<ProductionComputerMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("create_productionline", "textures/gui/production_computer.png");

    private static final int BUTTON_W = com.create.productionline.menu.GuiLayout.COMPUTER_BUTTON_WIDTH;
    private static final int BUTTON_H = com.create.productionline.menu.GuiLayout.COMPUTER_BUTTON_HEIGHT;

    public ProductionComputerScreen(ProductionComputerMenu menu, Inventory playerInventory, Component title) {
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
        int bx = left + (this.imageWidth - BUTTON_W) / 2;
        int by = top + com.create.productionline.menu.GuiLayout.COMPUTER_BUTTON_Y;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.create_productionline.compute"),
                b -> sendCompute())
                .bounds(bx, by, BUTTON_W, BUTTON_H)
                .build());
    }

    /**
     * Resolves the recipe on the client (mirrors JEI's data source) and sends the
     * hit to the server. The server re-resolves the recipe id against its own
     * recipe manager, so this is a hint rather than an instruction.
     */
    private void sendCompute() {
        var resolved = com.create.productionline.client.ClientRecipeResolver.resolve(this.menu.getTargetItem());
        com.create.productionline.ProductionLineMod.LOGGER.info(
                "CPL client resolve: recipe={} out={} cat={} inputs={}", resolved.recipeId(), resolved.outputId(),
                resolved.categoryId(), resolved.inputs());
        com.create.productionline.network.ModPayloads.sendComputeRequest(
                resolved.outputId(), resolved.recipeId(), resolved.categoryId(), resolved.inputs(),
                resolved.outputId());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BACKGROUND, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        for (Slot slot : this.menu.slots) {
            drawSlotBg(guiGraphics, slot);
        }
    }

    /** Draws the 18x18 slot cell exactly around each slot position (offset by the panel origin). */
    private void drawSlotBg(GuiGraphics guiGraphics, Slot slot) {
        int x = this.leftPos + slot.x - 1;
        int y = this.topPos + slot.y - 1;
        com.create.productionline.client.CreateGui.slot(guiGraphics, x, y, 18, 18);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, Component.translatable("container.inventory"),
                this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        // The panel shows what fits between the button and the player-inventory
        // groove; the same list goes to the player's chat in full (see
        // ModPayloads#handleCompute), so a long plan is never truncated away.
        List<Component> lines = statusLines();
        int y = com.create.productionline.menu.GuiLayout.COMPUTER_TEXT_Y;
        int maxY = com.create.productionline.menu.GuiLayout.TEXT_MAX_Y;
        for (Component line : lines) {
            if (y > maxY) {
                break;
            }
            for (String wrapped : com.create.productionline.client.CreateGui.wrap(this.font,
                    line.getString(), 160)) {
                if (y > maxY) {
                    break;
                }
                guiGraphics.drawString(this.font, wrapped, 12, y, 0x404040, false);
                y += 9;
            }
        }
    }

    private List<Component> statusLines() {
        return com.create.productionline.menu.ComputerStatus.lines(this.menu.getResultCode(),
                this.menu.getLastErrorCode(), this.menu.getSchemeItem(), this.menu.getClipboardItem());
    }
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}