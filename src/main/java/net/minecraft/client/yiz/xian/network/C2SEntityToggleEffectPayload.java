package net.minecraft.client.yiz.xian.network;

import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * C2S: 实体属性编辑工具「保护开关」请求（1.20.1 移植版）。
 * 对目标 YizxianMob 实体开关存在性保护效果（免清除 clear_immunity / 拉回 pullback）。
 * 只对本模组实体生效；普通实体静默忽略。点击即生效（发即改，不随「应用」提交）。
 */
public class C2SEntityToggleEffectPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final int targetId;
    private final String effectKey;
    private final boolean on;

    public C2SEntityToggleEffectPayload(int targetId, String effectKey, boolean on) {
        this.targetId = targetId;
        this.effectKey = effectKey;
        this.on = on;
    }

    public static void encode(C2SEntityToggleEffectPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetId);
        buf.writeUtf(msg.effectKey);
        buf.writeBoolean(msg.on);
    }

    public static C2SEntityToggleEffectPayload decode(FriendlyByteBuf buf) {
        return new C2SEntityToggleEffectPayload(buf.readInt(), buf.readUtf(), buf.readBoolean());
    }

    /** 客户端发送。 */
    public static void send(int targetId, String effectKey, boolean on) {
        NetworkHandler.CHANNEL.sendToServer(new C2SEntityToggleEffectPayload(targetId, effectKey, on));
    }

    /** 服务端接收处理。 */
    public static void handle(C2SEntityToggleEffectPayload payload, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            if (!(ctx.getSender() instanceof ServerPlayer)) return;
            ServerPlayer player = (ServerPlayer) ctx.getSender();
            if (!(player.level().getEntity(payload.targetId) instanceof LivingEntity target)) return;
            if (player.distanceToSqr(target) > 64.0 * 64.0) return;
            // 效果开关只对 YizxianMob 存在性保护有意义
            if (!(target instanceof YizxianMob mob)) return;
            // 归属校验走 setEffect（无主放行 / 操作者==owner / 本模组调用栈 trusted）
            boolean ok = net.minecraft.client.yiz.tool.effect.InstanceEffectState.setEffect(
                mob, player.getUUID(), payload.effectKey, payload.on);
            if (!ok) {
                LOGGER.warn("[ToggleEffect] 拒绝 {} {}={} on={}（归属或鉴权失败）", player.getName().getString(),
                    payload.effectKey, target.getUUID(), payload.on);
                return;
            }
            // 开关变化后刷新注册态：守卫线程注册表(拉回) / agent 免清除 id / SafeLevelCallback
            mob.refreshPresenceRegistration();
        });
        ctx.setPacketHandled(true);
    }
}
