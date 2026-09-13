package com.create.productionline.registry;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.item.LineSchemeItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative inventory tab "Create: Production Line".
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProductionLineMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_productionline"))
                    .icon(() -> new ItemStack(ModItems.LINE_SCHEME.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.PRODUCTION_COMPUTER.get());
                        output.accept(ModBlocks.SCHEME_LOADER.get());
                        output.accept(ModBlocks.DISMANTLER.get());
                        output.accept(ModItems.LINE_SCHEME.get());
                        output.accept(ModItems.GENERIC_INTERMEDIATE.get());
                        output.accept(ModItems.LINE_SCHEME_MIRROR.get());
                        output.accept(LineSchemeItem.sampleStack());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
