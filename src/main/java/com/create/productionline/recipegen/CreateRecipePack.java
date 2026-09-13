package com.create.productionline.recipegen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.create.productionline.ProductionLineMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Generates real, spec-conformant Create recipe JSON files for a target item and
 * installs them into a small datapack inside the current world
 * ({@code <world>/datapacks/cpl_converted/}), then triggers a server reload so
 * Create machines can use them right away.
 *
 * <p>Every emitted file follows the exact schema used by Create 6.x itself (see
 * {@code data/create/recipe/…} inside the Create jar):
 * <ul>
 *   <li>flat processing types: {@code {"type":"create:<m>","ingredients":[…],"results":[…],"processing_time":N}}</li>
 *   <li>mechanical crafting: {@code {"type":"create:mechanical_crafting","pattern":[…],"key":{…},"result":{…}}}</li>
 * </ul>
 * Ingredient entries are {@code {"item":…}} (tags are resolved to items by the
 * caller); results use {@code "id"} as Create 1.21.1 expects.
 */
public final class CreateRecipePack {

    public static final String PACK_FOLDER = "cpl_converted";
    public static final String PACK_NAMESPACE = "cpl";
    public static final int PACK_FORMAT = 34; // Minecraft 1.21.1

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Create machine block id -> the Create recipe type it performs. */
    private static final Map<String, String> FACILITY_TO_TYPE = Map.of(
            "create:mechanical_mixer", "create:mixing",
            "create:crushing_wheel", "create:crushing",
            "create:millstone", "create:milling",
            "create:mechanical_press", "create:pressing",
            "create:mechanical_saw", "create:cutting",
            "create:haunted_bell", "create:haunting",
            "create:encased_fan", "create:splashing");

    /** Default processing time per flat type (ms). */
    private static final Map<String, Integer> TYPE_TIME = Map.of(
            "create:mixing", 100, "create:crushing", 300, "create:milling", 200,
            "create:pressing", 100, "create:cutting", 50, "create:haunting", 100,
            "create:splashing", 100);

    private CreateRecipePack() {
    }

    /** Maps a Create facility id to the recipe type it performs, or null. */
    public static String methodOf(String facilityId) {
        return facilityId == null ? null : FACILITY_TO_TYPE.get(facilityId);
    }

    // --- JSON builders (spec-conformant) ---------------------------------------

    /** Builds a flat processing recipe JSON (mixing/crushing/...). */
    public static JsonObject flat(String type, List<String> inputs, String outputId) {
        return flat(type, inputs, outputId, 1);
    }

