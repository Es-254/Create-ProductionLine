package com.create.productionline.event;

import com.create.productionline.recipegen.RecipeHotSwap;
import com.mojang.brigadier.Command;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The mod's commands.
 *
 * <p>{@code /cpl reload recipes} re-reads the recipe JSON of every data pack the
 * server knows and installs the result, without the full {@code /reload}: nothing
 * except recipes is re-read, which is what a hand-edited recipe file (or a data
 * pack written by another tool) actually needs. It runs on the recipe reload
 * listener itself, so the NeoForge recipe conditions and the vanilla error
 * handling behave exactly as they do during a reload.
 *
 * <p>The reply goes to whoever ran the command and to nobody else — the mod never
 * broadcasts. Requires permission level 2, the same gate as the anvil flow: the
 * command re-reads data pack files, which is an operator action.
 */
public final class CommandEvents {

    private CommandEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cpl")
                .then(Commands.literal("reload")
                        .then(Commands.literal("recipes")
                                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> {
                                    RecipeHotSwap.Outcome outcome =
                                            RecipeHotSwap.reloadRecipes(context.getSource().getServer());
                                    if (!outcome.ok()) {
                                        context.getSource().sendFailure(Component.translatable(
                                                "commands.create_productionline.reload.failed", outcome.detail()));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "commands.create_productionline.reload.recipes",
                                            outcome.recipes(), outcome.millis()), false);
                                    return Command.SINGLE_SUCCESS;
                                }))));
    }
}
