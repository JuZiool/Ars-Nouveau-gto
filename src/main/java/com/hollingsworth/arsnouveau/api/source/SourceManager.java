package com.hollingsworth.arsnouveau.api.source;

import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.utils.TaskHandler;
import com.gto.datasynclib.datastream.DataComponentKey;
import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class SourceManager {
    public static final SourceManager INSTANCE = new SourceManager();
    private static final DataComponentKey<LevelSources> SOURCES =
            DataComponentKey.createNoCodec("ars_nouveau:source_index");

    private SourceManager() {}

    private static LevelSources get(Level level) {
        LevelSources sources = ILevel.getCapability(level, SOURCES);
        if (sources == null) {
            sources = new LevelSources(level);
            ILevel.setCapability(level, SOURCES, sources);
        }
        return sources;
    }

    public void addJar(SourceJarTile jar) {
        BlockPos pos = jar.getBlockPos();
        get(jar.getLevel()).jars.put(jar.getSourceProvider(), pos.getX(), pos.getY(), pos.getZ());
    }

    public void removeJar(SourceJarTile jar) {
        LevelSources sources = ILevel.getCapability(jar.getLevel(), SOURCES);
        if (sources != null) sources.jars.remove(jar.getSourceProvider());
    }

    public ArrayList<ISpecialSourceProvider> findJars(
            BlockPos pos, Level level, int range, boolean accepting, int amount) {
        ArrayList<ISpecialSourceProvider> result =
                get(level)
                        .jars
                        .find(
                                pos.getX(),
                                pos.getY(),
                                pos.getZ(),
                                range,
                                provider ->
                                        provider.isValid()
                                                && level.hasChunkAt(provider.getCurrentPos())
                                                && (accepting
                                                        ? provider.getSource().canAcceptSource()
                                                        : provider.getSource().getSource()
                                                                >= amount));
        result.sort(
                (first, second) ->
                        Long.compare(
                                distance(pos, first.getCurrentPos()),
                                distance(pos, second.getCurrentPos())));
        return result;
    }

    private static long distance(BlockPos first, BlockPos second) {
        return Math.abs((long) first.getX() - second.getX())
                + Math.abs((long) first.getY() - second.getY())
                + Math.abs((long) first.getZ() - second.getZ());
    }

    public void addInterface(Level level, ISpecialSourceProvider provider) {
        LevelSources sources = get(level);
        sources.special.add(provider);
        BlockPos pos = provider.getCurrentPos();
        sources.providers.put(provider, pos.getX(), pos.getY(), pos.getZ());
    }

    public Set<ISpecialSourceProvider> getSetForLevel(Level level) {
        return get(level).special;
    }

    public Set<ISpecialSourceProvider> getCopySetForLevel(Level level) {
        return get(level).special.clone();
    }

    private ArrayList<ISpecialSourceProvider> findSpecial(
            BlockPos pos, Level level, int range, Predicate<ISpecialSourceProvider> filter) {
        return get(level)
                .providers
                .find(
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        range,
                        provider ->
                                provider.isValid()
                                        && level.hasChunkAt(provider.getCurrentPos())
                                        && provider.getCurrentPos().closerThan(pos, range)
                                        && filter.test(provider));
    }

    @Nullable
    public ISpecialSourceProvider takeSourceNearby(
            BlockPos pos, Level level, int range, int amount) {
        ISpecialSourceProvider provider = hasSourceNearby(pos, level, range, amount);
        if (provider != null) provider.getSource().removeSource(amount);
        return provider;
    }

    @Nullable
    public ISpecialSourceProvider hasSourceNearby(
            BlockPos pos, Level level, int range, int amount) {
        ArrayList<ISpecialSourceProvider> providers =
                findSpecial(
                        pos, level, range, provider -> provider.getSource().getSource() >= amount);
        return providers.isEmpty() ? null : providers.get(0);
    }

    public List<ISpecialSourceProvider> canGiveSourceNearby(BlockPos pos, Level level, int range) {
        return findSpecial(pos, level, range, provider -> provider.getSource().canAcceptSource());
    }

    public List<ISpecialSourceProvider> canTakeSourceNearby(BlockPos pos, Level level, int range) {
        return findSpecial(pos, level, range, provider -> provider.getSource().getSource() > 0);
    }

    public void tick(Level level) {
        get(level).refreshSpecial();
    }

    private static final class LevelSources {
        private final SourceOctree<ISpecialSourceProvider> jars = new SourceOctree<>();
        private final SourceOctree<ISpecialSourceProvider> providers = new SourceOctree<>();
        private final ReferenceOpenHashSet<ISpecialSourceProvider> special =
                new ReferenceOpenHashSet<>();

        private LevelSources(Level level) {
            TaskHandler.enqueueTick(level, this::refreshSpecial, 1, 0);
        }

        private void refreshSpecial() {
            var iterator = special.iterator();
            while (iterator.hasNext()) {
                ISpecialSourceProvider provider = iterator.next();
                if (!provider.isValid()) {
                    providers.remove(provider);
                    iterator.remove();
                } else {
                    BlockPos pos = provider.getCurrentPos();
                    providers.put(provider, pos.getX(), pos.getY(), pos.getZ());
                }
            }
        }
    }
}
