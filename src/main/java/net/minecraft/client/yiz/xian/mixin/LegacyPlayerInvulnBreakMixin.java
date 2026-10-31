package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.item.legacy.LegacyIFrames;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家目标的无敌帧清零（PvP 用）；{@code Player#hurt} 覆盖了 {@code LivingEntity#hurt}，
 * 所以需要单独注入。
 */
@Mixin(Player.class)
public abstract class LegacyPlayerInvulnBreakMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void yizxianmod$legacyPlayerIFrames(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {
        LegacyIFrames.breakFor(source, (Player) (Object) this);
    }
}
