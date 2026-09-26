package com.create.productionline.block.entity;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;

/**
 * The Production Computer's "may I write a placeholder?" question, as a pure decision table.
 *
 * <p>When a compute fails because the target's recipe cannot be turned into a Create line, the
 * computer no longer writes the placeholder on its own: it asks the operator first, privately, with
 * two clickable answers. That question is <b>state</b> — it belongs to one player, one target item
 * and one moment — and the state has to be judged identically by the click handler and by the QA
 * self test, so the judgement lives here as a pure function of the recorded {@link Pending} and of
 * the facts at the moment the answer arrived ({@link Moment}). The block entity owns the state and
 * performs the write; this class owns the rules.
 *
 * <p>The checks run in a fixed order, and the order <em>is</em> the rule: a later check must never
 * be reachable by a state an earlier one already refused.
 *
 * <ol>
 *   <li>nothing pending → {@link Outcome#EXPIRED}: a click with no question behind it (an old chat
 *       line, a re-opened menu, a hand-typed command);</li>
 *   <li>somebody else's click → {@link Outcome#REJECTED}: a stranger must not be able to answer,
 *       and — just as important — must not be able to <em>consume</em> the asker's question, so the
 *       pending state is deliberately left untouched;</li>
 *   <li>no authoring permission → {@link Outcome#REJECTED}: the permission level is re-checked on
 *       the server at the moment of the click. The command's own requirement is a convenience for
 *       tab completion, never the check;</li>
 *   <li>past the deadline, menu closed, or the target slot no longer holds the item that was asked
 *       about → {@link Outcome#EXPIRED}: the question no longer describes the world it came from,
 *       and answering it would write a scheme for an item the player is no longer computing;</li>
 *   <li>declined → {@link Outcome#DECLINED};</li>
 *   <li>accepted with no carrier left → {@link Outcome#NO_CARRIER}: there is nothing to write the
 *       scheme on, which is the same refusal the compute itself reports ({@code RESULT_NO_SCHEME});
 *       </li>
 *   <li>otherwise → {@link Outcome#WRITE}.</li>
 * </ol>
 */
public final class PlaceholderPrompt {

    /**
     * How long an unanswered question stays valid: 30 s at 20 ticks/s, so a player who is reading
     * the prompt (or finishing a sentence in chat) still finds it working, while a prompt left over
     * from a session that was put down does not write into a slot the player has since re-purposed.
     */
    public static final long TIMEOUT_TICKS = 600L;

    /**
     * The click targets. They live here, next to the state they answer, so the command
     * registration and the clickable chat buttons can never drift apart: a renamed command whose
     * prompt still points at the old name is a button that silently stops working.
     */
    public static final String ACCEPT_COMMAND = "/cpl placeholder accept";
    public static final String DECLINE_COMMAND = "/cpl placeholder decline";

    /** The one question standing: who was asked, about which target, until when. */
    public record Pending(UUID asker, ResourceLocation targetId, long deadline) {

        /**
         * True when {@code player} is the player this question was put to.
         *
         * <p>{@code asker} is null only for a run that had no player at all, which a headless
         * dedicated server produces in exactly one way: the QA self test drives
         * {@code runCompute(boolean)} directly. No prompt is ever displayed then, and production
         * cannot reach that state — {@code computeProvided} only grants the permission to a real
         * {@code ServerPlayer}, and the click command refuses a non-player source instead of passing
         * a null actor — so a null actor matching a null asker is the self test answering its own
         * question and nothing a client can aim at.
         */
        public boolean asked(UUID player) {
            return Objects.equals(asker, player);
        }

        /** True once the deadline has passed; the boundary itself is still valid. */
        public boolean isExpired(long now) {
            return now > deadline;
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
        /** No question, or one that is no longer valid: nothing is written. */
        EXPIRED,
        /** A live question, but not this player's to answer (or not this player's to author). */
        REJECTED,
        /** Accepted, but the carriers are gone: nothing to write on. */
        NO_CARRIER;

        /**
         * Whether the question is settled and must never be answered again. {@link #REJECTED} is
         * the one outcome that leaves it standing: the asker still has a live question, and a
         * stranger's click (or a player whose permission was revoked) must not silently take it
         * away from them.
         */
        public boolean clearsPending() {
            return this != REJECTED;
        }
    }

    /** The world as it is when the answer arrives — gathered by the caller, never carried by it. */
    public record Moment(UUID actor, Answer answer, boolean permitted, boolean menuOpen,
            String currentTargetId, boolean carrierAvailable, long now) {
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
            return Outcome.EXPIRED;
        }
        if (!pending.asked(moment.actor())) {
            return Outcome.REJECTED;
        }
        if (!moment.permitted()) {
            return Outcome.REJECTED;
        }
        if (pending.isExpired(moment.now())
                || !moment.menuOpen()
                || !pending.targetId().toString().equals(moment.currentTargetId())) {
            return Outcome.EXPIRED;
        }
        if (moment.answer() != Answer.ACCEPT) {
            // Anything that is not an explicit ACCEPT declines. A malformed click must not be the
            // one shape of input that writes something.
            return Outcome.DECLINED;
        }
        return moment.carrierAvailable() ? Outcome.WRITE : Outcome.NO_CARRIER;
    }
}
