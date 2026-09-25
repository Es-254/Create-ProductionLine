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

    /** Number of strips currently shown; -1 forces the first update to apply. */
    private int segments = -1;

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
        applySegments(blockEntity.getRenderSegments());
        relight(strips.toArray(new FlatLit[0]));
    }

    @Override
    public Plan<TickableVisual.Context> planTick() {
        return SimplePlan.<TickableVisual.Context>of(this::tick);
    }

    private void tick() {
        applySegments(blockEntity.getRenderSegments());
    }

    /** Also checked per frame: the bar has to follow a scheme being taken out at once. */
    @Override
    public void update(float partialTick) {
        applySegments(blockEntity.getRenderSegments());
    }

    @Override
    public void updateLight(float partialTick) {
        if (!strips.isEmpty()) {
            relight(strips.toArray(new FlatLit[0]));
        }
    }

    private void applySegments(int next) {
        if (next == segments) {
            return;
        }
        segments = next;
        for (int strip = 0; strip < strips.size(); strip++) {
            strips.get(strip).setVisible(strip < next);
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
