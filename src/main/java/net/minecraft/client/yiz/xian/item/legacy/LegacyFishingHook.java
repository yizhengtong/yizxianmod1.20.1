package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.8.9 鱼饵：命中实体时把实体向外击退；收回时沿用原版 {@code retrieve -> pullEntity} 拉回。
 *
 * <p>拉回路径会经过库的 {@code FishingHookKnockbackMixin}，所以极尽套的击退免疫同样生效。</p>
 */
public class LegacyFishingHook extends FishingHook {

    public LegacyFishingHook(Player player, Level level, int luck, int lureSpeed) {
        super(player, level, luck, lureSpeed);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide()) {
            return;
        }

        Entity entity = result.getEntity();
        if (entity instanceof LivingEntity living && getOwner() instanceof Player player) {
            Vec3 dir = new Vec3(living.getX() - player.getX(), 0.0D, living.getZ() - player.getZ());
            if (dir.lengthSqr() > 1.0E-4D) {
                dir = dir.normalize().scale(0.4D);
                living.push(dir.x, 0.0D, dir.z);
            }
        }
    }
}
