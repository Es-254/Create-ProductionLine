package com.create.productionline.line.analyzer;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.line.mapper.RecipeDescriptor;
import com.create.productionline.line.scheme.LineScheme;

/**
 * The machine-selection core ("万能算法"): maps recipe material features to a
 * feature-matched, complexity-scaled list of Create machines, then lays the
 * pipeline out as an ordered sequence.
 *
 * <p>Two kinds of plans exist:
 * <ul>
 *   <li><b>Assembly / crafting plans</b> — executed by Create's
 *       {@code create:sequenced_assembly}: the first material enters the line
 *       first (any feeder), and <i>every following material is applied by its
 *       own Deployer station</i> (机械手) in order — Create consumes the item a
 *       deployer holds as the step ingredient, so the plan lists one Deployer
 *       per extra material, exactly matching the generated sequence recipe.</li>
 *   <li><b>Machine-process plans</b> — feature-chosen machines (press/mixer/
 *       crusher/…) plus flexible feed steps for extra materials.</li>
 * </ul>
 *
 * <pre>
 * 例：目标 步枪弹药，材料 钢锭+铜板+火药+底火 =>
 *   [钢锭 投料] → [机械手·铜板] → [机械手·火药] → [机械手·底火] → 步枪弹药×48
 * </pre>
 */
public final class MachineSelector {

    public static final String FEED = "cpl:feed";
    /** The transitional item chained between stations of a plan. */
    public static final String INTERMEDIATE = "create_productionline:generic_intermediate";
    /** Feeder for inserting raw materials onto a belt: the Mechanical Arm (动力臂). */
    public static final String ARM = "create:mechanical_arm";
    /** Deployer (机械手) applies/uses items — NOT a belt feeder. */
    public static final String DEPLOYER = "create:deployer";
    public static final String CRUSHING_WHEEL = "create:crushing_wheel";
    public static final String MILLSTONE = "create:millstone";
    public static final String MIXER = "create:mechanical_mixer";
    public static final String PRESS = "create:mechanical_press";
    public static final String SAW = "create:mechanical_saw";

    private MachineSelector() {
    }

    /**
     * Chooses the machine set for a source recipe. Assembly / crafting recipes
     * are turned into a Deployer sequence (the embedded
     * {@code create:sequenced_assembly} recipe uses one deploy step per extra
     * material — see {@link #appendAssemblySteps}); the returned list is only a
     * hint for the fallback single-machine path. Otherwise the material features
     * select the machine. The Mechanical Crafter is NOT used.
     */
    public static List<String> selectForSource(String categoryId, RecipeAnalyzer.Result analysis) {
        if (RecipeDescriptor.isAssemblyLikeCategory(categoryId)) {
            return List.of(DEPLOYER);
        }
        return selectMachines(analysis);
    }

    /** Feature → machine rules (see the design draft). */
    public static List<String> selectMachines(RecipeAnalyzer.Result analysis) {
        List<String> machines = new ArrayList<>();

        // 1. pre-processing stage
        if (analysis.isOre) {
            machines.add(CRUSHING_WHEEL);
        } else if (analysis.isOrganic) {
            machines.add(MILLSTONE);
        }

        // 2. mixing / pressing stage
        if (analysis.uniqueCount >= 3 || analysis.hasFluid) {
            machines.add(MIXER);
        }

        // 3. finishing stage
        if (analysis.needsPrecision) {
            machines.add(DEPLOYER);   // deployer applies items (wax/planting etc.)
        } else if (analysis.isWood) {
            machines.add(SAW);
        }

        // 4. fallback
        if (machines.isEmpty()) {
            machines.add(PRESS);
        }
        return machines;
    }

    /** Repeat factor from complexity (how many machines / how large the line). */
    public static int scaleOf(RecipeAnalyzer.Result analysis) {
        int c = analysis.complexity();
        if (c <= 6) {
            return 1;
        }
        if (c <= 12) {
            return 2;
        }
        return 3;
    }

    /**
     * Lays the whole plan out as ONE linear chain:
     *
     * <pre>
     * [基底] -&gt; [器械1 + 原料1] -&gt; [器械2 + 原料2] -&gt; … -&gt; [产物]
     * </pre>
     *
     * <p>Reading the plan top to bottom is reading the line:
     * <ul>
     *   <li><b>step 1</b> — the base material goes onto the line (any feeder:
     *       arm / funnel / chute / drop-in);</li>
     *   <li><b>every following step</b> — exactly one machine paired with exactly
     *       one material, i.e. the machine that applies that material. For a
     *       sequenced-assembly plan that machine is always a Deployer, so the
     *       station count equals the deploy steps of the embedded recipe; for a
     *       machine-process plan the feature-selected machines are walked in
     *       order;</li>
     *   <li>the carried item is chained through the stations
     *       (base → 通用中间产物 → …) and only the <b>last</b> station yields the
     *       product.</li>
     * </ul>
     *
     * <p>If there are more machines than extra materials, the surplus machines
     * still get their own (material-less) station so the plan does not hide any
     * facility the player has to place.
     *
     * @param uniqueInputs ordered, de-duplicated materials; {@code [0]} is the base
     * @param machines     the ordered machine list for this recipe (never empty)
     * @param outputItem   the product, annotated on the final station
     */
    public static void appendChainSteps(LineScheme scheme, List<String> uniqueInputs,
            List<String> machines, String outputItem) {
        if (scheme == null || uniqueInputs == null || uniqueInputs.isEmpty()) {
            return;
        }
        String base = uniqueInputs.get(0);

        // 1) the base enters the line
        LineScheme.Step head = scheme.addStep(FEED, 1);
        head.addInput(base);
        head.addOutput(base);

        List<String> ms = (machines == null || machines.isEmpty()) ? List.of(PRESS) : machines;
        int extra = uniqueInputs.size() - 1;              // materials still to be applied
        int stations = Math.max(extra, ms.size());        // never hide a machine
        for (int i = 0; i < stations; i++) {
            String machine = ms.get(Math.min(i, ms.size() - 1));
            LineScheme.Step step = scheme.addStep(machine, 1);
            if (i < extra) {
                step.addInput(uniqueInputs.get(i + 1));   // the material this machine applies
            }
            boolean last = (i == stations - 1);
            boolean hasProduct = outputItem != null && !outputItem.isBlank();
            step.addOutput(last && hasProduct ? outputItem : INTERMEDIATE);
        }
    }
}
