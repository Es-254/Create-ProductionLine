package com.create.productionline.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.compat.ClipboardCompat;
import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.line.mapper.Mappers;
import com.create.productionline.line.mapper.RecipeDescriptor;
import com.create.productionline.line.mapper.ServerRecipeLookup;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.registry.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Headless, server-side QA self test. Activated by
 * {@code -Dcreate_productionline.selfTest=true}. Runs against the real game
 * registries / NBT / component system / recipe manager, prints one line per
 * check and stops the server afterwards.
 *
 * <p>Coverage (against the SRS QA list) — 9 checks, in run order:
 * <ol>
 *   <li>TC-05 scheme NBT round-trip + version;</li>
 *   <li>TC-02 clipboard build-guide injection NBT shape;</li>
 *   <li>TC-01 recipe derivation (positive, live recipes -&gt; Create payload): the
 *       live lookup feeds {@code RecipeDeriver.derive}, i.e. the same production
 *       path the Scheme Loader uses, and at least one real recipe must produce an
 *       installable entry;</li>
 *   <li>TC-01 recipe derivation (negative, unmappable): a fake item yields no
 *       recipe and an unknown category is refused (no fabricated payload);</li>
 *   <li>Create recipe JSON schema + datapack install canary into an ISOLATED
 *       datapack folder (A7: the self test never touches the live
 *       {@code cpl_converted} pack nor the {@code contributions/} of the active
 *       scheme loaders), with a live-state canary asserting that both survive a
 *       self-test run, plus a {@code RecipeManager} presence assertion proving the
 *       two installed recipes really parsed;</li>
 *   <li>Tag ingredients kept in flat recipes (A1 — writing {@code "item": "#tag"}
 *       used to report success and then never load);</li>
 *   <li>Deriver refuses an unconvertible single-material crafting recipe instead
 *       of writing a fake one (skipped when the mapping config makes it
 *       convertible, so a user config cannot cause a false negative);</li>
 *   <li>Scheme embeds generated recipes (round trip);</li>
 *   <li>Plan topology (chain: base -&gt; machine+material -&gt; product).</li>
 * </ol>
 */
public final class SelfTest {

    private static final AtomicInteger PASS = new AtomicInteger();
    private static final AtomicInteger FAIL = new AtomicInteger();

