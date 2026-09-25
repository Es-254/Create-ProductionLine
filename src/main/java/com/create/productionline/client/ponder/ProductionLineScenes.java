package com.create.productionline.client.ponder;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.block.SchemeLoaderBlock;
import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.menu.GuiLayout;
import com.create.productionline.registry.ModBlocks;
import com.create.productionline.registry.ModItems;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The line story, as the two chapters the author's table describes: the computer writes a plan, the
 * cabinet puts it to work. Both storyboards are registered for both machines by
 * {@link ProductionLinePonderPlugin}, so they are one entry with two chapters and the ponder screen
 * offers A/D to move between them.
 *
 * <p>Each scene carries its own structure schematic ({@code assets/create_productionline/ponder/
 * <id>.nbt}): a scene without one logs "Ponder schematic missing" and renders an empty world, which
 * is exactly what happened before these files existed. The schematic holds the base plate only —
 * every machine block is placed by the scene itself.
 *
 * <p>That is also why those blocks are revealed with {@code showIndependentSection} rather than
 * {@code showSection}: {@code showSection} reveals what the schematic's backup contains, and a block
 * that the scene placed at runtime is not in it. Create's own scenes use independent sections for
 * exactly this, and without one the machine simply never appeared on the plate.
 *
 * <p>Text elements are numbered by the order they are shown — {@code text_1}, {@code text_2}, … — so
 * the order of {@code showText} calls here is the order of the narration table.
 */
public final class ProductionLineScenes {

    private static final ResourceLocation COMPUTER_GUI = ResourceLocation.fromNamespaceAndPath(
            "create_productionline", "textures/gui/production_computer.png");
    private static final ResourceLocation LOADER_GUI = ResourceLocation.fromNamespaceAndPath(
            "create_productionline", "textures/gui/scheme_loader.png");

    /** The screens' title label y for the panels whose well starts at y=17. */
    private static final int TITLE_Y = 6;

    private ProductionLineScenes() {
    }

    // --- chapter 1: the computer writes the plan ---------------------------------

    public static void productionComputer(SceneBuilder builder, SceneBuildingUtil util) {
        com.simibubi.create.foundation.ponder.CreateSceneBuilder scene =
                new com.simibubi.create.foundation.ponder.CreateSceneBuilder(builder);
        scene.title(ProductionLinePonderPlugin.COMPUTER_SCENE, "Writing a Line Scheme");

        BlockPos machine = util.grid().at(2, 1, 2);
        ItemStack target = new ItemStack(Items.IRON_INGOT, 8);
        ItemStack blankScheme = new ItemStack(ModItems.LINE_SCHEME.get());
        ItemStack paper = new ItemStack(Items.PAPER);
        ItemStack writtenScheme = writtenScheme();

        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);

        // The base plate is the schematic's layer 0; revealing it that way is what Create's own scenes do.
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().setBlock(machine, ModBlocks.PRODUCTION_COMPUTER.get().defaultBlockState(), false);
        scene.world().showIndependentSection(util.select().position(machine), Direction.DOWN);
        scene.idle(15);
        scene.special().movePointOfInterest(machine);

        MachineGuiElement panel = new MachineGuiElement(COMPUTER_GUI,
                new int[]{GuiLayout.computerSlotX(0), GuiLayout.computerSlotX(1), GuiLayout.computerSlotX(2)},
                new int[]{GuiLayout.COMPUTER_SLOT_Y, GuiLayout.COMPUTER_SLOT_Y, GuiLayout.COMPUTER_SLOT_Y},
                ModBlocks.PRODUCTION_COMPUTER.get().getName(), TITLE_Y, false)
                .withButton((GuiLayout.PANEL_WIDTH - GuiLayout.COMPUTER_BUTTON_WIDTH) / 2,
                        GuiLayout.COMPUTER_BUTTON_Y, GuiLayout.COMPUTER_BUTTON_WIDTH,
                        GuiLayout.COMPUTER_BUTTON_HEIGHT,
                        Component.translatable("gui.create_productionline.compute"));
        scene.addInstruction(s -> s.addElement(panel));

        // 1 — what the machine is for
        scene.addInstruction(s -> panel.setVisible(true));
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Turn any recipe into a production line plan")
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(80);

        // 2 — what goes into the first two slots
        scene.addInstruction(s -> panel.setStack(0, target));
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Slot 1 takes the item to produce, slot 2 a blank Line Scheme")
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(60);
        scene.addInstruction(s -> panel.setStack(1, blankScheme));
        scene.idle(60);

        // 3 — the optional paper carrier
        scene.addInstruction(s -> panel.setStack(2, paper));
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Slot 3 may hold paper: it stays readable as a manual after the scheme is loaded")
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(90);

        // 4 — press Compute
        scene.overlay().showControls(util.vector().topOf(machine), Pointing.DOWN, 60).leftClick();
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Press Compute: the server derives it from its live recipes and writes the scheme")
                .colored(PonderPalette.INPUT)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.effects().indicateSuccess(machine);
        scene.addInstruction(s -> panel.setStack(1, writtenScheme));
        scene.idle(80);

