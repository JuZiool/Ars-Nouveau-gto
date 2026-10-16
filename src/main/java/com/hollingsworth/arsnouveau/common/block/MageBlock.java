package com.hollingsworth.arsnouveau.common.block;

import com.hollingsworth.arsnouveau.common.block.tile.MageBlockTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class MageBlock extends TickableModBlock {
    public static final BooleanProperty TEMPORARY = BooleanProperty.create("temporary");

    public MageBlock() {
        super(defaultProperties().lightLevel(bs -> 7).noOcclusion().dynamicShape());
        registerDefaultState(defaultBlockState().setValue(TEMPORARY, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MageBlockTile(pos, state);
    }

    @Override
    public boolean canDropFromExplosion(
            BlockState state, BlockGetter world, BlockPos pos, Explosion explosion) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(TEMPORARY);
    }

    @Override
    public boolean isTicking(BlockState state) {
        return state.getValue(TEMPORARY);
    }
}
