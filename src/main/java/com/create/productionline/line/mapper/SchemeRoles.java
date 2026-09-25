package com.create.productionline.line.mapper;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.line.scheme.LineScheme;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;

/**
 * The roles a source recipe gives its own materials, recorded onto the plan.
 *
 * <p>A plan's steps only say <em>which</em> material a station handles, never whether that
 * material is consumed: a Deployer holding an item applies it in USE mode, and Create decides
 * per item whether that is a consumption. One case is decided by the source recipe rather than
 * by Create's heuristics — a <b>smithing</b> recipe's {@code base} is the equipment being
 * upgraded (a diamond sword on its way to netherite), i.e. exactly the item a Deployer uses and
 * keeps, so it must not be listed as a material the player has to keep feeding.
 *
 * <p>This runs where the plan is built (the computer), from the <em>server's</em> live recipe
 * manager, and stores the result on the scheme so every reader — tooltip, mirror, anvil copy —
 * sees the same answer without needing recipe access of its own.
 */
public final class SchemeRoles {

    private SchemeRoles() {
    }

    /**
     * Marks every material of {@code scheme} that its source recipe treats as equipment to be
     * used rather than consumed. No-op for recipes we cannot inspect (no id, not loaded, not a
     * smithing recipe) — the plan is then simply all-consumed, as before.
     */
    public static void markToolMaterials(MinecraftServer server, LineScheme scheme) {
        if (server == null || scheme == null || scheme.getRecipeId() == null || scheme.getRecipeId().isBlank()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(scheme.getRecipeId());
        if (id == null) {
            return;
        }
        RecipeHolder<?> holder = server.getRecipeManager().byKey(id).orElse(null);
        if (holder == null || !(holder.value() instanceof SmithingRecipe smithing)) {
            return;
        }
        for (String material : materialsOf(scheme)) {
            if (material.startsWith("#")) {
                continue; // a tag cannot be tested against an Ingredient without a member
            }
            ResourceLocation key = ResourceLocation.tryParse(material);
            if (key == null) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(key);
            // 1.21.1 exposes the roles as predicates, not as Ingredients.
            if (item != null && smithing.isBaseIngredient(new ItemStack(item))) {
                scheme.addToolMaterial(material);
            }
        }
    }

    /** Every material the plan mentions: the belt head plus each station's inputs. */
    private static List<String> materialsOf(LineScheme scheme) {
        List<String> out = new ArrayList<>();
        if (scheme.getBaseMaterial() != null && !scheme.getBaseMaterial().isBlank()) {
            out.add(scheme.getBaseMaterial());
        }
        for (LineScheme.Step step : scheme.getSteps()) {
            for (String input : step.getInputs()) {
                if (input != null && !input.isBlank() && !out.contains(input)) {
                    out.add(input);
                }
            }
        }
        return out;
    }
}
