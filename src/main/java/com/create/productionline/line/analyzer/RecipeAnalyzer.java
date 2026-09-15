package com.create.productionline.line.analyzer;

import java.util.Locale;

/**
 * Single-material semantic machine picker ("单材料语义选机").
 *
 * <p>Its ONE job: given the registry id of a <b>single</b> material (an item id
 * such as {@code "minecraft:iron_ore"} or a tag reference such as
 * {@code "#minecraft:planks"}), return the real Create machine that can process
 * that material. The classification is a purely deterministic name heuristic —
 * the same id always yields the same machine, on every side and every run.
 *
 * <p>Only the single-material path of the derivation calls this (see
 * {@code RecipeDeriver.entriesFor} step 5): a multi-material recipe is never
 * routed through a single machine, so this class deliberately has no notion of
 * "features", "complexity" or "scaling". The former feature-bag
 * {@code analyze(...)}/{@code Result} machinery (which fed an index-by-index
 * machine list) has been removed together with its only caller.
 */
public final class RecipeAnalyzer {

    /** Woodworking / cutting: saw. */
    public static final String SAW = "create:mechanical_saw";
    /** Ore & raw material reduction: crushing wheels. */
    public static final String CRUSHING_WHEEL = "create:crushing_wheel";
    /** Organic / milling: millstone. */
    public static final String MILLSTONE = "create:millstone";
    /** Metals, gems and everything else: mechanical press (also the fallback). */
    public static final String PRESS = "create:mechanical_press";

    private static final String[] WOODWORKING = {
            "log", "planks", "wood", "stick", "bamboo", "paper", "button", "fence",
            "slab", "trapdoor", "sign",
    };
    private static final String[] ORGANIC = {
            "wheat", "seed", "flour", "dough", "sugar", "plant", "cactus", "kelp",
            "vine", "leaf", "carrot", "potato", "beetroot", "berry", "mushroom",
            "bone", "egg",
    };
    private static final String[] METAL_OR_GEM = {
            "dust", "ingot", "nugget", "gem", "diamond", "emerald", "quartz",
            "amethyst", "lapis", "redstone", "coal", "charcoal", "clay", "brick",
    };

    private RecipeAnalyzer() {
    }

    /**
     * Picks the Create machine for ONE material. Rules are applied in a fixed
     * order (first hit wins), so the result is deterministic:
     *
     * <ol>
     *   <li>woodworking keywords → {@code create:mechanical_saw};</li>
     *   <li>ore / {@code raw_} materials → {@code create:crushing_wheel};</li>
     *   <li>organic &amp; milling keywords → {@code create:millstone};</li>
     *   <li>metal / gem / earth keywords → {@code create:mechanical_press};</li>
     *   <li>fallback → {@code create:mechanical_press}.</li>
     * </ol>
     *
     * @param materialId an item id ({@code "modid:item"}) or a tag
     *                   ({@code "#tag"}); may be {@code null}
     * @return a Create machine block id, never {@code null}
     */
    public static String machineForMaterial(String materialId) {
        if (materialId == null || materialId.isBlank()) {
            return PRESS;
        }
        String lower = materialId.toLowerCase(Locale.ROOT).trim();
        // The item PATH is what carries the semantics ("minecraft:raw_iron"), the
        // namespace is ignored — but a bare (namespace-less) id still works.
        int colon = lower.indexOf(':');
        String path = colon >= 0 ? lower.substring(colon + 1) : lower;

        if (containsAny(lower, WOODWORKING)) {
            return SAW;
        }
        if (lower.contains("ore") || path.startsWith("raw_") || lower.startsWith("raw_")) {
            return CRUSHING_WHEEL;
        }
        if (containsAny(lower, ORGANIC)) {
            return MILLSTONE;
        }
        if (containsAny(lower, METAL_OR_GEM)) {
            return PRESS;
        }
        return PRESS;
    }

    private static boolean containsAny(String haystack, String[] needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
