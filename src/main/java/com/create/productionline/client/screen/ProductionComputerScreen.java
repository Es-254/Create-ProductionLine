package com.create.productionline.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.block.entity.ProductionComputerBlockEntity;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.menu.ProductionComputerMenu;
import com.create.productionline.network.ModPayloads;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Production Computer screen.
 *
 * <p>Slots are rendered by the screen at their exact coordinates, so the dark
 * cells always match the real slots. Computation only runs when the player
 * presses the "Compute" button (explicit trigger), and the status area reports
 * the target item / step totals instead of raw facility spam.
 */
public class ProductionComputerScreen extends AbstractContainerScreen<ProductionComputerMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("create_productionline", "textures/gui/production_computer.png");

    private static final int BUTTON_W = 60;
    private static final int BUTTON_H = 16;

    public ProductionComputerScreen(ProductionComputerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 80;
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        int bx = left + (this.imageWidth - BUTTON_W) / 2;
        int by = top + 56;
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

        List<String> lines = statusLines();
        int y = 38;
        int maxY = 50;
        for (String line : lines) {
            if (y > maxY) {
                break;
            }
            for (String wrapped : wrap(this.font, line, 160)) {
                if (y > maxY) {
                    break;
                }
                guiGraphics.drawString(this.font, wrapped, 12, y, 0x404040, false);
                y += 9;
            }
        }
    }

    private List<String> statusLines() {
        int code = this.menu.getResultCode();
        List<String> out = new ArrayList<>();
        switch (code) {
            case ProductionComputerBlockEntity.RESULT_GENERATED -> {
                out.add(Component.translatable("screen.create_productionline.computer.generated").getString());
                LineScheme scheme = LineSchemeSerializer.fromStack(this.menu.getSchemeItem());
                if (scheme.isEmpty()) {
                    scheme = LineSchemeSerializer.fromStack(this.menu.getClipboardItem());
                }
                if (!scheme.isEmpty()) {
                    String name = displayName(scheme.getOutputItem());
                    out.add(Component.translatable("screen.create_productionline.computer.product", name).getString());
                    out.add(Component.translatable("screen.create_productionline.computer.plan_size",
                            scheme.getSteps().size(), scheme.totalFacilityCount()).getString());
                }
            }
            case ProductionComputerBlockEntity.RESULT_NOT_CONVERTIBLE ->
                    out.add(Component.translatable("screen.create_productionline.computer.not_convertible").getString());
            case ProductionComputerBlockEntity.RESULT_NO_SCHEME ->
                    out.add(Component.translatable("screen.create_productionline.computer.no_scheme").getString());
            case ProductionComputerBlockEntity.RESULT_NO_RECIPE -> {
                // M7: the result code alone cannot say WHY nothing could be mapped
                // ("no recipe" == no recipe at all / no usable output / no registry
                // id), so the server-reported cause is shown as an extra line.
                //
                // ORDER MATTERS: renderLabels() only has room for two 9px lines
                // (y=38, y=47, then maxY=50 - below that sits the Compute button),
                // and the generic cannot_map text alone wraps to ~4 lines at 160px.
                // Appending the specific reason after it would therefore never be
                // drawn, so the specific reason goes FIRST and cannot_map second.
                switch (this.menu.getLastErrorCode()) {
                    case ProductionComputerBlockEntity.ERROR_NO_RECIPE_PRODUCING ->
                            out.add(Component.translatable(
                                    "screen.create_productionline.computer.no_recipe_found").getString());
                    case ProductionComputerBlockEntity.ERROR_NO_USABLE_OUTPUT ->
                            out.add(Component.translatable(
                                    "screen.create_productionline.computer.no_usable_output").getString());
                    case ProductionComputerBlockEntity.ERROR_NO_REGISTRY_ID ->
                            out.add(Component.translatable(
                                    "screen.create_productionline.computer.no_registry_id").getString());
                    default -> {
                        // 0 / 4: nothing more specific to say
                    }
                }
                out.add(Component.translatable("screen.create_productionline.computer.cannot_map").getString());
            }
            case ProductionComputerBlockEntity.RESULT_NO_TARGET ->
                    out.add(Component.translatable("screen.create_productionline.computer.no_target").getString());
            default ->
                    out.add(Component.translatable("screen.create_productionline.computer.empty").getString());
        }
        return out;
    }

    private static String displayName(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key != null) {
            var item = BuiltInRegistries.ITEM.get(key);
            if (item != null) {
                return item.getName(new net.minecraft.world.item.ItemStack(item)).getString();
            }
        }
        return itemId;
    }

    /** Splits a line so it fits the given pixel width (word- and char-aware). */
    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] words = text.split("(?<=\\s)|(?=\\s)");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String probe = line.length() == 0 ? word : line + word;
            if (font.width(probe) <= maxWidth || line.length() == 0) {
                line.append(word);
            } else {
                lines.add(line.toString().trim());
                line.setLength(0);
                if (font.width(word) > maxWidth) {
                    String rest = word;
                    while (font.width(rest) > maxWidth) {
                        int cut = 1;
                        while (cut < rest.length() && font.width(rest.substring(0, cut + 1)) <= maxWidth) {
                            cut++;
                        }
                        lines.add(rest.substring(0, cut));
                        rest = rest.substring(cut);
                    }
                    line.append(rest);
                } else {
                    line.append(word);
                }
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString().trim());
        }
        return lines;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
