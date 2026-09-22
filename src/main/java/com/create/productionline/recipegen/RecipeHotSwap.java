package com.create.productionline.recipegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.create.productionline.ProductionLineMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Recipe-level data refresh: brings recipe changes into a running server
 * <em>without</em> the full {@code /reload}.
 *
 * <p>Why this exists: {@code /reload} rebuilds every data-driven registry — all
 * data packs, tags, loot tables, advancements, functions — and re-scans the pack
 * folder. A Scheme Loader only ever changes <em>recipes</em>, so paying for the
 * rest on every activation is both slow (a visible tick spike on a modded world)
 * and wide-reaching (every listener runs again). The pack files stay the source
 * of truth and are still written to {@code world/datapacks/cpl_converted}; this
 * class only decides how the running server learns about them.
 *
 * <p>Two refresh paths, both using public API only (no mixins, no access
 * wideners, no re-implementation of recipe parsing):
 * <ul>
 *   <li>{@link #applyOwned} — injects the recipe union this mod owns. The recipe
 *       set is rebuilt as “everything currently loaded, minus the ids this pack
 *       installed last time, plus the freshly parsed union”, then handed to
 *       {@link RecipeManager#replaceRecipes}. Independent of whether the data
 *       pack has been discovered by the pack repository yet, so an activation
 *       never needs a reload at all.</li>
 *   <li>{@link #reloadRecipes} — re-reads the recipe JSON of <em>every</em> data
 *       pack the server currently knows, by running the recipe reload listener
 *       through the public
 *       {@link net.minecraft.server.packs.resources.PreparableReloadListener#reload}
 *       contract. Same parse rules as a real reload (NeoForge recipe conditions
 *       included, because the listener keeps the condition context it was
 *       injected with), but nothing else is touched. This is what the
 *       {@code /cpl reload recipes} command runs.</li>
 * </ul>
 *
 * <p>Both paths finish with {@link #syncClients}, which reuses the server's own
 * post-reload sync ({@code PlayerList#reloadResources}) so clients, JEI/REI and
 * other data-driven mods see exactly what they would after a {@code /reload}.
 */
public final class RecipeHotSwap {

    /** Namespace of the data pack this mod writes ({@code cpl}). */
    private static final String NAMESPACE = CreateRecipePack.PACK_NAMESPACE;

    /** Which path produced an {@link Outcome}. */
    public enum Mode {
        /** The whole recipe set was re-read from the data packs (recipe listener only). */
        RECIPES_ONLY,
        /** This mod's recipes were parsed and injected into the live set. */
        OWNED_INJECT,
        /** Nothing was applied; the caller should fall back to a full reload. */
        FAILED
    }

    /**
     * Result of one refresh.
     *
     * @param mode      which path ran
     * @param recipes   recipes registered in the manager afterwards
     * @param installed recipes this call added
     * @param millis    wall-clock duration
     * @param detail    failure reason (empty when {@link #ok()})
     */
    public record Outcome(Mode mode, int recipes, int installed, long millis, String detail) {
        public boolean ok() {
            return mode != Mode.FAILED;
        }
    }

    private RecipeHotSwap() {
    }

    // --- path 1: inject the recipes this mod owns -----------------------------

    /**
     * Replaces this mod's recipes in the live {@link RecipeManager} with
     * {@code union}, leaving every other recipe untouched.
     *
     * @param union          file name (without {@code .json}) -&gt; recipe JSON, the
     *                       same map {@link CreateRecipePack} just wrote to disk
     * @param installedBefore file names this pack installed in an earlier call
     *                       (or that were on disk and therefore loaded at server
     *                       start); their ids are dropped so a shrinking union
     *                       really removes recipes
     */
    public static Outcome applyOwned(MinecraftServer server, Map<String, String> union,
            Collection<String> installedBefore) {
        long start = System.nanoTime();
        RecipeManager manager = server.getRecipeManager();
        try {
            List<RecipeHolder<?>> parsed = parseOwned(server, union);
            Set<ResourceLocation> stale = new java.util.HashSet<>();
            for (String name : installedBefore) {
                stale.add(recipeId(name));
            }
            List<RecipeHolder<?>> next = new ArrayList<>(manager.getRecipes().size() + parsed.size());
            for (RecipeHolder<?> holder : manager.getRecipes()) {
                if (!stale.contains(holder.id())) {
                    next.add(holder);
                }
            }
            next.addAll(parsed);
            manager.replaceRecipes(next);
            syncClients(server);
            long millis = millisSince(start);
            ProductionLineMod.LOGGER.info(
                    "CPL recipe hot swap: {} recipe(s) injected, {} live total, {} ms (no /reload)",
                    parsed.size(), manager.getRecipes().size(), millis);
            return new Outcome(Mode.OWNED_INJECT, manager.getRecipes().size(), parsed.size(), millis, "");
        } catch (Exception e) {
            long millis = millisSince(start);
            ProductionLineMod.LOGGER.warn("CPL recipe hot swap refused: {}", e.toString());
            return new Outcome(Mode.FAILED, manager.getRecipes().size(), 0, millis, e.toString());
        }
    }

    /**
     * Parses this pack's recipe payloads into holders, with the same codec the
     * server uses when it reads the data pack from disk.
     *
     * <p>Conditional payloads ({@code neoforge:conditions}) are refused on
     * purpose: evaluating them needs the condition context the reload machinery
     * injects, and a wrongly-loaded conditional recipe is worse than a slow one.
     * Our generator never emits them, so a refusal here means hand-edited files —
     * the caller falls back to a full reload, which handles conditions properly.
     *
     * @throws IllegalStateException when a payload cannot be parsed
     */
    public static List<RecipeHolder<?>> parseOwned(MinecraftServer server, Map<String, String> union)
            throws Exception {
        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<RecipeHolder<?>> holders = new ArrayList<>(union.size());
        for (Map.Entry<String, String> entry : union.entrySet()) {
            JsonObject json = JsonParser.parseString(entry.getValue()).getAsJsonObject();
            if (json.has("neoforge:conditions") || json.has("conditions")) {
                throw new IllegalStateException("recipe " + entry.getKey() + " is conditional");
            }
            String name = entry.getKey();
            Recipe<?> recipe = Recipe.CODEC.parse(ops, json)
                    .getOrThrow(message -> new IllegalStateException("recipe " + name + ": " + message));
            holders.add(new RecipeHolder<>(recipeId(name), recipe));
        }
        return holders;
    }

    /** {@code cpl} namespace id of one union entry (the file name is the path). */
    private static ResourceLocation recipeId(String fileName) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, fileName);
    }

    // --- path 2: re-read recipes of every known data pack ---------------------

    /**
     * Re-reads the recipe JSON of every data pack the server currently knows and
     * installs the result through the recipe reload listener.
     *
     * <p>Deliberately scoped to one listener: no tags, loot tables, advancements
     * or functions are re-read, and the pack repository is not re-scanned — a data
     * pack folder that appeared <em>after</em> the last reload still needs a full
     * reload to be discovered. Within that boundary this is exactly the recipe
     * half of a reload, because the listener is the same object with the same
     * parse rules.
     *
     * <p>The prepare and apply stages run inline on the calling (server) thread:
     * the listener mutates its own maps in {@code apply}, so handing the stages to
     * an executor could publish half-built state to the tick loop. The scan is the
     * server's recipe files only, which is a fraction of a full reload.
     */
    public static Outcome reloadRecipes(MinecraftServer server) {
        long start = System.nanoTime();
        RecipeManager manager = server.getRecipeManager();
        try {
            PreparationBarrier barrier = CompletableFuture::completedFuture;
            manager.reload(barrier, server.getResourceManager(),
                    InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Runnable::run, Runnable::run).join();
            syncClients(server);
            long millis = millisSince(start);
            int count = manager.getRecipes().size();
            ProductionLineMod.LOGGER.info("CPL recipe-only reload: {} recipe(s) re-read from the data packs in {} ms",
                    count, millis);
            return new Outcome(Mode.RECIPES_ONLY, count, count, millis, "");
        } catch (Exception e) {
            long millis = millisSince(start);
            ProductionLineMod.LOGGER.warn("CPL recipe-only reload failed: {}", e.toString());
            return new Outcome(Mode.FAILED, manager.getRecipes().size(), 0, millis, e.toString());
        }
    }

    // --- shared ---------------------------------------------------------------

    /**
     * Tells the clients (and data-driven mods) what the server now has. Reusing
     * the server's own post-reload sync keeps this in step with a {@code /reload}:
     * advancements, {@code OnDatapackSyncEvent}, the tag packet, the recipe packet
     * and the recipe book refresh. Without it a client keeps the recipe list it
     * received when it joined, and recipe viewers keep showing stale data.
     */
    private static void syncClients(MinecraftServer server) {
        server.getPlayerList().reloadResources();
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
