package com.create.productionline.line.analyzer;

import java.util.List;

import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.recipegen.CreateRecipePack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Plan generator: turns the <b>derived Create recipe JSON</b> into the display
 * chain of a scheme.
 *
 * <p>The plan is a MIRROR of the recipe ("计划 = 推导配方的镜像"): both come from
 * the single derivation entry point ({@code RecipeDeriver.entriesFor}), and the
 * station list is read out of the very JSON that gets installed. There is no
 * separate machine-picking heuristic any more, so a machine can never be paired
 * with a material it does not actually process (the old index-by-index pairing
 * was a fake semantics: it produced plans that looked plausible and matched
 * nothing).
 *
 * <p>Two shapes exist, and both are read off the recipe {@code type}:
 * <ul>
 *   <li><b>{@code create:sequenced_assembly}</b> — the base material goes onto the
 *       line first, then <i>every following material is applied by its own
 *       Deployer station</i> (机械手), because Create's sequenced assembly consumes
 *       the item a deployer holds as that step's ingredient. One machine, one
 *       material, in recipe order.</li>
 *   <li><b>every other type</b> (flat processing, {@code create:mechanical_crafting})
 *       — ONE station carried by the machine that really performs that type
 *       ({@link CreateRecipePack#facilityOf}); it lists the remaining materials as
 *       its inputs (an empty list means "this machine processes the item the feed
 *       already put on the line"). A single-material recipe therefore really is
 *       "one machine + the material it converts".</li>
 * </ul>
 *
 * <pre>
 * 例：目标 铁镐（木板+木棍+铁锭 序列装配） =>
 *   [木板 投料] → [机械手·木棍] → [机械手·铁锭] → 铁镐
 * 例：目标 木棍（#木板 切割） =>
 *   [木板 投料] → [机械锯·(线上物品)] → 木棍
 * </pre>
 */
public final class MachineSelector {

    /** The pseudo facility of the first station: materials entering the line. */
    public static final String FEED = "cpl:feed";
    /** The transitional item chained between stations of a plan. */
    public static final String INTERMEDIATE = "create_productionline:generic_intermediate";
    /** Deployer (机械手) applies/uses items — NOT a belt feeder. */
    public static final String DEPLOYER = "create:deployer";
    /** Fallback facility for an unknown / unparsable recipe type. */
    public static final String PRESS = "create:mechanical_press";

    /** The recipe type of a sequenced assembly (the only multi-station shape). */
    private static final String SEQUENCED_ASSEMBLY = "create:sequenced_assembly";

    private MachineSelector() {
    }

    /**
     * Appends the plan chain of one derived recipe to {@code scheme}:
     *
     * <pre>
     * [基底] -&gt; [器械1 + 原料1] -&gt; [器械2 + 原料2] -&gt; … -&gt; [产物]
     * </pre>
     *
     * <p>Step 1 is always the feed station: the base material ({@code orderedInputs[0]})
     * enters the line and is carried on. What follows depends on the recipe type of
     * {@code entry} (missing/malformed JSON is treated as a flat recipe):
     * <ul>
     *   <li>{@code create:sequenced_assembly}: one {@code create:deployer} station
     *       per extra material, in order; the carried item is the generic
     *       intermediate between stations and only the LAST station yields the
     *       product;</li>
     *   <li>otherwise: exactly one station, run by
     *       {@link CreateRecipePack#facilityOf} of that type ({@code create:mechanical_press}
     *       when the type is unknown), whose inputs are every remaining material and
     *       whose output is the product.</li>
     * </ul>
     *
     * @param scheme        the plan being built
     * @param orderedInputs the recipe's materials, in recipe order; {@code [0]} is the base
     * @param entry         the derived Create recipe entry the plan mirrors
     * @param output        the product of the recipe
     */
    public static void appendChainSteps(LineScheme scheme, List<String> orderedInputs,
            LineScheme.CreateRecipeEntry entry, String output) {
        if (scheme == null || orderedInputs == null || orderedInputs.isEmpty()) {
            return; // defensively nothing to lay out (the caller guarantees materials)
        }
        String product = (output == null || output.isBlank()) ? INTERMEDIATE : output;

        // 1) the base enters the line and stays on it
        String base = orderedInputs.get(0);
        LineScheme.Step head = scheme.addStep(FEED, 1);
        head.addInput(base);
        head.addOutput(base);

        String type = recipeType(entry);
        if (SEQUENCED_ASSEMBLY.equals(type)) {
            if (orderedInputs.size() == 1) {
                // A sequence payload normally carries >= 1 deploy step; with no extra
                // material the chain would never yield anything, so show the single
                // deployer station that produces the item instead.
                scheme.addStep(DEPLOYER, 1).addOutput(product);
                return;
            }
            for (int i = 1; i < orderedInputs.size(); i++) {
                LineScheme.Step station = scheme.addStep(DEPLOYER, 1);
                station.addInput(orderedInputs.get(i));   // the one material this deployer applies
                boolean last = (i == orderedInputs.size() - 1);
                station.addOutput(last ? product : INTERMEDIATE);
            }
            return;
        }

        // Flat processing / mechanical crafting: ONE machine, the one that performs
        // the derived type, fed with every material the feed did not put on the belt.
        String facility = CreateRecipePack.facilityOf(type);
        LineScheme.Step station = scheme.addStep(facility == null ? PRESS : facility, 1);
        for (int i = 1; i < orderedInputs.size(); i++) {
            station.addInput(orderedInputs.get(i));
        }
        station.addOutput(product);
    }

    /**
     * Reads the {@code type} of a derived recipe payload. A missing, blank or
     * malformed payload/tag returns {@code null}, which the caller treats as a
     * flat single-machine recipe.
     */
    private static String recipeType(LineScheme.CreateRecipeEntry entry) {
        if (entry == null || entry.getJson() == null || entry.getJson().isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(entry.getJson());
            if (parsed != null && parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                if (root.has("type") && root.get("type").isJsonPrimitive()) {
                    return root.get("type").getAsString();
                }
            }
        } catch (RuntimeException e) {
            // Unreadable payload: fall back to the flat layout (one machine).
        }
        return null;
    }
}
