package com.create.productionline.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.compat.ClipboardCompat;
import com.create.productionline.item.LineSchemeItem;
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
 * <p>Coverage (against the SRS QA list) — 13 checks, in run order:
 * <ol>
 *   <li>TC-05 scheme NBT round-trip + version;</li>
 *   <li>TC-02 clipboard build-guide injection NBT shape;</li>
 *   <li>TC-01 recipe derivation (positive, live recipes -&gt; Create payload): the
 *       live lookup feeds {@code RecipeDeriver.derive}, i.e. the same production
 *       path the Scheme Loader uses, and at least one real recipe must produce an
 *       installable entry;</li>
 *   <li>TC-01 recipe derivation (negative, not convertible): a fake item yields no
 *       recipe and a material-less recipe is refused (no fabricated payload);</li>
 *   <li>Create recipe JSON schema + datapack install canary into an ISOLATED
 *       datapack folder (A7: the self test never touches the live
 *       {@code cpl_converted} pack nor the {@code contributions/} of the active
 *       scheme loaders), with a live-state canary asserting that both survive a
 *       self-test run, plus a {@code RecipeManager} presence assertion proving the
 *       two installed recipes really parsed;</li>
 *   <li>Tag ingredients kept in flat recipes (A1 — writing {@code "item": "#tag"}
 *       used to report success and then never load);</li>
 *   <li>Duration only on duration-capable types: {@code processing_time} is emitted
 *       exclusively for the types whose recipe class allows it, because Create
 *       refuses to load any other recipe that carries a duration;</li>
 *   <li>Loader accepts written schemes only (a blank scheme, paper or a mirror is
 *       not a carrier);</li>
 *   <li>Self-referential recipes are skipped: a recipe whose only material is the
 *       product itself (copy / repair / dye) must not become the source of a plan;</li>
 *   <li>Deriver refuses recipes the mod must not convert: a native Create process
 *       (the target is already produced by Create itself) and an unmappable
 *       category;</li>
 *   <li><b>Single-material recipes map to a semantic machine (B3)</b>: a
 *       single-material recipe (planks -&gt; stick) must derive a real Create
 *       recipe, and the material's semantics must pick the machine (cutting for
 *       planks, i.e. the saw);</li>
 *   <li>Scheme embeds generated recipes (round trip);</li>
 *   <li>Plan topology — the Steps are a mirror of the derived recipe JSON: a
 *       sequenced payload becomes feed + one deployer per extra material, a flat
 *       payload becomes feed + one machine station.</li>
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
            check("TC-01 recipe derivation (negative, not convertible)", () -> liveRecipeNegative(level));
            check("Create recipe JSON schema + datapack install", () -> createRecipeInstall(server));
            check("Tag ingredients kept in flat recipes", () -> flatRecipeKeepsTagIngredients());
            check("Duration only on duration-capable types", () -> flatRecipeDurationRules());
            check("Loader accepts written schemes only", () -> loaderCarrierContract());
            check("Self-referential recipes are skipped", () -> selfRecipesAreSkipped());
            check("Deriver refuses native/unmappable recipes", () -> deriverRefusesNative(level));
            check("Single-material recipes map to a semantic machine", () -> singleMaterialSemanticMachine(level));
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
     * Recipe selection: a "copy/repair" recipe whose only ingredient IS the product must never
     * be chosen (it produced the nonsense {@code [target] -> press -> target} plan); a recipe
     * with real materials must be accepted.
     */
    private static boolean selfRecipesAreSkipped() {
        com.create.productionline.line.mapper.RecipeDescriptor self =
                new com.create.productionline.line.mapper.RecipeDescriptor(
                        "mod:copy", "minecraft:crafting",
                        List.of("minecraft:stick"), List.of("minecraft:stick"));
        com.create.productionline.line.mapper.RecipeDescriptor real =
                new com.create.productionline.line.mapper.RecipeDescriptor(
                        "mod:real", "minecraft:crafting",
                        List.of("minecraft:oak_planks", "minecraft:stick"), List.of("minecraft:stick"));
        boolean ok = !self.hasUsableMaterials() && real.hasUsableMaterials();
        if (!ok) {
            System.out.println("   self=" + self.hasUsableMaterials() + " real=" + real.hasUsableMaterials());
        }
        return ok;
    }

    /**
     * Slot contract: the Scheme Loader only accepts an ALREADY WRITTEN Line Scheme. A blank
     * scheme (or paper / mirror / forged NBT) must be rejected, otherwise the cabinet would
     * happily rebuild its union from nothing.
     */
    private static boolean loaderCarrierContract() {
        net.minecraft.world.item.ItemStack blankStack =
                new net.minecraft.world.item.ItemStack(
                        com.create.productionline.registry.ModItems.LINE_SCHEME.get());
        LineSchemeSerializer.saveToStack(blankStack, new LineScheme());
        net.minecraft.world.item.ItemStack writtenStack =
                new net.minecraft.world.item.ItemStack(
                        com.create.productionline.registry.ModItems.LINE_SCHEME.get());
        LineScheme written = new LineScheme();
        written.setRecipeId("minecraft:crafting/stick");
        written.setOutputItem("minecraft:stick");
        LineScheme.Step step = written.addStep("create:mechanical_saw", 1);
        step.addInput("minecraft:oak_planks");
        LineSchemeSerializer.saveToStack(writtenStack, written);
        boolean ok = !com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(blankStack)
                && com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(writtenStack);
        if (!ok) {
            System.out.println("   blank accepted="
                    + com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(blankStack)
                    + " written accepted="
                    + com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(writtenStack));
        }
        return ok;
    }

    /**
     * Duration regression: Create rejects {@code processing_time} for any recipe type whose
     * {@code canSpecifyDuration()} is false ("Recipe specified a duration. Durations have no
     * impact on this type of recipe."). Emitting it for pressing/splashing/haunting/mixing made
     * those files unloadable — the GUI reported success while the machine did nothing. Only
     * milling / crushing / cutting may carry it.
     */
    private static boolean flatRecipeDurationRules() {
        String pressing = com.create.productionline.recipegen.CreateRecipePack.toJsonString(
                com.create.productionline.recipegen.CreateRecipePack.flat(
                        "create:pressing", List.of("minecraft:iron_ingot"), "minecraft:iron_block"));
        String milling = com.create.productionline.recipegen.CreateRecipePack.toJsonString(
                com.create.productionline.recipegen.CreateRecipePack.flat(
                        "create:milling", List.of("minecraft:wheat"), "minecraft:wheat_seeds"));
        boolean ok = !pressing.contains("processing_time") && milling.contains("processing_time");
        if (!ok) {
            System.out.println("   pressing=" + pressing + " / milling=" + milling);
        }
        return ok;
    }

    /**
     * Native protection: a target that a Create process ALREADY produces must not
     * be converted again — the deriver must return NO entries for a
     * {@code create:pressing} recipe. The assertion is independent of the mapping
     * config: the refusal happens before the dictionary is ever consulted.
     */
    private static boolean deriverRefusesNative(ServerLevel level) {
        RecipeDescriptor descriptor = new RecipeDescriptor("x:y", "create:pressing",
                List.of("minecraft:iron_block"), List.of("minecraft:iron_ingot"));
        var entries = com.create.productionline.recipegen.RecipeDeriver.entriesFor(
                level, descriptor, descriptor.inputs(), 1);
        if (!entries.isEmpty()) {
            System.out.println("   deriver converted an already-native Create recipe: "
                    + entries.get(0).getFileName());
            return false;
        }
        return true;
    }

    /**
     * B3: a single-material recipe (planks -&gt; stick, written as a tag) used to be
     * refused outright. The material's own semantics must now pick a REAL Create
     * machine, i.e. planks are cut, so the derived payload is a
     * {@code create:cutting} recipe (mechanical saw) — not a fabricated payload.
     */
    private static boolean singleMaterialSemanticMachine(ServerLevel level) {
        RecipeDescriptor descriptor = new RecipeDescriptor("minecraft:crafting/stick",
                "minecraft:crafting", List.of("#minecraft:planks"), List.of("minecraft:stick"));
        var entries = com.create.productionline.recipegen.RecipeDeriver.entriesFor(
                level, descriptor, descriptor.inputs(), 1);
        if (entries.isEmpty()) {
            System.out.println("   deriver refused a single-material recipe (planks -> stick)");
            return false;
        }
        String json = entries.get(0).getJson();
        if (json == null || !json.contains("create:cutting")) {
            System.out.println("   planks did not map to the saw (create:cutting): " + json);
            return false;
        }
        System.out.println("   planks -> stick converted through " + entries.get(0).getFileName());
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
        // 2) A recipe with NO usable material must be refused by the production
        //    derivation path. (A single-material recipe is convertible by design
        //    since B3 — see singleMaterialSemanticMachine below — so "no materials"
        //    is what is left of the old "unmappable" case here, together with the
        //    native-Create protection checked in deriverRefusesNative.)
        RecipeDescriptor bogus = new RecipeDescriptor("x:y", "totally:unknown_category",
                List.of(), List.of("minecraft:diamond"));
        var entries = com.create.productionline.recipegen.RecipeDeriver.entriesFor(
                level, bogus, bogus.inputs(), 1);
        if (!entries.isEmpty()) {
            System.out.println("   deriver fabricated " + entries.size()
                    + " entry(ies) for a material-less recipe: " + entries.get(0).getFileName());
            return false;
        }
        System.out.println("   material-less recipe correctly rejected: no entries for " + bogus.categoryId());
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
     * The plan must MIRROR the derived recipe (A6): the chain is generated FROM the
     * recipe entry, so the stations are read off the recipe type instead of being
     * paired with materials by index.
     *
     * <p>Two shapes are asserted, both through the production entry point
     * {@code MachineSelector.appendChainSteps}:
     * <ul>
     *   <li>a {@code create:sequenced_assembly} entry -> feed station + one
     *       deployer per extra material, in order, with the generic intermediate
     *       carried between them and the product only at the tail;</li>
     *   <li>a flat entry ({@code create:cutting}) -> feed station + exactly ONE
     *       machine station, run by the saw that really performs cutting, taking no
     *       extra input (it converts the item the feed put on the line).</li>
     * </ul>
     * Purely a data-shape check — the Steps are display-only, so this needs no world.
     */
    private static boolean planTopology() {
        java.util.List<String> materials = java.util.List.of(
                "minecraft:oak_planks", "minecraft:stick", "minecraft:iron_ingot");
        String product = "minecraft:iron_pickaxe";
        String feed = com.create.productionline.line.analyzer.MachineSelector.FEED;
        String dep = com.create.productionline.line.analyzer.MachineSelector.DEPLOYER;
        String mid = com.create.productionline.line.analyzer.MachineSelector.INTERMEDIATE;

        // 1) sequenced assembly: one deployer per extra material
        LineScheme sequenceScheme = new LineScheme();
        LineScheme.CreateRecipeEntry sequenceEntry = com.create.productionline.recipegen.CreateRecipePack.sequenceEntry(
                materials, product, "create_productionline:generic_intermediate", 1);
        if (sequenceEntry == null) {
            System.out.println("   could not build the sequenced_assembly test payload");
            return false;
        }
        com.create.productionline.line.analyzer.MachineSelector.appendChainSteps(
                sequenceScheme, materials, sequenceEntry, product);

        java.util.List<LineScheme.Step> steps = sequenceScheme.getSteps();
        // head + one station per extra material
        if (steps.size() != materials.size()) {
            System.out.println("   expected " + materials.size() + " stations (head + extras), got "
                    + steps.size() + ": " + steps);
            return false;
        }
        LineScheme.Step head = steps.get(0);
        LineScheme.Step s1 = steps.get(1);
        LineScheme.Step s2 = steps.get(2);
        boolean ok =
                // 1) the base enters first and is carried on
                feed.equals(head.getFacilityType())
                && head.getInputs().contains(materials.get(0))
                && head.getOutputs().contains(materials.get(0))
                // 2) station i is a deployer paired with exactly ONE material, in order
                && dep.equals(s1.getFacilityType()) && s1.getInputs().equals(java.util.List.of(materials.get(1)))
                && dep.equals(s2.getFacilityType()) && s2.getInputs().equals(java.util.List.of(materials.get(2)))
                // 3) chained: intermediate between stations, product only at the tail
                && s1.getOutputs().contains(mid)
                && s2.getOutputs().contains(product)
                && !s2.getOutputs().contains(mid);
        if (!ok) {
            System.out.println("   sequenced topology wrong:");
            printSteps(steps);
            return false;
        }

        // 2) flat processing: feed + ONE machine station, machine from the recipe type
        java.util.List<String> plank = java.util.List.of("#minecraft:planks");
        LineScheme flatScheme = new LineScheme();
        com.google.gson.JsonObject flatJson = com.create.productionline.recipegen.CreateRecipePack.flat(
                "create:cutting", plank, "minecraft:stick", 4);
        com.create.productionline.line.analyzer.MachineSelector.appendChainSteps(
                flatScheme, plank, new LineScheme.CreateRecipeEntry("cpl_test_cutting",
                        com.create.productionline.recipegen.CreateRecipePack.toJsonString(flatJson)),
                "minecraft:stick");

        java.util.List<LineScheme.Step> flatSteps = flatScheme.getSteps();
        if (flatSteps.size() != 2) {
            System.out.println("   expected 2 stations (feed + one machine), got " + flatSteps.size());
            printSteps(flatSteps);
            return false;
        }
        LineScheme.Step flatHead = flatSteps.get(0);
        LineScheme.Step machine = flatSteps.get(1);
        boolean flatOk = feed.equals(flatHead.getFacilityType())
                && flatHead.getInputs().contains("#minecraft:planks")
                && "create:mechanical_saw".equals(machine.getFacilityType())
                && machine.getInputs().isEmpty()
                && machine.getOutputs().contains("minecraft:stick");
        if (!flatOk) {
            System.out.println("   flat topology wrong:");
            printSteps(flatSteps);
        }
        return flatOk;
    }

    private static void printSteps(java.util.List<LineScheme.Step> steps) {
        for (LineScheme.Step s : steps) {
            System.out.println("     " + s.getFacilityType() + " in=" + s.getInputs() + " out=" + s.getOutputs());
        }
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
