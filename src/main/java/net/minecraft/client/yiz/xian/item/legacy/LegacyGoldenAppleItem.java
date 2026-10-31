package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 1.8.9 附魔金苹果（严格 1.8 效果）。
 *
 * <p>1.8.9：再生 V 30s（600t）、吸收 I 2:00（2400t）、抗性提升 I 5:00（6000t）、
 * 抗火 5:00（6000t）；饥饿 4、饱和度 9.6、可满饥饿吃、食用 1.6s。<br>
 * 对比 1.20.1：再生 II 20s + 吸收 IV 2:00（由物品属性与 {@code finishUsingItem} 复刻旧值）。</p>
 *
 * <p>效果单独抽到 {@link #applyEffects(LivingEntity)}，供「极尽」变体覆写。</p>
 */
public class LegacyGoldenAppleItem extends Item {

    public LegacyGoldenAppleItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide) {
            applyEffects(entity);
        }
        return result;
    }

    /** 1.8 基础效果；极尽变体覆写这里。 */
    protected void applyEffects(LivingEntity entity) {
        // 1.8 的再生 V：1 点 / 3 tick，30 秒。
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 4));
        // 1.8 的吸收 I：4 点，2 分钟。
        entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 0));
        entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0));
    }

    /** 1.8 的附魔金苹果带附魔光效。 */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
