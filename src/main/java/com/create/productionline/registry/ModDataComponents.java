package com.create.productionline.registry;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.line.scheme.CustomAssembly;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registered data components.
 *
 * <p>Player-authored scheme state lives here rather than in the scheme's legacy
 * {@code custom_data} blob: a registered component has a codec (persistence) and a
 * stream codec (server → client sync), which is what keeps the hand-built material
 * list authoritative on the server.
 */
public final class ModDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProductionLineMod.MODID);

    /** Ordered hand-built material list, lock flag and the single-material fallback flag. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomAssembly>> CUSTOM_ASSEMBLY =
            DATA_COMPONENTS.registerComponentType("custom_assembly", builder -> builder
                    .persistent(CustomAssembly.CODEC)
                    .networkSynchronized(CustomAssembly.STREAM_CODEC));

    private ModDataComponents() {
    }
}
