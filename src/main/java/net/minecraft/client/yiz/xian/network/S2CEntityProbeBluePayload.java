package net.minecraft.client.yiz.xian.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: 被探查实体蓝量刷新 —— 服务端读 ManaTracker 后回传当前/上限蓝，客户端刷新蓝条。
 */
public class S2CEntityProbeBluePayload {

    private final float current;
    private final float max;

    public S2CEntityProbeBluePayload(float current, float max) {
        this.current = current;
        this.max = max;
    }

    public static void encode(S2CEntityProbeBluePayload msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.current);
        buf.writeFloat(msg.max);
    }

    public static S2CEntityProbeBluePayload decode(FriendlyByteBuf buf) {
        return new S2CEntityProbeBluePayload(buf.readFloat(), buf.readFloat());
    }

    public static void handle(S2CEntityProbeBluePayload msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                net.minecraft.client.yiz.xian.client.screen.EntityProbeScreen.setBlue(msg.current, msg.max));
        });
        ctx.setPacketHandled(true);
    }
}
