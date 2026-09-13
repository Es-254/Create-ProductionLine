package com.create.productionline.registry;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.block.entity.ProductionComputerBlockEntity;
import com.create.productionline.block.entity.SchemeLoaderBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block entity types: the production computer, the scheme loader cabinet and the dismantler.
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProductionLineMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProductionComputerBlockEntity>> PRODUCTION_COMPUTER =
            BLOCK_ENTITY_TYPES.register("production_computer",
                    () -> BlockEntityType.Builder.of(ProductionComputerBlockEntity::new,
                            ModBlocks.PRODUCTION_COMPUTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SchemeLoaderBlockEntity>> SCHEME_LOADER =
            BLOCK_ENTITY_TYPES.register("scheme_loader",
                    () -> BlockEntityType.Builder.of(SchemeLoaderBlockEntity::new,
                            ModBlocks.SCHEME_LOADER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.create.productionline.block.entity.DismantlerBlockEntity>> DISMANTLER =
            BLOCK_ENTITY_TYPES.register("dismantler",
                    () -> BlockEntityType.Builder.of(com.create.productionline.block.entity.DismantlerBlockEntity::new,
                            ModBlocks.DISMANTLER.get()).build(null));

    private ModBlockEntities() {
    }
}
