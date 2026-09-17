package com.create.productionline.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads a recipe's datapack JSON and extracts ingredient ids. Works for recipes
 * whose {@code Recipe} object hides their ingredients (smithing transform/trim,
 * custom mod machine types). Field names covered: {@code key}, {@code
 * ingredients}, {@code inputs}, {@code template/base/addition}, {@code
 * input/ingredient} — plus a last-resort recursive scan of every {@code
 * item}/{@code tag} reference (excluding the result).
 */
public final class RecipeJsonReader {

    private RecipeJsonReader() {
    }

    /** A shaped crafting grid parsed from JSON: pattern rows + char -> item key. */
    public record ShapedJson(java.util.List<String> pattern, java.util.Map<Character, String> keyItems) {
        public boolean valid() {
            return pattern != null && !pattern.isEmpty() && keyItems != null && !keyItems.isEmpty();
        }
    }

    /** Reads a shaped crafting {@code pattern}/{@code key} from a recipe JSON (for mechanical_crafting). */
    public static ShapedJson shapedFromJson(ResourceManager manager, ResourceLocation recipeId) {        if (manager == null || recipeId == null) {
            return null;
        }
        try {
            var opt = manager.getResource(ResourceLocation.fromNamespaceAndPath(recipeId.getNamespace(),
                    "recipe/" + recipeId.getPath() + ".json"));
            if (opt.isEmpty()) {
                return null;
            }
            JsonObject obj = com.google.gson.JsonParser.parseString(readAll(opt.get().open())).getAsJsonObject();
            if (!obj.has("pattern") || !obj.has("key")) {
                return null;
            }
            java.util.List<String> pattern = new ArrayList<>();
            for (var e : obj.getAsJsonArray("pattern")) {
                pattern.add(e.getAsString());
            }
            java.util.Map<Character, String> keyItems = new java.util.LinkedHashMap<>();
            var key = obj.getAsJsonObject("key");
            for (var e : key.entrySet()) {
                char c = e.getKey().charAt(0);
                String item = ingredientItem(e.getValue());
                if (item != null) {
                    keyItems.put(c, item);
                }
            }
            return new ShapedJson(pattern, keyItems);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ordered, deduplicated material entries of a shaped recipe, read from its
     * JSON in row-major cell order. Entries preserve their original identity:
     * item ids stay plain, tag ingredients are returned as {@code "#tag"}.
     * Falls back to {@code fallback} (item ids) when the recipe is not a simple
     * shaped {@code pattern}/{@code key} JSON.
     */
    public static java.util.List<String> shapedMaterialOrder(ResourceManager manager, ResourceLocation recipeId,
            java.util.List<String> fallback) {
        ShapedJson shaped = shapedFromJson(manager, recipeId);
        if (shaped == null || !shaped.valid()) {
            return fallback == null ? List.of() : fallback;
        }
        java.util.List<String> order = new ArrayList<>();
        for (String row : shaped.pattern()) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c == ' ' || c == '.') {
                    continue;
                }
                String entry = shaped.keyItems().get(c);
                if (entry != null && !order.contains(entry)) {
                    order.add(entry);
                }
            }
        }
        return order.isEmpty() ? (fallback == null ? List.of() : fallback) : order;
    }

    private static String ingredientItem(JsonElement el) {
        try {
            if (el.isJsonObject()) {
                var o = el.getAsJsonObject();
                if (o.has("item")) {
                    return normalizeMaterial(o.get("item").getAsString());
                }
                if (o.has("tag")) {
                    return "#" + o.get("tag").getAsString();
                }
            }
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                return normalizeMaterial(el.getAsString());
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** Reads the result {@code count} from a recipe JSON (defaults to 1). */
    public static int resultCount(ResourceManager manager, ResourceLocation recipeId) {
        if (manager == null || recipeId == null) {
            return 1;
        }
        try {
            var opt = manager.getResource(ResourceLocation.fromNamespaceAndPath(recipeId.getNamespace(),
                    "recipe/" + recipeId.getPath() + ".json"));
            if (opt.isEmpty()) {
                return 1;
            }
            JsonObject obj = com.google.gson.JsonParser.parseString(readAll(opt.get().open())).getAsJsonObject();
            if (obj.has("result") && obj.get("result").isJsonObject()) {
                var r = obj.getAsJsonObject("result");
                if (r.has("count")) {
                    return Math.max(1, r.get("count").getAsInt());
                }
            }
            if (obj.has("results") && obj.get("results").isJsonArray()) {
                var arr = obj.getAsJsonArray("results");
                if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                    var r = arr.get(0).getAsJsonObject();
                    if (r.has("count")) {
                        return Math.max(1, r.get("count").getAsInt());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    public static List<String> readIngredients(ResourceManager manager, ResourceLocation recipeId) {
        List<String> out = new ArrayList<>();
        if (manager == null || recipeId == null) {
            return out;
        }
        try {
            // Recipe files live at data/<ns>/recipe/<name>.json (note the extension).
            var opt = manager.getResource(
                    ResourceLocation.fromNamespaceAndPath(recipeId.getNamespace(),
                            "recipe/" + recipeId.getPath() + ".json"));
            if (opt.isEmpty()) {
                return out;
            }
            JsonObject obj = com.google.gson.JsonParser.parseString(readAll(opt.get().open())).getAsJsonObject();
            collectFromJson(obj, out);
            if (out.isEmpty()) {
                String resultId = null;
                if (obj.has("result") && obj.get("result").isJsonObject()) {
                    var r = obj.getAsJsonObject("result");
                    if (r.has("id")) {
                        resultId = r.get("id").getAsString();
                    } else if (r.has("item")) {
                        resultId = r.get("item").getAsString();
                    }
                }
                collectAllItemRefs(obj, out, resultId);
            }
        } catch (Exception ignored) {
            // best effort
        }
        return out;
    }

    /** Convenience: reads the recipe JSON as text (for debugging/tests). */
    public static String readAll(InputStream in) throws Exception {
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int n;
            while ((n = reader.read(buf)) >= 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }

    /** Collects item ids from {@code key}, {@code ingredients/inputs}, {@code template/base/addition/input/ingredient}. */
    public static void collectFromJson(JsonObject obj, List<String> out) {
        if (obj.has("key") && obj.get("key").isJsonObject()) {
            var key = obj.getAsJsonObject("key");
            for (var e : key.entrySet()) {
                addIngredient(e.getValue(), out);
            }
        }
        for (String field : new String[]{"ingredients", "inputs"}) {
            if (obj.has(field) && obj.get(field).isJsonArray()) {
                for (var e : obj.getAsJsonArray(field)) {
                    addIngredient(e, out);
                }
            }
        }
        for (String field : new String[]{"template", "base", "addition", "input", "ingredient"}) {
            if (obj.has(field) && !obj.get(field).isJsonNull()) {
                addIngredient(obj.get(field), out);
            }
        }
    }

    public static void addIngredient(JsonElement e, List<String> out) {
        try {
            if (e.isJsonArray()) {
                for (var sub : e.getAsJsonArray()) {
                    addIngredient(sub, out);
                }
                return;
            }
            if (e.isJsonObject()) {
                var o = e.getAsJsonObject();
                if (o.has("item")) {
                    String id = normalizeMaterial(o.get("item").getAsString());
                    if (id != null && (out.isEmpty() || !out.contains(id))) {
                        out.add(id);
                    }
                } else if (o.has("tag")) {
                    String tag = o.get("tag").getAsString();
                    String ref = "#" + tag;
                    if (out.isEmpty() || !out.contains(ref)) {
                        out.add(ref);
                    }
                } else if (o.has("ingredients") && o.get("ingredients").isJsonArray()) {
                    for (var sub : o.getAsJsonArray("ingredients")) {
                        addIngredient(sub, out);
                    }
                }
            } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                String id = normalizeMaterial(e.getAsString());
                if (id != null && (out.isEmpty() || !out.contains(id))) {
                    out.add(id);
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Normalizes a material token coming from a foreign recipe format.
     *
     * <p>Some mods (e.g. superbwarfare's {@code vehicle_assembling}) write their
     * ingredients as <b>count-prefixed strings</b>: {@code "8 #c:storage_blocks/steel"},
     * {@code "24 superbwarfare:cemented_carbide_block"}, {@code "2 superbwarfare:track"}.
     * Passing those through verbatim produced raw unreadable tooltips, invalid
     * {@code {"item": "8 #…"}} payloads (recipe never loads) and text overflow.
     * This strips the numeric prefix and returns {@code "#tag"} / {@code "modid:item"}.
     *
     * @return the normalized token, or {@code null} when nothing usable remains
     */
    public static String normalizeMaterial(String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.trim();
        if (token.isEmpty()) {
            return null;
        }
        // "8 #c:storage_blocks/steel" / "24 superbwarfare:cemented_carbide_block" / "2x item"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d+)\\s*[xX]?\\s+(.+)$")
                .matcher(token);
        if (m.matches()) {
            token = m.group(2).trim();
        }
        // A remaining whitespace means the token is still not a single id: keep the
        // first word so we never emit an illegal ingredient.
        int space = token.indexOf(' ');
        if (space > 0) {
            token = token.substring(0, space);
        }
        return token.isEmpty() ? null : token;
    }

    /**
     * True when the recipe's result is an ENTITY ({@code "result": {"entity": …}}),
     * i.e. it is assembled by a machine of its own mod (vehicles, turrets, …) and
     * must not be converted into a Create processing line.
     */
    public static boolean resultIsEntity(ResourceManager manager, ResourceLocation recipeId) {
        JsonObject obj = readRecipeJson(manager, recipeId);
        if (obj == null || !obj.has("result") || !obj.get("result").isJsonObject()) {
            return false;
        }
        JsonObject r = obj.getAsJsonObject("result");
        return r.has("entity") || r.has("entity_type");
    }

    /**
     * A parsed {@code create:sequenced_assembly} payload: the base material, the material
     * each deploy step adds (in order) and the final result item. Used by the Dismantler to
     * refund exactly the materials an unfinished intermediate has already absorbed.
     */
    public record SequenceParts(String base, List<String> stepMaterials, String resultItem) {
    }

    /** Parses a sequenced-assembly recipe JSON, or {@code null} when it is not one. */
    public static SequenceParts sequenceParts(ResourceManager manager, ResourceLocation recipeId) {
        JsonObject obj = readRecipeJson(manager, recipeId);
        if (obj == null || !obj.has("sequence") || !obj.get("sequence").isJsonArray()) {
            return null;
        }
        String base = null;
        if (obj.has("ingredient")) {
            base = normalizeMaterial(ingredientItem(obj.get("ingredient")));
        }
        List<String> materials = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("sequence")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject step = el.getAsJsonObject();
            if (!step.has("ingredients") || !step.get("ingredients").isJsonArray()) {
                continue;
            }
            var arr = step.getAsJsonArray("ingredients");
            // ingredients[0] is the current item (base / transitional), [1] the added material
            if (arr.size() >= 2) {
                String added = normalizeMaterial(ingredientItem(arr.get(1)));
                if (added != null) {
                    materials.add(added);
                }
            }
        }
        return new SequenceParts(base, materials, resultItemId(obj));
    }

    /** Reads the single result item id of a recipe JSON, or {@code null}. */
    public static String resultItemId(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        if (obj.has("result") && obj.get("result").isJsonObject()) {
            JsonObject r = obj.getAsJsonObject("result");
            if (r.has("id")) {
                return r.get("id").getAsString();
            }
            if (r.has("item")) {
                return r.get("item").getAsString();
            }
        }
        if (obj.has("results") && obj.get("results").isJsonArray() && !obj.getAsJsonArray("results").isEmpty()) {
            JsonElement first = obj.getAsJsonArray("results").get(0);
            if (first.isJsonObject()) {
                JsonObject r = first.getAsJsonObject();
                if (r.has("id")) {
                    return r.get("id").getAsString();
                }
                if (r.has("item")) {
                    return r.get("item").getAsString();
                }
            }
        }
        return null;
    }

    /** Reads a recipe JSON by id, or {@code null} when unavailable/unreadable. */
    public static JsonObject readRecipeJson(ResourceManager manager, ResourceLocation recipeId) {        if (manager == null || recipeId == null) {
            return null;
        }
        try {
            var opt = manager.getResource(ResourceLocation.fromNamespaceAndPath(
                    recipeId.getNamespace(), "recipe/" + recipeId.getPath() + ".json"));
            if (opt.isEmpty()) {
                return null;
            }
            return com.google.gson.JsonParser.parseString(readAll(opt.get().open())).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Recursive scan for every {@code item}/{@code tag} reference (skips result blocks). */
    public static void collectAllItemRefs(JsonElement el, List<String> out, String excludeResult) {
        if (el == null) {
            return;
        }
        if (el.isJsonObject()) {
            var o = el.getAsJsonObject();
            if (o.has("item") && o.get("item").isJsonPrimitive()) {
                String id = o.get("item").getAsString();
                if (!id.equals(excludeResult) && !out.contains(id)) {
                    out.add(id);
                }
            } else if (o.has("tag") && o.get("tag").isJsonPrimitive()) {
                String tag = o.get("tag").getAsString();
                String ref = "#" + tag;
                if (!out.contains(ref)) {
                    out.add(ref);
                }
            }
            for (var e : o.entrySet()) {
                // Output blocks ("result" / "results") and condition metadata are
                // never ingredients — never let them leak into the input list.
                String key = e.getKey();
                if ("result".equals(key) || "results".equals(key)) {
                    continue;
                }
                collectAllItemRefs(e.getValue(), out, excludeResult);
            }
        } else if (el.isJsonArray()) {
            for (var e : el.getAsJsonArray()) {
                collectAllItemRefs(e, out, excludeResult);
            }
        }
    }
}
