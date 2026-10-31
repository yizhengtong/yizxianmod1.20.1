package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeEnderPearlItem;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「极尽末影珍珠」的投掷物理。
 *
 * <p>原版 {@code ThrowableProjectile#tick} 每 tick 先乘 0.99 空气衰减，再扣 0.03 重力。
 * 极尽珍珠在 {@code ThrownEnderpearl#tick} 的 TAIL 做两件事：</p>
 * <ul>
 *   <li>抵消 0.99 的空气衰减（÷0.99）；</li>
 *   <li>再额外乘 1.05，形成持续加速（约 5%/tick），上限 4.0 格/tick。</li>
 * </ul>
 * <p>初速也由物品侧的 {@code getThrowSpeed()} 从 1.5 提到 3.0。</p>
 */
@Mixin(ThrownEnderpearl.class)
public abstract class ExtremeEnderPearlPhysicsMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void yizxianmod$accelerateExtremePearl(CallbackInfo ci) {
        ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;
        if (self.isRemoved() || !(self.getItem().getItem() instanceof LegacyExtremeEnderPearlItem)) {
            return;
        }

        Vec3 movement = self.getDeltaMovement();
        double vertical = movement.y;
        Vec3 horizontal = new Vec3(movement.x, 0.0D, movement.z);
        // 抵消空气衰减 + 5% 加速；只作用于水平方向，重力仍按原版。
        horizontal = horizontal.scale(1.0D / 0.99D * 1.05D);
        double max = 4.0D;
        if (horizontal.length() > max) {
            horizontal = horizontal.normalize().scale(max);
        }
        self.setDeltaMovement(horizontal.x, vertical, horizontal.z);
    }
}
