package com.create.productionline.block;

import com.create.productionline.block.entity.DismantlerBlockEntity;

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
 * 破拆机 — reverts a generic intermediate (or processed item) back to its
 * pre-processing raw materials and leaves a read-only Line Scheme Mirror.
 */
public class DismantlerBlock extends Block implements EntityBlock {

    public DismantlerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DismantlerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof DismantlerBlockEntity be) {
                serverPlayer.openMenu(be.menuProvider());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // NOTE: vanilla pistons can never move a block that owns a block entity —
        // PistonBaseBlock.isPushable ends with !state.hasBlockEntity() — so a
        // Dismantler is never actually relocated by a piston and its inventory is
        // never carried to a new position. The movedByPiston guard is therefore
        // pure defence in depth (it also covers modded movers): were the block ever
        // moved, the old position's contents must not be dropped a second time.
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            if (level.getBlockEntity(pos) instanceof DismantlerBlockEntity be) {
                be.dropContents(level, pos);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
