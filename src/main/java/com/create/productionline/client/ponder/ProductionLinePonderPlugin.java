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
 * <p><b>Chapters are scene lists.</b> Ponder's {@code PonderChapter} type is dead code in this
 * version — {@code getTitle()} returns a constant empty string and {@code PonderUI}'s chapter field
 * is only ever assigned {@code null} — so the grouping that actually works is the one Ponder itself
 * uses: every storyboard registered for the <em>same component</em> becomes a scene of that
 * component's entry, and the entry's scenes are the chapters the arrow buttons switch between.
 * Those buttons carry the left/right key shortcuts, which are {@code Options.keyLeft/keyRight} —
 * strafe left/right, A and D by default. A component with a single scene has no arrows and no
 * chapter keys, which is why registering one storyboard per machine produced three one-scene
 * entries that A and D could not move through.
 *
 * <p>So the computer and the cabinet are two chapters of one entry: both storyboards are registered
 * for both items, and either machine's ponder screen opens on chapter 1 and can page to chapter 2.
 * The dismantler runs the other way, so it stays its own single-scene entry (the author's call).
 * {@link #MACHINES_TAG} still groups all three, because the ponder index lists entries by tag.
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
    /** The dismantler runs the other way, so it is its own entry (the author's call). */
    public static final String DISMANTLER_SCENE = "dismantler";

    /** The tag that groups all three machines in the ponder index. */
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
        // Both storyboards for both items: that is what makes the pair one entry with two chapters,
        // i.e. what gives the ponder screen its arrows and its A/D shortcuts.
        scenes.forComponents(ModBlocks.PRODUCTION_COMPUTER.get(), ModBlocks.SCHEME_LOADER.get())
                .addStoryBoard(COMPUTER_SCENE, ProductionLineScenes::productionComputer)
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
