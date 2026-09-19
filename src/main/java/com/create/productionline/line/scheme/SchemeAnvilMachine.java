package com.create.productionline.line.scheme;

import java.util.List;

import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.registry.ModDataComponents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The anvil state machine, extracted from the event handler so the whole decision
 * table can be asserted without an anvil, a player or a server.
 *
 * <p>Three states, entered in order:
 *
 * <pre>
 *   CLEAR   [scheme with a plan] + paper -> cleared scheme (target kept, materials dropped)
 *   HAMMER  [cleared scheme]     + item  -> scheme + 1 material (order preserved, repeatable)
 *   LOCK    [scheme, >= 1 item]  + paper -> locked scheme (frozen)
 * </pre>
 *
 * <p>Every branch that is not one of those three is {@link Action#REFUSE} (the item is
 * left exactly as it was) or {@link Action#PASS} (not our item at all — the vanilla
 * anvil keeps working).
 *
 * <p>The caller is responsible for the two things this class deliberately cannot know:
 * the server side (the component is only ever written server-side) and the permission
 * check, which is passed in as {@code allowed}.
 */
public final class SchemeAnvilMachine {

    public enum Action {
        /** Not our item: leave the anvil alone. */
        PASS,
        /** Illegal input: cancel, nothing consumed. */
        REFUSE,
        /** Step 1. */
        CLEAR,
        /** Step 2. */
        HAMMER,
        /** Step 3. */
        LOCK
    }

    /**
     * @param action       what the caller has to do
     * @param next         the component to write (null for PASS/REFUSE)
     * @param plan         the plan to write (null for PASS/REFUSE)
     * @param materialCost right-slot items consumed (always 1 when something happens)
     * @param levelCost    level cost the caller must charge (1: the vanilla gate requires
     *                     {@code cost > 0}, the level is refunded on pickup)
     * @param notice       the player should be told this mode is provisional (single material)
     */
    public record Decision(Action action, CustomAssembly next, LineScheme plan, int materialCost,
            int levelCost, boolean notice) {

        public boolean applied() {
            return action == Action.CLEAR || action == Action.HAMMER || action == Action.LOCK;
        }

        static Decision pass() {
            return new Decision(Action.PASS, null, null, 0, 0, false);
        }

        static Decision refuse() {
            return new Decision(Action.REFUSE, null, null, 0, 0, false);
        }
    }

    private SchemeAnvilMachine() {
    }

    /**
     * Decides what the anvil should do.
     *
     * @param left    the left input slot
     * @param right   the right input slot
     * @param allowed whether the acting player passed the permission gate
     */
    public static Decision decide(ItemStack left, ItemStack right, boolean allowed) {
        if (left == null || left.isEmpty() || !(left.getItem() instanceof LineSchemeItem)) {
            return Decision.pass(); // not our item: the anvil stays vanilla
        }
        if (!allowed) {
            return Decision.pass(); // not an OP: plain vanilla anvil behaviour
        }
        if (left.getCount() != 1) {
            return Decision.refuse(); // stacked schemes are never a legal input
        }

        CustomAssembly custom = left.get(ModDataComponents.CUSTOM_ASSEMBLY.get());
        ItemStack rightStack = right == null ? ItemStack.EMPTY : right;
        boolean paper = rightStack.is(Items.PAPER);

        if (custom != null && custom.locked()) {
            return Decision.refuse(); // a locked scheme is frozen
        }

        if (paper) {
            if (custom == null) {
                return clear(left);
            }
            return lock(custom);
        }

        if (custom == null || rightStack.isEmpty()) {
            return Decision.refuse(); // a material before clearing, or an empty right slot
        }
        String material = itemIdOf(rightStack);
        if (material == null) {
            return Decision.refuse(); // unmappable stack
        }
        // The product itself IS a legal material: a self-recursive / doubling recipe
        // ("A + B = 2A") needs A as the base that goes on the belt first.
        CustomAssembly next = custom.withMaterial(material);
        return new Decision(Action.HAMMER, next, CustomAssemblyPlanner.rebuild(next), 1, 1, false);
    }

    private static Decision clear(ItemStack left) {
        LineScheme source = LineSchemeSerializer.fromStack(left);
        String target = source.getOutputItem();
        if (target == null || target.isBlank()) {
            return Decision.refuse(); // an empty scheme has no target to keep
        }
        // The compute-time numbers are inherited, never recomputed here: the player asked
        // for `targetOutputCount` items when they ran the computer.
        CustomAssembly custom = new CustomAssembly(List.of(), false, false, target,
                source.getTargetOutputCount(), source.getRepeatCount());
        return new Decision(Action.CLEAR, custom, CustomAssemblyPlanner.cleared(target), 1, 1, false);
    }

    private static Decision lock(CustomAssembly custom) {
        if (!custom.isReadyToLock()) {
            return Decision.refuse(); // nothing hammered yet
        }
        if (custom.isSingleMaterial() && custom.materials().get(0).equals(custom.targetItem())) {
            // Only the product was hammered: not a recipe (it would consume A and hand A
            // back). A doubling line needs the product as its BASE plus a further material.
            return Decision.refuse();
        }
        CustomAssembly locked = custom.withLocked(true);
        return new Decision(Action.LOCK, locked, CustomAssemblyPlanner.rebuild(locked), 1, 1,
                locked.singleMaterialFallback());
    }

    private static String itemIdOf(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }
}
