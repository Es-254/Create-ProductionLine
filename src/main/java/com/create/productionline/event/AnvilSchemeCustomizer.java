package com.create.productionline.event;

import java.util.List;

import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.line.scheme.CustomAssembly;
import com.create.productionline.line.scheme.CustomAssemblyPlanner;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.registry.ModDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * OP-only anvil flow that hand-authors a {@link LineScheme}:
 *
 * <pre>
 *   [scheme with a plan] + paper -> cleared scheme        (target kept, materials dropped)
 *   [cleared scheme]     + item  -> scheme + 1 material   (repeatable, order preserved)
 *   [scheme, >= 1 item]  + paper -> locked scheme         (frozen)
 * </pre>
 *
 * <p>Vanilla anvil logic is skipped by handing the event a non-empty output — that
 * is the documented contract of {@link AnvilUpdateEvent} ("if not canceled and
 * output is non-empty, the provided stack is used and vanilla logic is skipped"),
 * which is what keeps repairing, renaming and the level cost out of this flow.
 * Illegal input is answered with {@code setCanceled(true)}, whose contract is
 * "vanilla logic is skipped and the output is set to EMPTY": nothing is consumed
 * and the item in the slot is left exactly as it was.
 *
 * <p>The player never needs to remember which material came last: the ordered list
 * lives in the {@link ModDataComponents#CUSTOM_ASSEMBLY} component, which travels
 * with the item when the player takes the result out and puts it back in.
 */
public final class AnvilSchemeCustomizer {

    /** OP permission level required for every custom write. */
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    private AnvilSchemeCustomizer() {
    }

    @SubscribeEvent
    public static void update(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (!(left.getItem() instanceof LineSchemeItem)) {
            return; // not our item: the anvil stays vanilla, for everyone
        }

        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            // createResult() also runs on the client for prediction; the component is
            // only ever written here on the server, and the result slot is a container
            // slot the client cannot set, so a forged scheme cannot be produced.
            return;
        }
        if (!player.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
            return; // not an OP: plain vanilla anvil behaviour
        }

        if (left.getCount() != 1) {
            reject(event); // stacked schemes are never a legal input
            return;
        }

        CustomAssembly custom = left.get(ModDataComponents.CUSTOM_ASSEMBLY.get());
        ItemStack right = event.getRight();
        boolean paper = right.is(Items.PAPER);

        if (custom != null && custom.locked()) {
            reject(event); // a locked scheme is frozen
            return;
        }

        if (paper) {
            if (custom == null) {
                clear(event, left);
            } else {
                lock(event, player, left, custom);
            }
            return;
        }

        if (custom == null || right.isEmpty()) {
            reject(event); // a material before clearing, or an empty right slot
            return;
        }
        String material = itemIdOf(right);
        if (material == null || material.equals(custom.targetItem())) {
            reject(event); // unmappable, or the material is the product itself
            return;
        }
        hammer(event, left, custom, material);
    }

    /** Step 1: keep the target, drop every material step and every cached payload. */
    private static void clear(AnvilUpdateEvent event, ItemStack left) {
        LineScheme source = LineSchemeSerializer.fromStack(left);
        String target = source.getOutputItem();
        if (target == null || target.isBlank()) {
            reject(event); // an empty scheme has no target to keep
            return;
        }
        // The compute-time numbers are inherited, never recomputed here: the player
        // asked for `targetOutputCount` items when they ran the computer, and hammering
        // materials in must not quietly reset that to a single craft.
        CustomAssembly custom = new CustomAssembly(List.of(), false, false, target,
                source.getTargetOutputCount(), source.getRepeatCount());
        emit(event, left, CustomAssemblyPlanner.cleared(target).repeatedLike(source), custom);
    }

    /** Step 2: append exactly one material, preserving the order. */
    private static void hammer(AnvilUpdateEvent event, ItemStack left, CustomAssembly custom, String material) {
        CustomAssembly next = custom.withMaterial(material);
        emit(event, left, CustomAssemblyPlanner.rebuild(next), next);
    }

    /** Step 3: freeze the scheme; one material means the single-material fallback. */
    private static void lock(AnvilUpdateEvent event, Player player, ItemStack left, CustomAssembly custom) {
        if (!custom.isReadyToLock()) {
            reject(event); // nothing hammered yet
            return;
        }
        CustomAssembly locked = custom.withLocked(true);
        emit(event, left, CustomAssemblyPlanner.rebuild(locked), locked);
        if (locked.singleMaterialFallback()) {
            // One material cannot become an assembly line: say so, once, in the GUI.
            player.displayClientMessage(
                    Component.translatable("screen.create_productionline.anvil.single_pending"), true);
        }
    }

    /**
     * Writes the output stack and takes over the anvil's numbers.
     *
     * <p>{@code setMaterialCost(1)} consumes exactly one right-slot item; the API
     * documents that {@code 0} consumes the <em>entire</em> stack. The repair penalty
     * is cleared so a scheme that has been through the anvil often never turns into
     * "Too Expensive".
     *
     * <p>The level cost cannot be zero: {@code AnvilMenu.mayPickup} is
     * {@code (hasInfiniteMaterials || experienceLevel >= cost) && cost > 0}, so a
     * zero cost would make the result impossible to take. We therefore charge the
     * minimum (one level) and give it back in {@link #onRepair}, which makes the net
     * cost zero while still passing the vanilla gate.
     */
    private static void emit(AnvilUpdateEvent event, ItemStack left, LineScheme scheme, CustomAssembly custom) {
        ItemStack output = left.copyWithCount(1);
        output.remove(DataComponents.REPAIR_COST);
        LineSchemeSerializer.saveToStack(output, scheme);
        output.set(ModDataComponents.CUSTOM_ASSEMBLY.get(), custom);

        event.setOutput(output);
        event.setCost(1);         // the vanilla gate needs cost > 0 to allow taking it
        event.setMaterialCost(1); // one paper / one material per operation
    }

    /**
     * Refunds the single level charged above, so a custom write costs no experience.
     * Only touched for our own output; every other anvil use keeps vanilla numbers.
     */
    @SubscribeEvent
    public static void onRepair(net.neoforged.neoforge.event.entity.player.AnvilRepairEvent event) {
        ItemStack result = event.getOutput();
        if (result == null || result.isEmpty()
                || !result.has(ModDataComponents.CUSTOM_ASSEMBLY.get())) {
            return;
        }
        Player player = event.getEntity();
        if (player != null && !player.level().isClientSide() && !player.getAbilities().instabuild) {
            player.giveExperienceLevels(1);
        }
    }

    /** Illegal input: cancel, which skips vanilla logic and empties the output. */
    private static void reject(AnvilUpdateEvent event) {
        event.setCanceled(true);
    }

    private static String itemIdOf(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }
}
