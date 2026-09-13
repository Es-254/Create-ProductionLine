package com.create.productionline.line.mapper;

import java.util.List;
import java.util.Locale;

import com.create.productionline.line.scheme.LineScheme;

/**
 * Turns a {@link RecipeDescriptor} into an ordered {@link LineScheme} of Create
 * facilities (SRS 4.1).
 *
 * <p>Mapping rules (heuristic, extensible through {@link MappingDictionary}):
 * <ul>
 *   <li>an assembly-like / multi-input recipe becomes one "deploy" step with
 *       {@code create:mechanical_arm}s (1 arm per distinct ingredient, capped);</li>
 *   <li>a machine process (e.g. {@code create:milling}) becomes an optional
 *       loading step plus the processing step at the mapped facility;</li>
 *   <li>an unmapped category yields {@link MappingResult#failure} — the computer
 *       shows the reason instead of generating a bogus plan (TC-01).</li>
 * </ul>
 *
 * <p>Note: JEI itself is client-side; the descriptors used here are normally built
 * from the server-side {@code RecipeManager}, which holds the same data recipes.
 * The JEI category name is used as the category id for compatibility.
 */
public final class RecipeMapper {

    private final MappingDictionary dictionary;

    public RecipeMapper(MappingDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public MappingDictionary getDictionary() {
        return dictionary;
    }

    /**
     * Maps a descriptor to a scheme. Never returns {@code null}.
     *
     * <p>Each Create recipe type is performed by exactly one machine, so a
     * mapped descriptor produces a single processing step at that facility with
     * the recipe's inputs and output recorded. Unmapped categories yield
     * {@link MappingResult#failure} (TC-01).
     */
    public MappingResult map(RecipeDescriptor descriptor) {
        if (descriptor == null || descriptor.outputs().isEmpty()) {
            return MappingResult.failure("recipe has no outputs");
        }

        String facility = dictionary.lookup(descriptor.categoryId());
        if (facility == null) {
            return MappingResult.failure(String.format(Locale.ROOT,
                    "cannot map category '%s': no Create facility known (add it to config/%s)",
                    descriptor.categoryId(), MappingDictionary.CONFIG_FILE));
        }

        LineScheme scheme = new LineScheme();
        scheme.setRecipeId(descriptor.recipeId());
        scheme.setOutputItem(descriptor.outputs().get(0));
        List<String> inputs = descriptor.uniqueInputs();
        LineScheme.Step step = scheme.addStep(facility, 1);
        for (String input : inputs) {
            step.addInput(input);
        }
        for (String output : descriptor.outputs()) {
            step.addOutput(output);
        }
        return MappingResult.success(scheme);
    }

    /**
     * Renders the scheme as plain text build instructions (used for tooltips and
     * the Create clipboard guide, SRS 3.2).
     */
    public static List<String> renderBuildGuide(LineScheme scheme) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add("Output: " + scheme.getOutputItem());
        int i = 1;
        for (LineScheme.Step step : scheme.getSteps()) {
            StringBuilder sb = new StringBuilder();
            sb.append(i++).append(". Place ");
            sb.append(step.getCount()).append('x').append(' ').append(step.getFacilityType());
            if (!step.getInputs().isEmpty()) {
                sb.append(" | in: ").append(String.join(",", step.getInputs()));
            }
            if (!step.getOutputs().isEmpty()) {
                sb.append(" | out: ").append(String.join(",", step.getOutputs()));
            }
            lines.add(sb.toString());
        }
        return lines;
    }
}
