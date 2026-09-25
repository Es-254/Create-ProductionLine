package com.create.productionline.client.ponder;

import com.create.productionline.menu.GuiLayout;
import com.create.productionline.registry.ModBlocks;
import com.create.productionline.registry.ModItems;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The dismantler's own scene: it runs the opposite way to the rest of the mod, so it is not part of
 * the line story (the author's call). Four narrated steps plus one silent one, matching the table:
 * a product, a written scheme, and finally an unfinished intermediate.
 */
public final class DismantlerScenes {

    private static final ResourceLocation DISMANTLER_GUI = ResourceLocation.fromNamespaceAndPath(
            "create_productionline", "textures/gui/dismantler.png");

    private DismantlerScenes() {
    }

    public static void dismantler(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ProductionLinePonderPlugin.DISMANTLER_SCENE, "Dismantling what you do not need");

        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos machine = util.grid().at(2, 1, 2);
        scene.world().setBlock(machine, ModBlocks.DISMANTLER.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(machine), Direction.DOWN);
        scene.idle(15);
        scene.special().movePointOfInterest(machine);

        MachineGuiElement panel = new MachineGuiElement(DISMANTLER_GUI,
                new int[]{GuiLayout.dismantlerItemX(), GuiLayout.dismantlerSchemeX()},
                new int[]{GuiLayout.dismantlerSlotY(), GuiLayout.dismantlerSlotY()},
                false);
        scene.addInstruction(s -> s.addElement(panel));

        ItemStack product = new ItemStack(Items.IRON_BLOCK);
        ItemStack mirror = new ItemStack(ModItems.LINE_SCHEME_MIRROR.get());
        ItemStack blankScheme = new ItemStack(ModItems.LINE_SCHEME.get());
        ItemStack intermediate = new ItemStack(ModItems.GENERIC_INTERMEDIATE.get());

        // 1 — what the left slot takes
        scene.addInstruction(s -> panel.setVisible(true));
        scene.addInstruction(s -> panel.setStack(0, product));
        scene.overlay().showText(80)
                .text("The left slot takes a finished product, or an unfinished intermediate")
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(90);

        // 2 — dismantle it
        scene.overlay().showControls(util.vector().topOf(machine), Pointing.DOWN, 45).leftClick();
        scene.overlay().showText(80)
                .text("Dismantling hands the materials back and leaves a read-only mirror")
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.addInstruction(s -> panel.setStack(0, mirror));
        scene.effects().indicateSuccess(machine);
        scene.idle(90);

        // 3 — a written scheme instead: it is erased, not refunded
        scene.addInstruction(s -> {
            panel.setStack(0, ItemStack.EMPTY);
            panel.setStack(1, com.create.productionline.item.LineSchemeItem.sampleStack());
        });
        scene.overlay().showText(90)
                .text("A written scheme is erased: a mirror of the plan, and a blank scheme back")
                .colored(PonderPalette.INPUT)
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(100);

        // 4 — silent: the swap itself
        scene.overlay().showControls(util.vector().topOf(machine), Pointing.DOWN, 35).leftClick();
        scene.addInstruction(s -> {
            panel.setStack(0, mirror);
            panel.setStack(1, blankScheme);
        });
        scene.effects().indicateSuccess(machine);
        scene.idle(70);

        // 5 — the intermediate case
        scene.addInstruction(s -> {
            panel.setStack(0, intermediate);
            panel.setStack(1, ItemStack.EMPTY);
        });
        scene.overlay().showText(90)
                .text("An unfinished intermediate gives back only what has been assembled so far")
                .placeNearTarget()
                .pointAt(util.vector().topOf(machine));
        scene.idle(100);
        scene.markAsFinished();
    }
}
