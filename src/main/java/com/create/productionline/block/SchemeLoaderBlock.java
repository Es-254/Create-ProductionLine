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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

/**
 * 方案加载柜 — a server-rack styled cabinet. Insert a written Line Scheme to make
 * the pipeline recipes stored on it effective (installed as a datapack); take
 * the scheme out again to disable them.
 */
public class SchemeLoaderBlock extends Block implements EntityBlock {

    /**
     * Loaded-scheme count (0 … {@value SchemeLoaderBlockEntity#SLOT_COUNT}) as the
     * block state carried it up to 1.0.2: six stage models
     * ({@code scheme_loader_empty}, {@code scheme_loader_bar_1} … {@code scheme_loader_bar_5},
     * {@code scheme_loader}) were selected through this property, so every scheme put
     * into the cabinet re-meshed the chunk section and sent a block update.
     *
     * <p><b>Nothing writes this property any more.</b> The bar is drawn by a renderer
     * now — {@code SchemeLoaderRenderer} (which is also what Ponder uses) and the
     * Flywheel visual {@code SchemeLoaderVisual} — reading the count from the block
     * entity ({@link SchemeLoaderBlockEntity#getRenderSegments()}), and every value of
     * this property maps to the bar-less {@code scheme_loader_empty} model. The
     * property and its variants are kept so that worlds which stored {@code fill=N}
     * still resolve to a valid model instead of falling back to the missing model.
     */
    public static final IntegerProperty FILL =
            IntegerProperty.create("fill", 0, SchemeLoaderBlockEntity.SLOT_COUNT);

    public SchemeLoaderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
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
            if (level.getBlockEntity(pos) instanceof SchemeLoaderBlockEntity loader) {
                if (movedByPiston) {
                    // DEFENSIVE branch: vanilla pistons cannot move this block at all —
                    // PistonBaseBlock.isPushable(…) ends in !state.hasBlockEntity() —
                    // and Create's contraption movers remove the block entity first.
                    // If a mover ever does report movedByPiston, only the OLD
                    // contribution is dropped here (never the contents, which travel
                    // with the block): leaving it behind would keep that cabinet's
                    // recipes active with no cabinet in the world. The real cleanup
                    // guarantee is the BE's persisted RegisteredKey plus
                    // CreateRecipePack.sweepOrphanContributions on server start.
                    loader.onMovedByPiston();
                } else {
                    loader.onRemoved();
                    loader.dropContents(level, pos);
                }
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SchemeLoaderBlockEntity loader) {
            // Covers the block entity being re-created at a new position (Create
            // contraption move, /clone, world restore) as well as ordinary placement:
            // make the cabinet re-register its contribution under its current
            // position, so a relocated loader never loses its activation.
            loader.markRelocated();
        }
    }
}
