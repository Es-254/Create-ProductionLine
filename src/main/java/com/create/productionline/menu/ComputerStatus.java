package com.create.productionline.menu;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.block.entity.PlaceholderPrompt;
import com.create.productionline.block.entity.ProductionComputerBlockEntity;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
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
 *
 * <p>This is also where the one line that is a QUESTION rather than a status lives
 * ({@link #placeholderPrompt}), because it shares the same channel and the same rule: it goes to
 * the player who pressed [Compute] and to nobody else.
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
                // reports RESULT_NO_RECIPE / RESULT_NOT_CONVERTIBLE (and writes nothing) for
                // everybody else, so this text must not read as an instruction every player
                // could follow.
                LineScheme scheme = LineSchemeSerializer.fromStack(schemeStack);
                if (scheme.getOutputItem().isBlank()) {
                    scheme = LineSchemeSerializer.fromStack(clipboardStack);
                }
                // A placeholder has zero steps, so isEmpty() is true for it by design and
                // cannot be used to detect that the write happened: the target id is the
                // proof, not the step count.
                //
                // The placeholder has two origins and they mean different things to the server:
                // "nothing produces this item" and "a live recipe exists that this mod cannot
                // convert". The player acts on the difference (build the line from nothing vs.
                // look at the mapping config), so the reason is reported here instead of one
                // blanket text. ERROR_NOT_CONVERTIBLE is the classification the computer attaches
                // to the second origin precisely for this line: losing it the moment a placeholder
                // was written would delete the only information the refusal used to carry.
                boolean notConvertible =
                        lastErrorCode == ProductionComputerBlockEntity.ERROR_NOT_CONVERTIBLE;
                if (scheme.getOutputItem().isBlank()) {
                    // The carrier was taken out (or replaced) after the run: nothing left to
                    // name, but the outcome is still "no plan, author it by hand". The reason is
                    // kept in both cases — telling a not-convertible player "no recipe was found"
                    // would send them looking for a recipe the server does have.
                    out.add(Component.translatable(notConvertible
                            ? "screen.create_productionline.computer.not_convertible"
                            : "screen.create_productionline.computer.no_recipe_found"));
                } else {
                    out.add(Component.translatable(notConvertible
                            ? "screen.create_productionline.computer.not_convertible_placeholder"
                            : "screen.create_productionline.computer.placeholder",
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

    // --- the placeholder question ---------------------------------------------

    /**
     * The private question the computer asks before it writes a placeholder for a target whose
     * recipe cannot be converted, plus the two clickable answers under it.
     *
     * <p>The reason stays part of the question — the same "cannot be converted" the refusal line
     * reports — because that is the information the answer depends on; the question only adds what
     * the player is being offered. Without it the operator would have to guess why the compute
     * failed before deciding whether authoring a line by hand is worth it.
     *
     * <p>The options are vanilla {@code RUN_COMMAND} clicks, because chat offers nothing else. That
     * is safe here not because of the click but because of what the click does: the commands behind
     * it re-check the answering player, the permission level, the deadline, the target slot and the
     * open menu server-side ({@link PlaceholderPrompt}), and the write itself can only ever produce
     * a scheme whose {@code RecipeId} is empty — an item, never a recipe.
     */
    public static List<Component> placeholderPrompt(String targetId) {
        return List.of(
                Component.translatable("screen.create_productionline.computer.placeholder_prompt",
                        displayName(targetId)),
                Component.empty()
                        .append(option("screen.create_productionline.computer.placeholder_accept",
                                PlaceholderPrompt.ACCEPT_COMMAND, ChatFormatting.YELLOW))
                        .append(Component.literal("   "))
                        .append(option("screen.create_productionline.computer.placeholder_decline",
                                PlaceholderPrompt.DECLINE_COMMAND, ChatFormatting.GRAY)));
    }

    /** The private reply when the player who was asked answered "no". */
    public static Component promptDeclined() {
        return Component.translatable("screen.create_productionline.computer.placeholder_declined");
    }

    /**
     * The private reply for every answer that no longer applies: an expired question, one asked of
     * somebody else, a permission that was revoked, or a click with no question behind it at all.
     * It says what happened (nothing was written) and how to get the offer back, which is the one
     * action that helps in all of those cases.
     */
    public static Component promptExpired() {
        return Component.translatable("screen.create_productionline.computer.placeholder_expired");
    }

    /** One clickable answer: a colour, an underline (it must look pressable) and its command. */
    private static Component option(String key, String command, ChatFormatting color) {
        return Component.translatable(key).withStyle(style -> style
                .withColor(color)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}
