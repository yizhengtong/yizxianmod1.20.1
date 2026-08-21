package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.core.RemovalGateAuth;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 区段容器移除闸门。
 *
 * <p>实体实际存放在按区段划分的容器里，遍历所有区段逐个摘除是绕开一切上层接口的做法。</p>
 *
 * <p>引擎自身的正常流程（区段迁移、区块卸载）与本模组调用放行，
 * 外部代码直接摘除本模组实体则拦截并返回未移除。</p>
 */
@Mixin(value = EntitySection.class, priority = Integer.MAX_VALUE)
public abstract class EntitySectionRemoveProtectionMixin {

    @Inject(method = "remove(Lnet/minecraft/world/level/entity/EntityAccess;)Z",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectSection(EntityAccess entityAccess, CallbackInfoReturnable<Boolean> cir) {
        if (!(entityAccess instanceof YizxianMob)) return;
        Entity entity = (Entity) entityAccess;
        if (RemovalGateAuth.isForeignStructureAccess(entity, "net.minecraft.world.level.entity.EntitySection")) {
            cir.setReturnValue(false);
        }
    }
}
