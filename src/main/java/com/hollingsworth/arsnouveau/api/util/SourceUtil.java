package com.hollingsworth.arsnouveau.api.util;

import com.hollingsworth.arsnouveau.ArsNouveau;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.client.particle.ParticleUtil;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArsNouveau.MODID)
public class SourceUtil {

    public static List<ISpecialSourceProvider> canGiveSource(BlockPos pos, Level world, int range) {
        ArrayList<ISpecialSourceProvider> posList =
                SourceManager.INSTANCE.findJars(pos, world, range, true, 0);
        List<ISpecialSourceProvider> provider =
                SourceManager.INSTANCE.canGiveSourceNearby(pos, world, range);
        for (ISpecialSourceProvider p : provider) {
            posList.add(p);
        }
        return posList;
    }

    public static List<ISpecialSourceProvider> canTakeSource(BlockPos pos, Level world, int range) {
        ArrayList<ISpecialSourceProvider> posList =
                SourceManager.INSTANCE.findJars(pos, world, range, false, 1);
        List<ISpecialSourceProvider> provider =
                SourceManager.INSTANCE.canTakeSourceNearby(pos, world, range);
        for (ISpecialSourceProvider p : provider) {
            posList.add(p);
        }
        return posList;
    }

    public static @Nullable ISpecialSourceProvider takeSource(
            BlockPos pos, Level level, int range, int source) {
        // Prefer indexed providers; only scan compatibility sources when neither index suffices.
        int minimum = Math.max(1, source);
        ArrayList<ISpecialSourceProvider> jars =
                SourceManager.INSTANCE.findJars(pos, level, range, false, minimum);
        ISpecialSourceProvider indexed =
                jars.isEmpty()
                        ? SourceManager.INSTANCE.hasSourceNearby(pos, level, range, minimum)
                        : jars.get(0);
        if (indexed != null) {
            indexed.getSource().removeSource(source);
            return indexed;
        }
        List<ISpecialSourceProvider> providers = canTakeSource(pos, level, range);
        for (ISpecialSourceProvider provider : providers) {
            if (provider.getSource().getSource() >= source) {
                provider.getSource().removeSource(source);
                return provider;
            }
        }
        return null;
    }

    public static @Nullable ISpecialSourceProvider takeSourceWithParticles(
            BlockPos pos, Level level, int range, int source) {
        ISpecialSourceProvider result = takeSource(pos, level, range, source);
        if (result != null) {
            ParticleUtil.spawnFollowProjectile(level, result.getCurrentPos(), pos);
        }
        return result;
    }

    /**
     * Searches for nearby mana jars that have enough mana. Returns the position where the source
     * was taken, or null if none were found.
     */
    public static boolean hasSourceNearby(BlockPos pos, Level world, int range, int source) {
        if (!SourceManager.INSTANCE.findJars(pos, world, range, false, source).isEmpty()) {
            return true;
        }
        return SourceManager.INSTANCE.hasSourceNearby(pos, world, range, source) != null;
    }
}