    /** Builds a flat processing recipe JSON, preserving the output count. */
    public static JsonObject flat(String type, List<String> inputs, String outputId, int count) {
        JsonObject root = new JsonObject();
        root.addProperty("type", type);
        JsonArray ingredients = new JsonArray();
        for (String in : inputs) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", in);
            ingredients.add(entry);
        }
        root.add("ingredients", ingredients);
        Integer time = TYPE_TIME.get(type);
        if (time != null) {
            root.addProperty("processing_time", time);
        }
        JsonArray results = new JsonArray();
        JsonObject result = new JsonObject();
        result.addProperty("id", outputId);
        if (count > 1) {
            result.addProperty("count", count);
        }
        results.add(result);
        root.add("results", results);
        return root;
    }

    /** Builds a mechanical_crafting JSON from a shaped pattern + key map. */
    public static JsonObject mechanical(List<String> pattern, Map<Character, String> keyItems, String outputId) {
        return mechanical(pattern, keyItems, outputId, 1);
    }

    /** Builds a mechanical_crafting JSON, preserving the output count. */
    public static JsonObject mechanical(List<String> pattern, Map<Character, String> keyItems, String outputId,
            int count) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "create:mechanical_crafting");
        JsonArray patternArr = new JsonArray();
        for (String row : pattern) {
            patternArr.add(row);
        }
        root.add("pattern", patternArr);
        JsonObject key = new JsonObject();
        for (Map.Entry<Character, String> e : keyItems.entrySet()) {
            JsonObject entry = new JsonObject();
            String v = e.getValue();
            if (v != null && v.startsWith("#")) {
                entry.addProperty("tag", v.substring(1));
            } else {
                entry.addProperty("item", v);
            }
            key.add(String.valueOf(e.getKey()), entry);
        }
        root.add("key", key);
        JsonObject result = new JsonObject();
        result.addProperty("id", outputId);
        if (count > 1) {
            result.addProperty("count", count);
        }
        root.add("result", result);
        root.addProperty("category", "misc");
        root.addProperty("accept_mirrored", true);
        root.addProperty("show_notification", false);
        return root;
    }

    // --- writing to the world datapack ----------------------------------------

    /**
     * (Re)installs the pack with the given recipes and reloads the server.
     *
     * @return the number of written recipe files
     */
    public static int install(MinecraftServer server, Map<String, String> fileNameToJson) {
        if (server == null || fileNameToJson == null || fileNameToJson.isEmpty()) {
            return 0;
        }
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_FOLDER);
        try {
            // Start from a clean pack so stale generated recipes disappear.
            deleteRecursively(packRoot);
            Files.createDirectories(packRoot.resolve("data/" + PACK_NAMESPACE + "/recipe"));
            JsonObject meta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", PACK_FORMAT);
            pack.addProperty("description", "Create: Production Line — converted recipes");
            meta.add("pack", pack);
            Files.writeString(packRoot.resolve("pack.mcmeta"), GSON.toJson(meta), StandardCharsets.UTF_8);

            for (Map.Entry<String, String> e : fileNameToJson.entrySet()) {
                Path file = packRoot.resolve("data/" + PACK_NAMESPACE + "/recipe/" + e.getKey() + ".json");
                Files.writeString(file, e.getValue(), StandardCharsets.UTF_8);
            }
            ProductionLineMod.LOGGER.info("Installed {} converted Create recipe(s) into datapack {}",
                    fileNameToJson.size(), packRoot.toAbsolutePath());
            // Make the datapack take effect immediately.
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
            return fileNameToJson.size();
        } catch (Exception e) {
            ProductionLineMod.LOGGER.warn("Could not install recipe datapack {}: {}", packRoot, e.toString());
            return 0;
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception ignored) {
                    // best effort
                }
            });
        } catch (Exception ignored) {
            // best effort
        }
    }

    // --- activation from a line scheme ----------------------------------------

    /** Installs the recipe payloads stored on a line scheme. */
    public static int installEntries(MinecraftServer server,
            List<com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry> entries) {
        java.util.LinkedHashMap<String, String> files = new java.util.LinkedHashMap<>();
        for (com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry e : entries) {
            if (e.isValid()) {
                files.put(e.getFileName(), e.getJson());
            }
        }
        return install(server, files);
    }

    /** Deactivates the datapack (removes the folder and reloads). */
    public static void deactivate(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_FOLDER);
        try {
            deleteRecursively(packRoot);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        } catch (Exception e) {
            ProductionLineMod.LOGGER.warn("Could not deactivate recipe datapack {}: {}", packRoot, e.toString());
        }
    }

    // --- per-loader additive activation -------------------------------------
    //
    // Several Scheme Loaders may be active at once on one line (e.g. an
    // intermediate product scheme + the final product scheme). Each loader owns
    // a small contribution file inside the datapack folder; the recipe files are
    // rebuilt as the UNION of all contribution files. Removing a scheme (or the
    // loader block) only drops that loader's contribution.

    private static final String CONTRIB_DIR = "contributions";

    private static String sanitize(String s) {
        if (s == null) {
            return "loader";
        }
        String clean = s.replaceAll("[^A-Za-z0-9_.\\-]", "_");
        return clean.isBlank() ? "loader" : clean;
    }

    /**
     * Replaces this loader's contribution (recipe files embedded in its current
     * scheme) and rebuilds the datapack from the union of all loaders.
     *
     * @param loaderKey stable identity of the loader (dimension + block pos)
     * @param ownFiles  fileName -> json of the loader's current scheme, or
     *                  {@code null}/{@code empty} to remove the contribution
     * @return the number of recipes currently active in the union
     */
    public static int reconcileContributions(MinecraftServer server, String loaderKey,
            Map<String, String> ownFiles) {
        if (server == null || loaderKey == null || loaderKey.isBlank()) {
            return 0;
        }
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_FOLDER);
        try {
            Path contribDir = packRoot.resolve(CONTRIB_DIR);
            Files.createDirectories(contribDir);
            Path own = contribDir.resolve(sanitize(loaderKey) + ".json");
            if (ownFiles == null || ownFiles.isEmpty()) {
                Files.deleteIfExists(own);
            } else {
                Files.writeString(own, GSON.toJson(ownFiles), StandardCharsets.UTF_8);
            }
            return rebuildUnion(server, packRoot);
        } catch (Exception e) {
            ProductionLineMod.LOGGER.warn("Could not reconcile loader {}: {}", loaderKey, e.toString());
            return 0;
        }
    }

    /** Writes pack.mcmeta + the union of all loader contribution files, then reloads. */
    private static int rebuildUnion(MinecraftServer server, Path packRoot) throws Exception {
        Path recipeDir = packRoot.resolve("data/" + PACK_NAMESPACE + "/recipe");
        deleteRecursively(recipeDir);
        Files.createDirectories(recipeDir);
        Files.createDirectories(packRoot.resolve(CONTRIB_DIR));

        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "Create: Production Line — converted recipes (multi-loader union)");
        meta.add("pack", pack);
        Files.writeString(packRoot.resolve("pack.mcmeta"), GSON.toJson(meta), StandardCharsets.UTF_8);

        Map<String, String> all = new LinkedHashMap<>();
        Path contribDir = packRoot.resolve(CONTRIB_DIR);
        List<Path> contribFiles = new ArrayList<>();
        try (var stream = Files.list(contribDir)) {
            stream.filter(x -> x.getFileName().toString().endsWith(".json")).forEach(contribFiles::add);
        }
        for (Path p : contribFiles) {
            String content = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject root = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            for (var e : root.entrySet()) {
                String name = e.getKey();
                String json = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString();
                if (isSafeFileName(name) && json != null && !json.isBlank()) {
                    all.put(name, json);
                }
            }
        }
        for (Map.Entry<String, String> e : all.entrySet()) {
            Files.writeString(recipeDir.resolve(e.getKey() + ".json"), e.getValue(), StandardCharsets.UTF_8);
        }
        ProductionLineMod.LOGGER.info("Active cpl recipe union now has {} recipe(s) from {} loader contribution file(s)",
                all.size(), contribFiles.size());
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        return all.size();
    }

    private static boolean isSafeFileName(String name) {
        if (name == null || name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return false;
        }
        return name.matches("[A-Za-z0-9_.\\-]+");
    }

    /** Builds a safe file base name from an item id and method. */
    public static String fileBase(String outputId, String type) {
        String cleaned = outputId.replace(':', '_').replace('/', '_');
        String method = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        return "cpl_" + cleaned + "_" + method;
    }

    /** Turns a flat/mechanical JsonObject into a pretty string. */
    public static String toJsonString(JsonObject object) {
        return GSON.toJson(object);
    }

    /**
     * Tries to convert one found foreign recipe into a native Create recipe JSON
     * payload. Emits a JSON only for the single-machine processing methods
     * (mixing/crushing/…); returns {@code null} for already-Create recipes and
     * for pure crafting (which would require the Mechanical Crafter) — those are
     * planned as Deployer + Press guidance instead.
     */
    public static com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry tryConvertOne(
            com.create.productionline.line.mapper.ServerRecipeLookup.Found found,
            com.create.productionline.line.mapper.MappingDictionary dictionary,
            net.minecraft.core.HolderLookup.Provider provider) {
        var descriptor = found.descriptor();
        return flatEntry(dictionary, descriptor.categoryId(), descriptor.uniqueInputs(),
                descriptor.outputs().isEmpty() ? null : descriptor.outputs().get(0));
    }

    /**
     * Builds a {@code create:sequenced_assembly} recipe payload for an assembly /
     * crafting recipe: the first material is the base, every following material
     * is deployed onto a generic intermediate item, and the final step yields the
     * product. This makes assembly recipes actually work in Create (the Scheme
     * Loader activates it) instead of being pure advice.
     */
    public static com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry sequenceEntry(
            java.util.List<String> inputs, String output, String intermediateId) {
        return sequenceEntry(inputs, output, intermediateId, 1);
    }

    /** Builds a sequenced_assembly payload, preserving the output count. */
    public static com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry sequenceEntry(
            java.util.List<String> inputs, String output, String intermediateId, int count) {
        if (inputs == null || inputs.size() < 2 || output == null) {
            return null;
        }
        JsonObject root = new JsonObject();
        root.addProperty("type", "create:sequenced_assembly");
        root.add("ingredient", asIngredient(inputs.get(0)));
        JsonObject transitional = new JsonObject();
        transitional.addProperty("id", intermediateId);
        root.add("transitional_item", transitional);

        JsonArray sequence = new JsonArray();
        String base = inputs.get(0);
        // Step 1 operates on the BASE item; every later step operates on the
        // transitional item (this is what Create's Sequenced Assembly matches).
        for (int i = 1; i < inputs.size(); i++) {
            String current = (i == 1) ? base : intermediateId;
            JsonObject step = new JsonObject();
            step.addProperty("type", "create:deploying");
            JsonArray stepIngredients = new JsonArray();
            stepIngredients.add(asIngredient(current));
            stepIngredients.add(asIngredient(inputs.get(i)));
            step.add("ingredients", stepIngredients);
            JsonArray stepResults = new JsonArray();
            JsonObject stepResult = new JsonObject();
            stepResult.addProperty("id", intermediateId);
            stepResults.add(stepResult);
            step.add("results", stepResults);
            sequence.add(step);
        }
        root.add("sequence", sequence);

        JsonArray results = new JsonArray();
        JsonObject result = new JsonObject();
        result.addProperty("id", output);
        if (count > 1) {
            result.addProperty("count", count);
        }
        results.add(result);
        root.add("results", results);
        root.addProperty("loops", 1);

        return new com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry(
                fileBase(output, "sequenced_assembly"), toJsonString(root));
    }

    /**
     * Builds an ingredient JSON object from an entry string. Tag ingredients are
     * passed as {@code "#namespace:tag"} (matching {@link #mechanical} and the
     * {@code RecipeJsonReader}); plain strings are item ids. Item/tag members of
     * the original foreign recipe are therefore preserved instead of being
     * pinned to one arbitrary tag member.
     */
    private static JsonObject asIngredient(String value) {
        JsonObject entry = new JsonObject();
        if (value != null && value.startsWith("#")) {
            entry.addProperty("tag", value.substring(1));
        } else {
            entry.addProperty("item", value == null ? "" : value);
        }
        return entry;
    }

    /** Same as {@link #tryConvertOne} but takes a category + ingredients directly (client-resolved). */
    public static com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry flatEntry(
            com.create.productionline.line.mapper.MappingDictionary dictionary, String categoryId,
            java.util.List<String> inputs, String output) {
        return flatEntry(dictionary, categoryId, inputs, output, 1);
    }

    /** Same as {@link #flatEntry} but preserves the output count. */
    public static com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry flatEntry(
            com.create.productionline.line.mapper.MappingDictionary dictionary, String categoryId,
            java.util.List<String> inputs, String output, int count) {
        if (categoryId == null || categoryId.startsWith("create:")) {
            return null; // already a native Create recipe
        }
        String facility = dictionary == null ? null : dictionary.lookup(categoryId);
        String method = methodOf(facility);
        if (method != null && output != null && inputs != null && !inputs.isEmpty()) {
            return new com.create.productionline.line.scheme.LineScheme.CreateRecipeEntry(
                    fileBase(output, method), toJsonString(flat(method, inputs, output, count)));
        }
        return null;
    }

    /** Helper: char-keyed ingredient map from unique item ids. */
    public static Map<Character, String> keyMap(List<String> items) {
        Map<Character, String> map = new LinkedHashMap<>();
        char c = 'A';
        for (String item : items) {
            map.putIfAbsent(c, item);
            c++;
        }
        return map;
    }

    /** Fills a row-major ingredient list (width x height) into pattern rows. */
    public static List<String> patternFromRowMajor(List<String> cells, int width, int height) {
        List<String> rows = new ArrayList<>();
        int idx = 0;
        for (int y = 0; y < height && y < 8; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < width && x < 8; x++) {
                row.append(idx < cells.size() ? cells.get(idx) : " ");
                idx++;
            }
            rows.add(row.toString());
        }
        return rows;
    }
}
