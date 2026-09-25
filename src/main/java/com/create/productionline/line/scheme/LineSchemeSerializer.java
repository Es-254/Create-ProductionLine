package com.create.productionline.line.scheme;

import com.create.productionline.ProductionLineMod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Serializes {@link LineScheme} to and from NBT.
 *
 * <p>All reads use safe accessors so a missing or corrupt field degrades to an
 * empty/partial scheme instead of an exception. Old-version schemes are migrated
 * to the current schema version on load.
 */
public final class LineSchemeSerializer {

    private static final String KEY_VERSION = "Version";
    private static final String KEY_RECIPE_ID = "RecipeId";
    private static final String KEY_OUTPUT_ITEM = "OutputItem";
    private static final String KEY_BASE_MATERIAL = "BaseMaterial";
    private static final String KEY_STEPS = "Steps";

    private static final String KEY_ORDER = "Order";
    private static final String KEY_FACILITY_TYPE = "FacilityType";
    private static final String KEY_COUNT = "Count";
    private static final String KEY_INPUTS = "Inputs";
    private static final String KEY_OUTPUTS = "Outputs";

    private static final String KEY_CREATE_RECIPES = "CreateRecipes";
    private static final String KEY_TOOL_MATERIALS = "ToolMaterials";
    private static final String CR_NAME = "Name";
    private static final String CR_JSON = "Json";
    private static final String KEY_TARGET_OUTPUT_COUNT = "TargetOutputCount";
    private static final String KEY_REPEAT_COUNT = "RepeatCount";

    private LineSchemeSerializer() {
    }

