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
 * <p>A scheme that names a target but carries NO plan counts as already cleared, so the
 * HAMMER row accepts it without the CLEAR step: paper drops a plan, and such a scheme has
 * nothing to drop. That is the placeholder the computer writes for an operator when the
 * server has no recipe for the target at all ({@code RESULT_PLACEHOLDER}) — without this row
 * the operator could never author a line for an item no recipe produces, because a computed
 * scheme cannot exist for it. Nothing else changes: a scheme that still carries a plan is
 * refused a material exactly as before, and a scheme without a target stays "empty" and
 * cannot be hammered either.
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

        if (rightStack.isEmpty()) {
            return Decision.refuse(); // empty right slot: nothing to hammer
        }
        // Which state the item is in. Normally the component says so, because the CLEAR row
        // below is what writes it. A scheme that names a target but has NO plan is already in
        // the state that row would produce — there is nothing left to drop — so the operator
        // can hammer the material straight in instead of burning a sheet of paper on a no-op.
        CustomAssembly base = custom != null ? custom : clearedWithoutPlan(left);
        if (base == null) {
            return Decision.refuse(); // a material before clearing: the plan is still there
        }
        String material = itemIdOf(rightStack);
        if (material == null) {
            return Decision.refuse(); // unmappable stack
        }
        // The product itself IS a legal material: a self-recursive / doubling recipe
        // ("A + B = 2A") needs A as the base that goes on the belt first.
        CustomAssembly next = base.withMaterial(material);
        return new Decision(Action.HAMMER, next, CustomAssemblyPlanner.rebuild(next), 1, 1, false);
    }

    private static Decision clear(ItemStack left) {
        CustomAssembly custom = clearedAssembly(LineSchemeSerializer.fromStack(left));
        if (custom == null) {
            return Decision.refuse(); // an empty scheme has no target to keep
        }
        return new Decision(Action.CLEAR, custom, CustomAssemblyPlanner.cleared(custom.targetItem()), 1, 1, false);
    }

    /**
     * The cleared assembly this scheme ALREADY is, or {@code null} when the plan has to be
     * dropped first.
     *
     * <p>"Already cleared" is decided from the plan itself (a target, no steps), not from the
     * placeholder marker: what makes hammering legal is that there is nothing to clear, and
     * that is true of every scheme in this shape — including the placeholder the computer
     * writes for a target no recipe produces ({@code RESULT_PLACEHOLDER}). Trusting the marker
     * instead would refuse a legitimately empty scheme for a reason that is not about what the
     * item holds.
     */
    private static CustomAssembly clearedWithoutPlan(ItemStack left) {
        LineScheme source = LineSchemeSerializer.fromStack(left);
        if (!source.getSteps().isEmpty()) {
            return null; // still a plan on the item: paper is what drops it
        }
        return clearedAssembly(source);
    }

    /**
     * The cleared assembly for a scheme that names a target: no material yet, and the
     * compute-time numbers inherited — never recomputed here, because the player asked for
     * {@code targetOutputCount} items when they ran the computer.
     *
     * <p>Returns {@code null} for a scheme without a target: a cleared scheme is defined by
     * the target it keeps, so there is nothing to hand to the anvil.
     */
    private static CustomAssembly clearedAssembly(LineScheme source) {
        String target = source.getOutputItem();
        if (target == null || target.isBlank()) {
            return null;
        }
        return new CustomAssembly(List.of(), false, false, target,
                source.getTargetOutputCount(), source.getRepeatCount());
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
