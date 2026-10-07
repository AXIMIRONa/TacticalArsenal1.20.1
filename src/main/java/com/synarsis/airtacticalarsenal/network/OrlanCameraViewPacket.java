package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OrlanCameraViewPacket {

    public static final int PATROLLING_CAMERA_CHUNK_RADIUS = 16;

    private final int entityId;
    private final boolean connect;

    public OrlanCameraViewPacket(int entityId, boolean connect) {
        this.entityId = entityId;
        this.connect = connect;
    }

    public OrlanCameraViewPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.connect = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeBoolean(this.connect);
    }

    public static void handle(OrlanCameraViewPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (player.level().getEntity(msg.entityId) instanceof OrlanEntity orlan) {
                if (msg.connect) {
                    orlan.setLinkedPlayer(player.getUUID().toString());
                } else {
                    if (orlan.isLinkedToPlayer(player.getUUID())) {
                        Vec3 physPos = orlan.getCameraPhysicsPos();
                        float physFall = orlan.getCameraPlayerFallDistance();
                        orlan.setLinkedPlayer("");
                    }
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}