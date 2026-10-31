package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * 1.8.9 锋利：每级 +1.25 伤害，1–5 级。
 *
 * <p>1.9 起原版锋利改为 {@code 0.5 + 0.5×等级}（V 级仅 +3），本附魔复刻 1.8 的
 * {@code 1.25×等级}（V 级 +6.25）。只对剑生效，且与 1.8 一样与亡灵杀手/节肢杀手互斥。</p>
 */
public class LegacySharpnessEnchantment extends Enchantment {

    public LegacySharpnessEnchantment() {
        super(Rarity.COMMON, EnchantmentCategory.WEAPON,
            new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 5;
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
    public float getDamageBonus(int level, MobType mobType) {
        return 1.25F * level;
    }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other)
            && other != Enchantments.SHARPNESS
            && other != Enchantments.SMITE
            && other != Enchantments.BANE_OF_ARTHROPODS;
    }

    /** 1.8 的锋利只上剑；斧头不参与。 */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }
}
