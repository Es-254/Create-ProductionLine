package com.create.productionline.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 通用中间产物 — extends Create's {@link SequencedAssemblyItem} so that Create's
 * Sequenced Assembly engine can track the assembly progress (NBT "progress") on
 * the stack itself. This is exactly what Create's own "incomplete" items do and
 * fixes the intermediate getting "stuck".
 */
public class GenericIntermediateItem extends com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem {

    public GenericIntermediateItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_productionline.generic_intermediate.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
