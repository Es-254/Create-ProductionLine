package com.create.productionline.line.mapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Maps a recipe category id (either the vanilla recipe-type id like
 * {@code minecraft:smelting} or a JEI category uid like {@code create:mixing})
 * to the Create facility that performs that operation.
 *
 * <p>Two layers, later wins:
 * <ol>
 *   <li>a built-in dictionary for vanilla + Create categories;</li>
 *   <li>an optional user JSON at
 *       {@code config/create_productionline-mappings.json} with
 *       {@code {"categories": {"some:cat": "create:mechanical_saw"}}}.</li>
 * </ol>
 */
public final class MappingDictionary {

    /** Key of the JSON file inside the config directory. */
    public static final String CONFIG_FILE = "create_productionline-mappings.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, String> categoryToFacility = new LinkedHashMap<>();

    public MappingDictionary() {
        installBuiltIns();
    }

    private void installBuiltIns() {
        // Vanilla (assembly/deployment style -> Deployer = Create 机械手)
        put("minecraft:crafting", "create:deployer");
        put("minecraft:smelting", "create:blaze_burner");
        put("minecraft:smoking", "create:blaze_burner");
        put("minecraft:blasting", "create:blaze_burner");
        put("minecraft:campfire_cooking", "create:blaze_burner");
        put("minecraft:stonecutting", "create:mechanical_saw");
        // Create recipe types -> the Create machine that performs them
        // (ids verified against Create 6.0.10; names: deployer=机械手,
        //  mechanical_crafter=动力合成器, haunted_bell=缠魂钟, crushing_wheel=粉碎轮 ...)
        put("create:cutting", "create:mechanical_saw");
        put("create:pressing", "create:mechanical_press");
        put("create:milling", "create:millstone");
        put("create:crushing", "create:crushing_wheel");
        put("create:mixing", "create:mechanical_mixer");
        put("create:compacting", "create:mechanical_press");
        put("create:deploying", "create:deployer");
        put("create:item_application", "create:deployer");
        put("create:sandpaper_polishing", "create:deployer");
        put("create:mechanical_crafting", "create:mechanical_crafter");
        put("create:haunting", "create:haunted_bell");
        // Fan-based bulk processing (washing/splashing/haunting ... use an
        // Encased Fan in front of the matching catalyst)
        put("create:splashing", "create:encased_fan");
        put("create:washing", "create:encased_fan");
        put("create:fan_washing", "create:encased_fan");
        put("create:fan_splashing", "create:encased_fan");
        put("create:fan_haunting", "create:encased_fan");
        put("create:fan_smoking", "create:blaze_burner");
        put("create:fan_blasting", "create:blaze_burner");
    }

    private void put(String category, String facility) {
        categoryToFacility.put(normalize(category), facility);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    /** Looks up the facility for a category id; {@code null} when unmapped. */
    public String lookup(String categoryId) {
        String direct = categoryToFacility.get(normalize(categoryId));
        if (direct != null) {
            return direct;
        }
        // Fallback: match by trailing key (e.g. "minecraft:recipes/cutting" vs "create:cutting").
        String key = normalize(categoryId);
        int idx = key.lastIndexOf(':');
        String suffix = idx >= 0 ? key.substring(idx + 1) : key;
        for (Map.Entry<String, String> entry : categoryToFacility.entrySet()) {
            if (entry.getKey().endsWith(":" + suffix)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Adds or overrides entries parsed from the user JSON config. */
    public void applyUserConfig(JsonObject root) {
        if (root == null || !root.has("categories")) {
            return;
        }
        try {
            JsonObject categories = root.getAsJsonObject("categories");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : categories.entrySet()) {
                String facility = entry.getValue().getAsString();
                if (facility != null && !facility.isBlank()) {
                    put(entry.getKey(), facility);
                }
            }
        } catch (RuntimeException e) {
            com.create.productionline.ProductionLineMod.LOGGER.warn(
                    "Failed to read user mapping config {}: {}", CONFIG_FILE, e.toString());
        }
    }

    /** Produces a JSON string of the current dictionary (for user reference). */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("assemblyMode", "sequenced");
        JsonObject categories = new JsonObject();
        for (Map.Entry<String, String> entry : categoryToFacility.entrySet()) {
            categories.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("categories", categories);
        return root;
    }

    /** Debug/export helper. */
    public String toPrettyJson() {
        return GSON.toJson(toJson());
    }

    @Override
    public String toString() {
        return Objects.requireNonNullElse(categoryToFacility, Map.of()).toString();
    }
}
