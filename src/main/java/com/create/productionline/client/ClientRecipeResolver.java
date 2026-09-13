package com.create.productionline.client;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.util.RecipeJsonReader;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side recipe resolution.
 *
 * <p>Like JEI, this scans the client resource packs directly (the client
 * {@code RecipeManager} holds no recipe data). Every {@code data/<ns>/recipe/*.json}
 * is enumerated and parsed; the recipe whose result is the target item provides
 * the REAL registry id of the matched file plus its category/ingredient ids.
 *
 * <p><b>Trust model:</b> the server never takes these values as authoritative.
 * It re-resolves {@link Resolved#recipeId()} against its own live
 * {@code RecipeManager} and only accepts it when that recipe really produces the
 * item sitting in the computer's target slot (anti-injection); the remaining
 * fields are a display/fallback hint only.
 */
public final class ClientRecipeResolver {

    /**
     * A client-side hit.
     *
     * @param recipeId   real registry id of the matched recipe JSON, e.g.
     *                   {@code superbwarfare:rifle_ammo} (empty when unknown) —
     *                   this is the only field the server treats as a hint worth
     *                   verifying
     * @param categoryId recipe type / category id, e.g. {@code minecraft:crafting}
     * @param inputs     ingredient ids as read from the recipe JSON (tags keep
     *                   their {@code #tag} form)
     * @param outputId   the recipe's result item id
     */
    public record Resolved(String recipeId, String categoryId, List<String> inputs, String outputId) {
        public boolean ok() {
            return outputId != null && !outputId.isBlank();
        }
    }

    private ClientRecipeResolver() {
    }

    public static Resolved resolve(ItemStack target) {
        if (target == null || target.isEmpty()) {
            return new Resolved("", "", List.of(), "");
        }
        ResourceLocation targetId = BuiltInRegistries.ITEM.getKey(target.getItem());
        if (targetId == null) {
            return new Resolved("", "", List.of(), target.toString());
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return new Resolved("", "", List.of(), targetId.toString());
        }
        ResourceManager rm = mc.getResourceManager();

        Resolved best = null;
        int bestCount = -1;
        try {
            var map = rm.listResources("recipe", loc -> loc.getPath().endsWith(".json"));
            for (var entry : map.entrySet()) {
                ResourceLocation full = entry.getKey();           // ns:recipe/name.json
                Resource resource = entry.getValue();
                JsonObject obj = parse(resource);
                if (obj == null) {
                    continue;
                }
                String result = resultId(obj);
                if (!targetId.toString().equals(result)) {
                    continue;
                }
                String category = typeId(obj);
                List<String> inputs = new ArrayList<>();
                RecipeJsonReader.collectFromJson(obj, inputs);
                if (inputs.isEmpty()) {
                    RecipeJsonReader.collectAllItemRefs(obj, inputs, result);
                }
                // Prefer the recipe with the most ingredients (most complete plan).
                if (inputs.size() > bestCount) {
                    bestCount = inputs.size();
                    best = new Resolved(recipeIdOf(full), category, inputs, result);
                    if (bestCount >= 3) {
                        return best;
                    }
                }
            }
        } catch (Exception e) {
            com.create.productionline.ProductionLineMod.LOGGER.info("CPL client scan error: {}", e.toString());
        }
        return best != null ? best : new Resolved("", "", List.of(), targetId.toString());
    }

    /**
     * Converts a resource-pack key ({@code ns:recipe/sub/name.json}) into a real
     * recipe registry id ({@code ns:sub/name}). {@code ResourceManager.listResources}
     * prefixes the requested folder and keeps the {@code .json} extension, so both
     * have to be stripped for the id to be resolvable via
     * {@code RecipeManager.byKey}.
     */
    private static String recipeIdOf(ResourceLocation full) {
        String path = full.getPath();
        if (path.startsWith("recipe/")) {
            path = path.substring("recipe/".length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return path.isBlank() ? "" : full.getNamespace() + ":" + path;
    }

    private static JsonObject parse(Resource resource) {
        try {
            String text = RecipeJsonReader.readAll(resource.open());
            return com.google.gson.JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String resultId(JsonObject obj) {
        if (obj.has("result") && obj.get("result").isJsonObject()) {
            var r = obj.getAsJsonObject("result");
            if (r.has("id")) {
                return r.get("id").getAsString();
            }
            if (r.has("item")) {
                return r.get("item").getAsString();
            }
        }
        return "";
    }

    private static String typeId(JsonObject obj) {
        if (obj.has("type") && obj.get("type").isJsonPrimitive()) {
            return obj.get("type").getAsString();
        }
        return "";
    }
}
