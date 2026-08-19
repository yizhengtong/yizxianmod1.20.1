package net.minecraft.client.yiz.xian.network;

import net.minecraft.client.yiz.xian.core.LightCompassData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: 光明指南针工作槽内容同步（1.20.1 移植版）。
 *
 * <p>服务端 Menu 在工作槽变更时把 3 个槽的物品注册表 ID（-1 空槽）推给客户端，
 * 客户端写入 {@link LightCompassData} 缓存，供 {@code LightCompassHighlightRenderer}
 * 扫描时读取（替代 1.21.1 的 PlayerDataAPI 附件同步）。</p>
 */
public class S2CLightCompassSlotsPayload {

    private final int[] slots;

    public S2CLightCompassSlotsPayload(int[] slots) {
        this.slots = slots;
    }

    public static void encode(S2CLightCompassSlotsPayload msg, FriendlyByteBuf buf) {
        int n = msg.slots != null ? msg.slots.length : 0;
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeVarInt(msg.slots[i]);
        }
    }

    public static S2CLightCompassSlotsPayload decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        int[] slots = new int[n];
        for (int i = 0; i < n; i++) {
            slots[i] = buf.readVarInt();
        }
        return new S2CLightCompassSlotsPayload(slots);
    }

    /** 客户端接收处理。 */
    public static void handle(S2CLightCompassSlotsPayload msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> LightCompassData.setClientSlots(msg.slots));
        ctx.setPacketHandled(true);
    }

    /** 服务端发送给指定玩家。 */
    public static void send(ServerPlayer player, int[] slots) {
        NetworkHandler.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new S2CLightCompassSlotsPayload(slots));
    }
}
