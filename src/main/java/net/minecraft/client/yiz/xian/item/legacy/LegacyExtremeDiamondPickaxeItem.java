package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.item.ItemStack;

/**
 * 怀旧钻石镐——极尽：耐久 ×10 + Unbreakable，并额外获得 200% 挖掘效率。
 *
 * <p>挖掘速度走物品内置的 {@link LegacyMiningSpeedItem}（倍率 3.0），
 * 由 {@code LegacyMiningSpeedEvents} 在 {@code BreakSpeed} 里直接乘；
 * 不再挂 {@code MINING_EFFICIENCY} 属性，属性面板/提示里不会显示。</p>
 */
public class LegacyExtremeDiamondPickaxeItem extends LegacyDiamondPickaxeItem
        implements LegacyExtremeItem, LegacyMiningSpeedItem {

    public LegacyExtremeDiamondPickaxeItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return 15620;
    }

    @Override
    public float miningSpeedMultiplier() {
        return 3.0F;
    }
}
