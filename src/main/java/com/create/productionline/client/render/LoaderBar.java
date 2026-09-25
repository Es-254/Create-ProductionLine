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
     * Says once, at startup, whether the six strip models really made it into the baked model
     * set. Without this line a bar that stays dark is a guessing game: the models being absent
     * and the count never arriving look exactly the same in game.
     */
    public static void logBaked(ModelEvent.BakingCompleted event) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (ResourceLocation location : LOCATIONS) {
            if (!event.getModels().containsKey(ModelResourceLocation.standalone(location))) {
                missing.add(location.toString());
            }
        }
        if (missing.isEmpty()) {
            LOGGER.info("Scheme Loader bar: all {} strip models baked", LOCATIONS.length);
        } else {
            LOGGER.error("Scheme Loader bar: {} of {} strip models were NOT baked ({} ) — the front bar "
                    + "cannot be drawn", missing.size(), LOCATIONS.length, String.join(", ", missing));
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
     * <p>{@code segments} is a fractional 0 … 6 count from
     * {@link SchemeLoaderBlockEntity#advanceBarAnimation()}: the last strip may be part way
     * through growing in, and is drawn that far grown from its bottom edge.
     */
    public static void render(float segments, BlockState state, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        // Availability first, count second: a bar that stays dark because the models are missing
        // and a bar that stays dark because the count never arrived looked identical in the log
        // once already, and that is exactly what the next line rules out.
        if (!available()) {
            return;
        }
        logFirstDraw("vanilla renderer", segments);
        if (segments <= 0.01F) {
            return;
        }
        // Same render layer the block's own model is baked into, so the bar keeps the look
        // it had when it was part of that model (no render layer is registered for it,
        // i.e. solid).
        RenderType type = ItemBlockRenderTypes.getChunkRenderType(state);
        VertexConsumer consumer = buffers.getBuffer(type);
        int whole = (int) Math.floor(segments);
        float partial = segments - whole;
        for (int strip = 0; strip < stripCount() && strip <= whole; strip++) {
            draw(strip, state, poseStack, consumer, packedLight, strip < whole ? 1.0F : partial);
        }
    }

    /** Draws one strip, grown to {@code grown} (0 … 1) of its height from the bottom edge. */
    private static void draw(int strip, BlockState state, PoseStack poseStack, VertexConsumer consumer,
            int packedLight, float grown) {
        if (grown <= 0.01F) {
            return;
        }
        SuperByteBuffer buffer = CachedBuffers.partial(MODELS[strip], state);
        buffer.light(packedLight);
        if (grown < 1.0F) {
            float[] pivot = SchemeLoaderBlockEntity.barStripPivot(strip);
            // Model pixels -> block space (0 … 1). Scaling about the strip's bottom edge
            // makes it rise out of the bar rather than inflate in place.
            float px = pivot[0] / 16.0F;
            float py = pivot[1] / 16.0F;
            float pz = pivot[2] / 16.0F;
            buffer.translate(px, py, pz).scale(1.0F, grow(grown), 1.0F).translate(-px, -py, -pz);
        }
        buffer.renderInto(poseStack, consumer);
    }

    /**
     * The bar's motion curve: {@code grown} is the raw 0 … 1 progress of one strip, returned
     * eased so the strip shoots up quickly and settles into place. Shared by both renderers,
     * so the bar looks the same in Flywheel and in the vanilla path (Ponder).
     */
    public static float grow(float grown) {
        float inverse = 1.0F - Math.max(0.0F, Math.min(1.0F, grown));
        return 1.0F - inverse * inverse;
    }

    /**
     * True when Flywheel is drawing the bar for this block entity, i.e. when
     * {@link SchemeLoaderVisual} is registered AND Flywheel visualizes the level. False
     * everywhere else — most importantly inside Ponder, whose scene renderer dispatches block
     * entity renderers but runs no Flywheel visuals.
     *
     * <p>Both halves matter. The registration is opt-in
     * ({@code -Dcreate_productionline.flywheelBar=true}) because the visual has not been seen
     * working in game yet; asking only whether the level is visualized would make the vanilla
     * renderer step aside for a visual that is not there, and the bar would be invisible — the
     * exact failure that made this a switch instead of a default.
     */
    public static boolean visualized(net.minecraft.world.level.LevelAccessor level) {
        return flywheelBarEnabled() && level != null
                && dev.engine_room.flywheel.api.visualization.VisualizationManager
                        .supportsVisualization(level);
    }

    /** Whether the bar's Flywheel visual is registered (system property, off by default). */
    public static boolean flywheelBarEnabled() {
        return FLYWHEEL_BAR;
    }

    private static final boolean FLYWHEEL_BAR = Boolean.getBoolean("create_productionline.flywheelBar");

    /** Number of strips the whole bar has. */
    public static int stripCount() {
        return MODELS.length;
    }

    /** One line per session and per path, so a dark bar can be told apart from a missing count. */
    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void logFirstDraw(String path, float segments) {
        if (LOGGED.add(path)) {
            LOGGER.info("Scheme Loader bar: first draw through the {} — {} strip(s), count {}",
                    path, MODELS.length, segments);
        }
    }
}
