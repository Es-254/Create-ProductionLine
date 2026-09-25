package com.create.productionline.network;

import java.util.ArrayList;
import java.util.List;

import com.create.productionline.ProductionLineMod;
import com.create.productionline.menu.ProductionComputerMenu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Client → server payloads.
 *
 * <p>{@link ComputerComputePayload} is sent when the player presses "Compute".
 * Because JEI (and any reliable recipe read) is client-side, the CLIENT resolves
 * the target recipe's ingredients (via the client recipe manager + resources)
 * and sends them here; the server then builds the pipeline plan / embeds the
 * native Create recipe JSON and writes it onto the carrier items.
 */
public final class ModPayloads {

    /**
     * Compute request sent when the player presses "Compute".
     *
     * <p>{@code recipeId} is the real registry id of the recipe the client matched
     * (see {@code ClientRecipeResolver.Resolved}). The server treats NOTHING in
     * this payload as authoritative: it re-resolves {@code recipeId} against its
     * own {@code RecipeManager} and only accepts it when that recipe really
     * produces {@code targetId} — which must itself be the item sitting in the
     * computer's target slot. {@code categoryId}/{@code inputs} are a
     * display-only fallback used when the server can find no recipe at all; in
     * that case no installable recipe is generated (anti-injection).
     */
    public record ComputerComputePayload(String targetId, String recipeId, String categoryId, List<String> inputs,
            String outputId) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ComputerComputePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                        ProductionLineMod.MODID, "computer_compute"));

        public static final StreamCodec<ByteBuf, ComputerComputePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ComputerComputePayload::targetId,
                ByteBufCodecs.STRING_UTF8, ComputerComputePayload::recipeId,
                ByteBufCodecs.STRING_UTF8, ComputerComputePayload::categoryId,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), ComputerComputePayload::inputs,
                ByteBufCodecs.STRING_UTF8, ComputerComputePayload::outputId,
                ComputerComputePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DismantlePayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DismantlePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                        ProductionLineMod.MODID, "dismantle"));
        public static final StreamCodec<ByteBuf, DismantlePayload> CODEC =
                StreamCodec.unit(new DismantlePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ModPayloads() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ComputerComputePayload.TYPE, ComputerComputePayload.CODEC,
                ModPayloads::handleCompute);
        registrar.playToServer(DismantlePayload.TYPE, DismantlePayload.CODEC, ModPayloads::handleDismantle);
    }

    private static void handleDismantle(DismantlePayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        context.enqueueWork(() -> {
            if (serverPlayer.containerMenu instanceof com.create.productionline.menu.DismantlerMenu menu) {
                // GUI 可能已失效(方块被破坏、玩家走远):menu.stillValid() 经由
                // ModContainer.stillValid() 校验 8 格距离 + 方块仍在原位,先校验再
                // 触碰任何物品槽,防止对已失效的容器执行拆解。
                if (!menu.stillValid(serverPlayer)) {
                    return;
                }
                // Tell the player who pressed the button what happened, privately, and hand
                // them the refund: the panel only shows the hint text, so before this a
                // refused dismantle was completely silent (the button looked broken).
                var outcome = menu.revert(serverPlayer);
                String key = outcome.result().langKey();
                if (key != null) {
                    // A refusal that knows what it is about names it (which recipe was gone),
                    // so the player is not left guessing between several items.
                    boolean detailed = !outcome.detail().isBlank()
                            && outcome.result() == com.create.productionline.block.entity
                                    .DismantlerBlockEntity.RevertResult.RECIPE_MISSING;
                    serverPlayer.displayClientMessage(detailed
                            ? net.minecraft.network.chat.Component.translatable(
                                    "dismantler.create_productionline.result.recipe_missing_detail",
                                    outcome.detail())
                            : net.minecraft.network.chat.Component.translatable(key), false);
                }
                // Fluid ingredients can never be handed back as items; say so instead of
                // letting the player wonder where the water went.
                if (outcome.fluidsSkipped() > 0) {
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "dismantler.create_productionline.result.fluids_skipped",
                            outcome.fluidsSkipped()), false);
                }
            }
        });
    }

    private static void handleCompute(ComputerComputePayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        context.enqueueWork(() -> {
            if (serverPlayer.containerMenu instanceof ProductionComputerMenu menu) {
                // GUI 可能已失效(方块被破坏、玩家走远):先经 ModContainer.stillValid()
                // 校验 8 格距离 + 方块仍在原位,再触碰任何槽位。
                if (!menu.stillValid(serverPlayer)) {
                    return;
                }
                // The run is queued for the next tick, so the block entity reports the
                // outcome to this player when it finishes (see
                // ProductionComputerBlockEntity#runCompute). Reading the status here would
                // always see RESULT_EMPTY and send the "how to use me" line instead.
                menu.computeProvided(serverPlayer, payload.targetId(), payload.recipeId(), payload.categoryId(),
                        payload.inputs(), payload.outputId());
            }
        });
    }

    /** Sends the compute request (client-resolved recipe hint; verified server-side). */
    public static void sendComputeRequest(String targetId, String recipeId, String categoryId, List<String> inputs,
            String outputId) {
        PacketDistributor.sendToServer(
                new ComputerComputePayload(targetId, recipeId, categoryId, inputs, outputId));
    }

    /** Sends the dismantle action from the dismantler GUI. */
    public static void sendDismantleRequest() {
        PacketDistributor.sendToServer(new DismantlePayload());
    }
}
