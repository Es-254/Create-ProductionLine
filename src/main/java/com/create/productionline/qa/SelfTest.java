package com.create.productionline.qa;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
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
 * <p>Coverage (against the SRS QA list) — 27 checks, in run order:
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
 *   <li>the dismantler's eleven-row decision table, its doubling refund and its
 *       fluid notice, driven through a real block entity;</li>
 *   <li>the computer writes the plan and the build guide onto both carriers;</li>
 *   <li><b>the OP placeholder path for a target no recipe produces</b>: without permission the
 *       compute still refuses with {@code RESULT_NO_RECIPE} and writes nothing, with permission
 *       it writes a scheme whose recipe id is EMPTY, with zero steps and the target it was
 *       computed for (a name the anvil flow can author against — and nothing a Scheme Loader
 *       could install: the loader refuses the item, counts it as no filled slot and derives no
 *       entry from it, so the bar stays dark);</li>
 *   <li><b>the OP placeholder QUESTION for a target whose live recipe cannot be converted</b> (the
 *       milk bucket dead end): the computer writes nothing and asks the requester instead, so the
 *       whole table is asserted — non-OP keeps the byte-for-byte refusal and is never asked, OP
 *       gets the question recorded and a prompt that really carries two {@code RUN_COMMAND} clicks,
 *       decline / expiry / a lapsed container / changed target slot / revoked permission write
 *       nothing (the revoked one keeps the question, because only the asked player may consume it),
 *       accept writes the placeholder while keeping the reason — and that placeholder installs
 *       nothing, by the same two loader rules as the other origin;</li>
 *   <li><b>the placeholder ANSWER through the click path production really uses</b>: a real
 *       {@code ProductionComputerMenu} opened for a real (fake) player standing at the computer,
 *       the ask driven through {@code computeProvided} + the tick's {@code runCompute}, and the
 *       answer judged with the menu's REAL {@code stillValid(player)} value — a stranger is rejected
 *       without consuming the asker's question, a player out of reach answers "the container is
 *       gone", closing the menu does not drop the question, and the asker who re-opens the computer
 *       writes the placeholder. This is the check that would have caught the live failure in which
 *       every click on 【写入占位方案】 was answered "该占位请求已失效": the previous coverage fed
 *       {@code menuOpen = true} by hand and never ran the ask through the payload entry point;</li>
 *   <li><b>each Ponder schematic holds the exact block at every position its scene
 *       shows, hides or modifies, and that position is inside the box those blocks
 *       span</b> — Ponder drops anything outside it without a log line, which is how
 *       the machines stayed invisible on a plate-only schematic.</li>
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
            check("Placeholder scheme for an unreachable item (OP only)",
                    () -> placeholderForUnreachableItem(server));
            check("Placeholder question for an unconvertible target (OP only)",
                    () -> placeholderQuestionForUnconvertibleTarget(server));
            check("Placeholder answer needs the asker's own live menu",
                    () -> placeholderAnswerNeedsLiveMenu(server));
            check("Ponder schematics hold every block their scene touches", () -> ponderSchematicCoverage());
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
            // The erase also hands back a mirror of what was on the scheme (the author's scene
            // shows it), into the other slot when that is free.
            ok &= layoutExpect("erasing a scheme leaves a mirror in the other slot",
                    inv.getItem(DismantlerBlockEntity.SLOT_SCHEME).getItem() == ModItems.LINE_SCHEME_MIRROR.get());

            // A written scheme alone on the RIGHT is the same request (the scene puts it there).
            inv.setItem(DismantlerBlockEntity.SLOT_ITEM, ItemStack.EMPTY);
            inv.setItem(DismantlerBlockEntity.SLOT_SCHEME, LineSchemeItem.sampleStack());
            DismantlerBlockEntity.RevertOutcome fromRight = dismantler.revert();
            ok &= layoutExpect("a written scheme on the right is erased too",
                    fromRight.result() == DismantlerBlockEntity.RevertResult.SCHEME_ERASED
                            && inv.getItem(DismantlerBlockEntity.SLOT_SCHEME).getItem() == ModItems.LINE_SCHEME.get()
                            && !inv.getItem(DismantlerBlockEntity.SLOT_SCHEME).has(
                                    com.create.productionline.registry.ModDataComponents.CUSTOM_ASSEMBLY.get())
                            && inv.getItem(DismantlerBlockEntity.SLOT_ITEM).getItem()
                                    == ModItems.LINE_SCHEME_MIRROR.get());
            inv.setItem(DismantlerBlockEntity.SLOT_ITEM, ItemStack.EMPTY);
            inv.setItem(DismantlerBlockEntity.SLOT_SCHEME, ItemStack.EMPTY);
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

            // --- a retired generated recipe is still readable for provenance -------
            // A generated recipe leaves the pack when its scheme leaves the loader; the item
            // crafted while it was live still names it, so the retirement folder is the last
            // place the refund can come from.
            java.nio.file.Path retired = com.create.productionline.recipegen.CreateRecipePack.retiredDir(server);
            java.nio.file.Files.createDirectories(retired);
            java.nio.file.Path retiredFile = retired.resolve("cpl_test_retired.json");
            java.nio.file.Files.writeString(retiredFile, """
                    {
                      "type": "create:sequenced_assembly",
                      "ingredient": { "item": "minecraft:copper_ingot" },
                      "loops": 1,
                      "results": [ { "id": "minecraft:gold_ingot" } ],
                      "sequence": [
                        {
                          "type": "create:deploying",
                          "ingredients": [ { "item": "minecraft:copper_ingot" }, { "item": "minecraft:redstone" } ],
                          "results": [ { "id": "minecraft:copper_ingot" } ]
                        }
                      ],
                      "transitional_item": { "id": "minecraft:copper_ingot" }
                    }
                    """);
            com.create.productionline.util.RecipeJsonReader.SequenceParts retiredParts =
                    com.create.productionline.util.RecipeJsonReader.sequenceParts(server.getResourceManager(),
                            ResourceLocation.fromNamespaceAndPath("cpl", "cpl_test_retired"), retired);
            ok &= layoutExpect("a retired generated recipe is still readable for provenance",
                    retiredParts != null && "minecraft:copper_ingot".equals(retiredParts.base())
                            && retiredParts.stepMaterials().contains("minecraft:redstone"));
            java.nio.file.Files.deleteIfExists(retiredFile);

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

    /**
     * The OP placeholder path, for a target NO recipe produces.
     *
     * <p>Without it the feature is unreachable from both ends: the computer refuses to write
     * anything, and the anvil flow can only refine a scheme the computer wrote — so an operator
     * can never obtain a scheme naming such an item, and therefore can never hand-author a line
     * for it. The check drives a REAL computer block entity through both sides of the gate,
     * because "everybody gets a placeholder" and "nobody does" are both wrong:
     *
     * <ul>
     *   <li>without the authoring permission the run ends in {@code RESULT_NO_RECIPE} with the
     *       carriers untouched — today's behaviour, unchanged, including its M7 reason;</li>
     *   <li>with it, the carriers get a placeholder: the target item, an EMPTY {@code RecipeId}
     *       (nothing the Scheme Loader could ever install) and zero steps;</li>
     *   <li>the anvil then treats it as an already cleared scheme and takes a material straight
     *       away, without the paper step — and the marker does not survive that first hammer
     *       strike, because the scheme is a plan from then on;</li>
     *   <li>a Scheme Loader counts it as no filled slot and derives no entry from it, which is
     *       what keeps the bar dark and the cabinet's recipe contribution empty.</li>
     * </ul>
     */
    private static boolean placeholderForUnreachableItem(MinecraftServer server) {
        ServerLevel level = server.overworld();
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(8, 250, 0);
        // Nothing in vanilla or Create produces a barrier, so the server-side lookup really
        // comes back empty and the run takes the "no usable recipe" branch.
        String targetId = "minecraft:barrier";
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
                    new ItemStack(Items.BARRIER));
            inv.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME,
                    new ItemStack(ModItems.LINE_SCHEME.get()));
            inv.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_CLIPBOARD,
                    new ItemStack(Items.PAPER));

            // (a) No permission: unchanged refusal, and nothing at all is written.
            computer.runCompute(false);
            boolean ok = layoutExpect("non-OP still refuses (result=" + computer.getResultCode()
                    + ", error=" + computer.getLastErrorCode() + ")",
                    computer.getResultCode() == com.create.productionline.block.entity
                            .ProductionComputerBlockEntity.RESULT_NO_RECIPE
                            && computer.getLastErrorCode() == com.create.productionline.block.entity
                                    .ProductionComputerBlockEntity.ERROR_NO_RECIPE_PRODUCING);
            LineScheme refused = LineSchemeSerializer.fromStack(inv.getItem(
                    com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME));
            ok &= layoutExpect("non-OP carries stay untouched (empty=" + refused.isEmpty()
                    + ", placeholder=" + refused.isPlaceholder() + ")",
                    refused.isEmpty() && !refused.isPlaceholder());

            // (b) With permission: a placeholder that names the target and installs nothing.
            computer.runCompute(true);
            ok &= layoutExpect("OP gets a placeholder (result=" + computer.getResultCode() + ")",
                    computer.getResultCode() == com.create.productionline.block.entity
                            .ProductionComputerBlockEntity.RESULT_PLACEHOLDER);
            LineScheme written = LineSchemeSerializer.fromStack(inv.getItem(
                    com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME));
            ok &= layoutExpect("placeholder names the target: " + written.getOutputItem(),
                    targetId.equals(written.getOutputItem()));
            ok &= layoutExpect("placeholder recipe id is EMPTY (nothing can be installed): '"
                    + written.getRecipeId() + "'", written.getRecipeId().isBlank());
            ok &= layoutExpect("placeholder has zero steps: " + written.getSteps().size(),
                    written.getSteps().size() == 0);
            ok &= layoutExpect("placeholder is marked as one", written.isPlaceholder());
            LineScheme paperCopy = LineSchemeSerializer.fromStack(inv.getItem(
                    com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_CLIPBOARD));
            ok &= layoutExpect("the second carrier got the same placeholder",
                    paperCopy.isPlaceholder() && targetId.equals(paperCopy.getOutputItem()));

            ItemStack carried = inv.getItem(
                    com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME).copyWithCount(1);

            // (c) The anvil accepts it straight away: "+ item" is the very next rule.
            var hammer = com.create.productionline.line.scheme.SchemeAnvilMachine.decide(
                    carried, new ItemStack(Items.IRON_INGOT), true);
            ok &= expectAction("placeholder + material (already cleared)",
                    com.create.productionline.line.scheme.SchemeAnvilMachine.Action.HAMMER, hammer);
            ok &= layoutExpect("the hammered scheme carries the material and keeps the target",
                    hammer.next() != null && hammer.plan() != null
                            && hammer.next().materials().equals(List.of("minecraft:iron_ingot"))
                            && targetId.equals(hammer.plan().getOutputItem()));
            ok &= layoutExpect("the marker does not survive the first hammer strike (it is a plan now)",
                    hammer.plan() != null && !hammer.plan().isPlaceholder());

            // (d) A placeholder is never an active line. The slot contract rejects the item
            // outright (a step-less scheme is not a loader carrier), and even with one sitting
            // in a slot it counts as no filled slot (the bar follows that number) and yields no
            // entry for the union — asserted against the very method the cabinet reconciles
            // with, so "the cabinet would install nothing" is measured, not assumed.
            ok &= layoutExpect("a loader slot refuses a placeholder scheme",
                    !ClipboardCompat.isLoaderCarrier(carried));
            com.create.productionline.block.entity.SchemeLoaderBlockEntity loader =
                    new com.create.productionline.block.entity.SchemeLoaderBlockEntity(
                            new net.minecraft.core.BlockPos(8, 249, 0),
                            com.create.productionline.registry.ModBlocks.SCHEME_LOADER.get().defaultBlockState());
            loader.getInventory().setItem(0, carried);
            ok &= layoutExpect("a placeholder fills no loader slot (bar stays dark): "
                    + loader.filledSlots(), loader.filledSlots() == 0);
            ok &= layoutExpect("a placeholder derives no installable entry",
                    com.create.productionline.block.entity.SchemeLoaderBlockEntity
                            .entriesForSlot(level, carried).isEmpty());
            return ok;
        } finally {
            level.removeBlock(pos, false);
        }
    }

    /**
     * The placeholder QUESTION for a target whose live recipe cannot be CONVERTED — the milk bucket
     * dead end, and the second origin of a placeholder scheme.
     *
     * <p>Here the computer DOES find a recipe for the item and then refuses to turn it into a Create
     * line (the bucket is filled by a native Create process of its own), so an operator could not
     * obtain a scheme naming it — and the anvil flow, which can only refine a scheme that already
     * exists, could not start one either. Unlike the "no recipe at all" case the computer must not
     * decide this on its own: it asks the requester first, privately, with two clickable answers.
     * The check installs exactly that shape of recipe into the self test's own data pack, so the
     * branch is decided by the LIVE {@code RecipeManager} rather than by a fixture production code
     * never reads, and then asserts the whole table on a real block entity:
     *
     * <ul>
     *   <li>non-OP: the old refusal byte for byte (result code, M7 reason, detail string, the one
     *       status line), no question recorded, nothing written;</li>
     *   <li>OP: still nothing written, but the question is recorded — and the prompt really carries
     *       two {@code RUN_COMMAND} clicks, at the commands the server registers;</li>
     *   <li>decline, expired, closed menu, changed target slot and revoked permission all write
     *       nothing; the revoked one additionally leaves the question standing, because a stranger
     *       (or a demoted operator) must not be able to consume the asked player's offer;</li>
     *   <li>accept writes the SAME placeholder the unreachable case writes (one marker, one result
     *       code; the reason rides in the diagnostic and therefore in the status line), and that
     *       placeholder installs nothing either — asserted against the very two methods the cabinet
     *       reconciles with;</li>
     *   <li>the pure decision table is driven row by row as well, so the ORDER of its checks (which
     *       is what stops a stranger's click from consuming somebody else's question) is asserted,
     *       not just each verdict on its own.</li>
     * </ul>
     */
    private static boolean placeholderQuestionForUnconvertibleTarget(MinecraftServer server) {
        ServerLevel level = server.overworld();
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(12, 250, 0);
        String targetId = "minecraft:milk_bucket";
        ResourceLocation seeded = ResourceLocation.fromNamespaceAndPath("cpl_selftest", "cpl_test_native_target");
        var files = new java.util.LinkedHashMap<String, String>();
        files.put("cpl_test_native_target", com.create.productionline.recipegen.CreateRecipePack.toJsonString(
                com.create.productionline.recipegen.CreateRecipePack.flat("create:mixing",
                        List.of("minecraft:bucket", "minecraft:sugar"), targetId)));
        try {
            // A genuine live recipe for the target, of a category the mod must not convert (native
            // Create protection). Without it the run would take the "no recipe" branch and this
            // check would pass while proving nothing about the branch it exists for.
            if (com.create.productionline.recipegen.CreateRecipePack.installIsolated(server, files) != 1
                    || server.getRecipeManager().byKey(seeded).isEmpty()) {
                System.out.println("   could not install a live recipe producing " + targetId);
                return false;
            }
            level.setBlockAndUpdate(pos, com.create.productionline.registry.ModBlocks.PRODUCTION_COMPUTER.get()
                    .defaultBlockState());
            if (!(level.getBlockEntity(pos) instanceof com.create.productionline.block.entity
                    .ProductionComputerBlockEntity computer)) {
                System.out.println("   could not place a production computer at " + pos);
                return false;
            }
            var inv = computer.getInventory();
            inv.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_TARGET,
                    new ItemStack(Items.MILK_BUCKET));
            resetCarriers(computer);

            // (a) No permission: the unchanged refusal, no question, nothing written.
            computer.runCompute(false);
            boolean ok = layoutExpect("non-OP still refuses an unconvertible target (result="
                    + computer.getResultCode() + ", error=" + computer.getLastErrorCode()
                    + ", detail='" + computer.getLastError() + "')",
                    computer.getResultCode() == com.create.productionline.block.entity
                            .ProductionComputerBlockEntity.RESULT_NOT_CONVERTIBLE
                            && computer.getLastErrorCode() == com.create.productionline.block.entity
                                    .ProductionComputerBlockEntity.ERROR_NOT_CONVERTIBLE
                            && targetId.equals(computer.getLastError()));
            ok &= layoutExpect("non-OP is never asked for a placeholder",
                    !computer.hasPendingPlaceholderRequest());
            List<String> refusedKeys = statusKeys(computer, inv);
            ok &= layoutExpect("non-OP still gets the one unchanged refusal line: " + refusedKeys,
                    refusedKeys.equals(List.of("screen.create_productionline.computer.not_convertible")));
            ok &= layoutExpect("non-OP carriers stay untouched", carriersUntouched(inv));

            // (b) With permission: NOTHING is written, and the question is recorded instead.
            computer.runCompute(true);
            ok &= layoutExpect("OP keeps the old result code, because nothing was written yet (result="
                    + computer.getResultCode() + ", error=" + computer.getLastErrorCode() + ")",
                    computer.getResultCode() == com.create.productionline.block.entity
                            .ProductionComputerBlockEntity.RESULT_NOT_CONVERTIBLE
                            && computer.getLastErrorCode() == com.create.productionline.block.entity
                                    .ProductionComputerBlockEntity.ERROR_NOT_CONVERTIBLE);
            ok &= layoutExpect("OP gets the question recorded instead of a scheme",
                    computer.hasPendingPlaceholderRequest());
            // The suspected regression of this feature: a run that records a question must not clear
            // it again on its way out (a clear at the END of runComputeInternal would make every
            // answer impossible). Asserted here as its own line because that is exactly the shape
            // the live report looked like; the production entry points are covered separately, by
            // placeholderAnswerNeedsLiveMenu.
            ok &= layoutExpect("the question survives the very run that recorded it",
                    computer.hasPendingPlaceholderRequest());
            ok &= layoutExpect("OP carriers are untouched until the question is answered",
                    carriersUntouched(inv));
            List<net.minecraft.network.chat.Component> prompt =
                    com.create.productionline.menu.ComputerStatus.placeholderPrompt(targetId);
            ok &= layoutExpect("the prompt is a question about the target plus the two options: "
                    + prompt.size() + " line(s)",
                    prompt.size() == 2 && prompt.get(0).getContents()
                            instanceof net.minecraft.network.chat.contents.TranslatableContents first
                            && first.getKey().equals(
                                    "screen.create_productionline.computer.placeholder_prompt"));
            ok &= layoutExpect("both answers are clickable commands, and they are the registered ones: "
                    + clickCommands(prompt.get(1)),
                    clickCommands(prompt.get(1)).equals(List.of(
                            "RUN_COMMAND " + com.create.productionline.block.entity.PlaceholderPrompt.ACCEPT_COMMAND,
                            "RUN_COMMAND " + com.create.productionline.block.entity.PlaceholderPrompt.DECLINE_COMMAND)));

            long now = level.getGameTime();
            // (c) "No": nothing is written, and the question is settled.
            resetCarriers(computer);
            computer.runCompute(true);
            ok &= expectOutcome("decline writes nothing",
                    com.create.productionline.block.entity.PlaceholderPrompt.Outcome.DECLINED,
                    computer.answerPlaceholderRequest(null,
                            com.create.productionline.block.entity.PlaceholderPrompt.Answer.DECLINE,
                            true, true, now));
            ok &= layoutExpect("a declined question is dropped", !computer.hasPendingPlaceholderRequest());
            ok &= layoutExpect("a declined question writes nothing", carriersUntouched(inv));

            // (d) An expired question is worth nothing — the deadline is what keeps a prompt found
            // in an old chat window from writing into a slot the player has since re-purposed.
            resetCarriers(computer);
            computer.runCompute(true);
            ok &= expectOutcome("an expired question writes nothing",
                    com.create.productionline.block.entity.PlaceholderPrompt.Outcome.EXPIRED,
                    computer.answerPlaceholderRequest(null,
                            com.create.productionline.block.entity.PlaceholderPrompt.Answer.ACCEPT,
                            true, true, now + com.create.productionline.block.entity.PlaceholderPrompt
                                    .TIMEOUT_TICKS + 1));
            ok &= layoutExpect("an expired question is dropped", !computer.hasPendingPlaceholderRequest());
            ok &= layoutExpect("an expired question writes nothing", carriersUntouched(inv));

            // (e) A closed menu no longer drops the question by itself: the question is asked and
            // answered in chat, so its life cannot be the menu's life. What a click without a live
            // menu does is decided by the click handler, and the row that decides "the container
            // behind the clicker's own menu is no longer the live one" is asserted below, with the
            // REAL menu value, by placeholderAnswerNeedsLiveMenu.
            resetCarriers(computer);
            computer.runCompute(true);
            ok &= layoutExpect("a closed menu leaves the question standing for its own asker",
                    computer.hasPendingPlaceholderRequest());
            ok &= expectOutcome("a click whose container is no longer the live one writes nothing",
                    com.create.productionline.block.entity.PlaceholderPrompt.Outcome.WINDOW_GONE,
                    computer.answerPlaceholderRequest(null,
                            com.create.productionline.block.entity.PlaceholderPrompt.Answer.ACCEPT,
                            true, false, now));
            ok &= layoutExpect("a lapsed container writes nothing", carriersUntouched(inv));
            ok &= layoutExpect("a lapsed container settles the question",
                    !computer.hasPendingPlaceholderRequest());

            // (f) Changing the target slot drops the question with the item it named.
            resetCarriers(computer);
            computer.runCompute(true);
            inv.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_TARGET,
                    new ItemStack(Items.BARRIER));
            ok &= layoutExpect("changing the target slot drops the question",
                    !computer.hasPendingPlaceholderRequest());
            ok &= expectOutcome("a question about a replaced target writes nothing",
                    com.create.productionline.block.entity.PlaceholderPrompt.Outcome.EXPIRED,
                    computer.answerPlaceholderRequest(null,
                            com.create.productionline.block.entity.PlaceholderPrompt.Answer.ACCEPT,
                            true, true, now));
            ok &= layoutExpect("a replaced target writes nothing", carriersUntouched(inv));
            inv.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_TARGET,
                    new ItemStack(Items.MILK_BUCKET));

            // (g) A revoked permission neither writes nor consumes: only the asked player may settle
            // their own question, so it stays standing for them.
            resetCarriers(computer);
            computer.runCompute(true);
            ok &= expectOutcome("a revoked permission writes nothing",
                    com.create.productionline.block.entity.PlaceholderPrompt.Outcome.REJECTED,
                    computer.answerPlaceholderRequest(null,
                            com.create.productionline.block.entity.PlaceholderPrompt.Answer.ACCEPT,
                            false, true, now));
            ok &= layoutExpect("a revoked permission leaves the question standing",
                    computer.hasPendingPlaceholderRequest());
            ok &= layoutExpect("a revoked permission writes nothing", carriersUntouched(inv));

            // (h) "Yes": the placeholder, exactly as the unreachable case writes it.
            resetCarriers(computer);
            computer.runCompute(true);
            ok &= expectOutcome("accept writes the placeholder",
                    com.create.productionline.block.entity.PlaceholderPrompt.Outcome.WRITE,
                    computer.answerPlaceholderRequest(null,
                            com.create.productionline.block.entity.PlaceholderPrompt.Answer.ACCEPT,
                            true, true, level.getGameTime()));
            ok &= layoutExpect("an answered question is dropped", !computer.hasPendingPlaceholderRequest());
            ok &= layoutExpect("accept reports the placeholder result code (result="
                    + computer.getResultCode() + ", error=" + computer.getLastErrorCode() + ")",
                    computer.getResultCode() == com.create.productionline.block.entity
                            .ProductionComputerBlockEntity.RESULT_PLACEHOLDER
                            && computer.getLastErrorCode() == com.create.productionline.block.entity
                                    .ProductionComputerBlockEntity.ERROR_NOT_CONVERTIBLE);
            LineScheme written = LineSchemeSerializer.fromStack(inv.getItem(
                    com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME));
            ok &= layoutExpect("the placeholder names the target: " + written.getOutputItem(),
                    targetId.equals(written.getOutputItem()));
            ok &= layoutExpect("the placeholder recipe id is EMPTY (nothing can be installed): '"
                    + written.getRecipeId() + "'", written.getRecipeId().isBlank());
            ok &= layoutExpect("the placeholder has zero steps: " + written.getSteps().size(),
                    written.getSteps().size() == 0);
            ok &= layoutExpect("the placeholder is marked as one", written.isPlaceholder());
            LineScheme paperCopy = LineSchemeSerializer.fromStack(inv.getItem(
                    com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_CLIPBOARD));
            ok &= layoutExpect("the second carrier got the same placeholder",
                    paperCopy.isPlaceholder() && targetId.equals(paperCopy.getOutputItem()));
            List<String> writtenKeys = statusKeys(computer, inv);
            ok &= layoutExpect("the write keeps the reason AND says a placeholder was written: " + writtenKeys,
                    writtenKeys.equals(List.of(
                            "screen.create_productionline.computer.not_convertible_placeholder",
                            "screen.create_productionline.computer.placeholder_anvil")));

            // (i) And it is still never an active line.
            ItemStack carried = inv.getItem(
                    com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME).copyWithCount(1);
            ok &= layoutExpect("a loader slot refuses this placeholder too",
                    !ClipboardCompat.isLoaderCarrier(carried));
            com.create.productionline.block.entity.SchemeLoaderBlockEntity loader =
                    new com.create.productionline.block.entity.SchemeLoaderBlockEntity(
                            new net.minecraft.core.BlockPos(12, 249, 0),
                            com.create.productionline.registry.ModBlocks.SCHEME_LOADER.get().defaultBlockState());
            loader.getInventory().setItem(0, carried);
            ok &= layoutExpect("an unconvertible-target placeholder fills no loader slot (bar stays dark): "
                    + loader.filledSlots(), loader.filledSlots() == 0);
            ok &= layoutExpect("an unconvertible-target placeholder derives no installable entry",
                    com.create.productionline.block.entity.SchemeLoaderBlockEntity
                            .entriesForSlot(level, carried).isEmpty());

            return ok & placeholderQuestionTable();
        } finally {
            level.removeBlock(pos, false);
            com.create.productionline.recipegen.CreateRecipePack.removeIsolated(server);
        }
    }

    /**
     * The production click path, driven end to end through the entry points the game uses — and the
     * regression guard for the one live failure this feature had.
     *
     * <p>The failure: the ask worked, the question reached the player, and every click on
     * 【写入占位方案】 answered "该占位请求已失效" while the player stood at the computer, well inside
     * the 30 s deadline, with the target slot untouched (the author's log: question at
     * {@code 01:58:51.649}, refused answer at {@code 01:58:55.573}). Two things can produce that
     * sentence and the previous checks could see neither, because they fed the decision table
     * {@code menuOpen = true} by hand:
     *
     * <ul>
     *   <li>a question that did not survive the menu — the question was asked in chat, so every
     *       answer is clicked with a chat screen up, and any menu churn (the player re-opening the
     *       computer, vanilla's per-tick validity poll, a broken/moved/reloaded block) used to drop
     *       it;</li>
     *   <li>a {@code menuOpen} value that is not what the menu really says, which no hard-coded true
     *       can catch.</li>
     * </ul>
     *
     * <p>So this check builds a REAL {@link com.create.productionline.menu.ProductionComputerMenu}
     * for a real (fake) player standing at the computer, drives the ask through the real entry points
     * ({@code computeProvided} with that player, then the tick's {@code runCompute}), and answers
     * with the REAL value of {@code menu.stillValid(player)}:
     *
     * <ul>
     *   <li>a player standing at the computer with its menu open gets {@code stillValid == true} —
     *       the assumption the live click depends on, asserted instead of assumed;</li>
     *   <li>the question is recorded with that player as the asker and SURVIVES the run that
     *       recorded it (a clear at the end of that run would make every answer impossible);</li>
     *   <li>a stranger clicking the same computer is rejected and leaves the asker's question
     *       standing, writing nothing;</li>
     *   <li>the asker, back at the computer with its menu open, writes the placeholder;</li>
     *   <li>a click whose container really is gone ({@code stillValid == false}, measured by moving
     *       the player out of reach) writes nothing and says so with its own verdict, and closing the
     *       menu does not drop the question — so the offer can still be answered through the
     *       re-opened computer, which is the flow the author actually performs.</li>
     * </ul>
     */
    private static boolean placeholderAnswerNeedsLiveMenu(MinecraftServer server) {
        ServerLevel level = server.overworld();
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(12, 248, 0);
        String targetId = "minecraft:milk_bucket";
        ResourceLocation seeded = ResourceLocation.fromNamespaceAndPath("cpl_selftest", "cpl_test_native_target");
        var files = new java.util.LinkedHashMap<String, String>();
        files.put("cpl_test_native_target", com.create.productionline.recipegen.CreateRecipePack.toJsonString(
                com.create.productionline.recipegen.CreateRecipePack.flat("create:mixing",
                        List.of("minecraft:bucket", "minecraft:sugar"), targetId)));
        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("cpl_selftest_placeholder".getBytes(StandardCharsets.UTF_8)),
                "CPL_SelfTest");
        com.mojang.authlib.GameProfile strangerProfile = new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("cpl_selftest_stranger".getBytes(StandardCharsets.UTF_8)),
                "CPL_SelfTest_Stranger");
        boolean opped = false;
        try {
            if (com.create.productionline.recipegen.CreateRecipePack.installIsolated(server, files) != 1
                    || server.getRecipeManager().byKey(seeded).isEmpty()) {
                System.out.println("   could not install a live recipe producing " + targetId);
                return false;
            }
            level.setBlockAndUpdate(pos, com.create.productionline.registry.ModBlocks.PRODUCTION_COMPUTER.get()
                    .defaultBlockState());
            if (!(level.getBlockEntity(pos) instanceof com.create.productionline.block.entity
                    .ProductionComputerBlockEntity computer)) {
                System.out.println("   could not place a production computer at " + pos);
                return false;
            }
            var inv = computer.getInventory();
            inv.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_TARGET,
                    new ItemStack(Items.MILK_BUCKET));
            resetCarriers(computer);

            // The ask only happens for a requester with the authoring permission, and the answer
            // re-reads it from the live player — so the player has to really hold it.
            server.getPlayerList().op(profile);
            opped = true;
            net.neoforged.neoforge.common.util.FakePlayer asker =
                    net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, profile);
            asker.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            boolean ok = layoutExpect("the asking player really holds the authoring permission",
                    asker.hasPermissions(2));

            // The menu a real click is answered through, built by the production factory for this
            // player (see openComputerMenu for why openMenu itself cannot be used headlessly).
            com.create.productionline.menu.ProductionComputerMenu menu = openComputerMenu(asker, computer, 1);
            if (menu == null) {
                System.out.println("   could not open the computer's menu for "
                        + asker.getGameProfile().getName());
                return false;
            }
            ok &= layoutExpect("the menu belongs to this computer (" + menu.getClass().getSimpleName() + ")",
                    menu.computer() == computer);
            // THE predicate the whole live failure turned on: the real value, read from the real
            // menu for a player standing at the open computer.
            boolean menuOpen = menu.stillValid(asker);
            ok &= layoutExpect("a player standing at the open computer is inside the menu's validity", menuOpen);

            // The ask, exactly as production runs it: the client payload entry point (which reads the
            // permission off the real player) and then the queued run the block entity's tick drives.
            computer.computeProvided(asker, targetId, "", "", List.of(), targetId);
            computer.runCompute();
            long now = level.getGameTime();
            ok &= layoutExpect("the queued run records the question for the asking player",
                    computer.hasPendingPlaceholderRequest());
            ok &= layoutExpect("the question survives the run that recorded it",
                    computer.hasPendingPlaceholderRequest());
            ok &= layoutExpect("nothing is written while the question stands", carriersUntouched(inv));

            // A stranger at the same computer (a second player can open the same block's menu): the
            // question is not theirs, they write nothing, and it stays standing for the asker.
            net.neoforged.neoforge.common.util.FakePlayer stranger =
                    net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, strangerProfile);
            stranger.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            com.create.productionline.menu.ProductionComputerMenu strangerMenu =
                    openComputerMenu(stranger, computer, 2);
            ok &= layoutExpect("the stranger has the same computer's menu open",
                    strangerMenu != null && strangerMenu.computer() == computer);
            ok &= expectOutcome("a stranger's click is refused as not theirs",
                    com.create.productionline.block.entity.PlaceholderPrompt.Outcome.REJECTED,
                    computer.answerPlaceholderRequest(stranger.getUUID(),
                            com.create.productionline.block.entity.PlaceholderPrompt.Answer.ACCEPT,
                            stranger.hasPermissions(2), true, level.getGameTime()));
            ok &= layoutExpect("a stranger's click leaves the asker's question standing and writes nothing",
                    computer.hasPendingPlaceholderRequest() && carriersUntouched(inv));

            // A click whose container is really gone: the same player, moved out of the menu's reach,
            // so the value fed to the answer is the menu's own, not a literal.
            asker.moveTo(pos.getX() + 100.5D, pos.getY(), pos.getZ());
            boolean farMenuOpen = menu.stillValid(asker);
            ok &= layoutExpect("a player out of reach is outside the menu's validity", !farMenuOpen);
            ok &= expectOutcome("a click whose container is really gone writes nothing",
                    com.create.productionline.block.entity.PlaceholderPrompt.Outcome.WINDOW_GONE,
                    computer.answerPlaceholderRequest(asker.getUUID(),
                            com.create.productionline.block.entity.PlaceholderPrompt.Answer.ACCEPT,
                            asker.hasPermissions(2), farMenuOpen, level.getGameTime()));
            ok &= layoutExpect("a click whose container is gone writes nothing", carriersUntouched(inv));
            asker.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

            // The flow the author performs: the chat screen took the computer screen away, the
            // question must still be there, and answering it through the re-opened computer must
            // write — while a click with NO menu at all still writes nothing.
            resetCarriers(computer);
            computer.computeProvided(asker, targetId, "", "", List.of(), targetId);
            computer.runCompute();
            asker.closeContainer(); // what closing the screen does server-side
            ok &= layoutExpect("closing the computer's menu does not drop the question",
                    computer.hasPendingPlaceholderRequest());
            ok &= layoutExpect("a click with no menu at all writes nothing",
                    computer.hasPendingPlaceholderRequest() && carriersUntouched(inv));
            com.create.productionline.menu.ProductionComputerMenu reopened =
                    openComputerMenu(asker, computer, 3);
            if (reopened == null) {
                System.out.println("   could not re-open the computer's menu for the asker");
                return false;
            }
            boolean reopenedValid = reopened.stillValid(asker);
            ok &= layoutExpect("the re-opened menu is a valid one for a player at the computer", reopenedValid);
            ok &= expectOutcome("answering through the re-opened computer writes the placeholder",
                    com.create.productionline.block.entity.PlaceholderPrompt.Outcome.WRITE,
                    computer.answerPlaceholderRequest(asker.getUUID(),
                            com.create.productionline.block.entity.PlaceholderPrompt.Answer.ACCEPT,
                            asker.hasPermissions(2), reopenedValid, level.getGameTime()));
            LineScheme answered = LineSchemeSerializer.fromStack(inv.getItem(
                    com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME));
            ok &= layoutExpect("the answered question wrote the placeholder for the asked target: "
                    + answered.getOutputItem(),
                    answered.isPlaceholder() && targetId.equals(answered.getOutputItem()));
            ok &= layoutExpect("the answered question is settled",
                    !computer.hasPendingPlaceholderRequest());
            return ok;
        } catch (Throwable t) {
            System.out.println("   placeholder answer check crashed: " + t);
            t.printStackTrace(System.out);
            return false;
        } finally {
            level.removeBlock(pos, false);
            com.create.productionline.recipegen.CreateRecipePack.removeIsolated(server);
            if (opped) {
                server.getPlayerList().deop(profile);
            }
        }
    }

    /**
     * Opens the computer's menu for a player the way the server does it, and returns it (or
     * {@code null} when the factory refuses).
     *
     * <p>A headless test cannot call {@code player.openMenu}: NeoForge's {@code FakePlayer} overrides
     * it to a no-op, because half of what it does is sending the window packet to a client that does
     * not exist. The server-side half is the part the click path depends on — the menu the
     * production factory builds for THIS player becomes the player's own open menu, and the block
     * entity it carries is the one the answer may write into — so the test performs exactly that
     * statement. Nothing else about the menu is faked: it is the real
     * {@link com.create.productionline.menu.ProductionComputerMenu}, on the real block entity, and
     * its {@code stillValid(player)} answer is the one the live command handler reads.
     */
    private static com.create.productionline.menu.ProductionComputerMenu openComputerMenu(
            net.minecraft.server.level.ServerPlayer player,
            com.create.productionline.block.entity.ProductionComputerBlockEntity computer, int containerId) {
        com.create.productionline.menu.ProductionComputerMenu menu =
                com.create.productionline.menu.ProductionComputerMenu.fromServer(containerId,
                        player.getInventory(), computer);
        player.containerMenu = menu;
        return menu;
    }

    /**
     * The pure half of the table: the checks whose ORDER decides the verdict, driven row by row.
     *
     * <p>A stranger's click must be refused <em>and</em> leave the asker's question standing
     * ({@code clearsPending()} false), which is the property no single verdict test can show; the
     * same goes for a revoked permission. The rows here are the ones a headless server cannot reach
     * through the block entity, because it has no players to be asked or to misbehave.
     */
    private static boolean placeholderQuestionTable() {
        java.util.UUID asked = java.util.UUID.randomUUID();
        java.util.UUID stranger = java.util.UUID.randomUUID();
        ResourceLocation target = ResourceLocation.withDefaultNamespace("milk_bucket");
        com.create.productionline.block.entity.PlaceholderPrompt.Pending pending =
                new com.create.productionline.block.entity.PlaceholderPrompt.Pending(asked, target, 1000L);
        var accept = com.create.productionline.block.entity.PlaceholderPrompt.Answer.ACCEPT;
        var decline = com.create.productionline.block.entity.PlaceholderPrompt.Answer.DECLINE;

        boolean ok = expectOutcome("no question at all",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.EXPIRED,
                com.create.productionline.block.entity.PlaceholderPrompt.decide(null,
                        new com.create.productionline.block.entity.PlaceholderPrompt.Moment(
                                asked, accept, true, true, target.toString(), true, 0L)));
        ok &= expectOutcome("a stranger's click",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.REJECTED,
                com.create.productionline.block.entity.PlaceholderPrompt.decide(pending,
                        new com.create.productionline.block.entity.PlaceholderPrompt.Moment(
                                stranger, accept, true, true, target.toString(), true, 0L)));
        ok &= layoutExpect("a stranger's click leaves the question standing",
                !com.create.productionline.block.entity.PlaceholderPrompt.Outcome.REJECTED.clearsPending());
        ok &= expectOutcome("a click without authoring permission",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.REJECTED,
                com.create.productionline.block.entity.PlaceholderPrompt.decide(pending,
                        new com.create.productionline.block.entity.PlaceholderPrompt.Moment(
                                asked, accept, false, true, target.toString(), true, 0L)));
        ok &= expectOutcome("a click past the deadline",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.EXPIRED,
                com.create.productionline.block.entity.PlaceholderPrompt.decide(pending,
                        new com.create.productionline.block.entity.PlaceholderPrompt.Moment(
                                asked, accept, true, true, target.toString(), true, 1001L)));
        ok &= expectOutcome("a question asked through a container that lapsed",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.WINDOW_GONE,
                com.create.productionline.block.entity.PlaceholderPrompt.decide(pending,
                        new com.create.productionline.block.entity.PlaceholderPrompt.Moment(
                                asked, accept, true, false, target.toString(), true, 0L)));
        ok &= expectOutcome("a question about an item no longer in the target slot",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.EXPIRED,
                com.create.productionline.block.entity.PlaceholderPrompt.decide(pending,
                        new com.create.productionline.block.entity.PlaceholderPrompt.Moment(
                                asked, accept, true, true, "minecraft:barrier", true, 0L)));
        ok &= expectOutcome("the asked player declining",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.DECLINED,
                com.create.productionline.block.entity.PlaceholderPrompt.decide(pending,
                        new com.create.productionline.block.entity.PlaceholderPrompt.Moment(
                                asked, decline, true, true, target.toString(), true, 0L)));
        ok &= expectOutcome("accepting with no carrier left",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.NO_CARRIER,
                com.create.productionline.block.entity.PlaceholderPrompt.decide(pending,
                        new com.create.productionline.block.entity.PlaceholderPrompt.Moment(
                                asked, accept, true, true, target.toString(), false, 0L)));
        ok &= expectOutcome("accepting a live question with a carrier",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.WRITE,
                com.create.productionline.block.entity.PlaceholderPrompt.decide(pending,
                        new com.create.productionline.block.entity.PlaceholderPrompt.Moment(
                                asked, accept, true, true, target.toString(), true, 1000L)));
        ok &= layoutExpect("every settled outcome clears the question, REJECTED does not: "
                + com.create.productionline.block.entity.PlaceholderPrompt.Outcome.values().length
                + " outcomes",
                com.create.productionline.block.entity.PlaceholderPrompt.Outcome.WRITE.clearsPending()
                        && com.create.productionline.block.entity.PlaceholderPrompt.Outcome.DECLINED.clearsPending()
                        && com.create.productionline.block.entity.PlaceholderPrompt.Outcome.EXPIRED.clearsPending()
                        && com.create.productionline.block.entity.PlaceholderPrompt.Outcome.WINDOW_GONE
                                .clearsPending()
                        && com.create.productionline.block.entity.PlaceholderPrompt.Outcome.NO_CARRIER.clearsPending()
                        && !com.create.productionline.block.entity.PlaceholderPrompt.Outcome.REJECTED.clearsPending());
        return ok;
    }

    /** Compares one decision-table row. */
    private static boolean expectOutcome(String what,
            com.create.productionline.block.entity.PlaceholderPrompt.Outcome expected,
            com.create.productionline.block.entity.PlaceholderPrompt.Outcome actual) {
        if (actual != expected) {
            System.out.println("   " + what + ": expected " + expected + ", got " + actual);
            return false;
        }
        return true;
    }

    /** Restores a blank scheme and paper in the computer's two carrier slots. */
    private static void resetCarriers(com.create.productionline.block.entity.ProductionComputerBlockEntity computer) {
        var inventory = computer.getInventory();
        inventory.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME,
                new ItemStack(ModItems.LINE_SCHEME.get()));
        inventory.setItem(com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_CLIPBOARD,
                new ItemStack(Items.PAPER));
    }

    /** True when neither carrier holds a written placeholder. */
    private static boolean carriersUntouched(net.minecraft.world.Container inventory) {
        for (int slot : new int[]{com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_SCHEME,
                com.create.productionline.block.entity.ProductionComputerBlockEntity.SLOT_CLIPBOARD}) {
            LineScheme scheme = LineSchemeSerializer.fromStack(inventory.getItem(slot));
            if (scheme.isPlaceholder() || !scheme.getOutputItem().isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Translation keys of the status lines the computer currently reports.
     *
     * <p>A headless server has no loaded language, so the lines cannot be compared as text —
     * {@code getString()} on a translatable component returns the key itself. The key is the right
     * thing to assert anyway: it is what decides the wording on the client, and it is exactly what
     * tells the two placeholder origins (and the two "cannot be converted" lines) apart.
     */
    private static List<String> statusKeys(com.create.productionline.block.entity.ProductionComputerBlockEntity computer,
            net.minecraft.world.Container inventory) {
        return com.create.productionline.menu.ComputerStatus.lines(computer.getResultCode(),
                        computer.getLastErrorCode(),
                        inventory.getItem(com.create.productionline.block.entity
                                .ProductionComputerBlockEntity.SLOT_SCHEME),
                        inventory.getItem(com.create.productionline.block.entity
                                .ProductionComputerBlockEntity.SLOT_CLIPBOARD))
                .stream()
                .map(line -> line.getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                                ? translatable.getKey()
                                : line.getString())
                .toList();
    }

    /** The {@code ACTION value} of every clickable component in a line, siblings included. */
    private static List<String> clickCommands(net.minecraft.network.chat.Component line) {
        List<String> out = new java.util.ArrayList<>();
        collectClicks(line, out);
        return out;
    }

    private static void collectClicks(net.minecraft.network.chat.Component component, List<String> out) {
        net.minecraft.network.chat.ClickEvent click = component.getStyle().getClickEvent();
        if (click != null) {
            out.add(click.getAction() + " " + click.getValue());
        }
        for (net.minecraft.network.chat.Component sibling : component.getSiblings()) {
            collectClicks(sibling, out);
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

    /**
     * Guards the Ponder scenes' one silent failure mode. {@code PonderLevel}'s schema-level bounds — the
     * box {@code scene.world().setBlock}/{@code modifyBlock} may touch, because
     * {@code ReplaceBlocksInstruction} starts with {@code if (level.getBounds().isInside(pos))} — are
     * computed from the blocks the schematic actually <em>placed</em>, not from its declared {@code size}
     * (see {@code SchematicLevel#setBlock}). A scene that places its machine at y=1 on a plate-only
     * schematic therefore has it dropped without a single log line: the plate shows, the machine does not.
     * So our scenes reveal every prop from the schematic, and this check asserts the invariant both ways:
     * each scene's schematic holds the exact block at every position the scene shows, hides or modifies,
     * and that position lies inside the box the schematic's blocks span.
     *
     * <p>It also pins the shape that loads as <em>nothing</em> while reporting no error at all:
     * {@code size} and every {@code pos} have to be a {@code TAG_List} of {@code TAG_Int}. Written as a
     * {@code TAG_Int_Array} — which a hand-rolled reader accepts just as happily — vanilla's
     * {@code getListOrEmpty} reads an empty list and the structure becomes (0,0,0) with no blocks.
     */
    private static boolean ponderSchematicCoverage() throws Exception {
        // scene id -> every position that scene shows, hides or modifies -> the block that must be there
        Map<String, Map<BlockPos, String>> scenes = new LinkedHashMap<>();

        Map<BlockPos, String> computer = new LinkedHashMap<>();
        computer.put(new BlockPos(2, 1, 2), "create_productionline:production_computer");
        scenes.put("production_computer", computer);

        Map<BlockPos, String> loader = new LinkedHashMap<>();
        loader.put(new BlockPos(2, 1, 2), "create_productionline:scheme_loader");
        loader.put(new BlockPos(2, 2, 2), "minecraft:redstone_lamp"); // sits on the cabinet: it lights when the cabinet emits
        loader.put(new BlockPos(0, 1, 0), "create:creative_motor"); // drives the closing picture's belt
        for (int x = 0; x <= 4; x++) {
            loader.put(new BlockPos(x, 1, 1), "create:belt"); // the closing picture's line
        }
        loader.put(new BlockPos(1, 3, 1), "create:deployer"); // the belt 2nd cell, two cells above it
        loader.put(new BlockPos(3, 3, 1), "create:deployer"); // the belt 4th cell, two cells above it
        scenes.put("scheme_loader", loader);

        Map<BlockPos, String> dismantler = new LinkedHashMap<>();
        dismantler.put(new BlockPos(2, 1, 2), "create_productionline:dismantler");
        scenes.put("dismantler", dismantler);

        for (Map.Entry<String, Map<BlockPos, String>> scene : scenes.entrySet()) {
            CompoundTag nbt = readPonderSchematic(scene.getKey());
            ListTag size = nbt.getList("size", Tag.TAG_INT);
            if (size.size() != 3) {
                System.out.println("   " + scene.getKey() + ": size is not a 3-int list -> vanilla loads an"
                        + " empty structure: " + nbt.get("size"));
                return false;
            }
            Map<BlockPos, String> placed = new LinkedHashMap<>();
            ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (Tag element : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
                ListTag pos = ((CompoundTag) element).getList("pos", Tag.TAG_INT);
                if (pos.size() != 3) {
                    System.out.println("   " + scene.getKey() + ": a block's pos is not a 3-int list");
                    return false;
                }
                BlockPos at = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                placed.put(at, palette.getCompound(((CompoundTag) element).getInt("state")).getString("Name"));
                minX = Math.min(minX, at.getX());
                minY = Math.min(minY, at.getY());
                minZ = Math.min(minZ, at.getZ());
                maxX = Math.max(maxX, at.getX());
                maxY = Math.max(maxY, at.getY());
                maxZ = Math.max(maxZ, at.getZ());
            }
            for (Map.Entry<BlockPos, String> want : scene.getValue().entrySet()) {
                BlockPos at = want.getKey();
                if (at.getX() < minX || at.getX() > maxX || at.getY() < minY || at.getY() > maxY
                        || at.getZ() < minZ || at.getZ() > maxZ) {
                    System.out.println("   " + scene.getKey() + ": " + at.toShortString() + " is outside the"
                            + " schematic's block bounds (" + minX + "," + minY + "," + minZ + ")..("
                            + maxX + "," + maxY + "," + maxZ + ") -> Ponder drops it silently");
                    return false;
                }
                String found = placed.get(at);
                if (!want.getValue().equals(found)) {
                    System.out.println("   " + scene.getKey() + ": expected " + want.getValue() + " at "
                            + at.toShortString() + " but the schematic has " + found);
                    return false;
                }
            }
        }
        return true;
    }

    /** Reads a Ponder schematic straight out of the mod's own resources (works on a dedicated server). */
    private static CompoundTag readPonderSchematic(String sceneId) throws Exception {
        String path = "/assets/create_productionline/ponder/" + sceneId + ".nbt";
        try (InputStream in = SelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + path);
            }
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
    }

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
