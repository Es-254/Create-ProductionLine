package com.create.productionline.recipegen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.block.SchemeLoaderBlock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
 * Ingredient entries are {@code {"item":…}} for item references and
 * {@code {"tag":…}} for {@code "#tag"} references — flat and assembly payloads
 * share {@link #asIngredient}, so tag fidelity holds on every path; results use
 * {@code "id"} as Create 1.21.1 expects.
 */
public final class CreateRecipePack {

    public static final String PACK_FOLDER = "cpl_converted";
    public static final String PACK_NAMESPACE = "cpl";
    /**
     * Minecraft 1.21.1 <b>data</b> pack format. (34 is the 1.21.1 <b>resource</b>
     * pack format — writing 34 into {@code pack.mcmeta} makes the pack
     * mis-versioned for a datapack, so the data pack number 48 is required here.)
     */
    public static final int PACK_FORMAT = 48;

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

    /**
     * Create recipe type id -> the Create machine block that performs it.
     * Deliberately an explicit table instead of an inversion of
     * {@link #FACILITY_TO_TYPE}: the two are not exact inverses (one facility
     * performs several types — the deployer does {@code deploying},
     * {@code item_application}, {@code sandpaper_polishing}), so the plan
     * generator needs the concrete type -> machine direction for the types that
     * this mod derives.
     */
    private static final Map<String, String> TYPE_TO_FACILITY = Map.of(
            "create:mixing", "create:mechanical_mixer",
            "create:crushing", "create:crushing_wheel",
            "create:milling", "create:millstone",
            "create:pressing", "create:mechanical_press",
            "create:cutting", "create:mechanical_saw",
            "create:haunting", "create:haunted_bell",
            "create:splashing", "create:encased_fan",
            "create:deploying", "create:deployer",
            "create:mechanical_crafting", "create:mechanical_crafter");

    /**
     * Processing time (ms) for the flat types that ACTUALLY accept a duration.
     *
     * <p>Create validates this: {@code ProcessingRecipe.canSpecifyDuration()} defaults to
     * {@code false} and a recipe carrying {@code processing_time} anyway fails to load with
     * <i>"Recipe specified a duration. Durations have no impact on this type of recipe."</i>
     * Only milling / crushing / cutting override it (Basin/mixing does too, but Create's own
     * mixing files omit the field, so we mirror them and omit it as well). Writing a duration
     * for pressing / splashing / haunting / mixing made those recipes unloadable — the machine
     * then did nothing even though the GUI reported success.
     */
    private static final Map<String, Integer> DURATION_TYPES = Map.of(
            "create:crushing", 300,
            "create:milling", 200,
            "create:cutting", 50);

    private CreateRecipePack() {
    }

    /** Maps a Create facility id to the recipe type it performs, or null. */
    public static String methodOf(String facilityId) {
        return facilityId == null ? null : FACILITY_TO_TYPE.get(facilityId);
    }

    /**
     * Maps a Create recipe type id to the machine block that performs it, or
     * {@code null} when no such facility is known. Used by the plan generator to
     * bind a station to the type of the recipe that was really derived, so the
     * displayed machine always mirrors the installed JSON.
     */
    public static String facilityOf(String recipeType) {
        if (recipeType == null) {
            return null;
        }
        return TYPE_TO_FACILITY.get(recipeType.toLowerCase(java.util.Locale.ROOT).trim());
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
            // Same normalization as the assembly path: item references stay items,
            // "#tag" references become {"tag": …} — writing {"item": "#tag"} would
            // produce an invalid recipe that silently never loads.
            ingredients.add(asIngredient(in));
        }
        root.add("ingredients", ingredients);
        Integer time = DURATION_TYPES.get(type);
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

    // --- self-test isolation --------------------------------------------------

    /** Datapack folder used by the QA self test — never the live pack. */
    public static final String SELFTEST_FOLDER = PACK_FOLDER + "_selftest";
    private static final String SELFTEST_NAMESPACE = "cpl_selftest";

    /**
     * Self-test only: installs recipes into a SEPARATE datapack folder and
     * reloads, so the live {@code cpl_converted} pack — and therefore every
     * scheme loader's contribution — is left untouched.
     *
     * @return the number of written recipe files
     */
    public static int installIsolated(MinecraftServer server, Map<String, String> fileNameToJson) {
        if (server == null || fileNameToJson == null || fileNameToJson.isEmpty()) {
            return 0;
        }
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(SELFTEST_FOLDER);
        try {
            deleteRecursively(packRoot);
            Files.createDirectories(packRoot.resolve("data/" + SELFTEST_NAMESPACE + "/recipe"));
            JsonObject meta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", PACK_FORMAT);
            pack.addProperty("description", "Create: Production Line — self-test recipes (temporary)");
            meta.add("pack", pack);
            Files.writeString(packRoot.resolve("pack.mcmeta"), GSON.toJson(meta), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> e : fileNameToJson.entrySet()) {
                Files.writeString(
                        packRoot.resolve("data/" + SELFTEST_NAMESPACE + "/recipe/" + e.getKey() + ".json"),
                        e.getValue(), StandardCharsets.UTF_8);
            }
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
            return fileNameToJson.size();
        } catch (Exception e) {
            ProductionLineMod.LOGGER.warn("Could not install self-test datapack {}: {}", packRoot, e.toString());
            return 0;
        }
    }

    /** Self-test only: removes the isolated self-test pack and reloads. */
    public static void removeIsolated(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(SELFTEST_FOLDER);
        try {
            deleteRecursively(packRoot);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        } catch (Exception e) {
            ProductionLineMod.LOGGER.warn("Could not remove self-test datapack {}: {}", packRoot, e.toString());
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

    /**
     * Metadata key inside a contribution file holding the loader's RAW
     * (un-sanitized) key, i.e. the exact key the owning cabinet registered under.
     * Keys starting with {@code __} are metadata and never become recipe files.
     */
    private static final String LOADER_KEY_FIELD = "__loader";

    /** Matches {@code BlockPos{x=1, y=2, z=3}} (and the bare {@code 1, 2, 3} form). */
    private static final java.util.regex.Pattern POS_PATTERN =
            java.util.regex.Pattern.compile("(?:x=)?(-?\\d+),\\s*(?:y=)?(-?\\d+),\\s*(?:z=)?(-?\\d+)");

    /**
     * Digest of the union last written to disk, or {@code null} when nothing has
     * been written in this server session (first run always writes). Lets an
     * unchanged union skip the delete + full rewrite + {@code /reload} cycle.
     */
    private static String lastUnionDigest = null;

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
     * <p>The written file also carries the loader's raw key under
     * {@value #LOADER_KEY_FIELD}, so a later {@link #sweepOrphanContributions}
     * pass can tell which cabinet a leftover contribution belongs to.
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
                // Package the recipes together with the raw loader key: the file NAME
                // is sanitized (so it cannot be parsed back), the metadata is not.
                JsonObject payload = new JsonObject();
                payload.addProperty(LOADER_KEY_FIELD, loaderKey);
                for (Map.Entry<String, String> e : ownFiles.entrySet()) {
                    payload.addProperty(e.getKey(), e.getValue());
                }
                Files.writeString(own, GSON.toJson(payload), StandardCharsets.UTF_8);
            }
            return rebuildUnion(server, packRoot);
        } catch (Exception e) {
            ProductionLineMod.LOGGER.warn("Could not reconcile loader {}: {}", loaderKey, e.toString());
            return 0;
        }
    }

    /**
     * Deletes one loader's contribution file WITHOUT rebuilding the union (the
     * caller re-reconciles right after, so this avoids a redundant reload).
     * Used to clean up the contribution registered under a loader's previous
     * coordinates after the cabinet was relocated (piston move, {@code /clone}, …).
     */
    public static boolean dropContribution(MinecraftServer server, String loaderKey) {
        if (server == null || loaderKey == null || loaderKey.isBlank()) {
            return false;
        }
        Path contribDir = server.getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(PACK_FOLDER).resolve(CONTRIB_DIR);
        try {
            return Files.deleteIfExists(contribDir.resolve(sanitize(loaderKey) + ".json"));
        } catch (Exception e) {
            ProductionLineMod.LOGGER.warn("Could not drop stale contribution {}: {}", loaderKey, e.toString());
            return false;
        }
    }

    /**
     * Writes pack.mcmeta + the union of all loader contribution files and makes
     * the running server see it. The union is hashed first: when it is
     * byte-identical to the union last written, the delete + rewrite + apply cycle
     * is skipped entirely (a refresh re-reads every recipe on the server, so a
     * no-op reconcile must not cost one).
     *
     * <p>The refresh itself goes through {@link RecipeHotSwap#applyOwned}: the
     * recipes this pack owns are parsed and swapped into the live
     * {@link net.minecraft.world.item.crafting.RecipeManager}, so activating a
     * scheme costs a parse of this pack's own files instead of a server-wide
     * {@code /reload}. A full reload remains the fallback when the swap refuses a
     * payload, because that path also handles recipe conditions and can discover a
     * data pack folder the server has not seen yet.
     *
     * @return the number of recipes in the union — unchanged whether or not the
     *         files were actually rewritten
     */
    private static int rebuildUnion(MinecraftServer server, Path packRoot) throws Exception {
        Path contribDir = packRoot.resolve(CONTRIB_DIR);
        Files.createDirectories(contribDir);

        Map<String, String> all = new LinkedHashMap<>();
        List<Path> contribFiles = new ArrayList<>();
        try (var stream = Files.list(contribDir)) {
            stream.filter(x -> x.getFileName().toString().endsWith(".json")).forEach(contribFiles::add);
        }
        // Deterministic union order: when two loaders contribute the same file name,
        // which one wins must not depend on the filesystem's enumeration order.
        contribFiles.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        for (Path p : contribFiles) {
            String content = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject root = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            for (var e : root.entrySet()) {
                String name = e.getKey();
                if (name.startsWith("__")) {
                    continue; // metadata (e.g. __loader), never a recipe file
                }
                String json = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString();
                if (isSafeFileName(name) && json != null && !json.isBlank()) {
                    all.put(name, json);
                }
            }
        }

        String digest = unionDigest(all);
        if (digest != null && digest.equals(lastUnionDigest)) {
            ProductionLineMod.LOGGER.debug(
                    "cpl recipe union unchanged ({} recipe(s), {} contribution file(s)) — skipping rewrite/reload",
                    all.size(), contribFiles.size());
            return all.size();
        }

        Path recipeDir = packRoot.resolve("data/" + PACK_NAMESPACE + "/recipe");
        // The files on disk are this pack's previous state — written by an earlier
        // pass of this session, or loaded by the server at start-up — so their names
        // are exactly the ids that have to leave the live recipe set. Without this a
        // union that shrank would keep serving the recipes it dropped.
        List<String> installedBefore = new ArrayList<>();
        try (var stream = Files.list(recipeDir)) {
            stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .forEach(n -> installedBefore.add(n.substring(0, n.length() - ".json".length())));
        } catch (java.io.IOException e) {
            ProductionLineMod.LOGGER.debug("No previous cpl recipe folder to diff against: {}", e.toString());
        }
        deleteRecursively(recipeDir);
        Files.createDirectories(recipeDir);

        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "Create: Production Line — converted recipes (multi-loader union)");
        meta.add("pack", pack);
        Files.writeString(packRoot.resolve("pack.mcmeta"), GSON.toJson(meta), StandardCharsets.UTF_8);

        for (Map.Entry<String, String> e : all.entrySet()) {
            Files.writeString(recipeDir.resolve(e.getKey() + ".json"), e.getValue(), StandardCharsets.UTF_8);
        }
        lastUnionDigest = digest;
        ProductionLineMod.LOGGER.info("Active cpl recipe union now has {} recipe(s) from {} loader contribution file(s)",
                all.size(), contribFiles.size());
        RecipeHotSwap.Outcome outcome = RecipeHotSwap.applyOwned(server, all, installedBefore);
        if (!outcome.ok()) {
            ProductionLineMod.LOGGER.warn("CPL recipe swap refused ({}), falling back to a full /reload",
                    outcome.detail());
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        }
        return all.size();
    }

    /**
     * Deterministic SHA-256 over the union entries, sorted by file name and
     * concatenated as {@code name=json;}. Two unions with the same content hash
     * the same regardless of file-system enumeration order.
     *
     * @return the hex digest, or {@code null} if hashing failed (which never
     *         equals a stored digest, so the caller performs a real write)
     */
    private static String unionDigest(Map<String, String> entries) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String name : new java.util.TreeSet<>(entries.keySet())) {
                md.update((name + "=" + entries.get(name) + ";").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            ProductionLineMod.LOGGER.warn("Could not hash the recipe union: {}", e.toString());
            return null;
        }
    }

    /**
     * Deletes contribution files whose owning cabinet no longer exists — the
     * crash/relocation half of contribution cleanup. A cabinet that was moved by a
     * Create contraption (or deleted while its chunk was unloaded) leaves its last
     * contribution behind; reconciling only ever touches the cabinet that is
     * currently ticking, so leftovers would otherwise serve recipes forever.
     *
     * <p>Conservative by construction: a file is deleted only when it carries the
     * {@value #LOADER_KEY_FIELD} metadata, its dimension is loaded, the chunk
     * holding the recorded position is loaded, and the block there is NOT a
     * {@link SchemeLoaderBlock}. Files written by older mod versions have no
     * metadata and are always skipped — they can only be cleaned up by the owning
     * cabinet re-reconciling under its new key.
     *
     * @return the number of contribution files deleted
     */
    public static int sweepOrphanContributions(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_FOLDER);
        Path contribDir = packRoot.resolve(CONTRIB_DIR);
        if (!Files.isDirectory(contribDir)) {
            return 0;
        }
        List<Path> contribFiles = new ArrayList<>();
        try (var stream = Files.list(contribDir)) {
            stream.filter(x -> x.getFileName().toString().endsWith(".json")).forEach(contribFiles::add);
        } catch (Exception e) {
            ProductionLineMod.LOGGER.warn("Could not list contributions in {}: {}", contribDir, e.toString());
            return 0;
        }
        int swept = 0;
        for (Path p : contribFiles) {
            try {
                JsonObject root = com.google.gson.JsonParser
                        .parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!root.has(LOADER_KEY_FIELD) || !root.get(LOADER_KEY_FIELD).isJsonPrimitive()) {
                    continue; // legacy contribution without metadata: never guess
                }
                String loaderKey = root.get(LOADER_KEY_FIELD).getAsString();
                int slash = loaderKey == null ? -1 : loaderKey.lastIndexOf('/');
                if (slash <= 0) {
                    continue;
                }
                String dimensionId = loaderKey.substring(0, slash);
                var matcher = POS_PATTERN.matcher(loaderKey.substring(slash + 1));
                if (!matcher.find()) {
                    continue;
                }
                BlockPos pos = new BlockPos(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                var level = server.getLevel(
                        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimensionId)));
                if (level == null) {
                    continue; // dimension not loaded now: cannot judge, keep
                }
                if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    continue; // position not loaded: cannot judge, keep
                }
                if (level.getBlockState(pos).getBlock() instanceof SchemeLoaderBlock) {
                    continue; // cabinet is still there
                }
                if (Files.deleteIfExists(p)) {
                    swept++;
                    ProductionLineMod.LOGGER.info("Swept orphan cpl contribution {} (no scheme loader at {})",
                            p.getFileName(), loaderKey);
                }
            } catch (Exception e) {
                ProductionLineMod.LOGGER.warn("Could not sweep contribution file {}: {}", p, e.toString());
            }
        }
        if (swept > 0) {
            try {
                rebuildUnion(server, packRoot);
            } catch (Exception e) {
                ProductionLineMod.LOGGER.warn("Could not rebuild the recipe union after sweeping {} orphan(s): {}",
                        swept, e.toString());
            }
        }
        return swept;
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
        // Foreign formats may hand us count-prefixed tokens ("8 #c:storage_blocks/steel");
        // normalize so we never emit an illegal ingredient that silently fails to load.
        String token = com.create.productionline.util.RecipeJsonReader.normalizeMaterial(value);
        JsonObject entry = new JsonObject();
        if (token == null) {
            entry.addProperty("item", "minecraft:air");
        } else if (token.startsWith("#")) {
            entry.addProperty("tag", token.substring(1));
        } else {
            entry.addProperty("item", token);
        }
        return entry;
    }

    /**
     * Builds a flat processing ({@code create:mixing} / crushing / …) recipe entry
     * from a recipe category id + ingredients, looking the facility up in
     * {@code dictionary}. Returns {@code null} for already-native Create recipes
     * and for categories no Create machine performs. Preserves the output count.
     */
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
}
