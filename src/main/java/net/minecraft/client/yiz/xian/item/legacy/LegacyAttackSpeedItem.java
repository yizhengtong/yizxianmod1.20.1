package net.minecraft.client.yiz.xian.item.legacy;

/**
 * 装备内置攻击速度配置。
 *
 * <p>不再给物品挂 {@code YizAttributes.COOLDOWN_REDUCTION} 属性修饰符；
 * {@code LegacyAttackSpeedMixin} 直接按主手物品的 {@link #attackSpeedDivisor()} 缩短攻击间隔，
 * 因此属性面板/提示里不会出现这条加成。</p>
 */
public interface LegacyAttackSpeedItem {

    /** 攻击间隔除数；101 约等于无冷却。 */
    float attackSpeedDivisor();
}
