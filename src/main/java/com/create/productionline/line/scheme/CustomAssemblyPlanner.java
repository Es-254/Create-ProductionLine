package com.create.productionline.line.scheme;

import java.util.List;

import com.create.productionline.line.analyzer.MachineSelector;
import com.create.productionline.line.mapper.RecipeDescriptor;
import com.create.productionline.recipegen.CreateRecipePack;
import com.create.productionline.recipegen.RecipeDeriver;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Turns the hand-authored state of a custom scheme into everything the rest of the
 * mod already consumes: a {@link LineScheme} plan and installable native Create JSON.
 *
 * <p>{@link RecipeDeriver} is deliberately untouched. The bridge is a synthetic
 * {@link RecipeDescriptor} whose <em>category</em> decides which of the deriver's
 * existing rules applies:
 *
 * <ul>
 *   <li>two or more materials → {@link #CATEGORY_ASSEMBLY} (assembly-like) → the
 *       deriver's assembly rule builds a {@code create:sequenced_assembly}: first
 *       material on the belt, one {@code create:deploying} step per later material;</li>
 *   <li>exactly one material → {@link #CATEGORY_SINGLE} (not assembly-like) → the
 *       deriver's single-material rule picks a real machine from the material's
 *       semantics (wood → saw, ore/raw → crushing wheel, organic → millstone,
 *       metal/gem → press, default press) and emits that flat recipe.</li>
 * </ul>
 *
 * <p>Both categories are synthetic on purpose: their recipe id never exists in any
 * datapack, so the deriver's live-JSON fallbacks (yield, shaped order, entity
 * result) stay at their neutral defaults.
 */
public final class CustomAssemblyPlanner {

    /** >= 2 materials: assembly-like, so the deriver builds a sequenced assembly. */
    public static final String CATEGORY_ASSEMBLY = "cpl:custom_assembly";

    /** 1 material: not assembly-like, so the deriver falls back to semantic machines. */
    public static final String CATEGORY_SINGLE = "cpl:custom_single_material";

    private CustomAssemblyPlanner() {
    }

    /** Step 1 result: the target survives, every material and cached payload is dropped. */
    public static LineScheme cleared(String targetItem) {
        LineScheme out = new LineScheme();
        out.setOutputItem(targetItem == null ? "" : targetItem);
        out.setRecipeId(syntheticRecipeId(targetItem));
        out.setBaseMaterial("");
        return out;
    }

    /**
     * Rebuilds the plan (and the embedded native payload) from the material list.
     * Called after every hammer strike and again when the scheme is locked, so the
     * item never carries a plan that disagrees with the material list.
     */
    public static LineScheme rebuild(CustomAssembly custom) {
        LineScheme out = cleared(custom.targetItem());
        // The player's numbers survive the whole clear/hammer/lock cycle: `outputCount`
        // is what the compute step asked for, `repeatCount` how often the line must run.
        out.setTargetOutputCount(custom.outputCount());
        // Path 2 (anvil): the embedded recipe is written with `count = outputCount`, so one
        // pass already produces N — repeating it would double-count. The inherited
        // computer-side budget stays in the component for reference only.
        out.setRepeatCount(1);
        List<String> materials = custom.materials();
        if (materials.isEmpty()) {
            return out;
        }
        out.setBaseMaterial(materials.get(0));
        LineScheme.CreateRecipeEntry entry = nativeEntry(custom);
        if (entry != null) {
            out.addCreateRecipe(entry.getFileName(), entry.getJson());
            MachineSelector.appendChainSteps(out, materials, entry, custom.targetItem());
        }
        return out;
    }

    /** The compatible interface: a descriptor the existing deriver already understands. */
    public static RecipeDescriptor toDescriptor(CustomAssembly custom) {
        String category = custom.singleMaterialFallback() ? CATEGORY_SINGLE : CATEGORY_ASSEMBLY;
        return new RecipeDescriptor(syntheticRecipeId(custom.targetItem()), category,
                custom.materials(), List.of(custom.targetItem()), custom.outputCount());
    }

    /**
     * Server-side derivation for a custom scheme — the same call the computer and
     * the scheme loader already make, so the anti-injection property is unchanged:
     * a forged component can only ever yield a recipe for the materials it names.
     */
    public static RecipeDeriver.Derived derive(ServerLevel level, CustomAssembly custom) {
        if (custom == null || custom.targetItem().isBlank() || custom.materials().isEmpty()) {
            return new RecipeDeriver.Derived(List.of(), 1, List.of());
        }
        return RecipeDeriver.derive(level, toDescriptor(custom));
    }

    /**
     * Deterministic shortcut used where no {@link ServerLevel} is at hand (the anvil).
     * {@link CreateRecipePack#sequenceEntry} returns {@code null} below two materials,
     * which is exactly the case the single-material fallback covers — there the
     * semantic route above is the only honest answer.
     */
    public static LineScheme.CreateRecipeEntry nativeEntry(CustomAssembly custom) {
        if (custom == null || !custom.isSequenced() || custom.targetItem().isBlank()) {
            return null;
        }
        return CreateRecipePack.sequenceEntry(custom.materials(), custom.targetItem(),
                RecipeDeriver.INTERMEDIATE_ID, custom.outputCount());
    }

    /** True when the item carries a custom scheme that has been locked. */
    public static boolean isLockedCustom(ItemStack stack) {
        CustomAssembly custom = stack == null || stack.isEmpty()
                ? null
                : stack.get(com.create.productionline.registry.ModDataComponents.CUSTOM_ASSEMBLY.get());
        return custom != null && custom.locked();
    }

    private static String syntheticRecipeId(String targetItem) {
        return "cpl:custom_" + (targetItem == null || targetItem.isBlank()
                ? "unknown"
                : targetItem.replace(':', '_').replace('/', '_'));
    }
}
