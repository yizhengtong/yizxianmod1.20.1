package net.minecraft.client.yiz.xian.item.legacy;

/**
 * 装备内置挖掘速度配置。
 *
 * <p>不再给物品挂 {@code YizAttributes.MINING_EFFICIENCY} 属性修饰符；
 * {@code LegacyMiningSpeedEvents} 在 {@code PlayerEvent.BreakSpeed} 里按主手物品倍率直接乘速度，
 * 属性面板/提示里不会出现这条加成。</p>
 */
public interface LegacyMiningSpeedItem {

    /** 挖掘速度倍率；3.0 = +200%。 */
    float miningSpeedMultiplier();
}
