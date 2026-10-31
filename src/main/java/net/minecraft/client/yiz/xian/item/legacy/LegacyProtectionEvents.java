package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 极尽保护的最终伤害减免：
 * 四件护甲等级总和 × 4.995%，总和封顶 20 级 → 99.9%。
 * 21 级及以上同样按 20 级计算。
 */
public final class LegacyProtectionEvents {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        int totalLevels = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            totalLevels += EnchantmentHelper.getItemEnchantmentLevel(
                YizxianMod.LEGACY_PROTECTION.get(), stack);
        }
        if (totalLevels <= 0) {
            return;
        }

        int effectiveLevels = Math.min(20, totalLevels);
        double reduction = Math.min(0.999D, effectiveLevels * 0.04995D);
        event.setAmount((float) (event.getAmount() * (1.0D - reduction)));
    }
}
