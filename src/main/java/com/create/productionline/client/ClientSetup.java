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
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.PRODUCTION_COMPUTER.get(), ProductionComputerScreen::new);
        event.register(ModMenuTypes.SCHEME_LOADER.get(), SchemeLoaderScreen::new);
        event.register(ModMenuTypes.DISMANTLER.get(), com.create.productionline.client.screen.DismantlerScreen::new);
    }

    /**
     * Ponder collects addon plugins during client setup — the same hook Create itself uses
     * ({@code CreateClient.clientInit}). The plugin's scenes are then indexed on the next
     * resource reload, and pressing W on our blocks opens them.
     */
    private static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        net.createmod.ponder.foundation.PonderIndex.addPlugin(
                new com.create.productionline.client.ponder.ProductionLinePonderPlugin());
    }
}
