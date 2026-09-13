package com.create.productionline.item;

import java.util.List;

import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 产线方案 (Line Scheme) — carries a computed {@link LineScheme} as NBT on the
 * item. Written by the Production Computer; put into a Scheme Loader it
 * activates the embedded recipes; the Dismantler reads it when turning a
 * product back into its inputs.
 */
public class LineSchemeItem extends Item {

    public LineSchemeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        LineScheme scheme = LineSchemeSerializer.fromStack(stack);
        if (scheme.isEmpty()) {
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.create_productionline.line_scheme.output",
                com.create.productionline.util.Names.nameOfItem(scheme.getOutputItem()))
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.create_productionline.line_scheme.recipe",
                scheme.getRecipeId()).withStyle(ChatFormatting.DARK_GRAY));
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
        if (!scheme.getCreateRecipes().isEmpty()) {
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.recipes",
                    scheme.getCreateRecipes().size()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** Produces a demo scheme stack used in the creative tab. */
    public static ItemStack sampleStack() {
        ItemStack stack = new ItemStack(com.create.productionline.registry.ModItems.LINE_SCHEME.get());
        LineScheme scheme = new LineScheme();
        scheme.setRecipeId("create_productionline:sample");
        scheme.setOutputItem("minecraft:iron_ingot");
        LineScheme.Step step = scheme.addStep("create:mechanical_press", 1);
        step.addInput("minecraft:iron_block");
        step.addOutput("minecraft:iron_ingot");
        LineSchemeSerializer.saveToStack(stack, scheme);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.translatable("item.create_productionline.line_scheme.sample")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)));
        return stack;
    }
}
