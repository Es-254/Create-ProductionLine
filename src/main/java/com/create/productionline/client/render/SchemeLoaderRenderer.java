package com.create.productionline.client.render;

import com.create.productionline.block.entity.SchemeLoaderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Draws the Scheme Loader's front bar with the vanilla block entity renderer.
 *
 * <p>This is the only path that shows the bar while Flywheel is not visualizing
 * the level — most importantly inside Ponder, whose scene renderer dispatches
 * block entity renderers but does not run Flywheel visuals, and the tutorial
 * chapter for this machine shows the bar filling up.
 */
public class SchemeLoaderRenderer implements BlockEntityRenderer<SchemeLoaderBlockEntity> {

    public SchemeLoaderRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SchemeLoaderBlockEntity loader, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (LoaderBar.visualized(loader.getLevel())) {
            return; // Flywheel draws the bar for this block entity (SchemeLoaderVisual)
        }
        // The count eases towards the target: a strip grows in from its bottom edge instead of
        // popping, which is what the renderer-driven bar bought us over the block model.
        LoaderBar.render(loader.advanceBarAnimation(), loader.getBlockState(), poseStack, buffers, packedLight,
                packedOverlay);
    }
}
