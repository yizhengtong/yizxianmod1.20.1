package net.minecraft.client.yiz.xian.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: 实体探查目标同步 —— 服务端 openMenu 后把被探查目标实体 id 推给客户端 Screen。
 * 1.20.1 无带数据 openMenu，故用独立 S2C 包传 targetId。
 */
public class S2CEntityProbeTargetPayload {

    private final int targetId;

    public S2CEntityProbeTargetPayload(int targetId) {
        this.targetId = targetId;
    }

    public static void encode(S2CEntityProbeTargetPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetId);
    }

    public static S2CEntityProbeTargetPayload decode(FriendlyByteBuf buf) {
        return new S2CEntityProbeTargetPayload(buf.readInt());
    }

    public static void handle(S2CEntityProbeTargetPayload msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Menu（A6 装备槽渲染/解析用）与 Screen（血条等渲染用）都记录目标 id
                net.minecraft.client.yiz.xian.menu.EntityProbeMenu.onTargetReceived(msg.targetId);
                net.minecraft.client.yiz.xian.client.screen.EntityProbeScreen.onTargetReceived(msg.targetId);
            });
        });
        ctx.setPacketHandled(true);
    }
}
