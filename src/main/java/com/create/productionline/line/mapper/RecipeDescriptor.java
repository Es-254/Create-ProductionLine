package com.create.productionline.line.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A neutral, JEI-free description of a recipe that the mapper can consume.
 * Descriptors are built server-side from the vanilla {@code RecipeManager}
 * (which already contains data recipes of every loaded mod, including Create's),
 * or client-side from JEI categories where useful.
 *
 * @param recipeId   registry id of the recipe, e.g. {@code "modid:recipe_name"}
 * @param categoryId category / recipe-type id, e.g. {@code "create:mixing"}
 * @param inputs     ingredient ids; item references are {@code "modid:item"}, tag
 *                   references keep their identity as {@code "#tag"} (never expanded)
 * @param outputs    result item ids
 * @param outputCount stack size of ONE live output — the amount a single craft
 *                   really produces ({@code ItemStack.getCount()} of the live
 *                   recipe result). When the datapack JSON's {@code result.count}
 *                   text is untrustworthy (a mod may produce more at runtime than
 *                   the JSON claims), this value is authoritative; always clamped
 *                   to at least {@code 1}.
 */
public record RecipeDescriptor(String recipeId, String categoryId, List<String> inputs, List<String> outputs,
        int outputCount) {

    public RecipeDescriptor {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        if (recipeId == null || recipeId.isBlank()) {
            recipeId = "unknown";
        }
        if (categoryId == null || categoryId.isBlank()) {
            categoryId = "unknown";
        }
        outputCount = Math.max(1, outputCount);
    }

    /**
     * Convenience constructor for call sites that only know the outputs, not the
     * per-craft stack size; the count defaults to {@code 1}.
     */
    public RecipeDescriptor(String recipeId, String categoryId, List<String> inputs, List<String> outputs) {
        this(recipeId, categoryId, inputs, outputs, 1);
    }

    /**
     * True when the recipe category denotes crafting/assembly rather than a
     * machine process. This is the single decision point — production code must
     * call this instead of re-implementing substring/regex checks.
     */
    public static boolean isAssemblyLikeCategory(String categoryId) {
        if (categoryId == null) {
            return false;
        }
        String c = categoryId.toLowerCase(java.util.Locale.ROOT);
        return c.contains("craft") || c.contains("assembly") || c.contains("table");
    }

    public List<String> uniqueInputs() {
        List<String> out = new ArrayList<>();
        for (String input : inputs) {
            if (!out.contains(input)) {
                out.add(input);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
