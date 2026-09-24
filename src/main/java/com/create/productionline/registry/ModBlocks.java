package com.create.productionline.registry;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.block.ProductionComputerBlock;
import com.create.productionline.block.SchemeLoaderBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the blocks of this mod: {@code production_computer} (产线计算机),
 * {@code scheme_loader} (方案加载柜 — activates the pipeline recipes stored on a
 * line scheme) and {@code dismantler} (破拆机 — reverts intermediates/products
 * into raw materials).
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProductionLineMod.MODID);
    public static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(ProductionLineMod.MODID);

    public static final DeferredBlock<ProductionComputerBlock> PRODUCTION_COMPUTER =
            BLOCKS.register("production_computer",
                    () -> new ProductionComputerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F, 6.0F)
                            .sound(SoundType.METAL)
                            // The 1.0.3 model is an open-sided chassis (base plate, top plate, four
                            // corner pillars, front panel) rather than a full cube: without this the
                            // neighbouring blocks would cull their faces against it and its own
                            // boundary faces would vanish into them.
                            .noOcclusion()
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<SchemeLoaderBlock> SCHEME_LOADER =
            BLOCKS.register("scheme_loader",
                    () -> new SchemeLoaderBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(4.0F, 8.0F)
                            .sound(SoundType.METAL)
                            // Same open-sided chassis as the computer: base plate, top
                            // plate, four corner pillars and a front panel, so the
                            // neighbours must not cull their faces against it.
                            .noOcclusion()
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<com.create.productionline.block.DismantlerBlock> DISMANTLER =
            BLOCKS.register("dismantler",
                    () -> new com.create.productionline.block.DismantlerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(4.0F, 8.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

    static {
        // BlockItems share the id of their block.
        BLOCK_ITEMS.registerSimpleBlockItem("production_computer", PRODUCTION_COMPUTER);
        BLOCK_ITEMS.registerSimpleBlockItem("scheme_loader", SCHEME_LOADER);
        BLOCK_ITEMS.registerSimpleBlockItem("dismantler", DISMANTLER);
    }

    private ModBlocks() {
    }
}