        // 5 — what the written scheme says
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The scheme names the product, the base and the whole chain; a self-referencing "
                        + "recipe is marked as an incremental line")
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(100);
        scene.markAsFinished();
    }

    // --- chapter 2: the cabinet loads it -----------------------------------------

    public static void schemeLoader(SceneBuilder builder, SceneBuildingUtil util) {
        com.simibubi.create.foundation.ponder.CreateSceneBuilder scene =
                new com.simibubi.create.foundation.ponder.CreateSceneBuilder(builder);
        scene.title(ProductionLinePonderPlugin.LOADER_SCENE, "Loading the scheme");

        BlockPos machine = util.grid().at(2, 1, 2);
        BlockPos lamp = util.grid().at(4, 1, 2);
        ItemStack writtenScheme = writtenScheme();

        int[] loaderSlotsX = new int[16];
        int[] loaderSlotsY = new int[16];
        for (int i = 0; i < 16; i++) {
            loaderSlotsX[i] = GuiLayout.loaderSlotX(i % 8);
            loaderSlotsY[i] = GuiLayout.loaderSlotY(i / 8);
        }

        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.9F);

        // The base plate is the schematic's layer 0; revealing it that way is what Create's own scenes do.
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().setBlock(machine, ModBlocks.SCHEME_LOADER.get().defaultBlockState(), false);
        ElementLink<WorldSectionElement> machineLink =
                scene.world().showIndependentSection(util.select().position(machine), Direction.DOWN);
        scene.idle(15);
        scene.special().movePointOfInterest(machine);

        MachineGuiElement panel = new MachineGuiElement(LOADER_GUI, loaderSlotsX, loaderSlotsY,
                ModBlocks.SCHEME_LOADER.get().getName(), GuiLayout.LOADER_TITLE_Y, false);
        scene.addInstruction(s -> s.addElement(panel));

        // 1 — the scheme goes in
        scene.addInstruction(s -> panel.setVisible(true));
        scene.addInstruction(s -> panel.setStack(0, writtenScheme));
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Put the written scheme into any slot of a Scheme Loader")
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(90);

        // 2 — it takes effect at once, and the bar lights up
        scene.world().modifyBlock(machine, state -> state.setValue(SchemeLoaderBlock.FILL, 1), false);
        scene.effects().indicateSuccess(machine);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("It takes effect immediately: only the recipes refresh, the server does not reload")
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(90);

        // 3 — more schemes, more of the bar
        scene.addInstruction(s -> {
            panel.setStack(1, writtenScheme.copy());
            panel.setStack(2, writtenScheme.copy());
        });
        scene.world().modifyBlock(machine, state -> state.setValue(SchemeLoaderBlock.FILL, 3), false);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("The bar on its side shows how much of the cabinet is in use")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(machine, Direction.NORTH));
        scene.idle(90);

        // 4 — redstone while recipes are active
        scene.world().setBlock(lamp, net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState(), false);
        ElementLink<WorldSectionElement> lampLink =
                scene.world().showIndependentSection(util.select().position(lamp), Direction.EAST);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("While recipes are active the cabinet emits a redstone signal")
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(lamp));
        scene.idle(80);
        scene.addInstruction(s -> panel.setVisible(false));

        // 5 — the line the plan describes
        scene.world().hideIndependentSection(lampLink, Direction.UP);
        scene.world().hideIndependentSection(machineLink, Direction.UP);
        scene.idle(10);
        buildLinePreview(scene, util);
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Build the line it describes: base onto the belt first, one Deployer per added "
                        + "material facing down")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 2)));
        scene.idle(110);
        scene.markAsFinished();
    }

    /**
     * The closing picture: a belt with the base on it and two Deployers facing down over it — the
     * shape every derived plan has. A picture, not a simulated line: Ponder scenes cannot run
     * Create's kinetics. Every block here is placed by the scene, so all of it arrives as independent
     * sections.
     */
    private static void buildLinePreview(com.simibubi.create.foundation.ponder.CreateSceneBuilder scene,
            SceneBuildingUtil util) {
        for (int x = 0; x <= 4; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 2),
                    com.simibubi.create.AllBlocks.BELT.get().defaultBlockState(), false);
        }
        scene.world().showIndependentSection(util.select().fromTo(0, 1, 2, 4, 1, 2), Direction.DOWN);
        scene.idle(10);
        scene.world().createItemOnBelt(util.grid().at(0, 1, 2), Direction.EAST, new ItemStack(Items.IRON_INGOT));
        scene.idle(20);

        List<BlockPos> deployers = new ArrayList<>();
        for (int x = 1; x <= 3; x += 2) {
            BlockPos pos = util.grid().at(x, 2, 2);
            scene.world().setBlock(pos, com.simibubi.create.AllBlocks.DEPLOYER.get().defaultBlockState()
                    .setValue(com.simibubi.create.content.kinetics.deployer.DeployerBlock.FACING, Direction.DOWN),
                    false);
            deployers.add(pos);
        }
        scene.world().showIndependentSection(util.select().fromTo(1, 2, 2, 3, 2, 2), Direction.UP);
        scene.idle(10);
        for (BlockPos pos : deployers) {
            scene.world().moveDeployer(pos, 1f, 10);
            scene.idle(15);
        }
    }

    /** A written scheme, built the way the computer builds one — so its steps and tooltip are real. */
    static ItemStack writtenScheme() {
        ItemStack stack = new ItemStack(ModItems.LINE_SCHEME.get());
        LineScheme scheme = new LineScheme();
        scheme.setRecipeId("minecraft:iron_ingot_from_blasting_iron_ore");
        scheme.setOutputItem("minecraft:iron_ingot");
        scheme.setBaseMaterial("minecraft:iron_ore");
        LineScheme.Step feed = scheme.addStep("cpl:feed", 1);
        feed.addInput("minecraft:iron_ore");
        LineScheme.Step deploy = scheme.addStep("create:deploying", 1);
        deploy.addInput("minecraft:coal");
        deploy.addOutput("minecraft:iron_ingot");
        LineSchemeSerializer.saveToStack(stack, scheme);
        return stack;
    }
}
