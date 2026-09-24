package com.create.productionline.event;

import java.util.List;

import com.create.productionline.compat.ClipboardCompat;
import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.item.LineSchemeMirrorItem;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Makes the content of plan/guide carriers visible: any item (paper, clipboard,
 * ...) that carries our NBT gets a readable tooltip, so "nothing happened" is
 * never ambiguous.
 */
public final class ItemTooltipHandler {

    private ItemTooltipHandler() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof LineSchemeItem || stack.getItem() instanceof LineSchemeMirrorItem) {
            // The Line Scheme and mirror items already render their own tooltip.
            return;
        }
        List<Component> tooltip = event.getToolTip();

        net.minecraft.world.item.component.CustomData data = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag custom = data.copyTag();

        if (custom.contains(LineScheme.SCHEME_TAG_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            LineScheme scheme = LineSchemeSerializer.load(custom.getCompound(LineScheme.SCHEME_TAG_KEY));
            if (!scheme.isEmpty()) {
                tooltip.add(Component.translatable("item.create_productionline.line_scheme.output",
                        com.create.productionline.util.Names.nameOfItem(scheme.getOutputItem()))
                        .withStyle(ChatFormatting.GREEN));
                if (!scheme.getBaseMaterial().isBlank()) {
                    tooltip.add(Component.translatable("item.create_productionline.guide.base",
                            com.create.productionline.util.Names.cap(
                                    com.create.productionline.util.Names.nameOfItem(scheme.getBaseMaterial())))
                            .withStyle(ChatFormatting.GOLD));
                }
                for (String line : com.create.productionline.util.SchemeTopology.lines(scheme)) {
                    tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                }
                // The computer writes the plan AND the build guide onto the same carrier, so
                // both belong on this tooltip: returning here (as this did) made the guide
                // unreachable for exactly the items it is written to.
                if (custom.contains(ClipboardCompat.GUIDE_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    addBuildGuide(tooltip, scheme);
                }
                return;
            }
        }
        if (custom.contains(ClipboardCompat.GUIDE_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            // A guide whose plan was erased still says how the line is built.
            addBuildGuide(tooltip, null);
        }
    }

    /**
     * The "how to build it" block.
     *
     * <p>Deliberately not a re-print of the stored {@code LineBuildGuide} steps: those are the
     * raw English ids the clipboard payload carries, and the plan chain above already names
     * the same stations in the player's language. What the chain cannot say is how to <em>set
     * the line up</em> — belt first, one Deployer per material facing down, where the product
     * comes out, and that a repeating line needs its own return belt. Those lines are the
     * {@code guide.*} keys, and they only appear when they apply, so a flat single-machine
     * plan does not get assembly-line instructions it has no use for.
     */
    private static void addBuildGuide(List<Component> tooltip, LineScheme scheme) {
        boolean deployer = scheme == null || scheme.getSteps().stream().anyMatch(
                step -> step.getFacilityType() != null && step.getFacilityType().contains("deploy"));
        boolean repeats = scheme != null && scheme.repeats();
        if (!deployer && !repeats) {
            return; // the plan chain already says everything there is to say
        }
        tooltip.add(Component.translatable("item.create_productionline.guide.title")
                .withStyle(ChatFormatting.AQUA));
        if (deployer) {
            for (String hint : new String[]{"item.create_productionline.guide.howto",
                    "item.create_productionline.guide.deployer"}) {
                for (String wrapped : com.create.productionline.util.TextWrap.wrapIndented(
                        "  " + Component.translatable(hint).getString(), "  ")) {
                    tooltip.add(Component.literal(wrapped).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (repeats) {
            for (String wrapped : com.create.productionline.util.TextWrap.wrapIndented(
                    "  " + Component.translatable("item.create_productionline.guide.count").getString(), "  ")) {
                tooltip.add(Component.literal(wrapped).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}

