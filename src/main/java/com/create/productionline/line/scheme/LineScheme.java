package com.create.productionline.line.scheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable-ish view of a production line plan.
 *
 * <p>NBT schema (SRS 3.1):
 * <pre>
 * {
 *   "Version": 1,
 *   "RecipeId": "modid:recipe_name",
 *   "OutputItem": "modid:item",
 *   "Steps": [ { "Order":0, "FacilityType":"create:mechanical_saw", "Count":1,
 *                "Inputs":[...], "Outputs":[...] }, ... ],
 *   "Placeholder": true   // only present on a placeholder scheme (see isPlaceholder())
 * }
 * </pre>
 */
public final class LineScheme {

    public static final int CURRENT_VERSION = 2;
    public static final String SCHEME_TAG_KEY = "LineScheme";

    private int version = CURRENT_VERSION;
    private String recipeId = "";
    private String outputItem = "";
    private String baseMaterial = "";
    /**
     * Marker of a PLACEHOLDER scheme: it names the item to produce and carries no plan at
     * all, because the server has no usable recipe for that item. Written by the Production
     * Computer for a player with permission level 2, whose only other route to a scheme
     * naming that item is blocked — the OP anvil flow can refine a scheme, but a computed
     * scheme cannot exist without a recipe.
     *
     * <p>The marker is a <b>display</b> fact, never an authority: what makes a placeholder
     * harmless is the empty {@link #recipeId}, because every installed recipe is re-derived
     * from that id against the server's live {@code RecipeManager}
     * ({@code SchemeLoaderBlockEntity.entriesForSlot}). Combined with zero steps it also
     * keeps the placeholder out of the loader's filled-slot count, i.e. off the bar. Absent
     * from NBT means {@code false}, so schemes written before this field existed keep their
     * meaning (see {@link LineSchemeSerializer}).
     */
    private boolean placeholder = false;
    private final List<Step> steps = new ArrayList<>();
    private final List<CreateRecipeEntry> createRecipes = new ArrayList<>();
    /** Materials the line applies in USE mode instead of consuming (see {@link #getToolMaterials()}). */
    private final List<String> toolMaterials = new ArrayList<>();
    /**
     * The output count the player asked for (the target slot's stack size). The
     * installed recipe still produces one craft per pass — this is the number the
     * plan and the repeat metadata are measured against.
     */
    private int targetOutputCount = 1;
    /**
     * How often the line has to run to reach {@link #targetOutputCount}. 1 means one
     * pass is enough; feeding the product back is the player's build, because Create's
     * sequenced assembly cannot loop by itself.
     */
    private int repeatCount = 1;

    /** A generated, spec-conformant Create recipe JSON file payload. */
    public static final class CreateRecipeEntry {
        private String fileName = "";
        private String json = "";

        public CreateRecipeEntry() {
        }

        public CreateRecipeEntry(String fileName, String json) {
            this.fileName = fileName == null ? "" : fileName;
            this.json = json == null ? "" : json;
        }

        public String getFileName() {
            return fileName;
        }

        public String getJson() {
            return json;
        }

        public boolean isValid() {
            return !fileName.isBlank() && !json.isBlank();
        }
    }

    /** A single processing step of the plan. */
    public static final class Step {
        private int order;
        private String facilityType = "create:mechanical_press";
        private int count = 1;
        private final List<String> inputs = new ArrayList<>();
        private final List<String> outputs = new ArrayList<>();

        public Step() {
        }

        public Step(int order, String facilityType, int count, List<String> inputs, List<String> outputs) {
            this.order = order;
            this.facilityType = facilityType;
            this.count = Math.max(1, count);
            if (inputs != null) {
                this.inputs.addAll(inputs);
            }
            if (outputs != null) {
                this.outputs.addAll(outputs);
            }
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public String getFacilityType() {
            return facilityType;
        }

        public void setFacilityType(String facilityType) {
            this.facilityType = Objects.requireNonNullElse(facilityType, "");
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = Math.max(1, count);
        }

        public List<String> getInputs() {
            return Collections.unmodifiableList(inputs);
        }

        public void addInput(String input) {
            if (input != null && !input.isBlank() && !inputs.contains(input)) {
                inputs.add(input);
            }
        }

        public List<String> getOutputs() {
            return Collections.unmodifiableList(outputs);
        }

        public void addOutput(String output) {
            if (output != null && !output.isBlank() && !outputs.contains(output)) {
                outputs.add(output);
            }
        }

        @Override
        public String toString() {
            return "Step{" + order + ", " + facilityType + " x" + count + ", in=" + inputs + ", out=" + outputs + '}';
        }
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = Objects.requireNonNullElse(recipeId, "");
    }

    public String getOutputItem() {
        return outputItem;
    }

    public void setOutputItem(String outputItem) {
        this.outputItem = Objects.requireNonNullElse(outputItem, "");
    }

    public String getBaseMaterial() {
        return baseMaterial;
    }

    public void setBaseMaterial(String baseMaterial) {
        this.baseMaterial = Objects.requireNonNullElse(baseMaterial, "");
    }

