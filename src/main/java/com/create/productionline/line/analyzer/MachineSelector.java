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
     * Lays out an assembly / crafting plan the way Create actually executes
     * {@code create:sequenced_assembly} recipes:
     * <ol>
     *   <li>the base material first — a flexible feeder step (arm / funnel /
     *       chute / drop-in, any works);</li>
     *   <li>one <b>Deployer station per extra material</b>, in recipe order —
     *       that deployer holds the material and applies it to the carried item
     *       on the belt (Create consumes the held item). The count of Deployer
     *       steps therefore always equals the deploy steps of the embedded
     *       sequence recipe, so building "one machine per step" actually
     *       produces the item.</li>
     * </ol>
     */
    public static void appendAssemblySteps(LineScheme scheme, java.util.List<String> uniqueInputs) {
        if (scheme == null || uniqueInputs == null || uniqueInputs.size() < 2) {
            return;
        }
        LineScheme.Step base = scheme.addStep(FEED, 1);
        base.addInput(uniqueInputs.get(0));
        for (int i = 1; i < uniqueInputs.size(); i++) {
            LineScheme.Step step = scheme.addStep(DEPLOYER, 1);
            step.addInput(uniqueInputs.get(i));
        }
    }

    /** Marks the last non-feed station of an assembly plan as the product output. */
    public static void annotateAssemblyOutput(LineScheme scheme, String outputItem) {
        if (scheme == null || outputItem == null || outputItem.isBlank()) {
            return;
        }
        java.util.List<LineScheme.Step> steps = scheme.getSteps();
        for (int i = steps.size() - 1; i >= 0; i--) {
            LineScheme.Step step = steps.get(i);
            if (!FEED.equals(step.getFacilityType())) {
                step.addOutput(outputItem);
                return;
            }
        }
    }

    /**
     * Appends the ordered pipeline steps for a machine-process plan: a flexible
     * feed step for every raw material after the first, then the processing
     * machines (which also receive the base material / output product).
     */
    public static void appendSteps(LineScheme scheme, List<String> uniqueInputs, List<String> machines) {
        // Extra raw materials enter the machine(s) through flexible feeding
        // (arm / funnel / chute / drop-in — never a Deployer).
        for (int i = 1; i < uniqueInputs.size(); i++) {
            LineScheme.Step step = scheme.addStep(FEED, 1);
            step.addInput(uniqueInputs.get(i));
        }
        for (String machine : machines) {
            LineScheme.Step step = scheme.addStep(machine, 1);
            if (scheme.getSteps().size() == 1 && !uniqueInputs.isEmpty()) {
                step.addInput(uniqueInputs.get(0)); // base material enters the first machine
            }
        }
    }

    /**
     * Annotates the plan so it reads clearly: the base material enters the first
     * processing machine, and the last processing machine outputs the product.
     * (The Mechanical Crafter is intentionally excluded. A Deployer finishing
     * station IS a processing machine and keeps the output annotation.)
     */
    public static void annotate(LineScheme scheme, String baseMaterial, String outputItem) {
        List<LineScheme.Step> machines = new ArrayList<>();
        for (LineScheme.Step step : scheme.getSteps()) {
            if (!step.getFacilityType().equals(FEED) && !step.getFacilityType().equals(ARM)) {
                machines.add(step);
            }
        }
        if (!machines.isEmpty()) {
            if (baseMaterial != null && !baseMaterial.isBlank()) {
                machines.get(0).addInput(baseMaterial);
            }
            if (outputItem != null && !outputItem.isBlank()) {
                machines.get(machines.size() - 1).addOutput(outputItem);
            }
        }
    }
}
