package com.create.productionline.recipegen;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.line.analyzer.RecipeAnalyzer;
import com.create.productionline.line.mapper.Mappers;
import com.create.productionline.line.mapper.RecipeDescriptor;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.util.RecipeJsonReader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-authoritative recipe derivation.
 *
 * <p>Neither the Scheme Loader nor the Dismantler trusts the {@code Steps} or
 * embedded recipe JSON stored on a scheme item any more. Everything is
 * re-derived on the server from the scheme's {@code recipeId} against the live
 * {@code RecipeManager} — a forged scheme item can therefore only ever activate
 * / refund the recipes that actually exist on the server (anti-injection).
 */
public final class RecipeDeriver {

    /** The transitional item used by generated sequence recipes. */
    public static final String INTERMEDIATE_ID = "create_productionline:generic_intermediate";

    private RecipeDeriver() {
    }

    /** Result of deriving one recipe: ordering/count for plans plus the entries to install. */
    public record Derived(List<String> orderedInputs, int count, List<LineScheme.CreateRecipeEntry> entries) {
        public boolean hasEntries() {
            return entries != null && !entries.isEmpty();
        }
    }

    /** Categories whose recipes we convert into an EXTRA sequenced-assembly recipe. */
    public static boolean isConvertibleAssembly(String categoryId) {
        if (categoryId == null) {
            return false;
        }
        return RecipeDescriptor.isAssemblyLikeCategory(categoryId)
                || "create:mechanical_crafting".equals(categoryId);
    }

    /** Full server-side derivation for a descriptor (plan order/count + installable entries). */
    public static Derived derive(ServerLevel level, RecipeDescriptor source) {
        if (level == null || source == null || source.outputs().isEmpty()) {
            return new Derived(source == null ? List.of() : source.uniqueInputs(), 1, List.of());
        }
        // B7: the LIVE recipe decides the yield, the JSON is only a fallback. A mod
        // may produce more at runtime than its datapack JSON claims (source
        // .outputCount() is getResultItem().getCount() of the live recipe), so the
        // larger of the two wins; RecipeJsonReader.resultCount already returns 1
        // when the file is missing or carries no count, and the outer Math.max(1, …)
        // keeps a count-less recipe at "one craft yields one".
        int count = Math.max(1, source.outputCount());
        List<String> ordered = source.uniqueInputs();
        ResourceLocation rid = ResourceLocation.tryParse(source.recipeId());
        if (rid != null) {
            int jsonCount = RecipeJsonReader.resultCount(level.getServer().getResourceManager(), rid);
            count = Math.max(1, Math.max(source.outputCount(), jsonCount));
            List<String> o = RecipeJsonReader.shapedMaterialOrder(
                    level.getServer().getResourceManager(), rid, source.uniqueInputs());
            if (!o.isEmpty()) {
                ordered = o;
            }
        }
        return new Derived(ordered, count, entriesFor(level, source, ordered, count));
    }

