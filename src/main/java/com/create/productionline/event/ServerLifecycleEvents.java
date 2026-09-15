package com.create.productionline.event;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.line.mapper.Mappers;
import com.create.productionline.qa.SelfTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Server lifecycle handling: (re)loads the user-facing recipe mapping config and
 * sweeps orphaned Scheme Loader recipe contributions on every server start, and
 * (only when {@code -Dcreate_productionline.selfTest=true}) runs the headless QA
 * self test once the world is up, then shuts the server down (used by CI-style
 * verification).
 */
public final class ServerLifecycleEvents {

    private static boolean selfTestRan = false;

    private ServerLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Mappers.reload(FMLPaths.CONFIGDIR.get());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Runs on EVERY server start (deliberately before the self-test guard below,
        // which returns early in normal play): a cabinet that was moved by a Create
        // contraption or destroyed while its chunk was unloaded leaves its recipe
        // contribution behind, and only a startup pass can see that the recorded
        // position no longer holds a Scheme Loader.
        int swept = com.create.productionline.recipegen.CreateRecipePack
                .sweepOrphanContributions(event.getServer());
        ProductionLineMod.LOGGER.info("CPL orphan contribution sweep: {} stale contribution file(s) removed", swept);

        if (!SelfTest.isEnabled() || selfTestRan) {
            return;
        }
        selfTestRan = true;
        ProductionLineMod.LOGGER.info("CPL self-test requested: running headless QA checks…");
        boolean passed = SelfTest.runAll(event.getServer());
        ProductionLineMod.LOGGER.info("CPL self-test {}", passed ? "PASSED" : "FAILED");
        // Stop the server so the gradle runServer task can complete with a clear exit.
        event.getServer().halt(passed);
    }
}
