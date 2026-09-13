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
                int i = 1;
                for (LineScheme.Step step : scheme.getSteps()) {
                    String text = i++ + ". ";
                    if (!step.getInputs().isEmpty()) {
                        java.util.List<String> ins = new java.util.ArrayList<>();
                        for (String in : step.getInputs()) {
                            ins.add(com.create.productionline.util.Names.cap(
                                    com.create.productionline.util.Names.nameOfItem(in)));
                        }
                        text += "[" + String.join("+", ins) + "] -> ";
                    }
                    text += com.create.productionline.util.Names.cap(
                            com.create.productionline.util.Names.facilityName(step.getFacilityType()));
                    if (!step.getOutputs().isEmpty()) {
                        java.util.List<String> outs = new java.util.ArrayList<>();
                        for (String out : step.getOutputs()) {
                            outs.add(com.create.productionline.util.Names.cap(
                                    com.create.productionline.util.Names.nameOfItem(out)));
                        }
                        text += " -> [" + String.join(",", outs) + "]";
                    }
                    tooltip.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
                }
                return;
            }
        }
        if (custom.contains(ClipboardCompat.GUIDE_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag guide = custom.getCompound(ClipboardCompat.GUIDE_KEY);
            int total = guide.getInt("TotalSteps");
            if (total > 0) {
                tooltip.add(Component.translatable("item.create_productionline.guide.title")
                        .withStyle(ChatFormatting.AQUA));
                for (int i = 1; i <= total; i++) {
                    String line = guide.getString("Step_" + i);
                    if (!line.isBlank()) {
                        tooltip.add(Component.literal("  " + line).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        }
    }
}

