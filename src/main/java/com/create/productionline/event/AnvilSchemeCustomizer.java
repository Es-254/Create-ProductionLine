package com.create.productionline.event;

import com.create.productionline.item.LineSchemeItem;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.line.scheme.SchemeAnvilMachine;
import com.create.productionline.registry.ModDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * OP-only anvil flow that hand-authors a LineScheme:
 *
 * <pre>
 *   [scheme with a plan] + paper -> cleared scheme        (target kept, materials dropped)
 *   [cleared scheme]     + item  -> scheme + 1 material   (repeatable, order preserved)
 *   [scheme, >= 1 item]  + paper -> locked scheme         (frozen)
 * </pre>
 *
 * <p>The decision itself lives in {@link SchemeAnvilMachine} — a pure function of the two
 * slots and the permission gate, so the whole state table is asserted by the self test
 * instead of only by hand. This class does what needs a live event: the side and
 * permission gate, writing the output stack, and taking over the anvil's numbers.
 *
 * <p>Vanilla anvil logic is skipped by handing the event a non-empty output — the
 * documented contract of {@link AnvilUpdateEvent} ("if not canceled and output is
 * non-empty, the provided stack is used and vanilla logic is skipped"). Illegal input is
 * answered with {@code setCanceled(true)}, whose contract is "vanilla logic is skipped and
 * the output is set to EMPTY": nothing is consumed and the item is left exactly as it was.
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
            // createResult() also runs on the client for prediction; the component is only
            // ever written here on the server, and the result slot is a container slot the
            // client cannot set, so a forged scheme cannot be produced that way.
            return;
        }

        SchemeAnvilMachine.Decision decision = SchemeAnvilMachine.decide(left, event.getRight(),
                player.hasPermissions(REQUIRED_PERMISSION_LEVEL));

        switch (decision.action()) {
            case PASS -> {
                // leave the vanilla anvil alone
            }
            case REFUSE -> event.setCanceled(true);
            default -> apply(event, player, left, decision);
        }
    }

    /**
     * Writes the output stack and takes over the anvil's numbers.
     *
     * <p>The level cost cannot be zero: {@code AnvilMenu.mayPickup} is
     * {@code (hasInfiniteMaterials || experienceLevel >= cost) && cost > 0}, so a zero cost
     * would make the result impossible to take. The machine therefore charges the minimum
     * (one level) and {@link #onRepair} gives it back, which leaves a net cost of zero
     * while the player still needs one level to pick the item up. {@code setMaterialCost(1)}
     * consumes exactly one right-slot item; the API documents that {@code 0} consumes the
     * <em>entire</em> stack. The repair penalty is cleared so a scheme that has been through
     * the anvil often never turns into "Too Expensive".
     */
    private static void apply(AnvilUpdateEvent event, Player player, ItemStack left,
            SchemeAnvilMachine.Decision decision) {
        ItemStack output = left.copyWithCount(1);
        output.remove(DataComponents.REPAIR_COST);
        LineSchemeSerializer.saveToStack(output, decision.plan());
        output.set(ModDataComponents.CUSTOM_ASSEMBLY.get(), decision.next());

        event.setOutput(output);
        event.setCost(decision.levelCost());
        event.setMaterialCost(decision.materialCost());

        if (decision.notice()) {
            // One material cannot become an assembly line: say so, once, to this player.
            player.displayClientMessage(
                    Component.translatable("screen.create_productionline.anvil.single_pending"), true);
        }
    }

    /**
     * Refunds the single level charged above, so a custom write costs no experience. Only
     * touched for our own output; every other anvil use keeps vanilla numbers.
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
}
