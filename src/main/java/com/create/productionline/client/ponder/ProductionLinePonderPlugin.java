package com.create.productionline.client.ponder;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.registry.ModBlocks;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/**
 * Registers this mod's Ponder scenes.
 *
 * <p>Two scenes, deliberately not three. Writing a plan and putting it to work is <b>one</b> story:
 * the scheme is authored on the computer and loaded in the cabinet, and the author asked for both
 * chapters in a single scene so a player who presses W on either machine sees the whole round trip.
 * The dismantler is the opposite direction — it takes things apart — so it gets its own.
 *
 * <p>The scene text lives in the language files under
 * {@code create_productionline.ponder.<sceneId>.header} and {@code ….text_<n>}, numbered by the
 * order the text elements are shown in (see the scene classes).
 */
public class ProductionLinePonderPlugin implements PonderPlugin {

    /** Scene id shared by the computer and the cabinet (chapter 1 and 2 of the same story). */
    public static final String LINE_SCENE = "production_line";
    /** Scene id of the dismantler, which runs the other way. */
    public static final String DISMANTLER_SCENE = "dismantler";

    @Override
    public String getModId() {
        return ProductionLineMod.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemLike> scenes =
                helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));
        scenes.forComponents(ModBlocks.PRODUCTION_COMPUTER.get(), ModBlocks.SCHEME_LOADER.get())
                .addStoryBoard(LINE_SCENE, ProductionLineScenes::productionLine);
        scenes.forComponents(ModBlocks.DISMANTLER.get())
                .addStoryBoard(DISMANTLER_SCENE, DismantlerScenes::dismantler);
    }
}
