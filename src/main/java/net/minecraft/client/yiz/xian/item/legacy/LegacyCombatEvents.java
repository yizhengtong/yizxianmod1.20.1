package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.8.9 怀旧装备的战斗手感。
 *
 * <p>剑格挡：<b>主手持有时常驻 50% 减伤</b>；右键切换「强化格挡」后 75%，再右键切回 50%。
 * 减伤在 {@link LivingHurtEvent}（护甲结算之前）执行，与 1.8 的「先格挡、后护甲」顺序一致。</p>
 *
 * <p>不能改走原版 {@code SHIELD_BLOCK}：{@code LivingEntity#isBlocking()} 一旦识别到该 action，
 * 会按盾牌 100% 免伤。</p>
 */
public final class LegacyCombatEvents {

    private LegacyCombatEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity living = event.getEntity();
        if (living == null || living.level().isClientSide) {
            return;
        }

        ItemStack held = living.getMainHandItem();
        if (!(held.getItem() instanceof LegacyDiamondSwordItem)) {
            return;
        }

        // 1.8 格挡挡不住虚空/kill，也挡不住魔法、火、摔落等 bypasses_shield 伤害。
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) {
            return;
        }

        float factor;
        if (held.getItem() instanceof LegacyExtremeDiamondSwordItem) {
            // 极尽专属：常驻 50%，右键切换后 75%。
            boolean enhanced = living instanceof Player player
                && player.getPersistentData().getBoolean(LegacyDiamondSwordItem.GUARD_TAG);
            factor = enhanced ? 0.25F : 0.5F;
        } else {
            // 普通怀旧剑：严格 1.8，只有正在右键格挡时才 50%。
            if (!living.isUsingItem() || living.getUseItem().getItem() != held.getItem()) {
                return;
            }
            factor = 0.5F;
        }

        event.setAmount(event.getAmount() * factor);
    }
}
