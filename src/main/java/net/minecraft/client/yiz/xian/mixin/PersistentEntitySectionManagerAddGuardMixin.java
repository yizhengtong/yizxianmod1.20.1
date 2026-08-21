package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.core.WorldPresenceGuard;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 实体加入世界的抢先闸门 —— 本模组实体的加入不接受任何外部否决。
 *
 * <p>实体加入世界的所有路径最终都汇聚到这里的四个入口。外部只要在这些入口前端返回 false
 * （或取消加入事件），被清除的实体就永远回不来、同类实体也再生成不出来 —— 这比「删掉实体」
 * 更难对抗，因为任何自愈都要经过同一道门。</p>
 *
 * <p>对策是<b>抢先完成</b>：以最高优先级注入入口最前端，对本模组实体直接按原版完整流程
 * 完成加入并给出成功返回值。回调链在此终止，排在后面的否决没有执行机会。
 * 加入事件照常广播给正常监听方，只是不接受其否决结果。</p>
 *
 * <p>只对本模组实体生效，其他实体的加入流程完全不受影响。</p>
 */
@Mixin(value = PersistentEntitySectionManager.class, priority = Integer.MAX_VALUE)
public abstract class PersistentEntitySectionManagerAddGuardMixin {

    @Inject(method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$guardAddEntity(EntityAccess entity, boolean worldGen, CallbackInfoReturnable<Boolean> cir) {
        yizxianmod$forceAdd(entity, worldGen, true, cir);
    }

    @Inject(method = "addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$guardAddNewEntity(EntityAccess entity, CallbackInfoReturnable<Boolean> cir) {
        yizxianmod$forceAdd(entity, false, true, cir);
    }

    @Inject(method = "addEntityWithoutEvent(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void yizxianmod$guardAddEntityWithoutEvent(EntityAccess entity, boolean worldGen,
                                                       CallbackInfoReturnable<Boolean> cir) {
        yizxianmod$forceAdd(entity, worldGen, false, cir);
    }

    @Inject(method = "addNewEntityWithoutEvent(Lnet/minecraft/world/level/entity/EntityAccess;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void yizxianmod$guardAddNewEntityWithoutEvent(EntityAccess entity, CallbackInfoReturnable<Boolean> cir) {
        yizxianmod$forceAdd(entity, false, false, cir);
    }

    private void yizxianmod$forceAdd(EntityAccess entity, boolean worldGen, boolean postEvent,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof YizxianMob)) return;
        if (WorldPresenceGuard.forceAdd(this, entity, worldGen, postEvent)) {
            cir.setReturnValue(true);
        }
    }
}
