package com.create.productionline.recipegen;

import java.util.ArrayList;
import java.util.List;

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

    /** The installable native-Create entries for a descriptor (single source of truth). */
    public static List<LineScheme.CreateRecipeEntry> entriesFor(ServerLevel level,
            RecipeDescriptor source, List<String> orderedInputs, int count) {
        List<LineScheme.CreateRecipeEntry> out = new ArrayList<>();
        if (level == null || source == null || source.outputs().isEmpty() || source.categoryId() == null) {
            return out;
        }
        String output = source.outputs().get(0);
        // ONE normalized material list for every derived payload: an empty/absent
        // order falls back to the descriptor's own unique inputs, and both the flat
        // and the assembly payloads read from the same list (tag fidelity included).
        java.util.List<String> materials = (orderedInputs == null || orderedInputs.isEmpty())
                ? source.uniqueInputs()
                : orderedInputs;
        LineScheme.CreateRecipeEntry entry = CreateRecipePack.flatEntry(
                Mappers.getDictionary(), source.categoryId(), materials, output, count);
        if (entry == null && materials.size() > 1 && isConvertibleAssembly(source.categoryId())) {
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
        }
        if (entry != null) {
            out.add(entry);
        }
        return out;
    }
}
