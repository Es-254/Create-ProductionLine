package com.create.productionline.client.ponder;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.registry.ModBlocks;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/**
 * Registers this mod's Ponder scenes and their grouping.
 *
 * <p><b>Three scenes, one tag.</b> The author asked for the machines to be cut into chapters with
 * the built-in mechanism, and the dismantler kept separate. Ponder's {@code PonderChapter} cannot do
 * that in this version: {@code getTitle()} returns a constant empty string and {@code PonderUI}'s
 * chapter field is only ever assigned {@code null} — nothing in Ponder or Create ever calls
 * {@code PonderChapter.of} or {@code PonderChapterRegistry.addStoriesToChapter}. The grouping that
 * <em>does</em> work is {@link net.createmod.ponder.api.registration.PonderTag}: it is what Create
 * itself uses for its ponder index, and what the index screen lists scenes under. So each machine
 * gets its own scene, and the tag is the chapter that holds them.
 *
 * <p>Every scene needs a structure schematic at {@code assets/create_productionline/ponder/
 * <sceneId>.nbt} — without it Ponder logs "Ponder schematic missing" and the scene renders an empty
 * world (the base plate and every block simply do not appear). Those files are a 5x3x5 checkered
 * base plate, the same one Create's own scenes stand on.
 *
 * <p>Text lives in the language files as {@code create_productionline.ponder.<sceneId>.header} and
 * {@code ….text_<n>}, numbered by the order the scene shows them; the tag's title and description are
 * {@code create_productionline.ponder.tag.<path>} and {@code ….description}.
 */
public class ProductionLinePonderPlugin implements PonderPlugin {

    /** Chapter 1: the computer writes a plan. */
    public static final String COMPUTER_SCENE = "production_computer";
    /** Chapter 2: the cabinet loads it. */
    public static final String LOADER_SCENE = "scheme_loader";
    /** The dismantler runs the other way, so it is its own scene (the author's call). */
    public static final String DISMANTLER_SCENE = "dismantler";

    /** The tag (chapter) all three machines sit in. */
    public static final ResourceLocation MACHINES_TAG = ResourceLocation.fromNamespaceAndPath(
            ProductionLineMod.MODID, "machines");

    @Override
    public String getModId() {
        return ProductionLineMod.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemLike> scenes =
                helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));
        scenes.forComponents(ModBlocks.PRODUCTION_COMPUTER.get())
                .addStoryBoard(COMPUTER_SCENE, ProductionLineScenes::productionComputer);
        scenes.forComponents(ModBlocks.SCHEME_LOADER.get())
                .addStoryBoard(LOADER_SCENE, ProductionLineScenes::schemeLoader);
        scenes.forComponents(ModBlocks.DISMANTLER.get())
                .addStoryBoard(DISMANTLER_SCENE, DismantlerScenes::dismantler);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<ItemLike> tags =
                helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));
        tags.registerTag(MACHINES_TAG)
                .title("Create: Production Line")
                .description("Machines that turn any recipe into a Create production line")
                .item(ModBlocks.PRODUCTION_COMPUTER.get())
                .addToIndex()
                .register();
        tags.addTagToComponent(ModBlocks.PRODUCTION_COMPUTER.get(), MACHINES_TAG);
        tags.addTagToComponent(ModBlocks.SCHEME_LOADER.get(), MACHINES_TAG);
        tags.addTagToComponent(ModBlocks.DISMANTLER.get(), MACHINES_TAG);
    }
}
