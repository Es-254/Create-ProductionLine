package com.create.productionline.client.render;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.block.entity.SchemeLoaderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Scheme Loader's front bar: the six strips of the 1.0.3 model, and how many
 * of them are lit for a given loaded-scheme count.
 *
 * <p>The bar used to be baked into the block model — the block state carried the
 * count ({@code fill}, 0 … 16), the blockstate file mapped it onto six stage
 * models, and every change re-meshed the chunk section and sent a block update.
 * The strips are drawn by a renderer instead now: {@link SchemeLoaderVisual}
 * instances them through Flywheel, and {@link SchemeLoaderRenderer} draws them
 * with the vanilla block entity renderer wherever Flywheel is not visualizing
 * the level — which is also the path Ponder uses, so the tutorial scene keeps
 * showing the bar.
 *
 * <p>Both paths read the same six strip models. They are not hand-drawn: each one
 * is the difference between two of the stage models that were already in the mod
 * ({@code scheme_loader_bar_1} … {@code scheme_loader} minus
 * {@code scheme_loader_empty}), extracted by {@code tools/loader-bar-strips.js},
 * so the art stays where the author put it.
 *
 * <p>The strip models are referenced by nothing but this renderer, and Minecraft
 * only bakes models that something refers to, so they are registered for baking
 * explicitly ({@link #registerAdditional}).
 */
public final class LoaderBar {

    private static final Logger LOGGER = LoggerFactory.getLogger("create_productionline/loader-bar");

    private static final PartialModel[] MODELS = new PartialModel[SchemeLoaderBlockEntity.BAR_SEGMENTS];
    private static final ResourceLocation[] LOCATIONS = new ResourceLocation[SchemeLoaderBlockEntity.BAR_SEGMENTS];

    static {
        for (int strip = 0; strip < LOCATIONS.length; strip++) {
            LOCATIONS[strip] = ResourceLocation.fromNamespaceAndPath(ProductionLineMod.MODID,
                    "block/scheme_loader_strip_" + (strip + 1));
            MODELS[strip] = PartialModel.of(LOCATIONS[strip]);
        }
    }

    /** Set once the strip models have been found in the baked model set. */
    private static Boolean available;

    private LoaderBar() {
    }

    /** Registers the strip models for baking (they are referenced by no blockstate). */
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation location : LOCATIONS) {
            event.register(ModelResourceLocation.standalone(location));
        }
    }

    /**
     * True when the strip models are baked and can be rendered.
     *
     * <p>Resolved on first use — a world can be entered before the models are
     * there, and a missing model must not take the client down: the bar is then
     * simply not drawn, once, with a line in the log saying why.
     */
    public static boolean available() {
        if (available == null) {
            try {
                for (PartialModel model : MODELS) {
                    model.get();
                }
                available = Boolean.TRUE;
            } catch (RuntimeException e) {
                available = Boolean.FALSE;
                LOGGER.error("The Scheme Loader bar models could not be baked — the front bar stays dark. "
                        + "Check that assets/create_productionline/models/block/scheme_loader_strip_*.json exist.", e);
            }
        }
        return available;
    }

    /** The strip model, 1-based like the model files ({@code scheme_loader_strip_1} …). */
    public static PartialModel model(int strip) {
        return MODELS[strip];
    }

    /**
     * Draws the lit strips with the vanilla block entity renderer.
     *
     * <p>{@code segments} is 0 … 6 and comes from
     * {@link SchemeLoaderBlockEntity#getRenderSegments()}.
     */
    public static void render(int segments, BlockState state, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        if (segments <= 0 || !available()) {
            return;
        }
        // Same render layer the block's own model is baked into, so the bar keeps the look
        // it had when it was part of that model (no render layer is registered for it,
        // i.e. solid).
        RenderType type = ItemBlockRenderTypes.getChunkRenderType(state);
        VertexConsumer consumer = buffers.getBuffer(type);
        for (int strip = 0; strip < segments; strip++) {
            SuperByteBuffer buffer = CachedBuffers.partial(MODELS[strip], state);
            buffer.light(packedLight).renderInto(poseStack, consumer);
        }
    }

    /**
     * True when Flywheel is visualizing the level the block entity lives in, i.e.
     * when {@link SchemeLoaderVisual} draws the bar and the vanilla block entity
     * renderer has to keep its hands off it. False in Ponder, which renders block
     * entities but does not run Flywheel visuals.
     */
    public static boolean visualized(net.minecraft.world.level.LevelAccessor level) {
        return level != null && dev.engine_room.flywheel.api.visualization.VisualizationManager
                .supportsVisualization(level);
    }

    /** Number of strips the whole bar has. */
    public static int stripCount() {
        return MODELS.length;
    }
}
