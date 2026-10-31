package net.minecraft.client.yiz.xian.item.legacy;

/**
 * 怀旧末影珍珠——极尽。
 *
 * <p>初速从 1.5 提到 3.0；飞行中的额外加速/减阻由
 * {@code ExtremeEnderPearlPhysicsMixin} 处理（抵消 0.99 空气衰减 + 5%/tick 加速）。</p>
 */
public class LegacyExtremeEnderPearlItem extends LegacyEnderPearlItem implements LegacyExtremeItem {

    public LegacyExtremeEnderPearlItem(Properties properties) {
        super(properties);
    }

    @Override
    protected float getThrowSpeed() {
        return 3.0F;
    }
}
