package com.create.productionline.registry;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.item.LineSchemeItem;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Standalone items of this mod: the Line Scheme (产线方案, stores the computed
 * pipeline), the Line Scheme Mirror (产线方案镜像, a read-only copy) and the
 * Generic Intermediate (通用中间产物, the transitional in-line item).
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProductionLineMod.MODID);

    public static final DeferredItem<LineSchemeItem> LINE_SCHEME =
            ITEMS.register("line_scheme", () -> new LineSchemeItem(new Item.Properties().stacksTo(1)));

    /** 通用中间产物 — the opaque transitional item used in generated sequenced-assembly recipes. */
    public static final DeferredItem<com.create.productionline.item.GenericIntermediateItem> GENERIC_INTERMEDIATE =
            ITEMS.register("generic_intermediate",
                    () -> new com.create.productionline.item.GenericIntermediateItem(new Item.Properties()));

    /** 产线方案镜像 — a read-only copy of a plan (cannot be loaded/activated). */
    public static final DeferredItem<com.create.productionline.item.LineSchemeMirrorItem> LINE_SCHEME_MIRROR =
            ITEMS.register("line_scheme_mirror",
                    () -> new com.create.productionline.item.LineSchemeMirrorItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
