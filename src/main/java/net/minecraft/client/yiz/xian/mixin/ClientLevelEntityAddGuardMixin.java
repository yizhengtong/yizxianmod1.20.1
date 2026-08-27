package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.yiz.xian.client.ClientPresenceGuard;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端加入抢先闸门 —— 与服务端的新增闸门同一思路，覆盖客户端一侧。
 *
 * <p>客户端拒绝把实体放进世界时，玩家看不见也打不着，观感等同于实体被删。
 * 这里以最高优先级注入入口最前端，对本模组实体直接按原版语义完成加入并终止回调链，
 * 排在后面的否决没有执行机会。</p>
 */
@Mixin(value = ClientLevel.class, priority = Integer.MAX_VALUE)
public abstract class ClientLevelEntityAddGuardMixin {

    @Inject(method = "addEntity(ILnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$guardClientAdd(int id, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof YizxianMob)) return;
        if (!((YizxianMob) entity).isRemoveProtected()) return; // 每实例免移除关闭 → 放行原版客户端加入
        if (ClientPresenceGuard.forceAdd((ClientLevel) (Object) this, id, entity)) {
            ci.cancel();
        }
    }
}