    private SelfTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("create_productionline.selfTest");
    }

    /** Runs all checks against the given server; returns true when all passed. */
    public static boolean runAll(MinecraftServer server) {
        try {
            ServerLevel level = server.overworld();
            // The mapping config is already (re)loaded from the real game config
            // directory at ServerStartingEvent — do not reload from the save folder.

            check("TC-05 scheme NBT round-trip", () -> schemeRoundTrip());
            check("TC-02 clipboard guide injection", () -> clipboardInjection(level.registryAccess()));
            check("TC-01 recipe derivation (positive, live recipes)", () -> liveRecipePositive(level));
            check("TC-01 recipe derivation (negative, unmappable)", () -> liveRecipeNegative(level));
            check("Create recipe JSON schema + datapack install", () -> createRecipeInstall(server));
            check("Tag ingredients kept in flat recipes", () -> flatRecipeKeepsTagIngredients());
            check("Deriver refuses unconvertible recipe", () -> deriverRefusesUnconvertible(level));
            check("Scheme embeds generated recipes (round trip)", () -> schemeEmbedsRecipes());
            check("Plan topology (chain: base -> machine+material -> product)", () -> planTopology());
        } catch (Throwable t) {
            fail("self-test crashed: " + t);
            t.printStackTrace(System.out);
        }
        int pass = PASS.get();
        int failCount = FAIL.get();
        System.out.println("======================================================");
        System.out.println("CPL SELF-TEST RESULT: " + pass + " passed, " + failCount + " failed");
        System.out.println("======================================================");
        return failCount == 0;
    }

    private static boolean createRecipeInstall(MinecraftServer server) throws Exception {
        // Build two spec-conformant Create recipe files and install them into a
        // TEMPORARY datapack; the server reload acts as a real datapack parse canary.
        //
        // A7: the self test uses its own datapack directory
        // (SELFTEST_FOLDER = cpl_converted_selftest) and only ever calls
        // installIsolated/removeIsolated. The live cpl_converted pack and the
        // "contributions/" of the active scheme loaders are NEVER touched — the
        // production install()/deactivate() pair starts by wiping packRoot, so
        // using it here would clear every loader's activation state on each run.
        var flat = com.create.productionline.recipegen.CreateRecipePack.flat("create:mixing",
                List.of("minecraft:iron_ingot", "minecraft:charcoal"), "create:andesite_alloy");
        var craft = com.create.productionline.recipegen.CreateRecipePack.mechanical(
                List.of("AB", "BA"), java.util.Map.of('A', "minecraft:oak_planks", 'B', "minecraft:stick"), "minecraft:crafting_table");

        String flatStr = com.create.productionline.recipegen.CreateRecipePack.toJsonString(flat);
        String craftStr = com.create.productionline.recipegen.CreateRecipePack.toJsonString(craft);
        if (!flatStr.contains("\"type\": \"create:mixing\"") || !flatStr.contains("\"id\": \"create:andesite_alloy\"")) {
            System.out.println("   flat JSON schema wrong: " + flatStr);
            return false;
        }
        if (!craftStr.contains("\"type\": \"create:mechanical_crafting\"") || !craftStr.contains("\"pattern\"")) {
            System.out.println("   mechanical JSON schema wrong: " + craftStr);
            return false;
        }
        var files = new java.util.LinkedHashMap<String, String>();
        files.put("cpl_test_mixing", flatStr);
        files.put("cpl_test_mechanical", craftStr);

        // Live-state canary: snapshot what the production pack owns before the run
        // ("contributions" mirrors CreateRecipePack's private CONTRIB_DIR).
        Path livePack = server.getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(com.create.productionline.recipegen.CreateRecipePack.PACK_FOLDER);
        Path liveContributions = livePack.resolve("contributions");
        boolean livePackExisted = Files.exists(livePack);
        boolean contributionsExisted = Files.exists(liveContributions);

        try {
            int written = com.create.productionline.recipegen.CreateRecipePack.installIsolated(server, files);
            System.out.println("   installed test recipe files: " + written);
            if (written != 2) {
                return false;
            }
            // False-positive guard: "2 files written" only proves the files landed
            // on disk — a datapack the server refuses to parse still reports a full
            // file count. Both test recipes must therefore be present in the LIVE
            // RecipeManager, which is what a real recipe conversion depends on.
            ResourceLocation mixingId = ResourceLocation.fromNamespaceAndPath("cpl_selftest", "cpl_test_mixing");
            ResourceLocation mechanicalId = ResourceLocation.fromNamespaceAndPath("cpl_selftest", "cpl_test_mechanical");
            boolean mixingLoaded = server.getRecipeManager().byKey(mixingId).isPresent();
            boolean mechanicalLoaded = server.getRecipeManager().byKey(mechanicalId).isPresent();
            if (!mixingLoaded || !mechanicalLoaded) {
                System.out.println("   installIsolated reported " + written + " file(s) but the RecipeManager did not load them: "
                        + mixingId + " present=" + mixingLoaded + ", " + mechanicalId + " present=" + mechanicalLoaded);
                return false;
            }
            System.out.println("   loaded into RecipeManager: " + mixingId + ", " + mechanicalId);
            // The isolated install must not have removed the live pack / contributions.
            if ((livePackExisted && !Files.exists(livePack))
                    || (contributionsExisted && !Files.exists(liveContributions))) {
                System.out.println("   self test destroyed live datapack state: " + livePack);
                return false;
            }
            return true;
        } finally {
            // Always remove the temporary self-test pack (and reload), even on failure;
            // the live cpl_converted pack stays exactly as it was.
            com.create.productionline.recipegen.CreateRecipePack.removeIsolated(server);
        }
    }

    /**
     * A1 regression: a flat payload built from {@code "#tag"} inputs must emit a
     * {@code tag} ingredient. Writing {@code "item": "#tag"} produced an
     * ingredient that reported success yet never loaded, so the conversion
     * silently did nothing in game.
     */
    private static boolean flatRecipeKeepsTagIngredients() {
        var json = com.create.productionline.recipegen.CreateRecipePack.flat("create:mixing",
                List.of("#c:ingots/steel", "minecraft:charcoal"), "create:andesite_alloy");
        String str = com.create.productionline.recipegen.CreateRecipePack.toJsonString(json);
        boolean ok = str.contains("\"tag\": \"c:ingots/steel\"")
                && !str.contains("\"item\": \"#c:ingots/steel\"");
        if (!ok) {
            System.out.println("   tag ingredient not preserved in flat recipe: " + str);
        }
        return ok;
    }

    /**
     * A1 regression: a single-material crafting recipe (e.g. planks -> stick) has
     * no Create equivalent, so the deriver must return NO entries. Emitting a
     * fake flat/assembly recipe here would activate a payload that can never
     * match — the exact "claims success, does nothing" failure mode.
     */
    private static boolean deriverRefusesUnconvertible(ServerLevel level) {
        RecipeDescriptor descriptor = new RecipeDescriptor("minecraft:stick", "minecraft:crafting",
                List.of("minecraft:oak_planks"), List.of("minecraft:stick"));
        // Config-drift premise: with the DEFAULT dictionary "minecraft:crafting" has
        // no flat Create method, so this recipe is genuinely unconvertible and the
        // assertion below is meaningful. A user config may map crafting onto a
        // machine that DOES have a flat method — then the recipe is convertible and
        // demanding an empty result would be a false negative, so skip the assertion.
        if (com.create.productionline.recipegen.CreateRecipePack.flatEntry(
                Mappers.getDictionary(), "minecraft:crafting", List.of("minecraft:oak_planks"),
                "minecraft:stick", 1) != null) {
            System.out.println("   skipped: the mapping config maps 'minecraft:crafting' to a flat-capable"
                    + " machine, so this recipe is convertible by design");
            return true;
        }
        var entries = com.create.productionline.recipegen.RecipeDeriver.entriesFor(
                level, descriptor, descriptor.inputs(), 1);
        if (!entries.isEmpty()) {
            System.out.println("   deriver fabricated " + entries.size()
                    + " entry(ies) for an unconvertible recipe: " + entries.get(0).getFileName());
            return false;
        }
        return true;
    }

    // --- individual checks ----------------------------------------------------

    private static boolean schemeRoundTrip() {
        ItemStack stack = new ItemStack(ModItems.LINE_SCHEME.get());
        LineScheme scheme = new LineScheme();
        scheme.setRecipeId("minecraft:crafting/stick");
        scheme.setOutputItem("minecraft:stick");
        LineScheme.Step s1 = scheme.addStep("create:deployer", 1);
        s1.addInput("minecraft:oak_planks");
        s1.addInput("minecraft:stick");
        s1.addOutput("minecraft:stick");
        LineScheme.Step s2 = scheme.addStep("create:mechanical_saw", 1);
        s2.addInput("minecraft:oak_log");
        s2.addOutput("minecraft:oak_planks");
        LineSchemeSerializer.saveToStack(stack, scheme);

        LineScheme loaded = LineSchemeSerializer.fromStack(stack);
        boolean ok = loaded.getVersion() == LineScheme.CURRENT_VERSION
                && loaded.getOutputItem().equals("minecraft:stick")
                && loaded.getRecipeId().equals("minecraft:crafting/stick")
                && loaded.getSteps().size() == 2
                && loaded.getSteps().get(0).getCount() == 1
                && loaded.getSteps().get(0).getInputs().contains("minecraft:oak_planks")
                && loaded.getSteps().get(1).getOutputs().contains("minecraft:oak_planks");
        if (ok) {
            return true;
        }
        System.out.println("   scheme after load: " + loaded);
        return false;
    }

    private static boolean clipboardInjection(RegistryAccess registryAccess) {
        ItemStack paper = new ItemStack(Items.PAPER);
        LineScheme scheme = new LineScheme();
        scheme.setOutputItem("minecraft:iron_ingot");
        LineScheme.Step press = scheme.addStep("create:mechanical_press", 1);
        press.addInput("minecraft:iron_block");
        press.addOutput("minecraft:iron_ingot");
        ClipboardCompat.writeGuide(paper, scheme);

        net.minecraft.world.item.component.CustomData data = paper.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag custom = data.copyTag();
        if (!custom.contains(ClipboardCompat.GUIDE_KEY)) {
            System.out.println("   missing LineBuildGuide");
            return false;
        }
        CompoundTag guide = custom.getCompound(ClipboardCompat.GUIDE_KEY);
        int total = guide.getInt("TotalSteps");
        boolean ok = total >= 1 && guide.contains("Step_1") && guide.contains("Step_" + total);
        if (ok) {
            System.out.println("   guide: TotalSteps=" + total + ", Step_1='" + guide.getString("Step_1") + "'");
        }
        return ok;
    }

    /**
     * TC-01 positive on the PRODUCTION path: the live recipe lookup
     * ({@link ServerRecipeLookup#findDetailed}) feeds
     * {@code RecipeDeriver.derive} — exactly the call the Scheme Loader makes — and
     * at least one real recipe must yield an installable Create payload. The former
     * {@code Mappers.get().map(...)} engine was a dead path that could pass while
     * the real conversion produced nothing.
     */
    private static boolean liveRecipePositive(ServerLevel level) {
        // A recipe that always exists in vanilla data: stone_axe (crafting, cobblestone + stick).
        ResourceLocation target = BuiltInRegistries.ITEM.getKey(Items.STONE_AXE);
        if (target == null) {
            System.out.println("   minecraft:stone_axe is not registered — cannot run the positive check");
            return false;
        }
        List<ServerRecipeLookup.Found> found = ServerRecipeLookup.findDetailed(level, target);
        for (ServerRecipeLookup.Found candidate : found) {
            RecipeDescriptor descriptor = candidate.descriptor();
            var derived = com.create.productionline.recipegen.RecipeDeriver.derive(level, descriptor);
            if (derived.hasEntries()) {
                System.out.println("   derived " + descriptor.recipeId() + " [" + descriptor.categoryId() + "] -> "
                        + derived.entries().get(0).getFileName()
                        + " (materials " + derived.orderedInputs() + ", count " + derived.count() + ")");
                return true;
            }
        }
        System.out.println("   no derivable Create recipe for " + target + " among " + found.size() + " recipe(s)");
        return false;
    }

    /**
     * TC-01 negative: neither the live lookup nor the deriver may fabricate a
     * Create payload for something that cannot be converted.
     */
    private static boolean liveRecipeNegative(ServerLevel level) {
        // 1) A target with no recipe at all must yield nothing.
        List<ServerRecipeLookup.Found> none = ServerRecipeLookup.findDetailed(level,
                ResourceLocation.fromNamespaceAndPath("create_productionline", "not_a_real_item"));
        if (!none.isEmpty()) {
            System.out.println("   unexpected recipes for fake item: " + none.size());
            return false;
        }
        // 2) An unknown category must be refused by the production derivation path.
        RecipeDescriptor bogus = new RecipeDescriptor("x:y", "totally:unknown_category",
                List.of("minecraft:stick"), List.of("minecraft:diamond"));
        var entries = com.create.productionline.recipegen.RecipeDeriver.entriesFor(
                level, bogus, bogus.inputs(), 1);
        if (!entries.isEmpty()) {
            System.out.println("   deriver fabricated " + entries.size()
                    + " entry(ies) for unknown category: " + entries.get(0).getFileName());
            return false;
        }
        System.out.println("   unmapped category correctly rejected: no entries for " + bogus.categoryId());
        return true;
    }

    private static boolean schemeEmbedsRecipes() {
        ItemStack stack = new ItemStack(ModItems.LINE_SCHEME.get());
        LineScheme scheme = new LineScheme();
        scheme.setOutputItem("minecraft:diamond");
        scheme.addCreateRecipe("cpl_diamond_pressing",
                "{\"type\":\"create:pressing\",\"ingredients\":[{\"item\":\"minecraft:diamond_block\"}],\"results\":[{\"id\":\"minecraft:diamond\"}]}");
        LineSchemeSerializer.saveToStack(stack, scheme);
        LineScheme loaded = LineSchemeSerializer.fromStack(stack);
        boolean ok = loaded.getCreateRecipes().size() == 1
                && loaded.getCreateRecipes().get(0).getFileName().equals("cpl_diamond_pressing")
                && loaded.getCreateRecipes().get(0).getJson().contains("create:pressing");
        if (!ok) {
            System.out.println("   embedded recipes lost after round trip: " + loaded.getCreateRecipes());
        }
        return ok;
    }

    /**
     * The plan must read as ONE linear chain:
     * {@code [base] -> [machine1 + material1] -> ... -> [machineN + materialN] -> [product]}.
     * Purely a data-shape check — the Steps are display-only, so this needs no world.
     */
    private static boolean planTopology() {
        java.util.List<String> unique = java.util.List.of(
                "minecraft:oak_planks", "minecraft:stick", "minecraft:iron_ingot");
        String product = "minecraft:cart";
        LineScheme scheme = new LineScheme();
        com.create.productionline.line.analyzer.MachineSelector.appendChainSteps(
                scheme, unique,
                java.util.List.of(com.create.productionline.line.analyzer.MachineSelector.DEPLOYER),
                product);

        java.util.List<LineScheme.Step> steps = scheme.getSteps();
        String feed = com.create.productionline.line.analyzer.MachineSelector.FEED;
        String dep = com.create.productionline.line.analyzer.MachineSelector.DEPLOYER;
        String mid = com.create.productionline.line.analyzer.MachineSelector.INTERMEDIATE;

        // head + one station per extra material
        if (steps.size() != 3) {
            System.out.println("   expected 3 stations (head + 2), got " + steps.size() + ": " + steps);
            return false;
        }
        LineScheme.Step head = steps.get(0);
        LineScheme.Step s1 = steps.get(1);
        LineScheme.Step s2 = steps.get(2);
        boolean ok =
                // 1) the base enters first and is carried on
                feed.equals(head.getFacilityType())
                && head.getInputs().contains(unique.get(0))
                && head.getOutputs().contains(unique.get(0))
                // 2) station i is a machine paired with exactly ONE material, in order
                && dep.equals(s1.getFacilityType()) && s1.getInputs().equals(java.util.List.of(unique.get(1)))
                && dep.equals(s2.getFacilityType()) && s2.getInputs().equals(java.util.List.of(unique.get(2)))
                // 3) chained: intermediate between stations, product only at the tail
                && s1.getOutputs().contains(mid)
                && s2.getOutputs().contains(product)
                && !s2.getOutputs().contains(mid);
        if (!ok) {
            System.out.println("   topology wrong:");
            for (LineScheme.Step s : steps) {
                System.out.println("     " + s.getFacilityType() + " in=" + s.getInputs() + " out=" + s.getOutputs());
            }
        }
        return ok;
    }

    // --- harness ---------------------------------------------------------------

    public interface Check {
        boolean run() throws Exception;
    }

    private static void check(String name, Check check) {
        try {
            if (check.run()) {
                PASS.incrementAndGet();
                System.out.println("[PASS] " + name);
            } else {
                fail(name);
            }
        } catch (Exception e) {
            // Detail line only: fail(name) prints the single "[FAIL] <name>" line.
            System.out.println("   exception: " + e);
            fail(name);
        }
    }

    private static void fail(String name) {
        FAIL.incrementAndGet();
        System.out.println("[FAIL] " + name);
    }
}
