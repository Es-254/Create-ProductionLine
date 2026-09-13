package com.create.productionline.line.analyzer;

import java.util.List;

import com.create.productionline.line.mapper.RecipeDescriptor;

/**
 * Analyses the raw materials of a recipe and extracts feature metrics that
 * drive machine selection ("特征 → 机器匹配", instead of per-item hard-coding).
 */
public final class RecipeAnalyzer {

    public static final class Result {
        public final int totalCount;
        public final int uniqueCount;
        public final boolean hasFluid;
        public final boolean isOre;
        public final boolean isOrganic;
        public final boolean isWood;
        public final boolean needsPrecision;

        Result(int totalCount, int uniqueCount, boolean hasFluid, boolean isOre,
                boolean isOrganic, boolean isWood, boolean needsPrecision) {
            this.totalCount = totalCount;
            this.uniqueCount = uniqueCount;
            this.hasFluid = hasFluid;
            this.isOre = isOre;
            this.isOrganic = isOrganic;
            this.isWood = isWood;
            this.needsPrecision = needsPrecision;
        }

        /** Estimated pipeline complexity: more raw materials & kinds => bigger line. */
        public int complexity() {
            return totalCount * 1 + uniqueCount * 2;
        }
    }

    private RecipeAnalyzer() {
    }

    /**
     * Analyses the descriptor inputs. The feature detection uses lightweight
     * registry-name heuristics; unusual items can still be steered through the
     * user mapping config.
     */
    public static Result analyze(RecipeDescriptor descriptor) {
        List<String> inputs = descriptor.inputs();
        int total = inputs.size();
        int unique = descriptor.uniqueInputs().size();

        boolean hasFluid = false;
        boolean isOre = false;
        boolean isOrganic = false;
        boolean isWood = false;
        boolean needsPrecision = false;
        for (String id : descriptor.uniqueInputs()) {
            String lower = id.toLowerCase();
            String path = lower.contains(":") ? lower.substring(lower.indexOf(':') + 1) : lower;
            hasFluid |= path.endsWith("_bucket") || path.contains("water") || path.contains("lava")
                    || path.contains("fluid") || path.contains("milk");
            isOre |= path.contains("ore") || path.startsWith("raw_") || path.contains("deepslate_")
                    || path.contains("ingot") || path.contains("dust");
            isOrganic |= path.contains("seed") || path.contains("wheat") || path.contains("crop")
                    || path.contains("bone") || path.contains("flower") || path.contains("apple")
                    || path.contains("potato") || path.contains("carrot") || path.contains("berry")
                    || path.contains("egg") || path.contains("sugar") || path.contains("melon");
            isWood |= path.contains("log") || path.contains("planks") || path.contains("wood")
                    || path.contains("sapling");
            needsPrecision |= path.contains("wax") || path.contains("copper_block")
                    || path.contains("honeycomb") || path.contains("mold") || path.contains("piston");
        }
        return new Result(total, unique, hasFluid, isOre, isOrganic, isWood, needsPrecision);
    }
}
