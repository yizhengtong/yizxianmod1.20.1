package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.xian.network.C2SDreamAttackPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Player.class, priority = Integer.MAX_VALUE)
public abstract class PlayerAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void yizxianmod$onClientAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide()) return;
        if (!(target instanceof LivingEntity)) return;
        var inst = self.getAttribute(YizAttributes.FIRST_DREAM.get());
        if (inst == null || inst.getValue() <= 0) return;
        C2SDreamAttackPayload.send(target.getId());
    }
}
