package com.hollingsworth.arsnouveau.common.block.tile;

import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.source.AbstractSourceMachine;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.api.source.SourceProvider;
import com.hollingsworth.arsnouveau.common.block.ITickable;
import com.hollingsworth.arsnouveau.common.block.SourceJar;
import com.hollingsworth.arsnouveau.common.util.RegistryWrapper;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SourceJarTile extends AbstractSourceMachine implements ITooltipProvider, ITickable {

    private final SourceProvider sourceProvider = new SourceProvider(this, worldPosition);

    public SourceProvider getSourceProvider() {
        return sourceProvider;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        SourceManager.INSTANCE.addJar(this);
    }

    @Override
    public void setRemoved() {
        if (level != null) SourceManager.INSTANCE.removeJar(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) SourceManager.INSTANCE.removeJar(this);
        super.onChunkUnloaded();
    }

    public SourceJarTile(BlockPos pos, BlockState state) {
        super(BlockRegistry.SOURCE_JAR_TILE, pos, state);
    }

    public SourceJarTile(
            BlockEntityType<? extends SourceJarTile> tileTileEntityType,
            BlockPos pos,
            BlockState state) {
        super(tileTileEntityType, pos, state);
    }

    public SourceJarTile(
            RegistryWrapper<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type.get(), pos, state);
    }

    @Override
    public int getMaxSource() {
        return 10000;
    }

    @Override
    public boolean updateBlock() {
        super.updateBlock();
        updateFillState();
        return true;
    }

    @Override
    protected void sourceChanged() {
        super.sourceChanged();
        updateFillState();
    }

    private void updateFillState() {
        if (level == null || level.isClientSide) return;
        BlockState state = level.getBlockState(worldPosition);
        int fillState = 0;
        if (this.getSource() > 0 && this.getSource() < 1000) fillState = 1;
        else if (this.getSource() != 0) {
            fillState = (this.getSource() / 1000) + 1;
        }
        if (state.hasProperty(SourceJar.fill) && state.getValue(SourceJar.fill) != fillState)
            level.setBlock(worldPosition, state.setValue(SourceJar.fill, fillState), 3);
    }

    @Override
    public int getTransferRate() {
        return getMaxSource();
    }

    @Override
    public void getTooltip(List<Component> tooltip) {
        tooltip.add(
                Component.translatable(
                        "ars_nouveau.source_jar.fullness",
                        (getSource() * 100) / this.getMaxSource()));
    }
}
