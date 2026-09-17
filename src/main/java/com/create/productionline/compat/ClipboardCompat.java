package com.create.productionline.compat;

import java.util.List;

import com.create.productionline.line.mapper.RecipeMapper;
import com.create.productionline.line.scheme.LineScheme;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Injects the "build this line" guide into a clipboard-like item, following the
 * NBT contract of SRS 3.2:
 *
 * <pre>
 * custom_data: {
 *   LineBuildGuide: { "TotalSteps": 3,
 *                     "Step_1": "Place Mechanical Saw on Belt (Y+2)", ... }
 * }
 * </pre>
 *
 * <p>Only vanilla NBT is used, so no Create/other classes are required.
 */
public final class ClipboardCompat {

    public static final String CUSTOM_DATA_KEY = "custom_data";
    public static final String GUIDE_KEY = "LineBuildGuide";

    private ClipboardCompat() {
    }

    /** Known paper-like item ids that can carry plans and build guides. */
    private static boolean hasKnownPaperId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = key == null ? "minecraft:air" : key.toString();
        return id.equals("create:clipboard") || id.equals("minecraft:paper")
                || id.equals("create:schematic_and_quill") || id.equals("create:schematic");
    }

    /** True when the stack can carry the build guide (paper-like or Create's clipboard). */
    public static boolean isClipboardLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (hasKnownPaperId(stack)) {
            return true;
        }
        return customData(stack).contains(GUIDE_KEY);
    }

    /**
     * True when the stack can act as a "carrier": a blank Line Scheme item, a
     * paper-like item, or any stack that already carries plan data. The computer
     * writes the computed plan onto whichever carriers it finds in its two carrier
     * slots (middle = Line Scheme / paper / clipboard, right = clipboard-like).
     * Mirrors are explicitly excluded — they are read-only.
     */
    public static boolean isCarrier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof com.create.productionline.item.LineSchemeMirrorItem) {
            return false; // mirrors are read-only — never loadable/activatable
        }
        if (stack.getItem() instanceof com.create.productionline.item.LineSchemeItem) {
            return true;
        }
        if (hasKnownPaperId(stack)) {
            return true;
        }
        CompoundTag custom = customData(stack);
        return custom.contains(com.create.productionline.line.scheme.LineScheme.SCHEME_TAG_KEY)
                || custom.contains(GUIDE_KEY);
    }

    /**
     * The ONLY item the Scheme Loader accepts: a genuine, ALREADY WRITTEN Line Scheme
     * ({@code recipeId} present). Blank schemes, paper, clipboard, mirrors and any forged-NBT
     * carrier are rejected — the loader re-derives everything from the scheme's recipe id.
     */
    public static boolean isLoaderCarrier(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof com.create.productionline.item.LineSchemeItem)) {
            return false;
        }
        return !com.create.productionline.line.scheme.LineSchemeSerializer.fromStack(stack).isEmpty();
    }

    private static CompoundTag customData(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        return data.copyTag();
    }

    /** Writes the scheme's build guide onto the stack (SRS 3.2). */
    public static void writeGuide(ItemStack stack, LineScheme scheme) {
        if (stack == null || stack.isEmpty() || scheme == null) {
            return;
        }
        List<String> lines = RecipeMapper.renderBuildGuide(scheme);
        CompoundTag guide = new CompoundTag();
        guide.putInt("TotalSteps", lines.size());
        int i = 1;
        for (String line : lines) {
            guide.putString("Step_" + i, line);
            i++;
        }
        CompoundTag custom = customData(stack);
        custom.put(GUIDE_KEY, guide);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(custom));
    }
}
