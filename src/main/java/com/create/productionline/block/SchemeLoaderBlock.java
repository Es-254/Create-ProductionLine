package com.create.productionline.block;

import com.create.productionline.block.entity.SchemeLoaderBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

/**
 * 方案加载柜 — a server-rack styled cabinet. Insert a written Line Scheme to make
 * the pipeline recipes stored on it effective (installed as a datapack); take
 * the scheme out again to disable them.
 */
public class SchemeLoaderBlock extends Block implements EntityBlock {

    public SchemeLoaderBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SchemeLoaderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type == com.create.productionline.registry.ModBlockEntities.SCHEME_LOADER.get()) {
            return (lvl, pos, st, be) -> SchemeLoaderBlockEntity.tick(lvl, pos, st, (SchemeLoaderBlockEntity) be);
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SchemeLoaderBlockEntity loader) {
                serverPlayer.openMenu(loader.menuProvider());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof SchemeLoaderBlockEntity loader) {
            return loader.isActive() ? 15 : 0;
        }
        return 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!movedByPiston) {
                if (level.getBlockEntity(pos) instanceof SchemeLoaderBlockEntity loader) {
                    loader.onRemoved();
                    loader.dropContents(level, pos);
                    level.updateNeighbourForOutputSignal(pos, this);
                }
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
