package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.core.RemovalGateAuth;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体索引移除闸门。
 *
 * <p>按 id 与 UUID 索引实体的两张表被摘除后，世界层面「查不到这个实体」，
 * 依赖查表的逻辑会一致认为它已经不存在。</p>
 *
 * <p>引擎自身的正常流程与本模组调用放行，外部代码直接摘除本模组实体则拦截。</p>
 */
@Mixin(value = EntityLookup.class, priority = Integer.MAX_VALUE)
public abstract class EntityLookupRemoveProtectionMixin {

    @Inject(method = "remove(Lnet/minecraft/world/level/entity/EntityAccess;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectIndex(EntityAccess entityAccess, CallbackInfo ci) {
        if (!(entityAccess instanceof YizxianMob)) return;
        Entity entity = (Entity) entityAccess;
        if (RemovalGateAuth.isForeignStructureAccess(entity, "net.minecraft.world.level.entity.EntityLookup")) {
            ci.cancel();
        }
    }
}
