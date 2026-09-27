package com.create.productionline.block.entity;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The Production Computer's "may I write a placeholder?" question, as a pure decision table.
 *
 * <p>When a compute fails because the target's recipe cannot be turned into a Create line, the
 * computer no longer writes the placeholder on its own: it asks the operator first, privately, with
 * two clickable answers. That question is <b>state</b> — it belongs to one player, one computer, one
 * target item and one moment — and the state has to be judged identically by the click handler and
 * by the QA self test, so the judgement lives here as a pure function of the recorded {@link Pending}
 * and of the facts at the moment the answer arrived ({@link Moment}). The registry of standing
 * questions is {@link PlaceholderRequests}; the block entity performs the write; this class owns the
 * rules.
 *
 * <p>The question is asked and answered in <b>chat</b>, so the answer must not depend on the
 * computer's own window being open: the click arrives with the player standing wherever they like,
 * holding nothing, with no menu of any kind. What it does depend on is re-read from the world as the
 * click arrives — who is answering, whether they still hold the authoring permission, whether the
 * deadline has passed, and whether the computer recorded in the question is still there with the
 * target item the question named.
 *
 * <p>The checks run in a fixed order, and the order <em>is</em> the rule: a later check must never be
 * reachable by a state an earlier one already refused.
 *
 * <ol>
 *   <li>nothing standing for this player → {@link Outcome#NOT_ASKED}: a click with no question behind
 *       it (an old chat line, a hand-typed command, a question already answered). A stranger's click
 *       lands here too — it finds nothing of its own and, just as important, leaves the asker's offer
 *       untouched;</li>
 *   <li>a question that is not this actor's → {@link Outcome#REJECTED}: kept as a row even though
 *       {@link PlaceholderRequests} only ever hands a click its own question, because "the recorded
 *       asker must be the actor" is the property the rest rests on and it must stay asserted if the
 *       lookup ever changes;</li>
 *   <li>no authoring permission → {@link Outcome#REJECTED}: the permission level is re-read from the
 *       live player at the moment of the click, and this row deliberately leaves the question
 *       standing — a demoted operator must not be able to consume their own offer either. The
 *       command's own requirement is a convenience for tab completion, never the check;</li>
 *   <li>past the deadline → {@link Outcome#EXPIRED}: the offer was good for 30 s and no longer is;</li>
 *   <li>the computer is gone, is no longer ours, or its target slot no longer holds the item the
 *       question named → {@link Outcome#TARGET_MOVED}: answering would write a scheme for something
 *       the player is no longer computing;</li>
 *   <li>declined → {@link Outcome#DECLINED};</li>
 *   <li>accepted with no carrier left → {@link Outcome#NO_CARRIER}: there is nothing to write the
 *       scheme on, which is the same refusal the compute itself reports ({@code RESULT_NO_SCHEME});
 *       </li>
 *   <li>otherwise → {@link Outcome#WRITE}.</li>
 * </ol>
 */
public final class PlaceholderPrompt {

    /**
     * How long an unanswered question stays valid: 30 s at 20 ticks/s, so a player who is reading the
     * prompt (or finishing a sentence in chat) still finds it working, while a prompt left over from
     * a session that was put down does not write into a slot the player has since re-purposed.
     */
    public static final long TIMEOUT_TICKS = 600L;

    /**
     * The click targets. They live here, next to the state they answer, so the command
     * registration and the clickable chat buttons can never drift apart: a renamed command whose
     * prompt still points at the old name is a button that silently stops working.
     */
    public static final String ACCEPT_COMMAND = "/cpl placeholder accept";
    public static final String DECLINE_COMMAND = "/cpl placeholder decline";

    /**
     * The one question standing for one player: who was asked, where the computer is, about which
     * target, until when.
     *
     * <p>{@code dimension}/{@code pos} are what let the answer find its computer while the player has
     * no menu open at all — and they are the reason the click cannot name a computer of its own: the
     * position comes from the recorded question, never from the command.
     */
    public record Pending(UUID asker, ResourceKey<Level> dimension, BlockPos pos,
            ResourceLocation targetId, long deadline) {

        /**
         * True when {@code player} is the player this question was put to.
         *
         * <p>In production neither side is null: the question is only ever recorded for a real
         * {@code ServerPlayer} ({@code computeProvided} reads the permission off one), and the click
         * command refuses a non-player source instead of passing a null actor. The self test drives
         * the same path with a (fake) player, so this stays a real UUID comparison there too.
         */
        public boolean asked(UUID player) {
            return Objects.equals(asker, player);
        }

        /** True once the deadline has passed; the boundary itself is still valid. */
        public boolean isExpired(long now) {
            return now > deadline;
        }

        /** True when this question is about the computer at that place. */
        public boolean isAt(ResourceKey<Level> level, BlockPos blockPos) {
            return Objects.equals(dimension, level) && Objects.equals(pos, blockPos);
        }
    }

    /** The two answers a click can carry. */
    public enum Answer {
        ACCEPT,
        DECLINE
    }

    /** What one answer does. */
    public enum Outcome {
        /** Write the placeholder scheme, then report the status of the write. */
        WRITE,
        /** The asked player said no: nothing is written, ever. */
        DECLINED,
        /** Accepted, but the carriers are gone: nothing to write on. */
        NO_CARRIER,
        /** No question standing for this player: nothing is written. */
        NOT_ASKED,
        /** A live question, but not this player's to answer (or not this player's to author). */
        REJECTED,
        /** The question stood for 30 s without an answer: nothing is written. */
        EXPIRED,
        /** The computer or the item in its target slot moved on: nothing is written. */
        TARGET_MOVED;

        /**
         * Whether the question is settled and must never be answered again. {@link #REJECTED} is the
         * one outcome that leaves it standing: the asker still has a live offer, and a player whose
         * permission was revoked must not lose it by clicking. {@link #NOT_ASKED} also clears
         * nothing — there is no question of this player's to clear, and its callers have no pending
         * to remove in the first place.
         */
        public boolean clearsPending() {
            return this != REJECTED && this != NOT_ASKED;
        }
    }

    /**
     * The world as it is when the answer arrives — gathered by the caller, never carried by it.
     *
     * <p>{@code computerAvailable} is "the block entity recorded in the question is still there and
     * still ours". When it is false there is no target slot and no carrier to read, which is exactly
     * what {@link Moment#withoutComputer} expresses.
     */
    public record Moment(UUID actor, Answer answer, boolean permitted, boolean computerAvailable,
            String currentTargetId, boolean carrierAvailable, long now) {

        /**
         * The moment for a click whose computer is gone: no target slot and no carrier to read, so
         * only the verdicts that need neither are reachable.
         */
        public static Moment withoutComputer(UUID actor, Answer answer, boolean permitted, long now) {
            return new Moment(actor, answer, permitted, false, "", false, now);
        }
    }

    private PlaceholderPrompt() {
    }

    /**
     * Judges one answer against the recorded question. Never writes, never clears anything: the
     * caller acts on the {@link Outcome}, which is what makes the whole table assertable with no
     * server, no player and no menu.
     */
    public static Outcome decide(Pending pending, Moment moment) {
        if (pending == null || moment == null) {
            return Outcome.NOT_ASKED;
        }
        if (!pending.asked(moment.actor())) {
            return Outcome.REJECTED;
        }
        if (!moment.permitted()) {
            return Outcome.REJECTED;
        }
        if (pending.isExpired(moment.now())) {
            return Outcome.EXPIRED;
        }
        if (!moment.computerAvailable()
                || !pending.targetId().toString().equals(moment.currentTargetId())) {
            return Outcome.TARGET_MOVED;
        }
        if (moment.answer() != Answer.ACCEPT) {
            // Anything that is not an explicit ACCEPT declines. A malformed click must not be the
            // one shape of input that writes something.
            return Outcome.DECLINED;
        }
        return moment.carrierAvailable() ? Outcome.WRITE : Outcome.NO_CARRIER;
    }
}
