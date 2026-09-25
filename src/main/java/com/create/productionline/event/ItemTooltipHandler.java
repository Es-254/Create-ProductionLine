package com.create.productionline.event;

import java.util.List;

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
                // No build-guide block on purpose: the hand-written hint lines it used to
                // print ("belt first, one Deployer per material…") are generic boilerplate
                // the author asked to be dropped. The LineBuildGuide payload is still
                // written — it is what makes a plain item a carrier
                // (ClipboardCompat.isCarrier) — it is simply not rendered.
                return;
            }
        }
    }
}