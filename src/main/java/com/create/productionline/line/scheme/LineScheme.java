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
 *                "Inputs":[...], "Outputs":[...] }, ... ]
 * }
 * </pre>
 */
public final class LineScheme {

    public static final int CURRENT_VERSION = 1;
    public static final String SCHEME_TAG_KEY = "LineScheme";

    private int version = CURRENT_VERSION;
    private String recipeId = "";
    private String outputItem = "";
    private String baseMaterial = "";
    private final List<Step> steps = new ArrayList<>();
    private final List<CreateRecipeEntry> createRecipes = new ArrayList<>();

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

    @Override
    public String toString() {
        return "LineScheme{v" + version + ", recipe=" + recipeId + ", out=" + outputItem + ", steps=" + steps + '}';
    }
}
