package com.hollingsworth.arsnouveau.common.network;

import com.hollingsworth.arsnouveau.client.particle.ClientParticleEffects;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** One trigger for a complete client animation; positions are packed BlockPos longs. */
public record PacketParticlePath(
        BlockPos from, BlockPos to, int color, boolean burst, int despawn) {
    public PacketParticlePath(FriendlyByteBuf buffer) {
        this(
                buffer.readBlockPos(),
                buffer.readBlockPos(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(from);
        buffer.writeBlockPos(to);
        buffer.writeInt(color);
        buffer.writeBoolean(burst);
        buffer.writeVarInt(despawn);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get()
                .enqueueWork(
                        new Runnable() {
                            @Override
                            public void run() {
                                var level = Minecraft.getInstance().level;
                                if (level != null) {
                                    ClientParticleEffects.follow(
                                            level,
                                            from,
                                            to,
                                            ParticleColor.fromInt(color),
                                            burst,
                                            despawn);
                                }
                            }
                        });
        context.get().setPacketHandled(true);
    }
}
