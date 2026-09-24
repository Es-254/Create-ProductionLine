package com.create.productionline.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.menu.SchemeLoaderMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Scheme Loader screen: one carrier slot; shows whether the embedded pipeline
 * recipes are currently active, plus the list of generated Create recipes.
 */
public class SchemeLoaderScreen extends AbstractContainerScreen<SchemeLoaderMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("create_productionline", "textures/gui/scheme_loader.png");

    public SchemeLoaderScreen(SchemeLoaderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 196;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 102;
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

        List<String> lines = new ArrayList<>();
        if (this.menu.isActive()) {
            lines.add(Component.translatable("loader.create_productionline.active").getString());
        } else {
            lines.add(Component.translatable("loader.create_productionline.inactive").getString());
        }
        // Repeat budget (server-derived through the menu's data slot) comes second on
        // purpose: when a scheme in this cabinet has to run several times, the player
        // has to prepare the raw materials for every pass, and that line has to
        // survive even when the panel runs out of rows below.
        int repeats = this.menu.getRepeatNotice();
        if (repeats > 1) {
            lines.add(Component.translatable("loader.create_productionline.repeat_warning", repeats).getString());
        }
        int filled = 0;
        for (int i = 0; i < com.create.productionline.menu.SchemeLoaderMenu.LOADER_SLOT_COUNT; i++) {
            ItemStack stack = this.menu.getLoaderContainer().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            LineScheme scheme = LineSchemeSerializer.fromStack(stack);
            if (scheme.isEmpty()) {
                continue;
            }
            filled++;
        }
        lines.add(Component.translatable("loader.create_productionline.slots_filled", filled).getString());
        lines.add(Component.translatable("loader.create_productionline.active_recipes",
                this.menu.getActiveCount()).getString());
        // Rows come from GuiLayout: the panel's well ends at y=56 and the player
        // inventory groove starts at y=99, which leaves room for four 9 px lines.
        int y = com.create.productionline.menu.GuiLayout.LOADER_TEXT_Y;
        int maxY = com.create.productionline.menu.GuiLayout.TEXT_MAX_Y;
        for (String line : lines) {
            if (y > maxY) {
                break;
            }
            for (String wrapped : com.create.productionline.client.CreateGui.wrap(this.font, line, 160)) {
                if (y > maxY) {
                    break;
                }
                guiGraphics.drawString(this.font, wrapped, 8, y, 0x404040, false);
                y += 9;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
