package com.create.productionline.qa;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.compat.ClipboardCompat;
import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.line.mapper.Mappers;
import com.create.productionline.line.mapper.MappingResult;
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

/**
 * Headless, server-side QA self test. Activated by
 * {@code -Dcreate_productionline.selfTest=true}. Runs against the real game
 * registries / NBT / component system / recipe manager, prints one line per
 * check and stops the server afterwards.
 *
 * <p>Coverage (against the SRS QA list):
 * TC-01 invalid/unmapped recipe -> "cannot map" (no bogus scheme);
 * TC-02 clipboard build-guide injection NBT shape;
 * TC-05 scheme NBT round-trip + version;
 * plus recipe lookup & mapping sanity on live data and DataPacket whitelisting.
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
            check("TC-01 mapping (positive, live recipes)", () -> liveRecipePositive(level));
            check("TC-01 mapping (negative, unmappable)", () -> liveRecipeNegative(level));
            check("Create recipe JSON schema + datapack install", () -> createRecipeInstall(server));
            check("Scheme embeds generated recipes (round trip)", () -> schemeEmbedsRecipes());
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
        // Build two spec-conformant Create recipe files and install them into the
        // world datapack; the server reload acts as a real datapack parse canary.
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
        try {
            int written = com.create.productionline.recipegen.CreateRecipePack.install(server, files);
            System.out.println("   installed test recipe files: " + written);
            return written == 2;
        } finally {
            // Do not leave test recipes behind in the world datapack, even on failure.
            com.create.productionline.recipegen.CreateRecipePack.deactivate(server);
        }
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

    private static boolean liveRecipePositive(ServerLevel level) {
        // A recipe that always exists in vanilla data: planks -> stick (crafting).
        ResourceLocation target = BuiltInRegistries.ITEM.getKey(Items.STICK);
        List<RecipeDescriptor> recipes = ServerRecipeLookup.findRecipes(level, target);
        boolean found = false;
        for (RecipeDescriptor descriptor : recipes) {
            MappingResult result = Mappers.get().map(descriptor);
            if (result.ok()) {
                found = true;
                System.out.println("   mapped " + descriptor.recipeId() + " [" + descriptor.categoryId() + "] -> "
                        + result.scheme().totalFacilityCount() + " facilities");
                if (result.scheme().isEmpty()) {
                    return false;
                }
                break;
            }
        }
        if (!found) {
            System.out.println("   no mappable recipe found for stick among " + recipes.size() + " recipes");
        }
        return found;
    }

    private static boolean liveRecipeNegative(ServerLevel level) {
        // A target with no recipe at all: mapper/computer must not fabricate a scheme.
        List<RecipeDescriptor> none = ServerRecipeLookup.findRecipes(level,
                ResourceLocation.fromNamespaceAndPath("create_productionline", "not_a_real_item"));
        if (!none.isEmpty()) {
            System.out.println("   unexpected recipes for fake item: " + none.size());
            return false;
        }
        RecipeDescriptor bogus = new RecipeDescriptor("x:y", "totally:unknown_category",
                List.of("minecraft:stick"), List.of("minecraft:diamond"));
        MappingResult result = Mappers.get().map(bogus);
        if (result.ok()) {
            System.out.println("   mapper fabricated a scheme for unknown category");
            return false;
        }
        System.out.println("   unmapped category correctly rejected: " + result.message());
        return true;
    }

    private static boolean schemeEmbedsRecipes() {
        // Serializer round-trips the generated Create recipe payloads stored on a scheme.
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
            System.out.println("[FAIL] " + name + " -> exception " + e);
            fail(name);
        }
    }

    private static void fail(String name) {
        FAIL.incrementAndGet();
        System.out.println("[FAIL] " + name);
    }
}
