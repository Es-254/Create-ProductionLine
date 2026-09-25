package com.create.productionline.client;

import com.create.productionline.client.screen.ProductionComputerScreen;
import com.create.productionline.client.screen.SchemeLoaderScreen;
import com.create.productionline.registry.ModMenuTypes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only setup: binds menu types to their screens.
 */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientSetup::onRegisterMenuScreens);
        modEventBus.addListener(ClientSetup::onClientSetup);
        modEventBus.addListener(ClientSetup::onRegisterRenderers);
        // The bar strip models are referenced by no blockstate, so Minecraft would never
        // bake them: they are registered explicitly (see LoaderBar).
        modEventBus.addListener(com.create.productionline.client.render.LoaderBar::registerAdditional);
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.PRODUCTION_COMPUTER.get(), ProductionComputerScreen::new);
        event.register(ModMenuTypes.SCHEME_LOADER.get(), SchemeLoaderScreen::new);
        event.register(ModMenuTypes.DISMANTLER.get(), com.create.productionline.client.screen.DismantlerScreen::new);
    }

    /**
     * Block entity renderer for the Scheme Loader's front bar. It draws the bar wherever
     * Flywheel is not visualizing the level: Ponder dispatches block entity renderers but
     * runs no Flywheel visuals, and the loader's tutorial chapter shows the bar filling up.
     */
    private static void onRegisterRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.create.productionline.registry.ModBlockEntities.SCHEME_LOADER.get(),
                com.create.productionline.client.render.SchemeLoaderRenderer::new);
    }

    /**
     * Ponder collects addon plugins during client setup — the same hook Create itself uses
     * ({@code CreateClient.clientInit}). The plugin's scenes are then indexed on the next
     * resource reload, and pressing W on our blocks opens them.
     */
    private static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        net.createmod.ponder.foundation.PonderIndex.addPlugin(
                new com.create.productionline.client.ponder.ProductionLinePonderPlugin());
        // Flywheel visuals have to be registered before any level is visualized. The bar
        // used to be a block model; it is instanced now (SchemeLoaderVisual) so that
        // loading a scheme costs a packet instead of a chunk section re-mesh.
        event.enqueueWork(() -> dev.engine_room.flywheel.api.visualization.VisualizerRegistry.setVisualizer(
                com.create.productionline.registry.ModBlockEntities.SCHEME_LOADER.get(),
                new com.create.productionline.client.render.SchemeLoaderVisual.Visualizer()));
    }
}
