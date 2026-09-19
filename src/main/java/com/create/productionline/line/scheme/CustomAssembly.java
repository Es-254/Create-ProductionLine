package com.create.productionline.line.scheme;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The hand-authored state of a {@link LineScheme} written through the OP anvil flow.
 *
 * <p>Stored as a registered DATA COMPONENT, never as ad-hoc NBT: {@link #CODEC} is
 * the persistent/JSON form and {@link #STREAM_CODEC} is what carries the value to
 * clients, so the server decides what a scheme contains and a client cannot invent
 * a material list.
 *
 * <p>{@code materials} is ordered and deliberately allows repeats: two of the same
 * material means two deploy steps. The first entry is the base that goes onto the
 * belt, every following entry becomes one {@code create:deploying} step.
 *
 * <p>{@code locked} freezes the scheme — the anvil refuses every later operation on
 * it. {@code singleMaterialFallback} records that the scheme was locked with only
 * one material, which an assembly line cannot express; derivation then falls back
 * to the single-material semantic machine. It is stored explicitly rather than
 * inferred from {@code materials.size() == 1} so that older items keep their
 * meaning even if that rule ever changes.
 */
public record CustomAssembly(List<String> materials, boolean locked, boolean singleMaterialFallback,
        String targetItem, int outputCount) {

    public static final CustomAssembly EMPTY = new CustomAssembly(List.of(), false, false, "", 1);

    public static final Codec<CustomAssembly> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().fieldOf("materials").forGetter(CustomAssembly::materials),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(CustomAssembly::locked),
            Codec.BOOL.optionalFieldOf("single_material_fallback", false)
                    .forGetter(CustomAssembly::singleMaterialFallback),
            Codec.STRING.optionalFieldOf("target", "").forGetter(CustomAssembly::targetItem),
            Codec.INT.optionalFieldOf("count", 1).forGetter(CustomAssembly::outputCount))
            .apply(inst, CustomAssembly::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CustomAssembly> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), CustomAssembly::materials,
            ByteBufCodecs.BOOL, CustomAssembly::locked,
            ByteBufCodecs.BOOL, CustomAssembly::singleMaterialFallback,
            ByteBufCodecs.STRING_UTF8, CustomAssembly::targetItem,
            ByteBufCodecs.VAR_INT, CustomAssembly::outputCount,
            CustomAssembly::new);

    public CustomAssembly {
        materials = materials == null ? List.of() : List.copyOf(materials);
        targetItem = targetItem == null ? "" : targetItem;
        outputCount = Math.max(1, outputCount);
    }

    /** Step 1 done, nothing hammered yet. */
    public boolean isCleared() {
        return !locked && materials.isEmpty();
    }

    /** Base plus at least one material: a real sequenced assembly. */
    public boolean isSequenced() {
        return materials.size() >= 2;
    }

    /** Exactly the case the anvil cannot express as an assembly line. */
    public boolean isSingleMaterial() {
        return materials.size() == 1;
    }

    public boolean isReadyToLock() {
        return !locked && !materials.isEmpty();
    }

    public CustomAssembly withMaterial(String itemId) {
        List<String> next = new ArrayList<>(materials);
        next.add(itemId);
        return new CustomAssembly(next, false, false, targetItem, outputCount);
    }

    /** Locking is what decides the fallback flag: one material cannot be a sequence. */
    public CustomAssembly withLocked(boolean value) {
        return new CustomAssembly(materials, value, value && isSingleMaterial(), targetItem, outputCount);
    }

    public CustomAssembly withTarget(String itemId, int count) {
        return new CustomAssembly(materials, locked, singleMaterialFallback, itemId, count);
    }
}
