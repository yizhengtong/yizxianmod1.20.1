package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 受击红闪门控（1.20.1 移植版）— 拦截 {@link ServerLevel#broadcastDamageEvent}，
 * 只放行本模组传导扣血流程的红闪，拦截外部模组绕过 hurt() 直接广播的红闪。
 */
@Mixin(value = ServerLevel.class, priority = Integer.MAX_VALUE)
public abstract class ServerLevelDamageFlashProtectionMixin {

    @Inject(method = "broadcastDamageEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$gateDamageFlash(Entity entity, DamageSource damageSource, CallbackInfo ci) {
        if (!(entity instanceof YizxianMob)) return;
        if (YizxianMob.isConductionHitFlash()) return;
        ci.cancel();
    }
}
