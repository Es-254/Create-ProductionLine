package com.create.productionline.block;

import com.create.productionline.block.entity.ProductionComputerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

/**
 * 产线计算机 — looks up the recipe for the inserted target item (server
 * {@code RecipeManager}), converts foreign recipes into native Create recipe
 * JSON payloads and writes the ordered pipeline plan onto a carrier item
 * (paper / clipboard / Line Scheme).
 */
public class ProductionComputerBlock extends Block implements EntityBlock {

    public ProductionComputerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProductionComputerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type == com.create.productionline.registry.ModBlockEntities.PRODUCTION_COMPUTER.get()) {
            return (lvl, pos, st, be) -> ProductionComputerBlockEntity.tick(lvl, pos, st,
                    (ProductionComputerBlockEntity) be);
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ProductionComputerBlockEntity computer) {
                serverPlayer.openMenu(computer.menuProvider());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!movedByPiston) {
                if (level.getBlockEntity(pos) instanceof ProductionComputerBlockEntity computer) {
                    computer.dropContents(level, pos);
                    level.updateNeighbourForOutputSignal(pos, this);
                }
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
