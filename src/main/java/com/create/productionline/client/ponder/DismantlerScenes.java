package com.create.productionline.client.ponder;

import com.create.productionline.menu.GuiLayout;
import com.create.productionline.registry.ModBlocks;
import com.create.productionline.registry.ModItems;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The dismantler's own scene: it runs the opposite way to the rest of the mod, so it is not part of
 * the line story (the author's call) and is registered as a single-scene entry of its own. Four
 * narrated steps plus one silent one, matching the table: a product, a written scheme, and finally
 * an unfinished intermediate.
 */
public final class DismantlerScenes {

    private static final ResourceLocation DISMANTLER_GUI = ResourceLocation.fromNamespaceAndPath(
            "create_productionline", "textures/gui/dismantler.png");

    /** The screens' title label y for this panel (its well starts at y=21, like the computer's). */
    private static final int TITLE_Y = 6;

    private DismantlerScenes() {
    }

    public static void dismantler(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ProductionLinePonderPlugin.DISMANTLER_SCENE, "Dismantling what you do not need");

        scene.configureBasePlate(0, 0, 5);


        scene.scaleSceneView(0.9F);


        // The base plate is the schematic's layer 0; revealing it that way is what Create's own scenes do.


        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        BlockPos machine = util.grid().at(2, 1, 2);
        // The machine is a block of this scene's schematic, so it is revealed, not placed: a block the
        // scene places itself is dropped unless it sits inside the schematic's own bounding box (see
        // ProductionLineScenes for the full rule).
        scene.world().showSection(util.select().position(machine), Direction.DOWN);
        scene.idle(15);
        scene.special().movePointOfInterest(machine);

        MachineGuiElement panel = new MachineGuiElement(DISMANTLER_GUI,
                new int[]{GuiLayout.dismantlerItemX(), GuiLayout.dismantlerSchemeX()},
                new int[]{GuiLayout.dismantlerSlotY(), GuiLayout.dismantlerSlotY()},
                ModBlocks.DISMANTLER.get().getName(), TITLE_Y, false)
                .withButton((GuiLayout.PANEL_WIDTH - GuiLayout.DISMANTLER_BUTTON_WIDTH) / 2,
                        GuiLayout.DISMANTLER_BUTTON_Y, GuiLayout.DISMANTLER_BUTTON_WIDTH,
                        GuiLayout.DISMANTLER_BUTTON_HEIGHT,
                        Component.translatable("gui.create_productionline.dismantle"));
        scene.addInstruction(s -> s.addElement(panel));

        ItemStack product = new ItemStack(Items.IRON_BLOCK);
        ItemStack mirror = new ItemStack(ModItems.LINE_SCHEME_MIRROR.get());
        ItemStack blankScheme = new ItemStack(ModItems.LINE_SCHEME.get());
        ItemStack intermediate = new ItemStack(ModItems.GENERIC_INTERMEDIATE.get());

        // 1 — what the left slot takes: highlight the slot, a beat, then the item is in it
        scene.addInstruction(s -> panel.setVisible(true));
        scene.addInstruction(s -> panel.setSlotHighlighted(0));
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The left slot takes a finished product, or an unfinished intermediate")
                .placeNearTarget()
                .pointAt(ProductionLineScenes.highlight(util, machine));
        scene.idle(14);
        scene.addInstruction(s -> panel.setStack(0, product));
        scene.idle(76);

        // 2 — dismantle it. The cue is the button in the panel, not a click on the block: the block is only
        // what opens the GUI, while the action the narration describes is pressing 【拆解】. Same shape as
        // every other press in these scenes: highlight the control, a beat, then its effect.
        scene.addInstruction(s -> panel.setSlotHighlighted(-1));
        scene.idle(10);
        scene.addInstruction(s -> panel.setButtonHighlighted(true));
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Dismantling hands the materials back and leaves a read-only mirror")
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(ProductionLineScenes.highlight(util, machine));
        scene.idle(14);
        scene.addInstruction(s -> panel.setStack(0, mirror));
        scene.effects().indicateSuccess(machine);
        scene.idle(76);

        // 3 — a written scheme instead: it is erased, not refunded
        scene.addInstruction(s -> {
            panel.setButtonHighlighted(false);
            panel.setStack(0, ItemStack.EMPTY);
        });
        scene.addInstruction(s -> panel.setSlotHighlighted(1));
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("A written scheme is erased: a mirror of the plan, and a blank scheme back")
                .colored(PonderPalette.INPUT)
                .placeNearTarget()
                .pointAt(ProductionLineScenes.highlight(util, machine));
        scene.idle(14);
        scene.addInstruction(s -> panel.setStack(1, com.create.productionline.item.LineSchemeItem.sampleStack()));
        scene.idle(86);

        // 4 — silent: the swap itself, again behind the button
        scene.addInstruction(s -> {
            panel.setSlotHighlighted(-1);
            panel.setButtonHighlighted(true);
        });
        scene.idle(14);
        scene.addInstruction(s -> {
            panel.setStack(0, mirror);
            panel.setStack(1, blankScheme);
        });
        scene.effects().indicateSuccess(machine);
        scene.idle(66);

        // 5 — the intermediate case, which needs the button too — this step used to have no cue at all.
        // The item goes back in first, then the press: the same two-part shape as step 3 and 4, so the reader
        // sees the item arrive and then the button that acts on it.
        scene.addInstruction(s -> {
            panel.setButtonHighlighted(false);
            panel.setSlotHighlighted(0);
            panel.setStack(0, intermediate);
            panel.setStack(1, ItemStack.EMPTY);
        });
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("An unfinished intermediate gives back only what has been assembled so far")
                .placeNearTarget()
                .pointAt(ProductionLineScenes.highlight(util, machine));
        scene.idle(60);
        scene.addInstruction(s -> {
            panel.setSlotHighlighted(-1);
            panel.setButtonHighlighted(true);
        });
        scene.idle(14);
        scene.addInstruction(s -> panel.setStack(0, mirror));
        scene.effects().indicateSuccess(machine);
        scene.idle(66);
        scene.addInstruction(s -> panel.setButtonHighlighted(false));
        scene.idle(10);
        scene.markAsFinished();
    }
}