    /**
     * The installable native-Create entries for a descriptor (single source of
     * truth). At most ONE entry is ever produced, and the decision flow is a
     * fixed, ordered chain:
     *
     * <ol>
     *   <li>no materials at all -> nothing (a recipe without usable input cannot
     *       become a line);</li>
     *   <li><b>native protection</b>: a {@code create:} category that is not an
     *       assembly category is already produced by a Create process of its own —
     *       converting it would duplicate native content, so refuse;</li>
     *   <li><b>dictionary</b> (user-config contract) wins for a single-material
     *       recipe; for two or more materials it is only trusted when its method
     *       accepts several ingredients ({@code create:mixing}) — a single-input
     *       machine would otherwise be handed an ingredient list it cannot match;</li>
     *   <li><b>assembly</b> (crafting-like, incl. {@code create:mechanical_crafting})
     *       with 2+ materials -> mechanical crafting JSON when the config asks for
     *       it and a shaped pattern is readable, otherwise a
     *       {@code create:sequenced_assembly};</li>
     *   <li><b>single material</b> -> the material's own semantics pick a real
     *       Create machine ({@link RecipeAnalyzer#machineForMaterial}) and its flat
     *       processing type (B3: planks -> stick, log -> planks, buttons, … are
     *       convertible);</li>
     *   <li><b>no rule matched</b> -> nothing: unknown machine categories (e.g.
     *       {@code superbwarfare:vehicle_assembling}) and entity-result recipes are
     *       refused instead of being forced into a {@code create:mixing} line;</li>
     * </ol>
     */
    public static List<LineScheme.CreateRecipeEntry> entriesFor(ServerLevel level,
            RecipeDescriptor source, List<String> orderedInputs, int count) {
        List<LineScheme.CreateRecipeEntry> out = new ArrayList<>();
        if (level == null || source == null || source.outputs().isEmpty() || source.categoryId() == null) {
            return out;
        }
        String category = source.categoryId();
        String output = source.outputs().get(0);
        // ONE normalized material list for every derived payload: an empty/absent
        // order falls back to the descriptor's own unique inputs, and both the flat
        // and the assembly payloads read from the same list (tag fidelity included).
        List<String> materials = (orderedInputs == null || orderedInputs.isEmpty())
                ? source.uniqueInputs()
                : orderedInputs;
        if (materials.isEmpty()) {
            return out;
        }

        // 1b) Result-level guard: a recipe whose result is an ENTITY (vehicles,
        // turrets, superbwarfare:vehicle_assembling …) is assembled by a machine of
        // its own mod — converting it into a Create processing line would be wrong.
        ResourceLocation jsonId = ResourceLocation.tryParse(source.recipeId());
        if (jsonId != null && RecipeJsonReader.resultIsEntity(
                level.getServer().getResourceManager(), jsonId)) {
            return out;
        }

        // 2) Native protection: already a Create process of its own.
        if (category.startsWith("create:") && !isConvertibleAssembly(category)) {
            return out;
        }

        // 3) Dictionary (user config) first — with the multi-ingredient guard.
        LineScheme.CreateRecipeEntry entry = CreateRecipePack.flatEntry(
                Mappers.getDictionary(), category, materials, output, count);
        if (entry != null) {
            var dictionary = Mappers.getDictionary();
            String dictionaryMethod = CreateRecipePack.methodOf(
                    dictionary == null ? null : dictionary.lookup(category));
            // Safe for several materials only when the mapped facility really takes
            // an ingredient list; otherwise fall through to the assembly / mixing
            // paths below instead of writing a flat recipe with 2+ ingredients.
            if ("create:mixing".equals(dictionaryMethod) || materials.size() < 2) {
                out.add(entry);
                return out;
            }
            entry = null; // not multi-ingredient safe: fall through to assembly / mixing
        }

        // 4) Assembly / crafting: one deploy step per extra material.
        if (isConvertibleAssembly(category) && materials.size() >= 2) {
            boolean mechanical = "mechanical".equalsIgnoreCase(Mappers.getAssemblyMode());
            if (mechanical) {
                ResourceLocation rid = ResourceLocation.tryParse(source.recipeId());
                if (rid != null) {
                    var shaped = RecipeJsonReader.shapedFromJson(
                            level.getServer().getResourceManager(), rid);
                    if (shaped != null && shaped.valid()) {
                        var json = CreateRecipePack.mechanical(
                                shaped.pattern(), shaped.keyItems(), output, count);
                        entry = new LineScheme.CreateRecipeEntry(
                                CreateRecipePack.fileBase(output, "mechanical_crafting"),
                                CreateRecipePack.toJsonString(json));
                    }
                }
            }
            if (entry == null) {
                entry = CreateRecipePack.sequenceEntry(materials, output, INTERMEDIATE_ID, count);
            }
            if (entry != null) {
                out.add(entry);
                return out;
            }
        }

        // 5) Single material: its semantics choose a real Create machine (B3).
        if (materials.size() == 1) {
            String facility = RecipeAnalyzer.machineForMaterial(materials.get(0));
            String method = CreateRecipePack.methodOf(facility);
            if (method == null) {
                method = "create:pressing";
            }
            entry = new LineScheme.CreateRecipeEntry(
                    CreateRecipePack.fileBase(output, method),
                    CreateRecipePack.toJsonString(CreateRecipePack.flat(method, materials, output, count)));
            out.add(entry);
            return out;
        }

        // 6) No rule matched — refuse instead of forcing an unknown machine
        // category into a mixing line (D: semantic honesty over false success).
        return out;
    }
}
