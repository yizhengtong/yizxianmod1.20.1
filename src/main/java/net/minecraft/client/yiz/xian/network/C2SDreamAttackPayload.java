package net.minecraft.client.yiz.xian.network;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SDreamAttackPayload {

    private final int targetId;

    public C2SDreamAttackPayload(int targetId) {
        this.targetId = targetId;
    }

    public static void encode(C2SDreamAttackPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetId);
    }

    public static C2SDreamAttackPayload decode(FriendlyByteBuf buf) {
        return new C2SDreamAttackPayload(buf.readInt());
    }

    public static void send(int targetId) {
        NetworkHandler.CHANNEL.sendToServer(new C2SDreamAttackPayload(targetId));
    }

    public static void handle(C2SDreamAttackPayload payload, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            try {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                Entity entity = player.level().getEntity(payload.targetId);
                if (!(entity instanceof LivingEntity target)) return;
                if (player.distanceToSqr(target) > 64.0 * 64.0) return;
                var inst = player.getAttribute(YizAttributes.FIRST_DREAM.get());
                if (inst != null && inst.getValue() > 0) {
                    EntityASMUtil.applyDreamDamage(player, target, inst.getValue());
                }
                EntityASMUtil.applyPercentDreamDamage(player, target);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable t) {
                // 安全护栏：梦魇攻击包异常只记录，不打断服务端 tick（改血入口已有独立隔离）
                org.slf4j.LoggerFactory.getLogger("yizxianmod")
                    .error("[DreamAttack] 攻击包处理异常，已隔离", t);
            }
        });
        ctx.setPacketHandled(true);
    }
}
