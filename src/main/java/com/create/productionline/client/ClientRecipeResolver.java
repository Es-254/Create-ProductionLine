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
 *
 * <p><b>Caching (M11):</b> a full scan enumerates and parses every
 * {@code data/&lt;ns&gt;/recipe/*.json} of every loaded pack, which is far too
 * expensive to repeat on each Compute click. Results — including the
 * "nothing found" result — are memoized in a session-level, access-ordered
 * LRU map keyed by target item id. The cache lives for the client process
 * (i.e. the render/screen session) and is never persisted; resource reloads
 * happen on the client thread that also drives the screen, so there is no
 * cross-thread concurrency to guard against and no locking is used. Because a
 * datapack/resource reload can change which recipes exist, a stale entry only
 * ever means "the hint the server re-verifies is outdated" — never a wrong
 * installable recipe, since the server re-resolves the id itself.
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

    /**
     * Session-level LRU of resolved targets, keyed by target item id. Initial
     * capacity 32, load factor 0.75 and {@code accessOrder = true} so that both
     * a {@code get} and a {@code put} make an entry the most recently used one —
     * the first entry of the iteration order is then always the coldest.
     * Client-thread only: the resolver is called from the screen, and nothing
     * here is touched from the server or a network thread.
     */
    private static final java.util.LinkedHashMap<String, Resolved> CACHE =
            new java.util.LinkedHashMap<>(32, 0.75f, true);

    /** Maximum number of memoized targets; the coldest one is evicted first. */
    private static final int CACHE_LIMIT = 32;

    /**
     * Stores a result and returns it, evicting the coldest entry when the cache is
     * full. Negative results are cached too: "no recipe produces this item" is the
     * most expensive answer to recompute (it requires the FULL scan).
     */
    private static Resolved remember(String targetId, Resolved resolved) {
        if (CACHE.size() >= CACHE_LIMIT) {
            java.util.Iterator<java.util.Map.Entry<String, Resolved>> oldest = CACHE.entrySet().iterator();
            if (oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        CACHE.put(targetId, resolved);
        return resolved;
    }

    public static Resolved resolve(ItemStack target) {
        if (target == null || target.isEmpty()) {
            return new Resolved("", "", List.of(), "");
        }
        ResourceLocation targetId = BuiltInRegistries.ITEM.getKey(target.getItem());
        if (targetId == null) {
            return new Resolved("", "", List.of(), target.toString());
        }
        String cacheKey = targetId.toString();
        Resolved cached = CACHE.get(cacheKey); // access-order hit: bumps recency
        if (cached != null) {
            return cached;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return new Resolved("", "", List.of(), cacheKey);
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
                if (!cacheKey.equals(result)) {
                    continue;
                }
                String id = recipeIdOf(full);
                if (id.startsWith(com.create.productionline.recipegen.CreateRecipePack.PACK_NAMESPACE + ":")) {
                    continue; // never pick our own generated conversion as the source recipe
                }
                String category = typeId(obj);
                List<String> inputs = new ArrayList<>();
                RecipeJsonReader.collectFromJson(obj, inputs);
                if (inputs.isEmpty()) {
                    RecipeJsonReader.collectAllItemRefs(obj, inputs, result);
                }
                // Copy / repair / dye recipes consume only the product itself: they can never
                // become a production line ([target] -> machine -> target), so skip them.
                boolean hasOtherMaterial = false;
                for (String in : inputs) {
                    if (in != null && !in.isBlank() && !in.equals(result)) {
                        hasOtherMaterial = true;
                        break;
                    }
                }
                if (!hasOtherMaterial) {
                    continue;
                }
                // Prefer the recipe with the most ingredients (most complete plan).
                if (inputs.size() > bestCount) {
                    bestCount = inputs.size();
                    best = new Resolved(id, category, inputs, result);
                    if (bestCount >= 3) {
                        return remember(cacheKey, best); // early exit: cache the hit too
                    }
                }
            }
        } catch (Exception e) {
            com.create.productionline.ProductionLineMod.LOGGER.info("CPL client scan error: {}", e.toString());
        }
        return remember(cacheKey,
                best != null ? best : new Resolved("", "", List.of(), cacheKey));
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
        if (obj.has("results") && obj.get("results").isJsonArray() && !obj.getAsJsonArray("results").isEmpty()) {
            var first = obj.getAsJsonArray("results").get(0);
            if (first.isJsonObject()) {
                var r = first.getAsJsonObject();
                if (r.has("id")) {
                    return r.get("id").getAsString();
                }
                if (r.has("item")) {
                    return r.get("item").getAsString();
                }
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
