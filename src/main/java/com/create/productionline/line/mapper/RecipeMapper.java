package com.create.productionline.line.mapper;

import java.util.List;

import com.create.productionline.line.scheme.LineScheme;

/**
 * Renders a {@link LineScheme} as plain text build instructions (SRS 3.2), used
 * for tooltips and the Create clipboard guide.
 *
 * <p>This class no longer maps recipes. The former descriptor -&gt; scheme engine
 * ({@code map(RecipeDescriptor)} returning a {@code MappingResult}) had no
 * production caller left — the computer, the Scheme Loader and the Dismantler all
 * go through {@link com.create.productionline.recipegen.RecipeDeriver}, which
 * derives installable Create payloads server-side from the live
 * {@code RecipeManager} (TC-01). Only {@link #renderBuildGuide} remains, and
 * {@link com.create.productionline.compat.ClipboardCompat} is its sole caller.
 */
public final class RecipeMapper {

    private RecipeMapper() {
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
