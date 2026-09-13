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
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.PRODUCTION_COMPUTER.get(), ProductionComputerScreen::new);
        event.register(ModMenuTypes.SCHEME_LOADER.get(), SchemeLoaderScreen::new);
        event.register(ModMenuTypes.DISMANTLER.get(), com.create.productionline.client.screen.DismantlerScreen::new);
    }
}
