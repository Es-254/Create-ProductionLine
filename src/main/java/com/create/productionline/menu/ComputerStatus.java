package com.create.productionline.menu;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.block.entity.ProductionComputerBlockEntity;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Status lines of the Production Computer, built once and used twice: the screen
 * draws them inside the panel, and the server sends the very same list to the
 * player who pressed [Compute] — privately, in that player's chat, so the
 * outcome of a calculation stays readable after the GUI is closed.
 *
 * <p>Order is by importance, because the panel only has room for four rows
 * (see {@link GuiLayout}): product, target output / repeat budget, material
 * budget, plan size, embedded recipe count. Chat always receives the whole list.
 */
public final class ComputerStatus {

    private ComputerStatus() {
    }

    /**
     * @param resultCode    {@link ProductionComputerBlockEntity#getResultCode()}
     * @param lastErrorCode {@link ProductionComputerBlockEntity#getLastErrorCode()}
     * @param schemeStack   the carrier slot (a written scheme on success)
     * @param clipboardStack the optional paper slot, used when the scheme slot is empty
     */
    public static List<Component> lines(int resultCode, int lastErrorCode, ItemStack schemeStack,
            ItemStack clipboardStack) {
        List<Component> out = new ArrayList<>();
        switch (resultCode) {
            case ProductionComputerBlockEntity.RESULT_GENERATED -> {
                LineScheme scheme = LineSchemeSerializer.fromStack(schemeStack);
                if (scheme.isEmpty()) {
                    scheme = LineSchemeSerializer.fromStack(clipboardStack);
                }
                if (scheme.isEmpty()) {
                    out.add(Component.translatable("screen.create_productionline.computer.generated"));
                } else {
                    out.add(Component.translatable("screen.create_productionline.computer.product",
                            displayName(scheme.getOutputItem())));
                    if (scheme.recyclesProduct()) {
                        // The product is one of its own inputs: the line is incremental, which is
                        // what lets it run from a single seed item — and what the numbers alone
                        // do not say.
                        out.add(Component.translatable(
                                "item.create_productionline.line_scheme.self_reference"));
                    }
                    if (scheme.repeats()) {
                        out.add(Component.translatable("screen.create_productionline.computer.repeat",
                                scheme.getTargetOutputCount(), scheme.getRepeatCount()));
                        out.add(Component.translatable("screen.create_productionline.computer.material_budget",
                                scheme.materialsPerPass(), scheme.getRepeatCount(), scheme.materialBudget()));
                    } else {
                        out.add(Component.translatable("screen.create_productionline.computer.target_output",
                                scheme.getTargetOutputCount()));
                    }
                    out.add(Component.translatable("screen.create_productionline.computer.plan_size",
                            scheme.getSteps().size(), scheme.totalFacilityCount()));
                    out.add(Component.translatable("screen.create_productionline.computer.embedded",
                            scheme.getCreateRecipes().size()));
                    // No topology preview here on purpose: the plan chain belongs to the
                    // item tooltip (see ScreenTopology); the computer only reports totals.
                }
            }
            case ProductionComputerBlockEntity.RESULT_NOT_CONVERTIBLE ->
                    out.add(Component.translatable("screen.create_productionline.computer.not_convertible"));
            case ProductionComputerBlockEntity.RESULT_PLACEHOLDER -> {
                // Only ever reached for a requester with authoring permission: the computer
                // reports RESULT_NO_RECIPE (and writes nothing) for everybody else, so this
                // text must not read as an instruction every player could follow.
                LineScheme scheme = LineSchemeSerializer.fromStack(schemeStack);
                if (scheme.getOutputItem().isBlank()) {
                    scheme = LineSchemeSerializer.fromStack(clipboardStack);
                }
                // A placeholder has zero steps, so isEmpty() is true for it by design and
                // cannot be used to detect that the write happened: the target id is the
                // proof, not the step count.
                if (scheme.getOutputItem().isBlank()) {
                    // The carrier was taken out (or replaced) after the run: nothing left to
                    // name, but the outcome is still "no recipe, author it by hand".
                    out.add(Component.translatable("screen.create_productionline.computer.no_recipe_found"));
                } else {
                    out.add(Component.translatable("screen.create_productionline.computer.placeholder",
                            displayName(scheme.getOutputItem())));
                }
                out.add(Component.translatable("screen.create_productionline.computer.placeholder_anvil"));
            }
            case ProductionComputerBlockEntity.RESULT_NO_SCHEME ->
                    out.add(Component.translatable("screen.create_productionline.computer.no_scheme"));
            case ProductionComputerBlockEntity.RESULT_NO_RECIPE -> {
                switch (lastErrorCode) {
                    case ProductionComputerBlockEntity.ERROR_NO_RECIPE_PRODUCING ->
                            out.add(Component.translatable(
                                    "screen.create_productionline.computer.no_recipe_found"));
                    case ProductionComputerBlockEntity.ERROR_NO_USABLE_OUTPUT ->
                            out.add(Component.translatable(
                                    "screen.create_productionline.computer.no_usable_output"));
                    case ProductionComputerBlockEntity.ERROR_NO_REGISTRY_ID ->
                            out.add(Component.translatable(
                                    "screen.create_productionline.computer.no_registry_id"));
                    default -> {
                        // 0 / 4: nothing more specific to say
                    }
                }
                out.add(Component.translatable("screen.create_productionline.computer.cannot_map"));
            }
            case ProductionComputerBlockEntity.RESULT_NO_TARGET ->
                    out.add(Component.translatable("screen.create_productionline.computer.no_target"));
            default -> out.add(Component.translatable("screen.create_productionline.computer.empty"));
        }
        return out;
    }

    /** Item name for a registry id, falling back to the raw id when it is unknown. */
    private static String displayName(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key != null) {
            var item = BuiltInRegistries.ITEM.get(key);
            if (item != null) {
                return item.getName(new ItemStack(item)).getString();
            }
        }
        return itemId;
    }
}
