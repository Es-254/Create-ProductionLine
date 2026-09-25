package com.create.productionline.qa;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.block.entity.DismantlerBlockEntity;
import com.create.productionline.compat.ClipboardCompat;
import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.line.mapper.RecipeDescriptor;
import com.create.productionline.line.mapper.ServerRecipeLookup;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.menu.GuiLayout;
import com.create.productionline.registry.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Headless, server-side QA self test. Activated by
 * {@code -Dcreate_productionline.selfTest=true}. Runs against the real game
 * registries / NBT / component system / recipe manager, prints one line per
 * check and stops the server afterwards.
 *
 * <p>Coverage (against the SRS QA list) — 21 checks, in run order:
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
 *   <li>Custom assembly (the OP anvil flow) builds one
 *       {@code create:sequenced_assembly}: the first material as the base, one
 *       {@code create:deploying} step per later material, and a plan that mirrors
 *       it with one Deployer per extra material;</li>
 *   <li>a single-material custom scheme falls back to the semantic single machine
 *       instead of a sequence, which the anvil cannot express;</li>
 *   <li>the target-output / repeat budget: a doubling recipe ({@code A + B = 2A})
 *       must repeat {@code ceil((target - 1) / net gain)} times, and a recipe that
 *       cannot grow the stock must not be looped;</li>
 *   <li>all twelve rows of the anvil state machine (clear / hammer / lock / refuse
 *       / not-our-item / non-OP / stacked scheme / …);</li>
 *   <li>the plan reports its material budget (units per pass × passes);</li>
 *   <li>a recipe file that appears AFTER a data pack has been discovered reaches the
 *       live {@code RecipeManager} through the recipe-only refresh, without a full
 *       {@code /reload} (this is what {@code /cpl reload recipes} and every scheme
 *       activation rely on);</li>
 *   <li>this pack's payloads round-trip through the server's recipe codec and land
 *       under the id the data pack would give them, while a conditional payload is
 *       refused instead of being loaded unconditionally.</li>
 *   <li><b>every GUI slot grid and text row fits the well the hand-drawn background
 *       provides</b> (centred, inside its well, clear of the button and of the
 *       player-inventory groove) — the one defect class no resource check can see,
 *       and the reason the loader grid and the dismantler hint are where they are.</li>
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
            check("Custom assembly builds a deployer sequence", () -> customAssemblySequence(level));
            check("Single-material custom scheme falls back to one machine",
                    () -> customSingleMaterialFallback(level));
            check("Doubling recipe repeats to reach the target output", () -> doublingRepeatBudget(level));
            check("Scheme anvil state machine table", () -> schemeAnvilStateTable());
            check("Plan reports the material budget", () -> planMaterialBudget());
            check("Recipe-only reload registers new recipes", () -> recipeOnlyReload(server));
            check("Owned recipes parse for injection", () -> ownedRecipeInjection(server));
            check("GUI layout fits the drawn wells", () -> guiLayoutFits());
            check("Dismantler decision table, doubling refund, fluid notice", () -> dismantlerRules(server));
            check("Computer writes plan + guide onto both carriers", () -> computerWritesBothCarriers(server));
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
     * The recipe-only refresh behind {@code /cpl reload recipes} and behind every
     * scheme activation: a recipe file that appears AFTER the server discovered a
     * data pack must reach the live {@code RecipeManager} without a full
     * {@code /reload}. The isolated self-test pack was just installed — and
     * installing is what made the server discover it — so one extra file written
     * straight into its folder reproduces exactly what a hand-edited data pack does.
     */
    private static boolean recipeOnlyReload(MinecraftServer server) throws Exception {
        var files = new java.util.LinkedHashMap<String, String>();
        files.put("cpl_test_hot", com.create.productionline.recipegen.CreateRecipePack.toJsonString(
                com.create.productionline.recipegen.CreateRecipePack.flat("create:mixing",
                        List.of("minecraft:copper_ingot", "minecraft:coal"), "create:brass_ingot")));
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(com.create.productionline.recipegen.CreateRecipePack.SELFTEST_FOLDER);
        ResourceLocation seeded = ResourceLocation.fromNamespaceAndPath("cpl_selftest", "cpl_test_hot");
        ResourceLocation added = ResourceLocation.fromNamespaceAndPath("cpl_selftest", "cpl_test_hot2");
        try {
            if (com.create.productionline.recipegen.CreateRecipePack.installIsolated(server, files) != 1
                    || server.getRecipeManager().byKey(seeded).isEmpty()) {
                System.out.println("   could not seed the isolated pack with " + seeded);
                return false;
            }
            // Written behind the server's back: only a recipe refresh can notice it.
            Files.writeString(packRoot.resolve("data/cpl_selftest/recipe/cpl_test_hot2.json"),
                    com.create.productionline.recipegen.CreateRecipePack.toJsonString(
                            com.create.productionline.recipegen.CreateRecipePack.flat("create:mixing",
                                    List.of("minecraft:gold_ingot", "minecraft:redstone"), "create:rose_quartz")),
                    StandardCharsets.UTF_8);
            if (server.getRecipeManager().byKey(added).isPresent()) {
                System.out.println("   " + added + " was already loaded — the check would prove nothing");
                return false;
            }
            com.create.productionline.recipegen.RecipeHotSwap.Outcome outcome =
                    com.create.productionline.recipegen.RecipeHotSwap.reloadRecipes(server);
            boolean addedLoaded = server.getRecipeManager().byKey(added).isPresent();
            boolean seededKept = server.getRecipeManager().byKey(seeded).isPresent();
            System.out.println("   recipe-only reload: mode=" + outcome.mode() + ", " + outcome.recipes()
                    + " recipe(s) in " + outcome.millis() + " ms, new=" + addedLoaded
                    + ", earlier=" + seededKept);
            return outcome.ok() && addedLoaded && seededKept;
        } finally {
            com.create.productionline.recipegen.CreateRecipePack.removeIsolated(server);
        }
    }

    /**
     * The primitive a scheme activation uses: this pack's payload must round-trip
     * through the same recipe codec the server uses when it reads the data pack,
     * and must receive the id the data pack gives it ({@code cpl:<file name>}) —
     * the live recipe set is rebuilt from exactly those ids. A conditional payload
     * must be REFUSED here: the caller then falls back to a full reload, which
     * evaluates conditions, instead of loading the recipe unconditionally.
     */
    private static boolean ownedRecipeInjection(MinecraftServer server) throws Exception {
        var union = new java.util.LinkedHashMap<String, String>();
        union.put("cpl_test_inject", com.create.productionline.recipegen.CreateRecipePack.toJsonString(
                com.create.productionline.recipegen.CreateRecipePack.flat("create:pressing",
                        List.of("minecraft:iron_ingot"), "create:iron_sheet")));
        List<RecipeHolder<?>> holders = com.create.productionline.recipegen.RecipeHotSwap.parseOwned(server, union);
        if (holders.size() != 1) {
            System.out.println("   parseOwned produced " + holders.size() + " holder(s), expected 1");
            return false;
        }
        RecipeHolder<?> holder = holders.get(0);
        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath("cpl", "cpl_test_inject");
        ResourceLocation expectedType = ResourceLocation.fromNamespaceAndPath("create", "pressing");
        ResourceLocation actualType = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        System.out.println("   injected holder: " + holder.id() + " type=" + actualType);
        if (!expectedId.equals(holder.id()) || !expectedType.equals(actualType)) {
            return false;
        }
        var conditional = new java.util.LinkedHashMap<String, String>();
        conditional.put("cpl_test_conditional",
                "{\"type\":\"create:pressing\",\"neoforge:conditions\":[],\"ingredients\":[],\"results\":[]}");
        try {
            com.create.productionline.recipegen.RecipeHotSwap.parseOwned(server, conditional);
        } catch (Exception refused) {
            return true;
        }
        System.out.println("   a conditional payload was accepted instead of being refused");
        return false;
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
        // A locked hand-built scheme is a carrier too, even with no Steps at all (a
        // single-material custom line is one machine); a blank scheme stays refused.
        net.minecraft.world.item.ItemStack customStack =
                new net.minecraft.world.item.ItemStack(
                        com.create.productionline.registry.ModItems.LINE_SCHEME.get());
        com.create.productionline.line.scheme.CustomAssembly lockedCustom =
                new com.create.productionline.line.scheme.CustomAssembly(
                        List.of("minecraft:iron_ingot"), true, true, "minecraft:iron_nugget", 1, 1);
        LineSchemeSerializer.saveToStack(customStack,
                com.create.productionline.line.scheme.CustomAssemblyPlanner.rebuild(lockedCustom));
        customStack.set(com.create.productionline.registry.ModDataComponents.CUSTOM_ASSEMBLY.get(), lockedCustom);
        boolean ok = !com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(blankStack)
                && com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(writtenStack)
                && com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(customStack);
        if (!ok) {
            System.out.println("   blank accepted="
                    + com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(blankStack)
                    + " written accepted="
                    + com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(writtenStack)
                    + " locked custom accepted="
                    + com.create.productionline.compat.ClipboardCompat.isLoaderCarrier(customStack));
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
    /**
     * A locked custom scheme with two or more materials must derive exactly one
     * {@code create:sequenced_assembly}: the first material is the base ingredient,
     * every later material is one {@code create:deploying} step, and the plan shows
     * one Deployer per extra material (the default machine for custom assemblies).
     */
    private static boolean customAssemblySequence(ServerLevel level) {
        com.create.productionline.line.scheme.CustomAssembly custom =
                new com.create.productionline.line.scheme.CustomAssembly(
                        List.of("minecraft:iron_block", "minecraft:gold_ingot", "minecraft:diamond"),
                        true, false, "minecraft:iron_ingot", 1, 1);
        var derived = com.create.productionline.line.scheme.CustomAssemblyPlanner.derive(level, custom);
        if (!derived.hasEntries() || derived.entries().size() != 1) {
            System.out.println("   custom assembly derived " + derived.entries().size() + " entries, expected 1");
            return false;
        }
        String json = derived.entries().get(0).getJson();
        if (json == null || !json.contains("create:sequenced_assembly")) {
            System.out.println("   custom assembly did not produce a sequenced assembly: " + json);
            return false;
        }
        int deploySteps = json.split("create:deploying", -1).length - 1;
        if (deploySteps != custom.materials().size() - 1) {
            System.out.println("   expected " + (custom.materials().size() - 1)
                    + " deploy steps, payload has " + deploySteps);
            return false;
        }
        int ingredientAt = json.indexOf("\"ingredient\"");
        int transitionalAt = json.indexOf("\"transitional_item\"");
        if (ingredientAt < 0 || transitionalAt < ingredientAt
                || !json.substring(ingredientAt, transitionalAt).contains(custom.materials().get(0))) {
            System.out.println("   the first material is not the base ingredient: " + json);
            return false;
        }
        LineScheme plan = com.create.productionline.line.scheme.CustomAssemblyPlanner.rebuild(custom);
        long deployers = plan.getSteps().stream()
                .filter(step -> com.create.productionline.line.analyzer.MachineSelector.DEPLOYER
                        .equals(step.getFacilityType()))
                .count();
        if (deployers != custom.materials().size() - 1) {
            System.out.println("   plan shows " + deployers + " deployers, expected "
                    + (custom.materials().size() - 1));
            return false;
        }
        System.out.println("   " + custom.materials().size() + " materials -> 1 sequence entry, "
                + deploySteps + " deploy steps, plan mirrors it");
        return true;
    }

    /**
     * The anvil cannot express a single-material sequence ({@code sequenceEntry}
     * returns null below two materials), so a scheme locked with one material
     * carries the fallback flag and must derive the semantic single machine instead
     * of a {@code create:sequenced_assembly}.
     */
    private static boolean customSingleMaterialFallback(ServerLevel level) {
        com.create.productionline.line.scheme.CustomAssembly custom =
                new com.create.productionline.line.scheme.CustomAssembly(
                        List.of("minecraft:iron_ingot"), true, true, "minecraft:iron_nugget", 1, 1);
        if (!custom.singleMaterialFallback()) {
            System.out.println("   the fallback flag was not recorded on the component");
            return false;
        }
        var derived = com.create.productionline.line.scheme.CustomAssemblyPlanner.derive(level, custom);
        if (!derived.hasEntries()) {
            System.out.println("   single-material custom scheme derived nothing");
            return false;
        }
        String json = derived.entries().get(0).getJson();
        if (json == null || json.contains("create:sequenced_assembly")) {
            System.out.println("   single-material scheme must not be a sequence: " + json);
            return false;
        }
        if (!json.contains("create:pressing")) {
            System.out.println("   iron is expected to press (create:pressing): " + json);
            return false;
        }
        System.out.println("   single material -> " + derived.entries().get(0).getFileName()
                + " (semantic machine, no sequence)");
        return true;
    }

    /**
     * A doubling recipe ("A + B = 2A") with target output 4: one pass turns 1 A + 1 B
     * into 2 A, so reaching 4 bootstraps from the unit on the belt and takes three
     * passes. The line must never be told to loop forever, and a recipe that cannot
     * grow the stock must be reported as unreachable instead.
     */
    private static boolean doublingRepeatBudget(ServerLevel level) {
        com.create.productionline.line.scheme.RepeatPlan doubling =
                com.create.productionline.line.scheme.RepeatPlan.of(4, 2, 1);
        if (doubling.repeatCount() != 3 || !doubling.reachable() || !doubling.selfFeeding()) {
            System.out.println("   doubling target 4 expected 3 passes, got " + doubling.repeatCount()
                    + " (reachable=" + doubling.reachable() + ")");
            return false;
        }
        if (doubling.scaledMaterialCount(2) != 6) {
            System.out.println("   material budget for 3 passes x 2 materials should be 6, got "
                    + doubling.scaledMaterialCount(2));
            return false;
        }
        com.create.productionline.line.scheme.RepeatPlan stuck =
                com.create.productionline.line.scheme.RepeatPlan.of(4, 1, 1);
        if (stuck.reachable() || stuck.repeatCount() != 1) {
            System.out.println("   a recipe that eats as much as it makes must be unreachable, not looped");
            return false;
        }
        // PATH 1 (computer): the scheme carries the repeat budget and has to say which
        // kind of repetition it is — the product is also an input, so the line can be
        // closed by feeding the product back to the belt head.
        LineScheme computed = new LineScheme();
        computed.setRecipeId("minecraft:crafting/iron_ingot_doubling");
        computed.setOutputItem("minecraft:iron_ingot");
        computed.setBaseMaterial("minecraft:iron_ingot");
        LineScheme.Step deploy = computed.addStep("create:deployer", 1);
        deploy.addInput("minecraft:coal");
        deploy.addOutput("minecraft:iron_ingot");
        computed.setRepeatPlan(doubling);
        if (!computed.recyclesProduct() || !computed.repeats()) {
            System.out.println("   the computed doubling plan lost its loop/repeat state");
            return false;
        }
        String topology = String.join(" | ", com.create.productionline.util.SchemeTopology.lines(computed));
        // The instruction is wrapped, so assert on fragments that cannot be split.
        if (!topology.contains("loop 3x -> 4")) {
            System.out.println("   the topology does not explain how to repeat: " + topology);
            return false;
        }
        // PATH 2 (anvil): the embedded recipe is written with count = outputCount, so one
        // pass already yields N and the plan must NOT ask for a second set of repeats.
        com.create.productionline.line.scheme.CustomAssembly custom =
                new com.create.productionline.line.scheme.CustomAssembly(
                        List.of("minecraft:iron_ingot", "minecraft:coal"), true, false,
                        "minecraft:iron_ingot", 4, doubling.repeatCount());
        LineScheme plan = com.create.productionline.line.scheme.CustomAssemblyPlanner.rebuild(custom);
        if (plan.getTargetOutputCount() != 4 || plan.getRepeatCount() != 1 || plan.repeats()) {
            System.out.println("   the custom plan must yield N in one pass and not repeat: target="
                    + plan.getTargetOutputCount() + " repeats=" + plan.getRepeatCount());
            return false;
        }
        if (!plan.getBaseMaterial().equals("minecraft:iron_ingot")) {
            System.out.println("   the product must be allowed as the base material, got base="
                    + plan.getBaseMaterial());
            return false;
        }
        var derived = com.create.productionline.line.scheme.CustomAssemblyPlanner.derive(level, custom);
        if (!derived.hasEntries() || !derived.entries().get(0).getJson().contains("create:sequenced_assembly")) {
            System.out.println("   doubling custom scheme did not derive a sequenced assembly");
            return false;
        }
        if (!derived.entries().get(0).getJson().contains("\"count\": 4")) {
            System.out.println("   the custom recipe must yield N in one pass: "
                    + derived.entries().get(0).getJson());
            return false;
        }
        System.out.println("   doubling A+B=2A: computer plan 3 passes / budget 6 / '"
                + topology.substring(Math.max(0, topology.length() - 30))
                + "'; anvil plan yields 4 per pass with no extra repeats");
        return true;
    }

    /**
     * The anvil decision table, exercised without an anvil, a player or a server. Every
     * row of the state machine is asserted here, which is what the first in-play bugs of
     * this feature were missing: the handler was only ever tested by hand.
     */
    private static boolean schemeAnvilStateTable() {
        net.minecraft.world.item.ItemStack paper = new net.minecraft.world.item.ItemStack(Items.PAPER);
        net.minecraft.world.item.ItemStack material =
                new net.minecraft.world.item.ItemStack(Items.IRON_INGOT);
        net.minecraft.world.item.ItemStack planStack = schemeStackWith(writtenStickScheme(), null);
        net.minecraft.world.item.ItemStack clearedStack = schemeStackWith(
                com.create.productionline.line.scheme.CustomAssemblyPlanner.cleared("minecraft:stick"),
                new com.create.productionline.line.scheme.CustomAssembly(
                        List.of(), false, false, "minecraft:stick", 1, 1));
        com.create.productionline.line.scheme.CustomAssembly one =
                new com.create.productionline.line.scheme.CustomAssembly(
                        List.of(), false, false, "minecraft:stick", 1, 1).withMaterial("minecraft:iron_ingot");
        net.minecraft.world.item.ItemStack oneStack = schemeStackWith(
                com.create.productionline.line.scheme.CustomAssemblyPlanner.rebuild(one), one);
        com.create.productionline.line.scheme.CustomAssembly lone =
                new com.create.productionline.line.scheme.CustomAssembly(
                        List.of(), false, false, "minecraft:stick", 1, 1).withMaterial("minecraft:stick");
        net.minecraft.world.item.ItemStack loneStack = schemeStackWith(
                com.create.productionline.line.scheme.CustomAssemblyPlanner.rebuild(lone), lone);
        com.create.productionline.line.scheme.CustomAssembly lockedCustom =
                new com.create.productionline.line.scheme.CustomAssembly(
                        List.of("minecraft:oak_planks", "minecraft:iron_ingot"), true, false,
                        "minecraft:stick", 1, 1);
        net.minecraft.world.item.ItemStack lockedStack = schemeStackWith(
                com.create.productionline.line.scheme.CustomAssemblyPlanner.rebuild(lockedCustom), lockedCustom);

        boolean ok = true;
        ok &= expectAction("not a scheme", com.create.productionline.line.scheme.SchemeAnvilMachine.Action.PASS,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(
                        new net.minecraft.world.item.ItemStack(Items.STONE), paper, true));
        ok &= expectAction("non-OP", com.create.productionline.line.scheme.SchemeAnvilMachine.Action.PASS,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(planStack, paper, false));
        ok &= expectAction("stacked scheme", com.create.productionline.line.scheme.SchemeAnvilMachine.Action.REFUSE,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(
                        planStack.copyWithCount(2), paper, true));
        ok &= expectAction("clear", com.create.productionline.line.scheme.SchemeAnvilMachine.Action.CLEAR,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(planStack, paper, true));
        ok &= expectAction("material before clearing",
                com.create.productionline.line.scheme.SchemeAnvilMachine.Action.REFUSE,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(planStack, material, true));
        ok &= expectAction("empty right slot", com.create.productionline.line.scheme.SchemeAnvilMachine.Action.REFUSE,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(
                        planStack, net.minecraft.world.item.ItemStack.EMPTY, true));
        ok &= expectAction("hammer", com.create.productionline.line.scheme.SchemeAnvilMachine.Action.HAMMER,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(clearedStack, material, true));
        ok &= expectAction("lock without material",
                com.create.productionline.line.scheme.SchemeAnvilMachine.Action.REFUSE,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(clearedStack, paper, true));
        var lockOne = com.create.productionline.line.scheme.SchemeAnvilMachine.decide(oneStack, paper, true);
        ok &= expectAction("lock single material",
                com.create.productionline.line.scheme.SchemeAnvilMachine.Action.LOCK, lockOne);
        if (!lockOne.notice()) {
            System.out.println("   a single-material lock must tell the player it is provisional");
            ok = false;
        }
        ok &= expectAction("lock lone product",
                com.create.productionline.line.scheme.SchemeAnvilMachine.Action.REFUSE,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(loneStack, paper, true));
        ok &= expectAction("locked + paper", com.create.productionline.line.scheme.SchemeAnvilMachine.Action.REFUSE,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(lockedStack, paper, true));
        ok &= expectAction("locked + material",
                com.create.productionline.line.scheme.SchemeAnvilMachine.Action.REFUSE,
                com.create.productionline.line.scheme.SchemeAnvilMachine.decide(lockedStack, material, true));
        if (ok) {
            System.out.println("   12 rows of the anvil state table behave as specified");
        }
        return ok;
    }

    private static boolean expectAction(String what,
            com.create.productionline.line.scheme.SchemeAnvilMachine.Action expected,
            com.create.productionline.line.scheme.SchemeAnvilMachine.Decision actual) {
        if (actual.action() != expected) {
            System.out.println("   " + what + ": expected " + expected + ", got " + actual.action());
            return false;
        }
        return true;
    }

    /**
     * GUI layout invariants: every slot grid and every text row must fit the well /
     * band the hand-drawn background provides, and the grids must be centred in it.
     *
     * <p>This is the one defect class nothing else in this file can see. The art is
     * fixed, so a grid that lost its margin, or a hint printed straight across its
     * own well, looks fine to every resource check and only shows up in game — both
     * actually happened. Pure arithmetic on {@link GuiLayout}: the numbers there are
     * the same ones the menus and screens use, so a drift fails here first.
     */
    private static boolean guiLayoutFits() {
        boolean ok = true;

        // Loader: 8 x 2 cells, edge to edge, centred in the well. A cell's frame is
        // drawn one pixel outside the slot, i.e. it spans [x-1, x+16].
        int loaderFrameLeft = GuiLayout.loaderSlotX(0) - 1;
        int loaderFrameRight = GuiLayout.loaderSlotX(GuiLayout.LOADER_COLUMNS - 1) + GuiLayout.SLOT_FRAME - 2;
        int loaderFrameTop = GuiLayout.loaderSlotY(0) - 1;
        int loaderFrameBottom = GuiLayout.loaderSlotY(GuiLayout.LOADER_ROWS - 1) + GuiLayout.SLOT_FRAME - 2;
        ok &= layoutExpect("loader grid inside its well",
                loaderFrameLeft >= GuiLayout.LOADER_WELL_LEFT && loaderFrameRight <= GuiLayout.LOADER_WELL_RIGHT
                        && loaderFrameTop >= GuiLayout.LOADER_WELL_TOP
                        && loaderFrameBottom <= GuiLayout.LOADER_WELL_BOTTOM);
        ok &= layoutExpect("loader grid centred in its well",
                loaderFrameLeft - GuiLayout.LOADER_WELL_LEFT
                        == GuiLayout.LOADER_WELL_RIGHT - loaderFrameRight
                        && loaderFrameTop - GuiLayout.LOADER_WELL_TOP
                                == GuiLayout.LOADER_WELL_BOTTOM - loaderFrameBottom);
        ok &= layoutExpect("loader text below the well, above the groove",
                GuiLayout.LOADER_TEXT_Y > GuiLayout.LOADER_WELL_BOTTOM
                        && GuiLayout.LOADER_TEXT_Y + 8 <= GuiLayout.DIVIDER_Y);
        ok &= layoutExpect("loader title clears the well",
                GuiLayout.LOADER_TITLE_Y + 8 <= GuiLayout.LOADER_WELL_TOP - 1);

        // Dismantler: two cells on the computer's spacing, centred, nothing crossing
        // the button.
        int disFrameLeft = GuiLayout.dismantlerItemX() - 1;
        int disFrameRight = GuiLayout.dismantlerSchemeX() + GuiLayout.SLOT_FRAME - 2;
        int disFrameTop = GuiLayout.dismantlerSlotY() - 1;
        int disFrameBottom = disFrameTop + GuiLayout.SLOT_FRAME - 1;
        ok &= layoutExpect("dismantler slots inside their well",
                disFrameLeft >= GuiLayout.DISMANTLER_WELL_LEFT
                        && disFrameRight <= GuiLayout.DISMANTLER_WELL_RIGHT
                        && disFrameTop >= GuiLayout.DISMANTLER_WELL_TOP
                        && disFrameBottom <= GuiLayout.DISMANTLER_WELL_BOTTOM);
        ok &= layoutExpect("dismantler slots symmetric in their well",
                disFrameLeft - GuiLayout.DISMANTLER_WELL_LEFT
                        == GuiLayout.DISMANTLER_WELL_RIGHT - disFrameRight
                        && disFrameTop - GuiLayout.DISMANTLER_WELL_TOP
                                == GuiLayout.DISMANTLER_WELL_BOTTOM - disFrameBottom);
        ok &= layoutExpect("dismantler text between well and button",
                GuiLayout.DISMANTLER_TEXT_Y > GuiLayout.DISMANTLER_WELL_BOTTOM
                        && GuiLayout.DISMANTLER_TEXT_MAX_Y + 8 <= GuiLayout.DISMANTLER_BUTTON_Y);
        ok &= layoutExpect("dismantler button above the groove",
                GuiLayout.DISMANTLER_BUTTON_Y + GuiLayout.DISMANTLER_BUTTON_HEIGHT < GuiLayout.DIVIDER_Y);

        // Computer: three cells, centred, with the button and text below the well.
        int cpuFrameLeft = GuiLayout.computerSlotX(0) - 1;
        int cpuFrameRight = GuiLayout.computerSlotX(2) + GuiLayout.SLOT_FRAME - 2;
        int cpuFrameTop = GuiLayout.COMPUTER_SLOT_Y - 1;
        int cpuFrameBottom = cpuFrameTop + GuiLayout.SLOT_FRAME - 1;
        ok &= layoutExpect("computer slots inside their well",
                cpuFrameLeft >= GuiLayout.COMPUTER_WELL_LEFT && cpuFrameRight <= GuiLayout.COMPUTER_WELL_RIGHT
                        && cpuFrameTop >= GuiLayout.COMPUTER_WELL_TOP
                        && cpuFrameBottom <= GuiLayout.COMPUTER_WELL_BOTTOM);
        ok &= layoutExpect("computer slots centred in their well",
                cpuFrameLeft - GuiLayout.COMPUTER_WELL_LEFT
                        == GuiLayout.COMPUTER_WELL_RIGHT - cpuFrameRight
                        && cpuFrameTop - GuiLayout.COMPUTER_WELL_TOP
                                == GuiLayout.COMPUTER_WELL_BOTTOM - cpuFrameBottom);
        ok &= layoutExpect("computer button clear of well and text",
                GuiLayout.COMPUTER_BUTTON_Y > GuiLayout.COMPUTER_WELL_BOTTOM
                        && GuiLayout.COMPUTER_TEXT_Y >= GuiLayout.COMPUTER_BUTTON_Y + GuiLayout.COMPUTER_BUTTON_HEIGHT
                        && GuiLayout.TEXT_MAX_Y + 8 <= GuiLayout.DIVIDER_Y);

        // The player's own grid must not run into the groove or off the panel.
        ok &= layoutExpect("player inventory below the groove",
                GuiLayout.PLAYER_SLOTS_Y > GuiLayout.DIVIDER_Y
                        && GuiLayout.PLAYER_SLOTS_Y + 4 * GuiLayout.SLOT_FRAME - 1
                                < GuiLayout.PANEL_HEIGHT);

        if (ok) {
            System.out.println("   loader 8x2 grid centred in the well, text below it;"
                    + " dismantler 2 slots symmetric; computer 3 slots centred");
        }
        return ok;
    }

    /**
     * The dismantler's decision table plus the two refund rules that are easy to get
     * wrong.
     *
     * <p>A written scheme carries <em>no materials</em> — authoring a plan costs one
     * blank carrier and nothing else — so dismantling one erases the plan and hands a
     * fresh blank scheme back. And a recipe that consumes its own product has to give
     * that product back too: {@code 1 A + 1 B = 2 A} dismantles into {@code A + B}, not
     * into {@code B} alone, which is what the doubling recipe installed below proves
     * for real (items dropped into the world are counted, not assumed). Fluid-form
     * ingredients can never come back as items, so the count of them is what the chat
     * line reports.
     */
    private static boolean dismantlerRules(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(0, 250, 0);
        var files = new java.util.LinkedHashMap<String, String>();
        files.put("cpl_test_doubling", """
                {
                  "type": "minecraft:crafting_shapeless",
                  "category": "misc",
                  "ingredients": [ { "item": "minecraft:iron_ingot" }, { "item": "minecraft:coal" } ],
                  "result": { "id": "minecraft:iron_ingot", "count": 2 }
                }
                """);
        files.put("cpl_test_fluid", """
                {
                  "type": "minecraft:crafting_shapeless",
                  "category": "misc",
                  "ingredients": [ { "item": "minecraft:glass" }, { "fluid": "minecraft:water", "amount": 100 } ],
                  "result": { "id": "minecraft:glass" }
                }
                """);
        // A real sequenced assembly, so an intermediate can be built with real provenance.
        files.put("cpl_test_sequence", """
                {
                  "type": "create:sequenced_assembly",
                  "ingredient": { "item": "minecraft:iron_ingot" },
                  "loops": 1,
                  "results": [ { "id": "minecraft:gold_ingot" } ],
                  "sequence": [
                    {
                      "type": "create:deploying",
                      "ingredients": [ { "item": "minecraft:iron_ingot" }, { "item": "minecraft:coal" } ],
                      "results": [ { "id": "minecraft:iron_ingot" } ]
                    }
                  ],
                  "transitional_item": { "id": "minecraft:iron_ingot" }
                }
                """);
        ResourceLocation doublingId = ResourceLocation.fromNamespaceAndPath("cpl_selftest", "cpl_test_doubling");
        ResourceLocation fluidId = ResourceLocation.fromNamespaceAndPath("cpl_selftest", "cpl_test_fluid");
        ResourceLocation sequenceId = ResourceLocation.fromNamespaceAndPath("cpl_selftest", "cpl_test_sequence");
        try {
            com.create.productionline.recipegen.CreateRecipePack.installIsolated(server, files);
            if (server.getRecipeManager().byKey(doublingId).isEmpty()) {
                System.out.println("   doubling test recipe did not load: " + doublingId);
                return false;
            }
            level.setBlockAndUpdate(pos, com.create.productionline.registry.ModBlocks.DISMANTLER.get()
                    .defaultBlockState());
            if (!(level.getBlockEntity(pos) instanceof DismantlerBlockEntity dismantler)) {
                System.out.println("   could not place a dismantler at " + pos);
                return false;
            }
            var inv = dismantler.getInventory();
            boolean ok = true;

            // --- the decision table ------------------------------------------------
            ok &= expectRevert("empty slot", dismantler, DismantlerBlockEntity.RevertResult.NOTHING_HELD);
            inv.setItem(DismantlerBlockEntity.SLOT_ITEM, new ItemStack(ModItems.LINE_SCHEME.get()));
            ok &= expectRevert("blank scheme", dismantler,
                    DismantlerBlockEntity.RevertResult.SCHEME_ALREADY_BLANK);
            inv.setItem(DismantlerBlockEntity.SLOT_ITEM, LineSchemeItem.sampleStack());
            DismantlerBlockEntity.RevertOutcome erased = dismantler.revert();
            ItemStack after = inv.getItem(DismantlerBlockEntity.SLOT_ITEM);
            ok &= layoutExpect("a written scheme is erased back to a blank one",
                    erased.result() == DismantlerBlockEntity.RevertResult.SCHEME_ERASED
                            && after.getItem() == ModItems.LINE_SCHEME.get()
                            && LineSchemeSerializer.fromStack(after).isEmpty());
            ItemStack mirror = new ItemStack(ModItems.LINE_SCHEME_MIRROR.get());
            com.create.productionline.item.LineSchemeMirrorItem.write(mirror, writtenStickScheme());
            inv.setItem(DismantlerBlockEntity.SLOT_ITEM, mirror);
            ok &= expectRevert("mirror", dismantler, DismantlerBlockEntity.RevertResult.MIRROR_READ_ONLY);
            inv.setItem(DismantlerBlockEntity.SLOT_ITEM, new ItemStack(ModItems.GENERIC_INTERMEDIATE.get()));
            ok &= expectRevert("intermediate without provenance", dismantler,
                    DismantlerBlockEntity.RevertResult.NO_PROVENANCE);

            // --- doubling refund (1 iron + 1 coal = 2 iron) ------------------------
            // The plan needs at least one step: LineScheme.isEmpty() is true for a scheme
            // with an output but no steps, and an "empty" scheme is ignored on purpose.
            LineScheme plan = new LineScheme();
            plan.setRecipeId(doublingId.toString());
            plan.setOutputItem("minecraft:iron_ingot");
            plan.addStep("create:deploying", 1).addInput("minecraft:coal");
            ItemStack written = new ItemStack(ModItems.LINE_SCHEME.get());
            LineSchemeSerializer.saveToStack(written, plan);
            inv.setItem(DismantlerBlockEntity.SLOT_SCHEME, written);
            inv.setItem(DismantlerBlockEntity.SLOT_ITEM, new ItemStack(Items.IRON_INGOT, 2));
            DismantlerBlockEntity.RevertOutcome outcome = dismantler.revert();
            var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2));
            long iron = dropped.stream().filter(e -> e.getItem().is(Items.IRON_INGOT)).count();
            long coal = dropped.stream().filter(e -> e.getItem().is(Items.COAL)).count();
            ok &= layoutExpect("1 A + 1 B = 2 A refunds A and B (result=" + outcome.result()
                    + ", iron=" + iron + ", coal=" + coal + ")",
                    outcome.result() == DismantlerBlockEntity.RevertResult.DONE && iron == 1 && coal == 1);
            dropped.forEach(net.minecraft.world.entity.Entity::discard);

            // --- a Create-native intermediate is dismantled by its COMPONENT ---------
            // Provenance lives on the SEQUENCED_ASSEMBLY component, not on our own item: keying
            // the branch on Generic Intermediate alone sent every Create-native transitional item
            // down the finished-product path, where it could only answer "no recipe for it" — the
            // reported "cannot dismantle intermediates at all".
            if (server.getRecipeManager().byKey(sequenceId).isEmpty()) {
                System.out.println("   sequence test recipe did not load: " + sequenceId);
                return false;
            }
            inv.setItem(DismantlerBlockEntity.SLOT_SCHEME, ItemStack.EMPTY);
            ItemStack carried = new ItemStack(Items.IRON_INGOT);
            carried.set(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY,
                    new com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.SequencedAssembly(
                            sequenceId, 1, 0f));
            inv.setItem(DismantlerBlockEntity.SLOT_ITEM, carried);
            DismantlerBlockEntity.RevertOutcome fromIntermediate = dismantler.revert();
            var refunded = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2));
            long baseBack = refunded.stream().filter(e -> e.getItem().is(Items.IRON_INGOT)).count();
            long stepBack = refunded.stream().filter(e -> e.getItem().is(Items.COAL)).count();
            ok &= layoutExpect("a component-bearing intermediate refunds base + applied steps"
                    + " (result=" + fromIntermediate.result() + ", iron=" + baseBack + ", coal=" + stepBack + ")",
                    fromIntermediate.result() == DismantlerBlockEntity.RevertResult.DONE
                            && baseBack == 1 && stepBack == 1);
            refunded.forEach(net.minecraft.world.entity.Entity::discard);
            inv.setItem(DismantlerBlockEntity.SLOT_ITEM, ItemStack.EMPTY);

            // --- fluid ingredients are counted, never silently dropped -------------
            var manager = server.getResourceManager();
            int fluids = com.create.productionline.util.RecipeJsonReader.countFluidIngredients(manager, fluidId);
            int none = com.create.productionline.util.RecipeJsonReader.countFluidIngredients(manager, doublingId);
            ok &= layoutExpect("fluid ingredients counted (fluid recipe=" + fluids + ", item recipe=" + none + ")",
                    fluids == 1 && none == 0);
            return ok;
        } finally {
            level.removeBlock(pos, false);
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2))
                    .forEach(net.minecraft.world.entity.Entity::discard);
            com.create.productionline.recipegen.CreateRecipePack.removeIsolated(server);
        }
    }

    /**
     * The computer's write path, seen from the data side.
     *
     * <p>With a target in slot 0, a blank scheme in slot 1 and paper in slot 2, one compute has
     * to put <b>both</b> the plan and the build guide onto <b>both</b> carriers — the tooltip
     * bug this guards against (a carrier that carried the guide but never showed it) was
     * invisible in the data, so the data is what gets asserted here.
     */
    private static boolean computerWritesBothCarriers(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(4, 250, 0);
        try {
            level.setBlockAndUpdate(pos, com.create.productionline.registry.ModBlocks.PRODUCTION_COMPUTER.get()
                    .defaultBlockState());
            if (!(level.getBlockEntity(pos) instanceof com.create.productionline.block.entity
                    .ProductionComputerBlockEntity computer)) {
                System.out.println("   could not place a production computer at " + pos);
                return false;
            }
            var inv = computer.getInventory();
            inv.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_TARGET,
                    new ItemStack(Items.IRON_INGOT, 4));
            inv.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME,
                    new ItemStack(ModItems.LINE_SCHEME.get()));
            inv.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_CLIPBOARD,
                    new ItemStack(Items.PAPER));
            computer.runCompute();
            if (computer.getResultCode() != com.create.productionline.block.entity
                    .ProductionComputerBlockEntity.RESULT_GENERATED) {
                System.out.println("   compute did not generate anything: result=" + computer.getResultCode()
                        + " error=" + computer.getLastError());
                return false;
            }
            boolean ok = true;
            for (int slot : new int[]{com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME,
                    com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_CLIPBOARD}) {
                ItemStack carrier = inv.getItem(slot);
                CompoundTag custom = carrier.getOrDefault(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                LineScheme written = LineSchemeSerializer.load(
                        custom.getCompound(LineScheme.SCHEME_TAG_KEY));
                boolean hasPlan = !written.isEmpty();
                boolean hasGuide = custom.getCompound(ClipboardCompat.GUIDE_KEY).getInt("TotalSteps") > 0;
                ok &= layoutExpect("carrier slot " + slot + " holds plan+guide (plan=" + hasPlan
                        + ", guide=" + hasGuide + ", item=" + carrier.getItem() + ")", hasPlan && hasGuide);
            }
            return ok;
        } finally {
            level.removeBlock(pos, false);
        }
    }

    /** Runs one dismantle and compares the classification. */
    private static boolean expectRevert(String what, DismantlerBlockEntity dismantler,
            DismantlerBlockEntity.RevertResult expected) {
        DismantlerBlockEntity.RevertOutcome outcome = dismantler.revert();
        if (outcome.result() != expected) {
            System.out.println("   " + what + ": expected " + expected + ", got " + outcome.result());
            return false;
        }
        return true;
    }

    /** One layout assertion; prints the failing rule instead of a bare false. */
    private static boolean layoutExpect(String what, boolean condition) {
        if (!condition) {
            System.out.println("   layout rule broken: " + what);
            return false;
        }
        return true;
    }

    /** A plan whose steps consume two units per pass (base + one deployed material). */
    private static LineScheme writtenStickScheme() {
        LineScheme scheme = new LineScheme();
        scheme.setRecipeId("minecraft:crafting/stick");
        scheme.setOutputItem("minecraft:stick");
        scheme.setBaseMaterial("minecraft:oak_planks");
        LineScheme.Step feed = scheme.addStep("cpl:feed", 1);
        feed.addInput("minecraft:oak_planks");
        LineScheme.Step deploy = scheme.addStep("create:deployer", 1);
        deploy.addInput("minecraft:iron_ingot");
        deploy.addOutput("minecraft:stick");
        return scheme;
    }

    private static net.minecraft.world.item.ItemStack schemeStackWith(LineScheme scheme,
            com.create.productionline.line.scheme.CustomAssembly custom) {
        net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(com.create.productionline.registry.ModItems.LINE_SCHEME.get());
        LineSchemeSerializer.saveToStack(stack, scheme);
        if (custom != null) {
            stack.set(com.create.productionline.registry.ModDataComponents.CUSTOM_ASSEMBLY.get(), custom);
        }
        return stack;
    }

    /**
     * The material budget a plan reports: units per pass times the repeat count, which is
     * the number the panels tell the player to prepare.
     */
    private static boolean planMaterialBudget() {
        LineScheme plan = writtenStickScheme();
        plan.setRepeatPlan(com.create.productionline.line.scheme.RepeatPlan.of(4, 2, 1));
        if (plan.materialsPerPass() != 2) {
            System.out.println("   expected 2 units per pass, got " + plan.materialsPerPass());
            return false;
        }
        if (plan.materialBudget() != 6) {
            System.out.println("   3 passes x 2 units should be 6, got " + plan.materialBudget());
            return false;
        }
        System.out.println("   budget: " + plan.materialsPerPass() + " units per pass x "
                + plan.getRepeatCount() + " passes = " + plan.materialBudget());
        return true;
    }
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
