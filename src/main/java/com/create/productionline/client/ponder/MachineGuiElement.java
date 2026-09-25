package com.create.productionline.client.ponder;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.client.CreateGui;
import com.create.productionline.menu.GuiLayout;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the machine half of one of this mod's container GUIs over a Ponder scene, in the top-right
 * corner, with stacks in their real slots.
 *
 * <p>The scenes are about a workflow that happens <em>in a GUI</em> — which slot takes what, which
 * button to press — so showing the panel beats pointing at a block. Slot coordinates come from
 * {@link GuiLayout}, the same numbers the screens use, so the panel can never show an item where
 * the real one would not.
 *
 * <p><b>Only the machine half.</b> A ponder scene has no player inventory, so everything below the
 * panel's groove ({@link GuiLayout#DIVIDER_Y}) would be empty furniture; the blit stops there. What
 * is drawn is what the screens themselves draw into that half: the background, one Create-style
 * cell frame per slot, the stacks, the title, and the button when the panel has one. The frames and
 * the button are <em>not</em> part of the background texture — the screens draw them in code — so a
 * panel that only blitted the texture looked blank in game.
 *
 * <p>The contents are a plain display list the scene fills in as its story advances: the scene shows
 * items appearing in the grid, it does not simulate the machine.
 *
 * <p>Coordinates are plain screen space at 1:1: {@code PonderUI} renders overlay elements through
 * {@code PonderScene#renderOverlay} with only a z-translation applied, next to the narration boxes.
 */
public class MachineGuiElement implements PonderOverlayElement {

    /** Vanilla's own button sprite — the widget the screens put on the panel is a vanilla button. */
    private static final ResourceLocation BUTTON_SPRITE =
            ResourceLocation.withDefaultNamespace("widget/button");

    /** The screens' title label, measured off them: 8 px in from the panel edge. */
    private static final int TITLE_X = 8;
    /** Distance from the screen edge, matching where Ponder's own widget column sits. */
    private static final int MARGIN_X = 14;
    /**
     * The panel hugs the top edge. Ponder puts every narration box at
     * {@code (min(0.75 * width, targetX + 50), targetY + 3)}, and our scenes point at the machine's
     * near-bottom corner, which lands those boxes around two thirds of the screen height — so with the
     * panel one panel-height down from the top (the 26 this used to be) a long line of narration ran
     * straight across its lower half. Up here the two cannot meet.
     */
    private static final int MARGIN_Y = 6;

    private static final int TITLE_COLOR = 0x404040;
    private static final int BUTTON_TEXT_COLOR = 0xFFFFFF;

    /**
     * Everything above the groove, its own dark line included: that line is the panel's edge in the
     * art, so stopping on it makes the half read as a closed panel instead of a cut-off one.
     */
    private static final int MACHINE_HEIGHT = GuiLayout.DIVIDER_Y + 1;

    private final ResourceLocation texture;
    private final List<ItemStack> stacks = new ArrayList<>();
    private final int[] slotX;
    private final int[] slotY;
    private final Component title;
    private final int titleY;
    private boolean visible;

    private Component buttonLabel;
    private boolean buttonHighlighted;
    private int slotHighlighted = -1;
    private int buttonX;
    private int buttonY;
    private int buttonWidth;
    private int buttonHeight;

    /**
     * @param texture the panel background (a 256x256 sheet, as the screens blit it)
     * @param slotX   x of each drawn slot inside the panel, in the menu's own coordinates
     * @param slotY   y of each drawn slot inside the panel
     * @param title   the panel's title label, as the screen prints it
     * @param titleY  y of that label inside the panel
     * @param visible whether it starts visible (scenes usually fade it in after the block appears)
     */
    public MachineGuiElement(ResourceLocation texture, int[] slotX, int[] slotY, Component title, int titleY,
            boolean visible) {
        this.texture = texture;
        this.slotX = slotX.clone();
        this.slotY = slotY.clone();
        this.title = title;
        this.titleY = titleY;
        for (int i = 0; i < slotX.length; i++) {
            stacks.add(ItemStack.EMPTY);
        }
        this.visible = visible;
    }

    /**
     * Adds the panel's button, drawn in the same place and size as the screen's own widget so the
     * scene can point at what the narration tells the player to press.
     */
    public MachineGuiElement withButton(int x, int y, int width, int height, Component label) {
        this.buttonX = x;
        this.buttonY = y;
        this.buttonWidth = width;
        this.buttonHeight = height;
        this.buttonLabel = label;
        return this;
    }

    /** Puts a stack into one of the drawn slots (or clears it with {@link ItemStack#EMPTY}). */
    public void setStack(int index, ItemStack stack) {
        if (index >= 0 && index < stacks.size()) {
            stacks.set(index, stack == null ? ItemStack.EMPTY : stack);
        }
    }

    /**
     * Draws a pulsing frame around the panel's button, which is how a scene says "press this".
     *
     * <p>Ponder's own cue vocabulary only reaches the world ({@code overlay().showControls(...)} anchors to a
     * position in a block), and everything these machines actually ask the player to do happens in a GUI:
     * a click is a click on a control, not on the block that opens the panel. So the two things a scene can
     * ask for are expressed here, with the same frame: {@link #setButtonHighlighted(boolean)} for "press
     * this button" and {@link #setSlotHighlighted(int)} for "this item goes into that slot". The block-level
     * cues stay for what is genuinely about the block — which side the bar is on, where the redstone lamp
     * sits — and pointing there is what {@code pointAt} is for.
     */
    public void setButtonHighlighted(boolean highlighted) {
        this.buttonHighlighted = highlighted;
    }

    /**
     * Draws the same pulsing frame around one drawn slot, for a step that puts an item into it. Pass -1 to
     * clear. Scenes use it the way they use the button highlight: on when the step asks for the slot, off
     * when the next step is about something else.
     */
    public void setSlotHighlighted(int index) {
        this.slotHighlighted = index;
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
        int x = ui.width - GuiLayout.PANEL_WIDTH - MARGIN_X;
        int y = MARGIN_Y;
        graphics.blit(texture, x, y, 0, 0, GuiLayout.PANEL_WIDTH, MACHINE_HEIGHT, 256, 256);

        // The cell frames, exactly as every screen draws them: one pixel outside the slot.
        for (int i = 0; i < stacks.size(); i++) {
            CreateGui.slot(graphics, x + slotX[i] - 1, y + slotY[i] - 1, GuiLayout.SLOT_FRAME, GuiLayout.SLOT_FRAME);
        }

        if (buttonLabel != null) {
            graphics.blitSprite(BUTTON_SPRITE, x + buttonX, y + buttonY, buttonWidth, buttonHeight);
            graphics.drawCenteredString(Minecraft.getInstance().font, buttonLabel,
                    x + buttonX + buttonWidth / 2, y + buttonY + (buttonHeight - 8) / 2, BUTTON_TEXT_COLOR);
            if (buttonHighlighted) {
                pulse(graphics, x + buttonX, y + buttonY, buttonWidth, buttonHeight);
            }
        }

        if (slotHighlighted >= 0 && slotHighlighted < stacks.size()) {
            pulse(graphics, x + slotX[slotHighlighted] - 1, y + slotY[slotHighlighted] - 1,
                    GuiLayout.SLOT_FRAME, GuiLayout.SLOT_FRAME);
        }

        graphics.drawString(Minecraft.getInstance().font, title, x + TITLE_X, y + titleY, TITLE_COLOR, false);

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + slotX[i], y + slotY[i]);
            }
        }
    }

    /**
     * The one highlight both cues share: a frame two pixels outside the control, pulsing between one and two
     * pixels thick, so it reads as "this one" next to the narration rather than as part of the panel's art.
     */
    private static void pulse(GuiGraphics graphics, int x, int y, int width, int height) {
        float pulse = 0.55F + 0.45F * Mth.sin(AnimationTickHolder.getTicks() * 0.35F);
        int colour = FastColor.ARGB32.color(Math.round(200 + 55 * pulse), 255, 215, 90);
        int thickness = pulse > 0.75F ? 2 : 1;
        for (int i = 0; i < thickness; i++) {
            graphics.renderOutline(x - 2 - i, y - 2 - i, width + 3 + 2 * i, height + 3 + 2 * i, colour);
        }
    }
}
