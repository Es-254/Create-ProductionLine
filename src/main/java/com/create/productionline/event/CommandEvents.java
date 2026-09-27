package com.create.productionline.event;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.block.entity.PlaceholderPrompt;
import com.create.productionline.block.entity.PlaceholderRequests;
import com.create.productionline.block.entity.ProductionComputerBlockEntity;
import com.create.productionline.menu.ComputerStatus;
import com.create.productionline.recipegen.RecipeHotSwap;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * {@link PlaceholderPrompt}, and {@link PlaceholderRequests} for where the standing offer lives).
 * It answers with nothing but a private line, and everything it can possibly write is an item whose
 * recipe id is empty. It needs no open menu: the offer is looked up by the clicker's UUID and the
 * computer is resolved from the offer's own recorded level and position.
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
     * checked where the answer is judged. The click carries no authority and no information beyond
     * "who clicked, and what": no item, no target id, no position. The question is looked up in
     * {@link PlaceholderRequests} <em>by the clicker's own UUID</em> — so a player can only ever
     * answer their own offer — and the computer is resolved from the level and position that
     * question recorded, never from anything the command said.
     *
     * <p>No menu is required, and that is the point: the question is asked and answered in chat, and
     * the clickable line is clicked with a chat screen open, which the client shows <em>in place
     * of</em> the computer's window. Requiring the window made the two halves of the feature
     * mutually exclusive (the author's words: 不是我开着界面怎么点聊天框啊). What protects the write
     * instead is what always did: the answer is judged against the live world — the asker's UUID, the
     * permission re-read from the live player, the 30 s deadline, and the target slot of the computer
     * the question was asked at — and the write can only ever be the inert placeholder that block
     * entity writes (target, EMPTY {@code RecipeId}, zero steps).
     *
     * <p>Every refusal is answered with its OWN sentence: "nothing is standing for you", "your
     * permission is gone", "the 30 s are up" and "that computer or its target moved on" are four
     * different situations with four different remedies, and one shared sentence for them is what
     * made the first live report of a refused click impossible to diagnose.
     */
    private static int answerPlaceholder(CommandContext<CommandSourceStack> context,
            PlaceholderPrompt.Answer answer) {
        ServerPlayer player = context.getSource().getPlayer();
        // Only a player can have been asked (the question is chat in one player's window), and only
        // a player has a UUID to look the offer up by, so a console or command-block source is
        // refused instead of being answered on behalf of an identity it does not have.
        if (player == null) {
            return 0;
        }
        java.util.UUID actor = player.getUUID();
        PlaceholderPrompt.Pending pending = PlaceholderRequests.peek(actor);
        // The computer comes from the question's own record (level + position) and from nowhere
        // else, so a click cannot aim the write at a block of its choosing.
        ServerLevel level = pending == null ? null : player.server.getLevel(pending.dimension());
        ProductionComputerBlockEntity computer =
                level != null && level.getBlockEntity(pending.pos()) instanceof ProductionComputerBlockEntity be
                        ? be
                        : null;
        // The deadline was recorded on the clock of the level the question was asked in; a player
        // who walked into another dimension since still gets that same clock this way.
        long now = level != null ? level.getGameTime() : player.serverLevel().getGameTime();
        boolean permitted = player.hasPermissions(Commands.LEVEL_GAMEMASTERS);
        PlaceholderPrompt.Outcome outcome;
        if (computer != null) {
            outcome = computer.answerPlaceholderRequest(pending, actor, answer, permitted, now);
        } else {
            // No computer at the recorded place any more (broken, moved, or its chunk is gone): the
            // question cannot be answered, so it is judged without one and settled if it is over.
            outcome = PlaceholderPrompt.decide(pending,
                    PlaceholderPrompt.Moment.withoutComputer(actor, answer, permitted, now));
            if (pending != null && outcome.clearsPending()) {
                PlaceholderRequests.remove(pending.asker());
            }
            ProductionLineMod.LOGGER.info(
                    "CPL placeholder: {} from {} -> {} (pending {}, no computer at {} in {})",
                    answer, actor, outcome, pending != null,
                    pending == null ? "-" : pending.pos(),
                    pending == null ? "-" : pending.dimension().location());
        }
        switch (outcome) {
            // The write already happened (and set the result code that describes it), so the reply
            // is the very status the panel shows — "placeholder written, here is why the line was
            // not derived, finish it in an anvil".
            case WRITE, NO_CARRIER -> sendStatus(player, computer);
            case DECLINED -> player.displayClientMessage(ComputerStatus.promptDeclined(), false);
            // A live offer this player may not use (their permission was taken away). Its own
            // sentence: the offer is still standing for them, so "expired" would be misleading.
            case REJECTED -> player.displayClientMessage(ComputerStatus.promptRejected(), false);
            case EXPIRED -> player.displayClientMessage(ComputerStatus.promptExpired(), false);
            case TARGET_MOVED -> player.displayClientMessage(ComputerStatus.promptTargetMoved(), false);
            default -> player.displayClientMessage(ComputerStatus.promptNotAsked(), false);
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
