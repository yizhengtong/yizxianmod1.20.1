package net.minecraft.client.yiz.xian.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: 实体属性编辑目标同步 —— 服务端打开菜单时把目标实体 id 推给客户端 Screen。
 * 1.20.1 无带数据 openMenu，故用独立 S2C 包传 targetId。
 */
public class S2CEntityTargetPayload {

    private final int targetId;

    public S2CEntityTargetPayload(int targetId) {
        this.targetId = targetId;
    }

    public static void encode(S2CEntityTargetPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetId);
    }

    public static S2CEntityTargetPayload decode(FriendlyByteBuf buf) {
        return new S2CEntityTargetPayload(buf.readInt());
    }

    public static void handle(S2CEntityTargetPayload msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                net.minecraft.client.yiz.xian.client.screen.EntityAttributeEditScreen.onTargetReceived(msg.targetId));
        });
        ctx.setPacketHandled(true);
    }
}
