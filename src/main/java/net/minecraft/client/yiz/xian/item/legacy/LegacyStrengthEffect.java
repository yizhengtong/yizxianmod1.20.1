package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 怀旧力量 Buff（严格按 1.6～1.8.9 的乘法公式）。
 *
 * <p>1.6～1.8.9 的 {@code PotionAttackDamage#getAttributeModifierAmount} 返回
 * {@code 1.3 × (amplifier + 1)}，配合 {@code AttributeModifier.Operation.MULTIPLY_TOTAL}
 * （旧版 operation 2）：总伤害 = 原伤害 × (1 + 1.3 × 等级)。</p>
 *
 * <ul>
 *   <li>力量 I：×2.3（+130%）</li>
 *   <li>力量 II：×3.6（+260%）</li>
 *   <li>极尽 III：×4.9（+390%）</li>
 * </ul>
 *
 * <p>1.9 起改回每级 +3 平砍伤害，所以不能用现行的 +3 写法。</p>
 */
public class LegacyStrengthEffect extends MobEffect {

    public LegacyStrengthEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x932423);
        addAttributeModifier(Attributes.ATTACK_DAMAGE,
            "b7e1f0a2-3c4d-4e5f-8a9b-0c1d2e3f4a5b", 1.3D,
            AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
