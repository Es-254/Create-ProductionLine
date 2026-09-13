package com.create.productionline.line.mapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.util.RecipeJsonReader;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Server-side recipe lookup. JEI itself only runs on the client, but every data
 * recipe (vanilla, Create and other mods alike) is present in the server
 * {@code RecipeManager}; JEI merely renders them. This class scans those recipes
 * and produces {@link RecipeDescriptor}s for the mapper.
 */
public final class ServerRecipeLookup {

    private ServerRecipeLookup() {
    }

    /** Category ids that describe machine processes (preferred over crafting). */
    private static final Predicate<String> PREFERRED =
            cat -> cat.startsWith("create:") || cat.equals("minecraft:smelting")
                    || cat.equals("minecraft:smoking") || cat.equals("minecraft:blasting");

    /**
     * Finds recipes whose primary output is {@code targetItem}, ordered so that
     * machine-process categories come first.
     */
    public static List<RecipeDescriptor> findRecipes(ServerLevel level, ResourceLocation targetItem) {
        List<RecipeDescriptor> out = new ArrayList<>();
        for (Found found : findDetailed(level, targetItem)) {
            out.add(found.descriptor());
        }
        return out;
    }

    /**
     * Resolves ONE recipe by its registry id and builds its neutral descriptor.
     * Used by the loader / dismantler as the authoritative, server-side source
     * of a scheme's {@code recipeId} (never trusts scheme NBT beyond the id).
     */
    public static RecipeDescriptor findById(ServerLevel level, String recipeId) {
        if (level == null || recipeId == null || recipeId.isBlank()) {
            return null;
        }
        ResourceLocation rid = ResourceLocation.tryParse(recipeId);
        if (rid == null) {
            return null;
        }
        var holderOpt = level.getRecipeManager().byKey(rid);
        if (holderOpt.isEmpty()) {
            return null;
        }
        RecipeHolder<?> holder = holderOpt.get();
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        if (typeId == null) {
            return null;
        }
        return toDescriptor(holder, typeId, level.registryAccess(), level);
    }

