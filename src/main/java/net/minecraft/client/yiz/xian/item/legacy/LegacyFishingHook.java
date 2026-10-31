package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.client.yiz.api.KnockbackImmunityRegistry;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 1.8.9 鱼钩实际代码行为（已按 MCP-919 源码核对）：
 *
 * <ul>
 *   <li>命中实体时只把实体设为 caughtEntity（0 伤害攻击），**没有命中击退**；</li>
 *   <li>收竿时才对 caughtEntity 施加拉回速度：</li>
 * </ul>
 *
 * <pre>
 * dx = angler.x - hook.x
 * dy = angler.y - hook.y
 * dz = angler.z - hook.z
 * dist = sqrt(dx² + dy² + dz²)
 * entity.motionX += dx * 0.1
 * entity.motionY += dy * 0.1 + sqrt(dist) * 0.08
 * entity.motionZ += dz * 0.1
 * </pre>
 */
public class LegacyFishingHook extends FishingHook {

    public LegacyFishingHook(Player player, Level level, int luck, int lureSpeed) {
        super(player, level, luck, lureSpeed);
    }

    @Override
    public int retrieve(ItemStack stack) {
        int damage = super.retrieve(stack);
        // 1.8 handleHookRetraction 对 caughtEntity 固定返回 3；1.20.1 对生物返回 5。
        return damage == 5 ? 3 : damage;
    }

    @Override
    protected void pullEntity(Entity entity) {
        Entity owner = getOwner();
        if (!(owner instanceof Player angler)) {
            return;
        }

        double factor = 1.0D;
        if (entity instanceof LivingEntity living) {
            if (KnockbackImmunityRegistry.isImmune(living)) {
                return;
            }
            var inst = living.getAttribute(YizAttributes.KNOCKBACK_IMMUNITY.get());
            if (inst != null && inst.getValue() > 0) {
                factor = Math.max(0.0D, 1.0D - Math.min(1.0D, inst.getValue()));
            }
        }
        if (factor <= 0.0D) {
            return;
        }

        double dx = angler.getX() - getX();
        double dy = angler.getY() - getY();
        double dz = angler.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        entity.setDeltaMovement(entity.getDeltaMovement().add(
            dx * 0.1D * factor,
            (dy * 0.1D + Math.sqrt(distance) * 0.08D) * factor,
            dz * 0.1D * factor));
    }
}
