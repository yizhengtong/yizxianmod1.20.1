package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.core.RemovalGateAuth;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 区块跟踪移除闸门 —— 比区块缓存层更底层的一道。
 *
 * <p>上层的区块缓存移除只是转调这里，外部拿到这一层的引用就能跳过上层闸门直接摘除跟踪，
 * 实体随即对所有客户端消失。这里补齐同一套鉴权：引擎自身的正常流程（区块卸载、存档写出、
 * 维度流转）与本模组调用照常放行，外部代码直接摘除本模组实体则拦截。</p>
 */
@Mixin(value = ChunkMap.class, priority = Integer.MAX_VALUE)
public abstract class ChunkMapRemoveProtectionMixin {

    @Inject(method = "removeEntity(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectTracking(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof YizxianMob)) return;
        if (!((YizxianMob) entity).isRemoveProtected()) return; // 每实例免移除关闭 → 放行跟踪摘除
        if (RemovalGateAuth.isForeignStructureAccess(entity, "net.minecraft.server.level.ChunkMap")) {
            ci.cancel();
        }
    }
}
