package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.core.EntityRemoveProtection;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ServerChunkCache 移除保护（1.20.1 新增深度防御）。
 *
 * <p>拦截 {@link ServerChunkCache#removeEntity(Entity)} —— 实体从世界存储（chunkMap/EntityLookup）
 * 移除的汇聚点，**绕过 {@code Entity.setRemoved}**（外部模组可直接调它清除辖界者，
 * 或经 Unsafe 直写 removed 字段后由 chunk 层剔除）。对 {@link YizxianMob} 做鉴权：
 * 本模组主动死亡（逻辑血量≤0，EntityRemoveProtection 标记）或本模组包调用放行，
 * 其余（外部模组直接移除辖界者）一律拦截 → 实体无法被外力从世界存储清除。</p>
 *
 * <p>放行判定复用 {@link EntityRemoveProtection#consumeDeathAllow}（本模组死亡监听）
 * 与调用栈鉴权（isYizCaller）。服务器停止/世界卸载由 {@link EntityRemoveProtectionMixin}
 * 的 setRemoved 白名单放行，此处仅兜底外部直接 removeEntity 的路径。</p>
 */
@Mixin(value = ServerChunkCache.class, priority = Integer.MAX_VALUE)
public abstract class ServerChunkCacheRemoveProtectionMixin {

    @Inject(method = "removeEntity(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectRemoveEntity(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof YizxianMob)) return;
        if (entity.level().isClientSide()) return;
        // 本模组主动死亡移除（逻辑血量≤0）放行
        if (EntityRemoveProtection.consumeDeathAllow(entity.getUUID())) return;
        // 服务器停止 / 世界卸载放行
        if (entity.level() instanceof ServerLevel sl && !sl.getServer().isRunning()) return;
        // 本模组包调用（/yiz remove 等）放行
        if (isYizCaller()) return;
        // 外部模组直接移除辖界者 → 拦截
        ci.cancel();
    }

    /** 调用栈第一个决定性帧属于本模组包。 */
    private static boolean isYizCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (cn.equals("net.minecraft.server.level.ServerChunkCache")) continue;
            if (cn.equals("net.minecraft.server.level.ServerLevel")) continue;
            if (cn.equals("net.minecraft.client.yiz.xian.mixin.ServerChunkCacheRemoveProtectionMixin")) continue;
            if (cn.startsWith("net.minecraft.client.yiz")) return true;
            return false;
        }
        return false;
    }
}