    /**
     * Serializes a scheme into a {@link CompoundTag} (root of the scheme block).
     */
    public static CompoundTag save(LineScheme scheme) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_VERSION, LineScheme.CURRENT_VERSION);
        tag.putString(KEY_RECIPE_ID, scheme.getRecipeId());
        tag.putString(KEY_OUTPUT_ITEM, scheme.getOutputItem());
        tag.putString(KEY_BASE_MATERIAL, scheme.getBaseMaterial());
        // V2 fields: the player's target output and how often the line must run for it.
        tag.putInt(KEY_TARGET_OUTPUT_COUNT, scheme.getTargetOutputCount());
        tag.putInt(KEY_REPEAT_COUNT, scheme.getRepeatCount());

        ListTag stepsTag = new ListTag();
        for (LineScheme.Step step : scheme.getSteps()) {
            CompoundTag stepTag = new CompoundTag();
            stepTag.putInt(KEY_ORDER, step.getOrder());
            stepTag.putString(KEY_FACILITY_TYPE, step.getFacilityType());
            stepTag.putInt(KEY_COUNT, step.getCount());

            ListTag inputs = new ListTag();
            for (String input : step.getInputs()) {
                inputs.add(net.minecraft.nbt.StringTag.valueOf(input));
            }
            stepTag.put(KEY_INPUTS, inputs);

            ListTag outputs = new ListTag();
            for (String output : step.getOutputs()) {
                outputs.add(net.minecraft.nbt.StringTag.valueOf(output));
            }
            stepTag.put(KEY_OUTPUTS, outputs);

            stepsTag.add(stepTag);
        }
        tag.put(KEY_STEPS, stepsTag);

        ListTag recipesTag = new ListTag();
        for (LineScheme.CreateRecipeEntry entry : scheme.getCreateRecipes()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(CR_NAME, entry.getFileName());
            entryTag.putString(CR_JSON, entry.getJson());
            recipesTag.add(entryTag);
        }
        tag.put(KEY_CREATE_RECIPES, recipesTag);
        // Materials the line uses rather than consumes (a smithing recipe's base equipment).
        // Absent in older schemes, which simply means "nothing is a tool".
        ListTag toolsTag = new ListTag();
        for (String material : scheme.getToolMaterials()) {
            toolsTag.add(net.minecraft.nbt.StringTag.valueOf(material));
        }
        if (!toolsTag.isEmpty()) {
            tag.put(KEY_TOOL_MATERIALS, toolsTag);
        }
        return tag;
    }

    /**
     * Reads a scheme from its NBT block. Migrates old versions; never throws.
     */
    public static LineScheme load(CompoundTag tag) {
        LineScheme scheme = new LineScheme();
        if (tag == null || tag.isEmpty()) {
            return scheme;
        }
        scheme.setVersion(tag.getInt(KEY_VERSION)); // defaults to 0 when missing

        scheme.setRecipeId(tag.getString(KEY_RECIPE_ID));
        scheme.setOutputItem(tag.getString(KEY_OUTPUT_ITEM));
        scheme.setBaseMaterial(tag.getString(KEY_BASE_MATERIAL));
        // V1 items simply have neither key: one pass, one output — the defaults.
        scheme.setTargetOutputCount(tag.contains(KEY_TARGET_OUTPUT_COUNT) ? tag.getInt(KEY_TARGET_OUTPUT_COUNT) : 1);
        scheme.setRepeatCount(tag.contains(KEY_REPEAT_COUNT) ? tag.getInt(KEY_REPEAT_COUNT) : 1);

        if (tag.contains(KEY_STEPS, Tag.TAG_LIST)) {
            ListTag stepsTag = tag.getList(KEY_STEPS, Tag.TAG_COMPOUND);
            for (int i = 0; i < stepsTag.size(); i++) {
                CompoundTag stepTag = stepsTag.getCompound(i);
                LineScheme.Step step = new LineScheme.Step();
                step.setOrder(stepTag.getInt(KEY_ORDER));
                step.setFacilityType(stepTag.getString(KEY_FACILITY_TYPE));
                step.setCount(stepTag.getInt(KEY_COUNT));

                if (stepTag.contains(KEY_INPUTS, Tag.TAG_LIST)) {
                    ListTag inputs = stepTag.getList(KEY_INPUTS, Tag.TAG_STRING);
                    for (int j = 0; j < inputs.size(); j++) {
                        step.addInput(inputs.getString(j));
                    }
                }
                if (stepTag.contains(KEY_OUTPUTS, Tag.TAG_LIST)) {
                    ListTag outputs = stepTag.getList(KEY_OUTPUTS, Tag.TAG_STRING);
                    for (int j = 0; j < outputs.size(); j++) {
                        step.addOutput(outputs.getString(j));
                    }
                }
                scheme.stepsMutable().add(step);
            }
        }
        if (tag.contains(KEY_CREATE_RECIPES, Tag.TAG_LIST)) {
            ListTag recipesTag = tag.getList(KEY_CREATE_RECIPES, Tag.TAG_COMPOUND);
            for (int i = 0; i < recipesTag.size(); i++) {
                CompoundTag entryTag = recipesTag.getCompound(i);
                String name = entryTag.getString(CR_NAME);
                String json = entryTag.getString(CR_JSON);
                if (!name.isBlank() && !json.isBlank()) {
                    scheme.addCreateRecipe(name, json);
                }
            }
        }
        if (tag.contains(KEY_TOOL_MATERIALS, Tag.TAG_LIST)) {
            ListTag toolsTag = tag.getList(KEY_TOOL_MATERIALS, Tag.TAG_STRING);
            for (int i = 0; i < toolsTag.size(); i++) {
                scheme.addToolMaterial(toolsTag.getString(i));
            }
        }
        migrate(scheme);
        return scheme;
    }

    /**
     * Reads a scheme stored on an item stack under {@link LineScheme#SCHEME_TAG_KEY}.
     * Never returns null.
     */
    public static LineScheme fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new LineScheme();
        }
        net.minecraft.world.item.component.CustomData data = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (!tag.contains(LineScheme.SCHEME_TAG_KEY, Tag.TAG_COMPOUND)) {
            return new LineScheme();
        }
        return load(tag.getCompound(LineScheme.SCHEME_TAG_KEY));
    }

    /** Stores a scheme on an item stack (replacing any previous one). */
    public static void saveToStack(ItemStack stack, LineScheme scheme) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        net.minecraft.world.item.component.CustomData data = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        tag.put(LineScheme.SCHEME_TAG_KEY, save(scheme));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
    }

    /**
     * Version migration. Currently only normalises legacy data that predates the
     * {@code Version} field (V1 data loaded with version 0).
     */
    private static void migrate(LineScheme scheme) {
        if (scheme.getVersion() == 0) {
            // Legacy schemes written before Version was tracked are structurally V1.
            scheme.setVersion(1);
        }
        if (scheme.getVersion() < 2) {
            // V1 has no target/repeat fields; the constructor defaults (1 / 1) are
            // already in place, so the upgrade only records the new version.
            scheme.setVersion(2);
        }
        if (scheme.getVersion() > LineScheme.CURRENT_VERSION) {
            ProductionLineMod.LOGGER.warn("Line scheme version {} is newer than supported {}; loading best-effort.",
                    scheme.getVersion(), LineScheme.CURRENT_VERSION);
            scheme.setVersion(LineScheme.CURRENT_VERSION);
        }
        // Re-index Order fields defensively.
        for (int i = 0; i < scheme.stepsMutable().size(); i++) {
            scheme.stepsMutable().get(i).setOrder(i);
        }
    }
}
