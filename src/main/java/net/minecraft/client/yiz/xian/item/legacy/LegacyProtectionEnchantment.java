package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * 极尽保护：每级 4.995% 免伤，四件总等级上限 20。
 *
 * <p>20 级 = 99.9%；21 级也按 20 级封顶，仍是 99.9%。</p>
 *
 * <p>由于原版 EPF 只能是整数（每点 4%），4.995% 无法用
 * {@link #getDamageProtection} 表达，所以这里返回 0，
 * 实际减伤由 {@link LegacyProtectionEvents} 在最终伤害阶段处理。</p>
 */
public class LegacyProtectionEnchantment extends Enchantment {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public LegacyProtectionEnchantment() {
        super(Rarity.COMMON, EnchantmentCategory.ARMOR, ARMOR_SLOTS);
    }

    @Override
    public int getMaxLevel() {
        return 20;
    }

    @Override
    public int getMinCost(int level) {
        return 1 + (level - 1) * 11;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

    @Override
    public int getDamageProtection(int level, DamageSource source) {
        return 0;
    }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other)
            && other != Enchantments.ALL_DAMAGE_PROTECTION
            && other != Enchantments.FIRE_PROTECTION
            && other != Enchantments.FALL_PROTECTION
            && other != Enchantments.BLAST_PROTECTION
            && other != Enchantments.PROJECTILE_PROTECTION;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem;
    }
}
