package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 怀旧附魔金苹果——极尽。
 *
 * <ul>
 *   <li>食用速度：2 tick（0.1 秒）</li>
 *   <li>吸收 IV：2:00（2400t）</li>
 *   <li>抗性提升 II：5:00（6000t）</li>
 *   <li>再生 V：60 秒（1200t）</li>
 *   <li>抗火 5:00（6000t）</li>
 * </ul>
 */
public class LegacyExtremeGoldenAppleItem extends LegacyGoldenAppleItem implements LegacyExtremeItem {

    public LegacyExtremeGoldenAppleItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 2;
    }

    @Override
    protected void applyEffects(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 4));
        entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0));
    }
}
