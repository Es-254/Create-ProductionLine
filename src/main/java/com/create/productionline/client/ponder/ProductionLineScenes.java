package com.create.productionline.client.ponder;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.block.SchemeLoaderBlock;
import com.create.productionline.client.ponder.MachineGuiElement;
import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.menu.GuiLayout;
import com.create.productionline.registry.ModBlocks;
import com.create.productionline.registry.ModItems;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The line story, in two chapters and one scene (see {@link ProductionLinePonderPlugin}):
 * <b>chapter 1</b> writes a plan on the Production Computer, <b>chapter 2</b> puts it to work in the
 * Scheme Loader and shows the line that gets built from it.
 *
 * <p>Text elements are shown in order — Ponder numbers them {@code text_1}, {@code text_2}, … — so
 * the order of {@code showText} calls here is the order of the narration table.
 */
public final class ProductionLineScenes {

    private static final ResourceLocation COMPUTER_GUI = ResourceLocation.fromNamespaceAndPath(
            "create_productionline", "textures/gui/production_computer.png");
    private static final ResourceLocation LOADER_GUI = ResourceLocation.fromNamespaceAndPath(
            "create_productionline", "textures/gui/scheme_loader.png");

    private ProductionLineScenes() {
    }

    public static void productionLine(SceneBuilder builder, SceneBuildingUtil util) {
        // Create's wrapper adds the helpers this scene needs (items on belts, Deployer punches);
        // everything else is plain Ponder.
        com.simibubi.create.foundation.ponder.CreateSceneBuilder scene =
                new com.simibubi.create.foundation.ponder.CreateSceneBuilder(builder);
        scene.title(ProductionLinePonderPlugin.LINE_SCENE, "Writing a Line Scheme");

        BlockPos machine = util.grid().at(2, 1, 2);
        int[] loaderSlotsX = new int[16];
        int[] loaderSlotsY = new int[16];
        for (int i = 0; i < 16; i++) {
            loaderSlotsX[i] = GuiLayout.loaderSlotX(i % 8);
            loaderSlotsY[i] = GuiLayout.loaderSlotY(i / 8);
        }

        ItemStack target = new ItemStack(Items.IRON_INGOT, 8);
        ItemStack blankScheme = new ItemStack(ModItems.LINE_SCHEME.get());
        ItemStack paper = new ItemStack(Items.PAPER);
        ItemStack writtenScheme = writtenScheme();

        // --- chapter 1: the computer writes the plan --------------------------
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().setBlock(machine, ModBlocks.PRODUCTION_COMPUTER.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(machine), Direction.DOWN);
        scene.idle(15);
        scene.special().movePointOfInterest(machine);

        MachineGuiElement panel = new MachineGuiElement(COMPUTER_GUI,
                new int[]{GuiLayout.computerSlotX(0), GuiLayout.computerSlotX(1), GuiLayout.computerSlotX(2)},
                new int[]{GuiLayout.COMPUTER_SLOT_Y, GuiLayout.COMPUTER_SLOT_Y, GuiLayout.COMPUTER_SLOT_Y},
                false);
        scene.addInstruction(s -> s.addElement(panel));

        // 1 — what the machine is for
        scene.addInstruction(s -> panel.setVisible(true));
        scene.overlay().showText(70)
                .text("Turn any recipe into a production line plan")
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(80);

        // 2 — what goes into the first two slots
        scene.addInstruction(s -> panel.setStack(0, target));
        scene.overlay().showText(80)
                .text("Slot 1 takes the item to produce, slot 2 a blank Line Scheme")
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(60);
        scene.addInstruction(s -> panel.setStack(1, blankScheme));
        scene.idle(60);

        // 3 — the optional paper carrier
        scene.addInstruction(s -> panel.setStack(2, paper));
        scene.overlay().showText(80)
                .text("Slot 3 may hold paper: it stays readable as a manual after the scheme is loaded")
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(90);

        // 4 — press Compute
        scene.overlay().showControls(util.vector().topOf(machine), net.createmod.catnip.math.Pointing.DOWN, 60)
                .leftClick();
        scene.overlay().showText(70)
                .text("Press Compute: the server derives it from its live recipes and writes the scheme")
                .colored(PonderPalette.INPUT)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.effects().indicateSuccess(machine);
        scene.addInstruction(s -> panel.setStack(1, writtenScheme));
        scene.idle(80);

        // 5 — what the written scheme says
        scene.overlay().showText(90)
                .text("The scheme names the product, the base and the whole chain; a self-referencing "
                        + "recipe is marked as an incremental line")
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(100);
        scene.addInstruction(s -> panel.setVisible(false));
        scene.world().hideSection(util.select().position(machine), Direction.UP);
        scene.idle(15);

        // --- chapter 2: the cabinet puts it to work ---------------------------
        // 6 — chapter title
        scene.overlay().showText(60)
                .text("Loading the scheme")
                .colored(PonderPalette.BLUE)
                .independent();
        scene.idle(70);
        scene.world().setBlock(machine, ModBlocks.SCHEME_LOADER.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(machine), Direction.DOWN);
        scene.special().movePointOfInterest(machine);
        scene.idle(15);
        scene.addInstruction(s -> panel.setVisible(true));

        // 7 — the scheme goes in
        scene.addInstruction(s -> panel.setStack(0, writtenScheme));
        scene.overlay().showText(80)
                .text("Put the written Line Scheme into any slot of a Scheme Loader")
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(90);

        // 8 — it takes effect at once, and the bar lights up
        scene.world().modifyBlock(machine, state -> state.setValue(SchemeLoaderBlock.FILL, 1), false);
        scene.effects().indicateSuccess(machine);
        scene.overlay().showText(80)
                .text("It takes effect immediately: only the recipes refresh, the server does not reload")
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(90);

        // 9 — more schemes, more of the bar
        scene.addInstruction(s -> {
            panel.setStack(1, writtenScheme);
            panel.setStack(2, writtenScheme);
        });
        scene.world().modifyBlock(machine, state -> state.setValue(SchemeLoaderBlock.FILL, 3), false);
        scene.overlay().showText(80)
                .text("The bar on its side shows how much of the cabinet is in use")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(machine, Direction.NORTH));
        scene.idle(90);

