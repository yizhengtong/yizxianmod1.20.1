package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;

/**
 * 1.8.9 钻石镐。
 *
 * <ul>
 *   <li>伤害 6（1.20.1 口径为 1 + 3 + 2；1.8 的「+5 攻击伤害」叠加空手 1 点）</li>
 *   <li>耐久 1562（1.8 off-by-one）</li>
 *   <li>挖掘速度 8、挖掘等级 3（黑曜石）、附魔性 10，均沿用 {@link Tiers#DIAMOND}</li>
 *   <li>无攻击冷却：内置 {@link LegacyAttackSpeedItem}，不再挂 COOLDOWN_REDUCTION 属性</li>
 * </ul>
 */
public class LegacyDiamondPickaxeItem extends PickaxeItem implements LegacyAttackSpeedItem {

    public LegacyDiamondPickaxeItem(Item.Properties properties) {
        super(Tiers.DIAMOND, 2, -2.8F, properties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return 1562;
    }

    @Override
    public float attackSpeedDivisor() {
        return 101.0F;
    }
}
