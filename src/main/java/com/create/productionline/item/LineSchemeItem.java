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
        com.create.productionline.line.scheme.CustomAssembly custom =
                stack.get(com.create.productionline.registry.ModDataComponents.CUSTOM_ASSEMBLY.get());
        // A placeholder is a target and nothing else. "Empty" would be technically true (it
        // has no steps) and useless: the player has to be told that this is a name to author
        // against in an anvil, and not a plan that failed to load. Checked before the empty
        // branch for exactly that reason.
        if (scheme.isPlaceholder() && custom == null) {
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.output",
                    com.create.productionline.util.Names.nameOfItem(scheme.getOutputItem()))
                    .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.placeholder")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        // A hand-built scheme has no Steps while it is being authored (cleared state, or
        // a single-material line), so "no steps" alone must not read as "empty scheme" —
        // that would hide the target the custom recipe is being built for.
        if (scheme.isEmpty() && custom == null) {
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.create_productionline.line_scheme.output",
                com.create.productionline.util.Names.nameOfItem(scheme.getOutputItem()))
                .withStyle(ChatFormatting.GREEN));
        if (custom != null) {
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.custom.anvil")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.recipe",
                    scheme.getRecipeId()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!scheme.getBaseMaterial().isBlank()) {
            tooltip.add(Component.translatable("item.create_productionline.guide.base",
                    com.create.productionline.util.Names.cap(
                            com.create.productionline.util.Names.nameOfItem(scheme.getBaseMaterial())))
                    .withStyle(ChatFormatting.GOLD));
        }
        // Topology chain: [base] -> Machine[material] -> … -> product
        for (String line : com.create.productionline.util.SchemeTopology.lines(scheme)) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        // Hand-built (anvil) state: how many materials, locked or not, and the
        // single-material caveat, which is the one case an assembly line cannot express.
        if (custom != null) {
            if (custom.locked()) {
                tooltip.add(Component.translatable("item.create_productionline.line_scheme.custom.locked",
                        custom.materials().size()).withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                tooltip.add(Component.translatable("item.create_productionline.line_scheme.custom.building",
                        custom.materials().size()).withStyle(ChatFormatting.YELLOW));
            }
            if (custom.locked() && custom.singleMaterialFallback()) {
                tooltip.add(Component.translatable("item.create_productionline.line_scheme.custom.single_pending")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        if (!scheme.getCreateRecipes().isEmpty()) {
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.recipes",
                    scheme.getCreateRecipes().size()).withStyle(ChatFormatting.DARK_AQUA));
        }
        // The player asked for more than one pass: the line has to run again (and the
        // materials have to be prepared for every pass).
        if (scheme.repeats()) {
            // Which kind of repetition this is decides what the player has to build: a
            // product that is also an input can be looped back, anything else has to be
            // restarted from a fresh base every pass.
            tooltip.add(Component.translatable(scheme.recyclesProduct()
                    ? "item.create_productionline.line_scheme.repeat_loop"
                    : "item.create_productionline.line_scheme.repeat_restart",
                    scheme.getRepeatCount(), scheme.getTargetOutputCount())
                    .withStyle(ChatFormatting.AQUA));
        } else if (scheme.recyclesProduct()) {
            // One pass, no repeat — and still incremental, because the product is one of the
            // line's own inputs. That is what makes it runnable from a single seed, and it is
            // precisely what the numbers alone do not say.
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.self_reference")
                    .withStyle(ChatFormatting.AQUA));
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
