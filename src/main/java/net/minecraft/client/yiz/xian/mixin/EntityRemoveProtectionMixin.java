package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.core.EntityRemoveProtection;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体移除保护（1.20.1 移植版）— 本模组实体（YizxianMob）的移除总闸门。
 *
 * <p>拦截 {@link Entity#setRemoved(RemovalReason)}（1.20.1 所有移除的最终汇聚点）。
 * 白名单放行：服务器停止/世界保存、本模组死亡监听（EntityRemoveProtection）、本模组包调用。
 * 其余移除一律拦截 → 辖界者无法被外力移除。</p>
 */
@Mixin(Entity.class)
public abstract class EntityRemoveProtectionMixin {

    @Inject(method = "setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof YizxianMob)) return;
        if (self.level().isClientSide()) return;
        if (shouldAllowRemove((YizxianMob) self)) return;
        ci.cancel();
    }

    private boolean shouldAllowRemove(YizxianMob mob) {
        if (mob.level() instanceof ServerLevel sl && !sl.getServer().isRunning()) return true;
        if (EntityRemoveProtection.consumeDeathAllow(mob.getUUID())) return true;
        return isYizCaller();
    }

    /** 调用栈第一个决定性帧属于本模组包（net.minecraft.client.yiz，前置库+下游共用根）。 */
    private static boolean isYizCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (cn.equals("net.minecraft.world.entity.Entity")) continue;
            if (cn.equals("net.minecraft.client.yiz.xian.mixin.EntityRemoveProtectionMixin")) continue;
            if (cn.startsWith("net.minecraft.client.yiz")) return true;
            return false;
        }
        return false;
    }
}
