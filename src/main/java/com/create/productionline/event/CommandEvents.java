package com.create.productionline.event;

import com.create.productionline.block.entity.PlaceholderPrompt;
import com.create.productionline.block.entity.ProductionComputerBlockEntity;
import com.create.productionline.menu.ComputerStatus;
import com.create.productionline.menu.ProductionComputerMenu;
import com.create.productionline.recipegen.RecipeHotSwap;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * <p>{@code /cpl placeholder accept|decline} is not a feature: it is the click target behind the
 * buttons the Production Computer puts in ONE player's chat when it asks whether to write a
 * placeholder scheme for a target whose recipe cannot be converted (see
 * {@link PlaceholderPrompt}). It answers with nothing but a private line, and everything it can
 * possibly write is an item whose recipe id is empty.
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
                                })))
                .then(Commands.literal("placeholder")
                        // Hidden behind the permission instead of advertised: this is a click target
                        // for a prompt one specific player received, not something to discover and
                        // try. Keeping it out of every other command tree also keeps the mod's
                        // "never broadcast" rule intact — there is nothing here to stumble into.
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("accept")
                                .executes(context -> answerPlaceholder(context, PlaceholderPrompt.Answer.ACCEPT)))
                        .then(Commands.literal("decline")
                                .executes(context -> answerPlaceholder(context, PlaceholderPrompt.Answer.DECLINE)))));
    }

    /**
     * Answers the placeholder question on behalf of the player who clicked.
     *
     * <p>The command requirement above is a convenience (it hides the branch); the real binding is
     * checked where the answer is judged: the block entity compares the answering player's UUID
     * with the one that was asked, re-reads the permission from the live player, and refuses
     * anything whose deadline, container or target slot moved on. Nothing about the request is taken
     * from the command — the player supplies only who they are and what they clicked.
     *
     * <p>The block entity comes from the clicker's OWN open menu ({@code player.containerMenu}) and
     * from nowhere else — never from the command text, never from "the computer that last asked
     * anybody" — so a click can only ever write into a computer its clicker has open. That menu
     * requirement is also the only reason a click without it writes nothing: the question itself
     * deliberately outlives the menu, because it is asked and answered in chat (see
     * {@code ProductionComputerBlockEntity#pendingPlaceholder}).
     *
     * <p>Every refusal is answered with its OWN sentence. Three failures used to share one
     * ("the request has expired") — no menu, a container that lapsed, and a genuinely stale
     * question — which left a live refusal in the author's session impossible to diagnose: the
     * reply could not say whether the question was gone, the window was gone or the player was
     * gone.
     */
    private static int answerPlaceholder(CommandContext<CommandSourceStack> context,
            PlaceholderPrompt.Answer answer) {
        ServerPlayer player = context.getSource().getPlayer();
        // Only a player can have been asked (the question is chat in one player's window) and only
        // a player has a menu to bind the answer to, so a console or command-block source is
        // refused instead of being answered on behalf of an identity it does not have.
        if (player == null) {
            return 0;
        }
        if (!(player.containerMenu instanceof ProductionComputerMenu menu) || menu.computer() == null) {
            // The write needs a container the clicker is looking at: without one there is nothing to
            // answer, and the sentence says so instead of pretending the question expired. This is
            // also the shape a player hits when the chat line is still in the log after they closed
            // the computer — re-opening it (and pressing [Compute] again if the question has already
            // been answered) is the way back.
            player.displayClientMessage(ComputerStatus.promptWindowClosed(), false);
            return 0;
        }
        ProductionComputerBlockEntity computer = menu.computer();
        // "The menu is still open" is answered by the identity of the menu the clicker currently
        // has, not by how far they stand from the block: the menu was taken from
        // player.containerMenu one statement ago, so this asserts that the click is answered
        // through the very window the question belongs to, and it keeps asserting it if the handler
        // is ever refactored to obtain the menu from anywhere else.
        boolean menuOpen = player.containerMenu == menu && menu.stillValid(player);
        PlaceholderPrompt.Outcome outcome = computer.answerPlaceholderRequest(player.getUUID(), answer,
                player.hasPermissions(Commands.LEVEL_GAMEMASTERS), menuOpen, player.serverLevel().getGameTime());
        switch (outcome) {
            // The write already happened (and set the result code that describes it), so the reply
            // is the very status the panel shows — "placeholder written, here is why the line was
            // not derived, finish it in an anvil".
            case WRITE, NO_CARRIER -> sendStatus(player, computer);
            case DECLINED -> player.displayClientMessage(ComputerStatus.promptDeclined(), false);
            // A live question that is not this player's to answer (a stranger, or an operator whose
            // permission was taken away). Its own sentence: the question is STILL standing for the
            // player who was asked, so "expired" would be actively misleading.
            case REJECTED -> player.displayClientMessage(ComputerStatus.promptRejected(), false);
            // The question is still this player's and still fresh, but the container behind their
            // menu is not the live one any more: re-opening the computer is what fixes it, so say
            // that instead of "expired".
            case WINDOW_GONE -> player.displayClientMessage(ComputerStatus.promptWindowGone(), false);
            default -> player.displayClientMessage(ComputerStatus.promptExpired(), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Sends the computer's current status privately to one player. */
    private static void sendStatus(ServerPlayer player, ProductionComputerBlockEntity computer) {
        var inventory = computer.getInventory();
        for (Component line : ComputerStatus.lines(computer.getResultCode(), computer.getLastErrorCode(),
                inventory.getItem(ProductionComputerBlockEntity.SLOT_SCHEME),
                inventory.getItem(ProductionComputerBlockEntity.SLOT_CLIPBOARD))) {
            player.displayClientMessage(line, false);
        }
    }
}
