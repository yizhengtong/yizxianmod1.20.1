package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeDiamondSwordItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code Player#attack} 一开始就清掉目标无敌帧。
 *
 * <p>比只在 {@code hurt} HEAD 清更早，能确保 {@code Player#attack} 内部任何
 * 预检查/分支之前目标已经是可命中状态；只对主手为极尽钻石剑生效，
 * 普通怀旧剑保持 1.8 的无敌帧限制。</p>
 */
@Mixin(Player.class)
public abstract class LegacyPlayerAttackIFramesMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void yizxianmod$resetTargetIFrames(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        if (target instanceof LivingEntity living
                && self.getMainHandItem().getItem() instanceof LegacyExtremeDiamondSwordItem) {
            living.invulnerableTime = 0;
        }
    }
}
