package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.item.legacy.LegacyIFrames;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 非玩家目标（僵尸、骷髅、自定义生物等）的无敌帧清零。
 * 玩家目标由 {@link LegacyPlayerInvulnBreakMixin} 处理。
 */
@Mixin(LivingEntity.class)
public abstract class LegacyInvulnBreakMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void yizxianmod$legacyIFrames(DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        LegacyIFrames.breakFor(source, (LivingEntity) (Object) this);
    }
}
