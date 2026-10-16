package com.hollingsworth.arsnouveau.common.block;

import javax.annotation.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public interface ITickableBlock extends EntityBlock {
    default boolean isTicking(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    default <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        return null;
    }

    @Nullable
    static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> type1,
            BlockEntityType<E> type2,
            BlockEntityTicker<? super E> ticker) {
        return type2 == type1 ? (BlockEntityTicker<A>) ticker : null;
    }
}
