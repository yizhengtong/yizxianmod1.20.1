package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.item.legacy.LegacyAttackSpeedItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 装备内置攻击速度：不再依赖 COOLDOWN_REDUCTION 属性，按主手物品的除数直接缩短攻击间隔。
 *
 * <p>前置库的 {@code PlayerMovementMixin} 仍会处理其它来源的 COOLDOWN_REDUCTION；
 * 这里只对实现了 {@link LegacyAttackSpeedItem} 的物品额外除一次。</p>
 */
@Mixin(Player.class)
public abstract class LegacyAttackSpeedMixin {

    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("RETURN"), cancellable = true)
    private void yizxianmod$legacyAttackSpeed(CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        ItemStack stack = self.getMainHandItem();
        if (stack.getItem() instanceof LegacyAttackSpeedItem item) {
            cir.setReturnValue(cir.getReturnValue() / item.attackSpeedDivisor());
        }
    }
}
