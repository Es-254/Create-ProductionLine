package com.create.productionline.block.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The standing "may I write a placeholder?" offers, one per player, server-side and transient.
 *
 * <p>This exists because the question is asked and answered in <b>chat</b>: the two clickable
 * answers sit in a chat line, and a chat line is clicked with a chat screen open — which the client
 * shows <em>in place of</em> the computer's window. Requiring the computer's own menu to still be
 * open at click time therefore made the feature unusable in the ordinary case (the author's words:
 * 不是我开着界面怎么点聊天框啊), and no amount of validation inside the menu could fix that: the
 * answer and the window are mutually exclusive by construction.
 *
 * <p>So the offer is not held by the menu and not looked up through one. It is held here, keyed by
 * the player who was asked, and it records everything the answer needs to be judged without asking
 * the clicker for anything: <b>who</b> was asked (the key), <b>where</b> the computer is (its level
 * and position — the block entity is resolved from this, never from the command text), <b>what</b>
 * the question was about (the target item) and <b>until when</b> it stands (30 s).
 *
 * <p>Nothing here is authority. Every fact is re-read when the click arrives: the asker's identity is
 * the key, the permission comes from the live player, the deadline, the target slot and the carrier
 * come from the world, and the write itself can only ever be the inert placeholder (empty
 * {@code RecipeId}, zero steps) this block entity already knows how to write.
 *
 * <p>Lifetime: {@link #put} when a compute asks, and removed by the answer (any settling verdict),
 * by the next compute for that computer ({@link #removeFor}), when the block entity leaves the world
 * ({@link #removeFor}), and — as a whole — when the server stops ({@link #clear}, wired to
 * {@code ServerLifecycleEvents}). It is deliberately NOT saved with the world: a question is about a
 * live chat line and a live clock.
 */
public final class PlaceholderRequests {

    /**
     * One standing offer per asker. The map itself is the mod's only server-side transient state of
     * this kind, and it is cleared on server stop so nothing survives into the next world of the
     * same JVM.
     */
    private static final Map<UUID, PlaceholderPrompt.Pending> STANDING = new ConcurrentHashMap<>();

    private PlaceholderRequests() {
    }

    /** Records (or replaces) the offer standing for its asker. */
    public static void put(PlaceholderPrompt.Pending pending) {
        STANDING.put(pending.asker(), pending);
    }

    /** The offer standing for this player, or {@code null}. */
    public static PlaceholderPrompt.Pending peek(UUID asker) {
        return asker == null ? null : STANDING.get(asker);
    }

    /** Drops this player's offer (an answer that settles it, or an offer that can never be used). */
    public static void remove(UUID asker) {
        if (asker != null) {
            STANDING.remove(asker);
        }
    }

    /**
     * Drops every offer that points at this computer: it is asked about a target slot that only
     * exists there, so a question for a computer that is gone (or that just computed again, which
     * records its own new question) must not outlive it.
     *
     * @return how many offers were dropped, so callers can log it
     */
    public static int removeFor(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return 0;
        }
        int before = STANDING.size();
        STANDING.values().removeIf(pending -> pending.isAt(dimension, pos));
        return before - STANDING.size();
    }

    /** True while a question for this computer is standing (whoever it was asked of). */
    public static boolean hasFor(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return false;
        }
        return STANDING.values().stream().anyMatch(pending -> pending.isAt(dimension, pos));
    }

    /** How many offers stand right now (diagnostics, and the QA self test). */
    public static int standing() {
        return STANDING.size();
    }

    /**
     * Forgets every offer. Called when the server stops: this state belongs to one running server,
     * so the next one must not inherit it (a stale entry could otherwise point at a position that
     * happens to hold another computer in the next world).
     */
    public static void clear() {
        STANDING.clear();
    }
}
