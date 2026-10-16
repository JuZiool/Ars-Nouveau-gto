package com.hollingsworth.arsnouveau.client.particle;

import com.gregtechceu.gtceu.utils.TaskHandler;
import com.hollingsworth.arsnouveau.common.entity.EntityFollowProjectile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Local animation only: no tracked entity, movement packet, or server tick. */
public final class ClientParticleEffects {
    private ClientParticleEffects() {}

    public static void follow(
            ClientLevel level,
            BlockPos from,
            BlockPos to,
            ParticleColor color,
            boolean burst,
            int despawn) {
        if (!level.hasChunkAt(from) || !level.hasChunkAt(to) || from.equals(to)) return;
        EntityFollowProjectile animation =
                new EntityFollowProjectile(level, from, to, color.toWrapper());
        animation.getEntityData().set(EntityFollowProjectile.SPAWN_TOUCH, burst);
        animation.setDespawnDistance(despawn);
        TaskHandler.enqueueTick(
                level,
                () -> animation.isRemoved() || !level.hasChunkAt(from) || !level.hasChunkAt(to),
                () -> {
                    animation.xOld = animation.getX();
                    animation.yOld = animation.getY();
                    animation.zOld = animation.getZ();
                    animation.tick();
                },
                1,
                0);
    }
}
