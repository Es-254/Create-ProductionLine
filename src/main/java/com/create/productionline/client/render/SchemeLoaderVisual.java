package com.create.productionline.client.render;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.block.entity.SchemeLoaderBlockEntity;

import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.task.SimplePlan;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;

/**
 * Flywheel visual for the Scheme Loader's front bar: one instance per strip, so
 * the bar is batched with everything else Flywheel draws instead of living in the
 * chunk mesh.
 *
 * <p>A strip is shown or hidden through its instance
 * ({@code setVisible}) the moment the loaded-scheme count changes — the block
 * state no longer carries the count, so nothing in the world re-meshes for it.
 *
 * <p>Where Flywheel does not visualize the level (Ponder, or a client that turned
 * the visualization backend off), {@link SchemeLoaderRenderer} draws the same
 * strips instead; {@code skipVanillaRender} is what keeps the two from painting
 * the bar twice.
 */
public class SchemeLoaderVisual extends AbstractBlockEntityVisual<SchemeLoaderBlockEntity> implements TickableVisual {

    /** One instance per strip of the bar, always all six — hidden ones are not drawn. */
    private final List<TransformedInstance> strips = new ArrayList<>(SchemeLoaderBlockEntity.BAR_SEGMENTS);

    /** Animated strip count already applied; -1 forces the first update to apply. */
    private float applied = -1.0F;

    public SchemeLoaderVisual(VisualizationContext context, SchemeLoaderBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        if (!LoaderBar.available()) {
            return;
        }
        for (int strip = 0; strip < LoaderBar.stripCount(); strip++) {
            Instancer<TransformedInstance> instancer = instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, Models.partial(LoaderBar.model(strip)));
            TransformedInstance instance = instancer.createInstance();
            instance.setIdentityTransform();
            instance.setChanged();
            strips.add(instance);
        }
        // First frame snaps to the count the cabinet already has; from then on the strips
        // grow in and shrink back out (see SchemeLoaderBlockEntity#advanceBarAnimation).
        apply(blockEntity.advanceBarAnimation());
        relight(strips.toArray(new FlatLit[0]));
    }

    @Override
    public Plan<TickableVisual.Context> planTick() {
        return SimplePlan.<TickableVisual.Context>of(this::tick);
    }

    private void tick() {
        apply(blockEntity.advanceBarAnimation());
    }

    /** Also advanced per frame, so the motion is smooth rather than 20 steps a second. */
    @Override
    public void update(float partialTick) {
        apply(blockEntity.advanceBarAnimation());
    }

    @Override
    public void updateLight(float partialTick) {
        if (!strips.isEmpty()) {
            relight(strips.toArray(new FlatLit[0]));
        }
    }

    /**
     * Shows the strips the animated count has reached. The strip the count is currently
     * inside is drawn part way grown, from its bottom edge, so the bar rises segment by
     * segment instead of switching pictures.
     */
    private void apply(float segments) {
        if (segments == applied) {
            return;
        }
        applied = segments;
        int whole = (int) Math.floor(segments);
        float partial = segments - whole;
        for (int strip = 0; strip < strips.size(); strip++) {
            float grown = strip < whole ? 1.0F : (strip == whole ? partial : 0.0F);
            TransformedInstance instance = strips.get(strip);
            if (grown <= 0.01F) {
                instance.setVisible(false);
                instance.setChanged();
                continue;
            }
            instance.setVisible(true);
            instance.setIdentityTransform();
            if (grown < 1.0F) {
                float[] pivot = SchemeLoaderBlockEntity.barStripPivot(strip);
                // Model pixels -> block space; scaling about the strip's bottom edge makes it
                // rise out of the bar. Same pivot and same curve as the vanilla renderer, so
                // Ponder (BER) and the game (Flywheel) move identically.
                float px = pivot[0] / 16.0F;
                float py = pivot[1] / 16.0F;
                float pz = pivot[2] / 16.0F;
                instance.translate(px, py, pz);
                instance.scale(1.0F, LoaderBar.grow(grown), 1.0F);
                instance.translate(-px, -py, -pz);
            }
            instance.setChanged();
        }
    }

    @Override
    protected void _delete() {
        for (TransformedInstance instance : strips) {
            instance.delete();
        }
        strips.clear();
    }

    /** The strips take part in the block-breaking overlay like the rest of the machine. */
    @Override
    public void collectCrumblingInstances(java.util.function.Consumer<dev.engine_room.flywheel.api.instance.Instance> consumer) {
        for (TransformedInstance instance : strips) {
            consumer.accept(instance);
        }
    }

    /** Registered in {@code ClientSetup}; Flywheel asks it for a visual per block entity. */
    public static class Visualizer implements BlockEntityVisualizer<SchemeLoaderBlockEntity> {

        @Override
        public BlockEntityVisual<? super SchemeLoaderBlockEntity> createVisual(VisualizationContext context,
                SchemeLoaderBlockEntity blockEntity, float partialTick) {
            return new SchemeLoaderVisual(context, blockEntity, partialTick);
        }

        @Override
        public boolean skipVanillaRender(SchemeLoaderBlockEntity blockEntity) {
            // Only when this block entity really gets a visual: in Ponder the vanilla
            // renderer stays in charge and keeps the bar on screen.
            return LoaderBar.visualized(blockEntity.getLevel());
        }
    }
}
