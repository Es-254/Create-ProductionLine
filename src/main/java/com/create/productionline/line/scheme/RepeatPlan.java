package com.create.productionline.line.scheme;

/**
 * How often a line has to run to reach the player's target output, and what that
 * means for the materials they have to prepare.
 *
 * <p>The installed Create recipe is always a <em>single craft</em> payload: Create's
 * sequenced assembly cannot loop on its own, and writing N crafts' worth of
 * materials into one payload would consume N times the input for one craft's output.
 * The repeat count is therefore metadata — it drives the plan instruction ("repeat
 * N times"), the material budget shown to the player, and the tooltip — and the
 * physical looping is the player's belt/feedback build.
 *
 * <p>Two shapes are distinguished:
 *
 * <ul>
 *   <li><b>ordinary</b> (the recipe does not consume its own product):
 *       {@code repeats = ceil(target / perCraft)};</li>
 *   <li><b>doubling / recursive</b> (the recipe consumes {@code c} copies of the
 *       product and yields {@code p > c}): the line bootstraps from the one unit it
 *       puts on the belt, so {@code repeats = ceil((target - 1) / (p - c))} and the
 *       net gain per pass is {@code p - c}.</li>
 * </ul>
 *
 * <p>When the recipe cannot grow the stock at all ({@code p <= c}) the target is
 * unreachable by repeating: {@code reachable} is false, {@code repeatCount} stays 1
 * and the player is told to bring the product themselves. There is deliberately no
 * "loop until it works" path anywhere in this class.
 */
public record RepeatPlan(int targetOutput, int perCraft, int consumedPerCraft, int repeatCount,
        boolean reachable) {

    /** Defensive upper bound for a target typed into the target slot. */
    public static final int MAX_TARGET = 4096;

    public static RepeatPlan of(int targetOutput, int perCraft, int consumedPerCraft) {
        int target = Math.max(1, Math.min(MAX_TARGET, targetOutput));
        int craft = Math.max(1, perCraft);
        int consumed = Math.max(0, consumedPerCraft);
        if (target <= 1) {
            return new RepeatPlan(target, craft, consumed, 1, true);
        }
        if (consumed == 0) {
            // Nothing of the product is eaten: every pass adds `craft` items.
            int repeats = (target + craft - 1) / craft;
            return new RepeatPlan(target, craft, consumed, Math.max(1, repeats), true);
        }
        int net = craft - consumed;
        if (net <= 0) {
            // The recipe eats as much as it makes (or more): repeating cannot grow
            // the stock, so the player has to supply the product.
            return new RepeatPlan(target, craft, consumed, 1, false);
        }
        // Bootstrapping from the single unit that goes onto the belt first.
        int repeats = (target - 1 + net - 1) / net;
        return new RepeatPlan(target, craft, consumed, Math.max(1, repeats), true);
    }

    /** A plan for "one craft", used wherever no target was expressed. */
    public static RepeatPlan single(int perCraft) {
        return of(1, perCraft, 0);
    }

    public boolean repeats() {
        return repeatCount > 1;
    }

    /** Raw materials one pass consumes, multiplied by the number of passes. */
    public int scaledMaterialCount(int materialsPerPass) {
        return Math.max(0, materialsPerPass) * repeatCount;
    }

    /** True when the product feeds itself, i.e. the line can bootstrap. */
    public boolean selfFeeding() {
        return consumedPerCraft > 0;
    }
}
