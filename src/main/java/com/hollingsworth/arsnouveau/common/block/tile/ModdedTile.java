package com.hollingsworth.arsnouveau.common.block.tile;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.utils.TaskHandler;
import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LazyFieldDataManager;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.network.DataSyncNetwork;
import com.hollingsworth.arsnouveau.api.source.AbstractSourceMachine;
import com.hollingsworth.arsnouveau.common.block.ITickable;
import com.hollingsworth.arsnouveau.common.block.ITickableBlock;
import com.hollingsworth.arsnouveau.common.util.RegistryWrapper;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ModdedTile extends BlockEntity implements IFieldDataHolder {

    private final LazyFieldDataManager fieldDataManager = new LazyFieldDataManager(this);
    private TickableSubscription tickSubscription;
    private TickableSubscription syncSubscription;
    private volatile boolean tasksStopped = true;

    @Override
    public FieldDataManager getFieldDataManager() {
        return fieldDataManager.get();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        tasksStopped = false;
        if (this instanceof ITickable && getBlockState().getBlock() instanceof ITickableBlock) {
            tickSubscription =
                    TaskHandler.enqueueTick(
                            level, tickSubscription, () -> tasksStopped, this::scheduledTick, 1, 0);
        }
        if (!level.isClientSide && getFieldDataManager().hasSyncFields(LogicalSide.SERVER)) {
            syncSubscription =
                    TaskHandler.enqueueAsyncTick(
                            level,
                            syncSubscription,
                            () -> tasksStopped,
                            this::syncFields,
                            5,
                            Math.floorMod(worldPosition.hashCode(), 5));
        }
    }

    private void scheduledTick() {
        if (tasksStopped
                || !level.hasChunkAt(worldPosition)
                || !level.shouldTickBlocksAt(worldPosition)) return;
        BlockState state = getBlockState();
        if (!getType().isValid(state)
                || !(state.getBlock() instanceof ITickableBlock block)
                || !block.isTicking(state)) return;
        ((ITickable) this).tick(level, state, worldPosition);
        if (this instanceof AbstractSourceMachine sourceMachine) {
            sourceMachine.tickSourceParticles();
        }
    }

    private void syncFields() {
        if (!tasksStopped) DataSyncNetwork.syncBlockEntityToClient(this, false, true);
    }

    private void stopTasks() {
        tasksStopped = true;
        if (tickSubscription != null) tickSubscription.unsubscribe();
        if (syncSubscription != null) syncSubscription.unsubscribe();
    }

    @Override
    public void setRemoved() {
        stopTasks();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        stopTasks();
        super.onChunkUnloaded();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        if (tag.contains("gto_sync")) {
            getFieldDataManager()
                    .readFromNetworkBuffer(LogicalSide.CLIENT, tag.getByteArray("gto_sync"));
        }
    }

    public ModdedTile(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state) {
        super(tileEntityTypeIn, pos, state);
    }

    public ModdedTile(
            RegistryWrapper<? extends BlockEntityType<?>> tileEntityTypeIn,
            BlockPos pos,
            BlockState state) {
        this(tileEntityTypeIn.get(), pos, state);
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        handleUpdateTag(pkt.getTag() == null ? new CompoundTag() : pkt.getTag());
    }

    public boolean updateBlock() {
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, 3);
            setChanged();
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        if (getFieldDataManager().hasSyncFields(LogicalSide.SERVER)) {
            // Initial chunk sync must not clear the async writer's pending changes.
            tag.putByteArray(
                    "gto_sync",
                    new FieldDataManager(this).writeToNetworkBuffer(LogicalSide.SERVER, true));
        }
        return tag;
    }

    public double getX() {
        return this.worldPosition.getX();
    }

    public double getY() {
        return this.worldPosition.getY();
    }

    public double getZ() {
        return this.worldPosition.getZ();
    }
}
