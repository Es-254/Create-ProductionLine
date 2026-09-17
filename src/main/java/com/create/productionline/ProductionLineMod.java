package com.create.productionline;

import com.create.productionline.registry.ModBlockEntities;
import com.create.productionline.registry.ModBlocks;
import com.create.productionline.registry.ModCreativeTabs;
import com.create.productionline.registry.ModItems;
import com.create.productionline.registry.ModMenuTypes;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Create: Production Line — 机械动力：产业线
 *
 * <p>An addon for the Create mod on Minecraft 1.21.1 / NeoForge. Core modules:
 * <ul>
 *   <li>Production Computer (产线计算机): put a target item and a blank Line Scheme
 *       (optionally paper as a second copy) into the computer; it computes an ordered
 *       production-line plan and writes the plan plus the generated native Create
 *       recipe JSON onto the carrier item.</li>
 *   <li>Scheme Loader (方案加载柜): a 16-slot cabinet that writes the Create recipes
 *       embedded in loaded Line Schemes into the world datapack ({@code cpl_converted})
 *       and reloads them; several cabinets are combined and the loader emits a
 *       redstone signal while active.</li>
 *   <li>Dismantler (破拆机): reverts a generic intermediate or finished product back
 *       into its raw materials and leaves a read-only Line Scheme Mirror.</li>
 *   <li>Line Scheme (产线方案) / Line Scheme Mirror (产线方案镜像) / Generic
 *       Intermediate (通用中间产物) — the mod's standalone items.</li>
 * </ul>
 */
@Mod(ProductionLineMod.MODID)
public class ProductionLineMod {

    public static final String MODID = "create_productionline";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProductionLineMod(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BLOCK_ITEMS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);

        if (FMLEnvironment.dist.isClient()) {
            com.create.productionline.client.ClientSetup.register(modEventBus);
        }

        modEventBus.addListener(com.create.productionline.network.ModPayloads::onRegisterPayloads);
        NeoForge.EVENT_BUS.register(com.create.productionline.event.ServerLifecycleEvents.class);
        NeoForge.EVENT_BUS.register(com.create.productionline.event.ItemTooltipHandler.class);
    }
}
