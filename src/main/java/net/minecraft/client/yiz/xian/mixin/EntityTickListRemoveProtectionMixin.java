package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.core.RemovalGateAuth;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 更新列表移除闸门。
 *
 * <p>把实体从更新列表里摘掉是所有「结构直删」里最致命的一步：实体不再被更新，
 * 挂在更新链路上的自愈逻辑随之全部失效，之后怎么打都没有反抗。</p>
 *
 * <p>引擎自身的正常流程（区块卸载、可见性变化）与本模组调用放行，
 * 外部代码直接摘除本模组实体则拦截。</p>
 */
@Mixin(value = EntityTickList.class, priority = Integer.MAX_VALUE)
public abstract class EntityTickListRemoveProtectionMixin {

    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectTicking(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof YizxianMob)) return;
        if (!((YizxianMob) entity).isRemoveProtected()) return; // 每实例免移除关闭 → 放行更新列表摘除
        if (RemovalGateAuth.isForeignStructureAccess(entity, "net.minecraft.world.level.entity.EntityTickList")) {
            ci.cancel();
        }
    }
}
