package com.create.productionline.item;

import java.util.List;

import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.util.Names;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 产线方案镜像 — a READ-ONLY, text-only copy of a plan.
 *
 * <p>Security: it never carries a parseable {@code LineScheme} (no Steps /
 * embedded Create recipes). Only a display snapshot under its own
 * {@value #KEY} is stored — it cannot be activated by a Scheme Loader nor fed
 * back into the Dismantler (those require a genuine Line Scheme item).
 */
public class LineSchemeMirrorItem extends Item {

    /** Custom-data key for the display snapshot. */
    public static final String KEY = "LineSchemeMirror";
    private static final String N_OUTPUT = "OutputItem";
    private static final String N_BASE = "BaseMaterial";
    private static final String N_STEPS = "Steps";
    private static final String N_FACILITY = "Facility";
    private static final String N_INPUTS = "Inputs";
    private static final String N_OUTPUTS = "Outputs";

    public LineSchemeMirrorItem(Properties properties) {
        super(properties);
    }

    /** Writes the display snapshot (text-only, no executable scheme data). */
    public static void write(ItemStack stack, LineScheme scheme) {
        if (stack == null || stack.isEmpty() || scheme == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(N_OUTPUT, scheme.getOutputItem());
        if (!scheme.getBaseMaterial().isBlank()) {
            tag.putString(N_BASE, scheme.getBaseMaterial());
        }
        ListTag steps = new ListTag();
        for (LineScheme.Step step : scheme.getSteps()) {
            CompoundTag s = new CompoundTag();
            s.putString(N_FACILITY, step.getFacilityType());
            ListTag ins = new ListTag();
            for (String in : step.getInputs()) {
                ins.add(StringTag.valueOf(in));
            }
            s.put(N_INPUTS, ins);
            ListTag outs = new ListTag();
            for (String out : step.getOutputs()) {
                outs.add(StringTag.valueOf(out));
            }
            s.put(N_OUTPUTS, outs);
            steps.add(s);
        }
        tag.put(N_STEPS, steps);

        net.minecraft.world.item.component.CustomData data = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag custom = data.copyTag();
        custom.put(KEY, tag);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(custom));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_productionline.line_scheme_mirror.note")
                .withStyle(ChatFormatting.RED));
        net.minecraft.world.item.component.CustomData data = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag tag = data.copyTag().getCompound(KEY);
        if (!tag.contains(N_OUTPUT)) {
            tooltip.add(Component.translatable("item.create_productionline.line_scheme.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.create_productionline.line_scheme.output",
                Names.nameOfItem(tag.getString(N_OUTPUT))).withStyle(ChatFormatting.GREEN));
        java.util.List<com.create.productionline.util.SchemeTopology.Station> stations =
                new java.util.ArrayList<>();
        if (tag.contains(N_STEPS, Tag.TAG_LIST)) {
            ListTag steps = tag.getList(N_STEPS, Tag.TAG_COMPOUND);
            for (int k = 0; k < steps.size(); k++) {
                CompoundTag s = steps.getCompound(k);
                java.util.List<String> ins = new java.util.ArrayList<>();
                ListTag insTag = s.getList(N_INPUTS, Tag.TAG_STRING);
                for (int j = 0; j < insTag.size(); j++) {
                    ins.add(insTag.getString(j));
                }
                stations.add(new com.create.productionline.util.SchemeTopology.Station(
                        s.getString(N_FACILITY), ins));
            }
        }
        for (String line : com.create.productionline.util.SchemeTopology.lines(
                tag.getString(N_BASE), stations, tag.getString(N_OUTPUT))) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
    }
}
