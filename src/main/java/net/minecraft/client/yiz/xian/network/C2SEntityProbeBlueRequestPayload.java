package net.minecraft.client.yiz.xian.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: 探查镜蓝条心跳 —— 客户端打开界面期间周期请求，服务端回 S2CEntityProbeBluePayload。
 */
public class C2SEntityProbeBlueRequestPayload {

    public C2SEntityProbeBlueRequestPayload() {
    }

    public static void encode(C2SEntityProbeBlueRequestPayload msg, FriendlyByteBuf buf) {
    }

    public static C2SEntityProbeBlueRequestPayload decode(FriendlyByteBuf buf) {
        return new C2SEntityProbeBlueRequestPayload();
    }

    public static void handle(C2SEntityProbeBlueRequestPayload msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp != null && sp.containerMenu instanceof net.minecraft.client.yiz.xian.menu.EntityProbeMenu pm) {
                net.minecraft.world.entity.LivingEntity target = pm.equipTargetLivingServer();
                if (target != null) {
                    float cur = net.minecraft.client.yiz.tool.health.ManaTracker.get(target);
                    float max = net.minecraft.client.yiz.tool.health.ManaTracker.getMax(target);
                    NetworkHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                        new S2CEntityProbeBluePayload(cur, max));
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
