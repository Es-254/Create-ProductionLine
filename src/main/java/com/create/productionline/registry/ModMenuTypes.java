package com.create.productionline.registry;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.menu.ProductionComputerMenu;
import com.create.productionline.menu.SchemeLoaderMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu types: production computer GUI, scheme loader GUI and dismantler GUI.
 */
public final class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, ProductionLineMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ProductionComputerMenu>> PRODUCTION_COMPUTER =
            MENU_TYPES.register("production_computer",
                    () -> new MenuType<>(ProductionComputerMenu::createClient,
                            net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<SchemeLoaderMenu>> SCHEME_LOADER =
            MENU_TYPES.register("scheme_loader",
                    () -> new MenuType<>(SchemeLoaderMenu::createClient,
                            net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<com.create.productionline.menu.DismantlerMenu>> DISMANTLER =
            MENU_TYPES.register("dismantler",
                    () -> new MenuType<>(com.create.productionline.menu.DismantlerMenu::createClient,
                            net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

    private ModMenuTypes() {
    }
}
