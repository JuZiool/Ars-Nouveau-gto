package com.hollingsworth.arsnouveau.api.source;

import com.gto.datasynclib.annotations.Codec;
import com.gto.datasynclib.annotations.SyncToClient;
import com.hollingsworth.arsnouveau.api.util.IWololoable;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.client.particle.ParticleUtil;
import com.hollingsworth.arsnouveau.common.block.tile.ModdedTile;
import com.hollingsworth.arsnouveau.common.util.RegistryWrapper;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractSourceMachine extends ModdedTile implements ISourceTile, IWololoable {

    @SyncToClient private volatile int source = 0;
    @SyncToClient private volatile int maxSource = 0;

    @SyncToClient
    @Codec(writeToBuffer = "writeColor", readFromBuffer = "readColor")
    private volatile ParticleColor color = ParticleColor.defaultParticleColor();

    @SyncToClient
    @Codec(writeToBuffer = "writeSourcePaths", readFromBuffer = "readSourcePaths")
    private volatile long[] sourceParticlePaths = new long[0];

    private final ArrayList<SourcePath> activeSourcePaths = new ArrayList<>(4);

    private void writeColor(FriendlyByteBuf buffer, ParticleColor value) {
        buffer.writeInt(value.getColor());
    }

    private ParticleColor readColor(FriendlyByteBuf buffer) {
        return ParticleColor.fromInt(buffer.readInt());
    }

    private void writeSourcePaths(FriendlyByteBuf buffer, long[] paths) {
        buffer.writeLongArray(paths);
    }

    private long[] readSourcePaths(FriendlyByteBuf buffer) {
        long[] paths = buffer.readLongArray();
        if (paths.length % 3 != 0)
            throw new IllegalArgumentException("Invalid source particle paths");
        return paths;
    }

    public void recordSourcePath(BlockPos from, BlockPos to, ParticleColor color) {
        long fromPosition = from.asLong();
        long toPosition = to.asLong();
        long expires = level.getGameTime() + 25;
        for (int i = 0; i < activeSourcePaths.size(); i++) {
            SourcePath path = activeSourcePaths.get(i);
            if (path.from == fromPosition
                    && path.to == toPosition
                    && path.color == color.getColor()) {
                path.expires = expires;
                return;
            }
        }
        activeSourcePaths.add(new SourcePath(fromPosition, toPosition, color.getColor(), expires));
        publishSourcePaths();
    }

    private void publishSourcePaths() {
        long[] paths = new long[activeSourcePaths.size() * 3];
        for (int i = 0; i < activeSourcePaths.size(); i++) {
            SourcePath path = activeSourcePaths.get(i);
            paths[i * 3] = path.from;
            paths[i * 3 + 1] = path.to;
            paths[i * 3 + 2] = path.color;
        }
        sourceParticlePaths = paths;
    }

    public void tickSourceParticles() {
        if (level.isClientSide) {
            if (level.getGameTime() % 20 != 0) return;
            long[] paths = sourceParticlePaths;
            for (int i = 0; i < paths.length; i += 3) {
                ParticleUtil.spawnFollowProjectile(
                        level,
                        BlockPos.of(paths[i]),
                        BlockPos.of(paths[i + 1]),
                        ParticleColor.fromInt((int) paths[i + 2]));
            }
        } else {
            boolean changed = false;
            for (int i = activeSourcePaths.size() - 1; i >= 0; i--) {
                if (activeSourcePaths.get(i).expires < level.getGameTime()) {
                    activeSourcePaths.remove(i);
                    changed = true;
                }
            }
            if (changed) publishSourcePaths();
        }
    }

    private static final class SourcePath {
        private final long from;
        private final long to;
        private final int color;
        private long expires;

        private SourcePath(long from, long to, int color, long expires) {
            this.from = from;
            this.to = to;
            this.color = color;
            this.expires = expires;
        }
    }

    public static String SOURCE_TAG = "source";
    public static String MAX_SOURCE_TAG = "max_source";
    public static String COLOR_TAG = "color";

    public AbstractSourceMachine(BlockEntityType<?> manaTile, BlockPos pos, BlockState state) {
        super(manaTile, pos, state);
    }

    public AbstractSourceMachine(
            RegistryWrapper<? extends BlockEntityType<?>> manaTile,
            BlockPos pos,
            BlockState state) {
        this(manaTile.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        source = tag.getInt(SOURCE_TAG);
        maxSource = tag.getInt(MAX_SOURCE_TAG);
        color = ParticleColor.fromInt(tag.getInt(COLOR_TAG));
        super.load(tag);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(SOURCE_TAG, getSource());
        tag.putInt(MAX_SOURCE_TAG, getMaxSource());
        tag.putInt(COLOR_TAG, getColor().getColor());
    }

    @Override
    public int setSource(int source) {
        int clamped = Math.max(0, Math.min(source, getMaxSource()));
        if (this.source == clamped) return this.source;
        this.source = clamped;
        sourceChanged();
        return this.source;
    }

    protected void sourceChanged() {
        setChanged();
    }

    @Override
    public int addSource(int source) {
        return this.setSource(this.getSource() + source);
    }

    @Override
    public int getSource() {
        return this.source;
    }

    @Override
    public int removeSource(int source) {
        if (source == 0) return this.getSource();
        this.setSource(this.getSource() - source);
        return this.getSource();
    }

    @Override
    public void setMaxSource(int max) {
        this.maxSource = max;
        sourceChanged();
    }

    @Override
    public int getMaxSource() {
        return maxSource;
    }

    public boolean canAcceptSource() {
        return this.getSource() < this.getMaxSource();
    }

    public boolean canAcceptSource(int source) {
        return this.getSource() + source <= this.getMaxSource();
    }

    /**
     * Transfers the maximum possible amount of source from one tile to another. Takes the maximum
     * transfer rate of the two tiles into account, and the space remaining.
     *
     * @return The amount of source that was transferred.
     */
    public int transferSource(ISourceTile from, ISourceTile to) {
        int transferRate = getTransferRate(from, to);
        from.removeSource(transferRate);
        to.addSource(transferRate);
        return transferRate;
    }

    /** Gets the maximum amount of source that can be transferred from one tile to another. */
    public int getTransferRate(ISourceTile from, ISourceTile to) {
        return Math.min(
                Math.min(from.getTransferRate(), from.getSource()),
                to.getMaxSource() - to.getSource());
    }

    public int transferSource(ISourceTile from, ISourceTile to, int fromTransferRate) {
        int transferRate = getTransferRate(from, to, fromTransferRate);
        if (transferRate == 0) return 0;
        from.removeSource(transferRate);
        to.addSource(transferRate);
        return transferRate;
    }

    public int getTransferRate(ISourceTile from, ISourceTile to, int fromTransferRate) {
        return Math.min(
                Math.min(fromTransferRate, from.getSource()), to.getMaxSource() - to.getSource());
    }

    @Override
    public ParticleColor getColor() {
        return color;
    }

    @Override
    public void setColor(ParticleColor color) {
        this.color = color;
        setChanged();
    }
}
