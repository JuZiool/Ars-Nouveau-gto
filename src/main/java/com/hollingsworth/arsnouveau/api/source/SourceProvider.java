package com.hollingsworth.arsnouveau.api.source;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SourceProvider implements ISpecialSourceProvider {
    private final ISourceTile tile;
    private final BlockPos pos;
    private final boolean isValid;

    public SourceProvider(ISourceTile tile, BlockPos pos) {
        this.tile = tile;
        this.pos = pos;
        this.isValid = tile != null;
    }

    public SourceProvider(ISpecialSourceProvider specialSourceProvider) {
        this.tile = specialSourceProvider.getSource();
        this.pos = specialSourceProvider.getCurrentPos();
        isValid = specialSourceProvider.isValid();
    }

    @Override
    public ISourceTile getSource() {
        return tile;
    }

    @Override
    public boolean isValid() {
        return isValid && (!(tile instanceof BlockEntity blockEntity) || !blockEntity.isRemoved());
    }

    @Override
    public BlockPos getCurrentPos() {
        return pos;
    }
}