        // 10 — redstone while recipes are active
        BlockPos lamp = util.grid().at(4, 1, 2);
        scene.world().setBlock(lamp, net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState(), false);
        scene.world().showSection(util.select().position(lamp), Direction.EAST);
        scene.overlay().showText(70)
                .text("While recipes are active the cabinet emits a redstone signal")
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(lamp));
        scene.idle(80);
        scene.addInstruction(s -> panel.setVisible(false));

        // 11 — the line the plan describes
        scene.world().hideSection(util.select().fromTo(lamp, lamp), Direction.UP);
        scene.world().hideSection(util.select().position(machine), Direction.UP);
        scene.idle(10);
        buildLinePreview(scene, util);
        scene.overlay().showText(100)
                .text("Build the line it describes: base onto the belt first, one Deployer per added "
                        + "material facing down")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 2, 2)));
        scene.idle(110);
        scene.markAsFinished();
    }

    /**
     * The closing picture: a belt with the base on it and two Deployers facing down over it — the
     * shape every derived plan has. It is a picture, not a simulated assembly line: Ponder scenes
     * cannot run Create's kinetics.
     */
    private static void buildLinePreview(com.simibubi.create.foundation.ponder.CreateSceneBuilder scene,
            SceneBuildingUtil util) {
        for (int x = 0; x <= 4; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 2),
                    com.simibubi.create.AllBlocks.BELT.get().defaultBlockState(), false);
        }
        scene.world().showSection(util.select().fromTo(0, 1, 2, 4, 1, 2), Direction.DOWN);
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
        scene.world().showSection(util.select().fromTo(1, 2, 2, 3, 2, 2), Direction.UP);
        scene.idle(10);
        for (BlockPos pos : deployers) {
            scene.world().moveDeployer(pos, 1f, 10);
            scene.idle(15);
        }
    }

    /** A written scheme, built the same way the computer builds one — so the tooltip/steps are real. */
    private static ItemStack writtenScheme() {
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
