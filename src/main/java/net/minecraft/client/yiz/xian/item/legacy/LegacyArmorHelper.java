package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * 识别玩家身上由 1.8.9 怀旧盔甲贡献的护甲点。
 *
 * <p>只统计本模组 {@link LegacyArmorMaterial} 的四件套；原版/其他模组盔甲不在此列，
 * 这样混穿时怀旧部分走 1.8 固定减伤，其余部分仍走 1.20.1 公式。</p>
 */
public final class LegacyArmorHelper {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private LegacyArmorHelper() {
    }

    /** 极尽四件套（头盔/胸甲/护腿/靴子）是否齐全。 */
    public static boolean isFullExtremeSet(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof LegacyExtremeArmorItem)) {
                return false;
            }
        }
        return true;
    }

    /** @return 怀旧盔甲提供的护甲点（普通 0..20，极尽 0..24）。 */
    public static int legacyPoints(LivingEntity entity) {
        int total = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem armor
                    && "yizxianmod:legacy_diamond".equals(armor.getMaterial().getName())) {
                // 普通套 3/8/6/3，极尽套 4/9/7/4；两套都算 1.8 固定减伤。
                total += armor.getDefense();
            }
        }
        return total;
    }
}