    /** Like {@link #findRecipes} but keeps the underlying recipe object. */
    public static List<Found> findDetailed(ServerLevel level, ResourceLocation targetItem) {
        RegistryAccess registryAccess = level.registryAccess();
        List<Found> found = new ArrayList<>();

        for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE) {
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);
            if (typeId == null) {
                continue;
            }
            List<? extends RecipeHolder<?>> holders = allRecipesFor(level.getRecipeManager(), type);
            for (RecipeHolder<?> holder : holders) {
                RecipeDescriptor descriptor = toDescriptor(holder, typeId, registryAccess, level);
                if (descriptor != null && matchesOutput(descriptor, targetItem)) {
                    found.add(new Found(descriptor, holder.value()));
                }
            }
        }

        // Smithing recipes may not always be enumerated by the registry loop;
        // scan them explicitly (smithing_transform / smithing_trim share SMITHING).
        collectFromType(level, RecipeType.SMITHING, "minecraft:smithing", registryAccess, found, targetItem);

        // De-duplicate by recipe id: the registry loop may already have covered
        // smithing, so the explicit scan above must not double-report recipes.
        java.util.LinkedHashMap<String, Found> unique = new java.util.LinkedHashMap<>();
        for (Found f : found) {
            unique.putIfAbsent(f.descriptor().recipeId(), f);
        }
        List<Found> deduped = new ArrayList<>(unique.values());
        deduped.sort(Comparator.comparingInt((Found f) -> PREFERRED.test(f.descriptor().categoryId()) ? 0 : 1)
                .thenComparing(f -> f.descriptor().categoryId()));
        return deduped;
    }

    private static void collectFromType(ServerLevel level, RecipeType<?> type, String categoryId,
            RegistryAccess registryAccess, List<Found> found, ResourceLocation targetItem) {
        try {
            List<? extends RecipeHolder<?>> holders = allRecipesFor(level.getRecipeManager(), type);
            for (RecipeHolder<?> holder : holders) {
                RecipeDescriptor descriptor = toDescriptor(holder, ResourceLocation.withDefaultNamespace(categoryId),
                        registryAccess, level);
                if (descriptor != null && matchesOutput(descriptor, targetItem)) {
                    found.add(new Found(descriptor, holder.value()));
                }
            }
        } catch (RuntimeException ignored) {
            // ignore exotic recipe types
        }
    }

    /** A found recipe: neutral descriptor plus the live recipe object. */
    public record Found(RecipeDescriptor descriptor, net.minecraft.world.item.crafting.Recipe<?> recipe) {
    }

    private static boolean matchesOutput(RecipeDescriptor descriptor, ResourceLocation target) {
        for (String out : descriptor.outputs()) {
            if (out.equals(target.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Raw, unchecked recipe iteration helper (recipe types are heterogeneous). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<? extends RecipeHolder<?>> allRecipesFor(net.minecraft.world.item.crafting.RecipeManager manager,
            RecipeType<?> type) {
        try {
            return manager.getAllRecipesFor((RecipeType) type);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static RecipeDescriptor toDescriptor(RecipeHolder<?> holder, ResourceLocation typeId,
            RegistryAccess registryAccess, ServerLevel level) {
        try {
            net.minecraft.world.item.crafting.Recipe<?> recipe = holder.value();
            java.util.List<String> outputs = new ArrayList<>();
            collectOutputs(recipe, registryAccess, outputs);
            if (outputs.isEmpty()) {
                return null;
            }
            // Ingredients are read from the recipe's own datapack JSON FIRST so
            // that tag references keep their identity ("#tag") instead of being
            // pinned to one arbitrary tag member. Recipe objects cannot carry
            // that information, so object-based extraction is only the fallback
            // when the JSON cannot be read.
            java.util.List<String> inputs = jsonInputs(level, holder.id());
            if (inputs.isEmpty()) {
                collectObjectInputs(recipe, registryAccess, inputs);
            }
            return build(holder.id().toString(), typeId.toString(), inputs, outputs);
        } catch (RuntimeException e) {
            ProductionLineMod.LOGGER.debug("Skipped recipe {} during lookup: {}", holder.id(), e.toString());
            return null;
        }
    }

    /**
     * Best-effort, tag-preserving ingredient extraction from the recipe's own
     * datapack JSON. Delegates to {@link RecipeJsonReader}; returns an empty
     * list when the JSON is unavailable.
     */
    private static java.util.List<String> jsonInputs(ServerLevel level, ResourceLocation recipeId) {
        if (level == null || level.getServer() == null || recipeId == null) {
            return List.of();
        }
        return RecipeJsonReader.readIngredients(level.getServer().getResourceManager(), recipeId);
    }

    private static void addSmithIngredient(Ingredient ingredient, RegistryAccess registryAccess, List<String> inputs) {
        String id = representative(ingredient, registryAccess);
        if (id != null && !inputs.contains(id)) {
            inputs.add(id);
        }
    }

    private static void addRepresentative(Ingredient ingredient, RegistryAccess registryAccess, List<String> inputs) {
        String id = representative(ingredient, registryAccess);
        if (id != null) {
            inputs.add(id);
        }
    }

    /** Output id(s) of a recipe object — cheap, no JSON involved. */
    private static void collectOutputs(net.minecraft.world.item.crafting.Recipe<?> recipe,
            RegistryAccess registryAccess, List<String> outputs) {
        try {
            ItemStack result = recipe.getResultItem(registryAccess);
            if (!result.isEmpty()) {
                outputs.add(itemId(result));
            }
        } catch (RuntimeException ignored) {
            // ignore exotic recipe shapes
        }
    }

    /**
     * Fallback input extraction from recipe objects (only used when the recipe's
     * JSON cannot be read). Reflects over the common {@code CraftingRecipe}
     * -style accessors without importing every recipe class.
     */
    @SuppressWarnings("unchecked")
    private static void collectObjectInputs(net.minecraft.world.item.crafting.Recipe<?> recipe,
            RegistryAccess registryAccess, List<String> inputs) {
        try {
            if (recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe cr) {
                for (Ingredient ingredient : cr.getIngredients()) {
                    addRepresentative(ingredient, registryAccess, inputs);
                }
                return;
            }
            if (recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cook) {
                java.util.List<Ingredient> ings = cook.getIngredients();
                addRepresentative(ings.isEmpty() ? Ingredient.EMPTY : ings.get(0), registryAccess, inputs);
                return;
            }
            if (recipe instanceof net.minecraft.world.item.crafting.SmithingRecipe smith) {
                // Smithing hides template/base/addition behind private fields; use
                // @Accessor mixins to read them (compile-time safe, no reflection).
                if (recipe instanceof com.create.productionline.mixin.SmithingTransformRecipeAccessor acc) {
                    addSmithIngredient(acc.createproductionline$getTemplate(), registryAccess, inputs);
                    addSmithIngredient(acc.createproductionline$getBase(), registryAccess, inputs);
                    addSmithIngredient(acc.createproductionline$getAddition(), registryAccess, inputs);
                } else if (recipe instanceof com.create.productionline.mixin.SmithingTrimRecipeAccessor acc) {
                    addSmithIngredient(acc.createproductionline$getTemplate(), registryAccess, inputs);
                    addSmithIngredient(acc.createproductionline$getBase(), registryAccess, inputs);
                    addSmithIngredient(acc.createproductionline$getAddition(), registryAccess, inputs);
                }
                return;
            }
            for (Ingredient ingredient : recipe.getIngredients()) {
                addRepresentative(ingredient, registryAccess, inputs);
            }
        } catch (RuntimeException ignored) {
            // ignore exotic recipe shapes
        }
    }

    /** Picks one representative item id per ingredient (tags are resolved through the registry). */
    private static String representative(Ingredient ingredient, net.minecraft.core.HolderLookup.Provider provider) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) {
            return null;
        }
        return itemId(stacks[0]);
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "minecraft:air" : key.toString();
    }

    private static RecipeDescriptor build(String recipeId, String categoryId,
            List<String> inputs, List<String> outputs) {
        return new RecipeDescriptor(recipeId, categoryId,
                inputs.stream().filter(s -> !s.equals("minecraft:air")).toList(),
                outputs.stream().filter(s -> !s.equals("minecraft:air")).toList());
    }
}
