package net.minecraft.client.yiz.xian.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.xian.menu.LightCompassMenu;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: 光明指南针工作槽操作（1.20.1 移植版）。
 *
 * 工作槽是「只进不出」的自定义槽，客户端无法通过原版 slot 点击修改它（mayPickup/mayPlace=false +
 * Menu.clicked 拦截）。展示栏点击放入 / 左键移除都走本包，由服务端在 player.containerMenu 上改 workContainer，
 * 再由原版容器同步回客户端 + 触发持久化（workContainer.setChanged → persistWorkSlots）。
 */
public class C2SLightCompassWorkSlotPayload {

    public static final int ACTION_ADD = 0;    // 放入：stack 进第一个空工作槽
    public static final int ACTION_REMOVE = 1; // 移除：清空指定 slotIndex 工作槽

    private final int action;
    private final ItemStack stack;
    private final int slotIndex;

    public C2SLightCompassWorkSlotPayload(int action, ItemStack stack, int slotIndex) {
        this.action = action;
        this.stack = stack;
        this.slotIndex = slotIndex;
    }

    public static void encode(C2SLightCompassWorkSlotPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeItem(msg.stack);
        buf.writeVarInt(msg.slotIndex);
    }

    public static C2SLightCompassWorkSlotPayload decode(FriendlyByteBuf buf) {
        return new C2SLightCompassWorkSlotPayload(buf.readVarInt(), buf.readItem(), buf.readVarInt());
    }

    /** 客户端发送：放入物品 */
    public static void sendAdd(ItemStack stack) {
        NetworkHandler.CHANNEL.sendToServer(new C2SLightCompassWorkSlotPayload(ACTION_ADD, stack, -1));
    }

    /** 客户端发送：移除指定工作槽 */
    public static void sendRemove(int slotIndex) {
        NetworkHandler.CHANNEL.sendToServer(new C2SLightCompassWorkSlotPayload(ACTION_REMOVE, ItemStack.EMPTY, slotIndex));
    }

    /** 服务端接收 */
    public static void handle(C2SLightCompassWorkSlotPayload msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            if (!(ctx.getSender() instanceof ServerPlayer)) return;
            ServerPlayer player = ctx.getSender();
            if (!(player.containerMenu instanceof LightCompassMenu)) return;
            LightCompassMenu menu = (LightCompassMenu) player.containerMenu;

            if (msg.action == ACTION_ADD) {
                menu.sendToWorkSlot(msg.stack);
            } else if (msg.action == ACTION_REMOVE) {
                menu.removeFromWorkSlot(msg.slotIndex);
            }
            menu.broadcastChanges();
        });
        ctx.setPacketHandled(true);
    }
}