    /**
     * Builds a placeholder for {@code targetItem}: the target, an <b>empty</b> recipe id,
     * no base material and <b>zero</b> steps.
     *
     * <p>The empty id is the security-critical part and not a formatting detail: the Scheme
     * Loader installs nothing but what it can re-derive from {@code RecipeId} against the
     * server's live {@code RecipeManager}, so a placeholder can never promise a recipe that
     * does not exist. Its whole value is naming the target, which is what lets an operator
     * hand-author the line in an anvil (see {@code SchemeAnvilMachine}, which accepts a
     * target without a plan as an already cleared scheme).
     */
    public static LineScheme placeholder(String targetItem) {
        LineScheme out = new LineScheme();
        out.setRecipeId(""); // nothing is installable from an empty id - that is the point
        out.setOutputItem(targetItem);
        out.setBaseMaterial("");
        out.setPlaceholder(true);
        return out;
    }

    /** True when this scheme only names a target and holds no plan yet. */
    public boolean isPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(boolean placeholder) {
        this.placeholder = placeholder;
    }

    public List<Step> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    /** Mutable access for the (same-package) serializer. */
    List<Step> stepsMutable() {
        return steps;
    }

    public Step addStep(String facilityType, int count) {
        Step step = new Step(steps.size(), facilityType, count, null, null);
        steps.add(step);
        return step;
    }

    /**
     * True when there is no usable plan on this scheme: no steps, or no target. A
     * placeholder ({@link #isPlaceholder()}) is "empty" by this same rule, which is what
     * keeps it out of the Scheme Loader's filled-slot count and therefore off the bar.
     */
    public boolean isEmpty() {
        return steps.isEmpty() || outputItem.isBlank();
    }

    public List<CreateRecipeEntry> getCreateRecipes() {
        return Collections.unmodifiableList(createRecipes);
    }

    public void addCreateRecipe(String fileName, String json) {
        if (fileName != null && !fileName.isBlank() && json != null && !json.isBlank()) {
            createRecipes.add(new CreateRecipeEntry(fileName, json));
        }
    }

    /** Total count of facilities referenced by the plan. */
    public int totalFacilityCount() {
        int total = 0;
        for (Step step : steps) {
            total += step.getCount();
        }
        return total;
    }

    public int getTargetOutputCount() {
        return targetOutputCount;
    }

    public void setTargetOutputCount(int targetOutputCount) {
        this.targetOutputCount = Math.max(1, Math.min(RepeatPlan.MAX_TARGET, targetOutputCount));
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = Math.max(1, repeatCount);
    }

    /** Stores both numbers of a computed {@link RepeatPlan}. */
    public void setRepeatPlan(RepeatPlan plan) {
        if (plan == null) {
            return;
        }
        setTargetOutputCount(plan.targetOutput());
        setRepeatCount(plan.reachable() ? plan.repeatCount() : 1);
    }

    /**
     * True when the product is also an input: the line can close the loop by feeding the
     * product back to the belt head ("A + B = 2A"). False means every pass has to start
     * from a freshly inserted base — the difference the plan has to spell out.
     */
    public boolean recyclesProduct() {
        if (outputItem == null || outputItem.isBlank()) {
            return false;
        }
        if (outputItem.equals(baseMaterial)) {
            return true;
        }
        for (Step step : steps) {
            if (step.getInputs().contains(outputItem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Materials the line <em>uses</em> rather than consumes: a Deployer holding one of these
     * applies it in USE mode, so it stays on the line while the line keeps running. Filled by
     * {@code SchemeRoles} from the source recipe's own roles (a smithing recipe's base is the
     * equipment being upgraded, which is what a Deployer uses here) — the plan's own steps
     * cannot tell a consumed material from a used one.
     */
    public List<String> getToolMaterials() {
        return Collections.unmodifiableList(toolMaterials);
    }

    public void addToolMaterial(String material) {
        if (material != null && !material.isBlank() && !toolMaterials.contains(material)) {
            toolMaterials.add(material);
        }
    }

    public boolean isToolMaterial(String material) {
        return material != null && toolMaterials.contains(material);
    }

    /**
     * Material units one pass consumes: the base plus everything the deployer stations
     * apply. Multiplied by {@link #getRepeatCount()} this is the raw-material budget the
     * player has to prepare, which is the number the panels report.
     */
    public int materialsPerPass() {
        int units = 0;
        for (Step step : steps) {
            units += step.getInputs().size();
        }
        return units;
    }

    /** Raw materials for the whole run: one pass multiplied by the repeat count. */
    public int materialBudget() {
        return materialsPerPass() * getRepeatCount();
    }

    /** The player asked for more than one pass, so the plan has to say so. */
    public boolean repeats() {
        return repeatCount > 1;
    }

    /** Copies another scheme's target/repeat numbers onto this one (fluent). */
    public LineScheme repeatedLike(LineScheme other) {
        if (other != null) {
            setTargetOutputCount(other.getTargetOutputCount());
            setRepeatCount(other.getRepeatCount());
        }
        return this;
    }

    @Override
    public String toString() {
        return "LineScheme{v" + version + ", recipe=" + recipeId + ", out=" + outputItem + ", steps=" + steps + '}';
    }
}
